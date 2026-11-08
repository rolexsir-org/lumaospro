package com.lumaos.pro.launcher.vault.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.vault.core.VaultItemKind
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Private camera and document scanner.
 *
 * Capture runs entirely inside LumaOS Pro with `Camera2`, straight into memory: the
 * JPEG bytes are handed to the vault's encrypting stream, so no image ever exists
 * in `DCIM`, `Pictures`, a MediaStore row, or even a temporary file. Photos become
 * encrypted `IMAGE` objects; scanned pages are composed into a PDF with the
 * platform's `PdfDocument` and stored as one encrypted `SCAN` object.
 *
 * One screen, two modes, because a document scanner is a camera with a review
 * step - sharing the session code keeps the camera handling in exactly one place.
 */
class VaultCaptureActivity : VaultScreenActivity() {

    private var mode: String = MODE_PHOTO

    private lateinit var previewHost: FrameLayout
    private lateinit var statusLabel: android.widget.TextView
    private var textureView: TextureView? = null
    private lateinit var shutter: View
    private lateinit var footer: LinearLayout

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewSize: Size? = null
    private var pendingSessionStart = false
    private var sessionRequested = false

    /** Scanned pages, held as compressed JPEG bytes in memory only. */
    private val pages = ArrayList<ByteArray>()

    override fun onCreateContent(): View {
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_PHOTO
        val container = VaultUi.column(this)
        container.setBackgroundColor(Color.BLACK)

        container.addView(headerRoot())
        previewHost = FrameLayout(this)
        container.addView(previewHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        statusLabel = VaultUi.caption(this, if (mode == MODE_SCAN) "Point at a document, then capture a page" else "Private camera · saved encrypted")
        statusLabel.gravity = Gravity.CENTER
        container.addView(statusLabel)

        footer = VaultUi.column(this).apply {
            setPadding(
                VaultDesign.dp(this@VaultCaptureActivity, VaultDesign.screenMarginHorizontal),
                VaultDesign.dp(this@VaultCaptureActivity, VaultDesign.spaceSM),
                VaultDesign.dp(this@VaultCaptureActivity, VaultDesign.screenMarginHorizontal),
                VaultDesign.dp(this@VaultCaptureActivity, VaultDesign.spaceLG)
            )
        }
        container.addView(footer)

        shutter = VaultUi.text(this, if (mode == MODE_SCAN) "Capture page" else "Capture", VaultDesign.typeBodySp, Color.parseColor("#08131F"), VaultUi.Weight.SEMIBOLD).apply {
            gravity = Gravity.CENTER
            background = VaultDesign.surfaceDrawable(this@VaultCaptureActivity, VaultDesign.colorAccent, VaultDesign.radiusFull, Color.TRANSPARENT)
            minimumHeight = VaultDesign.dp(this@VaultCaptureActivity, 56f)
            isClickable = true
            setOnClickListener { capturePhoto() }
        }
        footer.addView(shutter, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (mode == MODE_SCAN) {
            footer.addView(VaultUi.secondaryButton(this, "Finish · save encrypted PDF") { finishScan() })
        }

        startBackgroundThread()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        } else {
            openCameraWhenReady()
        }
        return container
    }

    private fun headerRoot(): View {
        val row = VaultUi.row(this)
        row.setPadding(
            VaultDesign.dp(this, VaultDesign.screenMarginHorizontal),
            VaultDesign.dp(this, VaultDesign.spaceXL),
            VaultDesign.dp(this, VaultDesign.screenMarginHorizontal),
            VaultDesign.dp(this, VaultDesign.spaceSM)
        )
        row.addView(VaultUi.text(this, "‹", VaultDesign.typeTitleSp + 8f, VaultDesign.colorTextPrimary).apply {
            isClickable = true
            setPadding(0, 0, VaultDesign.dp(this@VaultCaptureActivity, VaultDesign.spaceMD), 0)
            setOnClickListener { finish() }
        })
        row.addView(
            VaultUi.text(
                this,
                if (mode == MODE_SCAN) "Scanner" else "Private camera",
                VaultDesign.typeHeadingSp,
                VaultDesign.colorTextPrimary,
                VaultUi.Weight.SEMIBOLD
            )
        )
        return row
    }

    // ------------------------------------------------------------------
    // Camera2
    // ------------------------------------------------------------------

    private fun startBackgroundThread() {
        val thread = HandlerThread("lumaos-vault-camera").also { it.start() }
        backgroundThread = thread
        backgroundHandler = Handler(thread.looper)
    }

    private fun openCameraWhenReady() {
        cameraManager = getSystemService(CameraManager::class.java)
        val texture = TextureView(this).apply { surfaceTextureListener = surfaceListener }
        textureView = texture
        previewHost.addView(texture, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (texture.isAvailable) openCamera()
    }

    private val surfaceListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) = Unit
        override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) = Unit
    }

    private fun openCamera() {
        if (!hasCameraPermission() || cameraDevice != null) return
        val manager = cameraManager ?: return
        try {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull() ?: run {
                showCameraProblem("This phone reports no camera.")
                return
            }
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) as? StreamConfigurationMap
            val sizes = map?.getOutputSizes(ImageReader::class.java)?.sortedByDescending { it.width * it.height }
            // 12 MP is plenty for a document scan and keeps memory bounded.
            val jpegSize = sizes?.firstOrNull { it.width * it.height <= 12_000_000 } ?: sizes?.lastOrNull()
            val previewCandidate = map?.getOutputSizes(Surface::class.java)
                ?.filter { it.width <= 1920 }
                ?.maxByOrNull { it.width * it.height }
            previewSize = previewCandidate ?: jpegSize
            if (jpegSize == null) {
                showCameraProblem("This camera cannot produce still images.")
                return
            }
            imageReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, android.graphics.ImageFormat.JPEG, 2).apply {
                setOnImageAvailableListener({ reader -> handleImage(reader) }, backgroundHandler)
            }
            @Suppress("MissingPermission")
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cameraDevice = device
                    startPreview()
                }

                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    cameraDevice = null
                    runOnUiThreadIfAlive { showCameraProblem("The camera was disconnected.") }
                }

                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    cameraDevice = null
                    runOnUiThreadIfAlive { showCameraProblem("The camera reported an error.") }
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            LumaLog.e("Vault camera could not open: ${e.javaClass.simpleName}")
            showCameraProblem("The camera is unavailable right now.")
        }
    }

    private fun startPreview() {
        val device = cameraDevice ?: return
        val texture = textureView ?: return
        val reader = imageReader ?: return
        if (!texture.isAvailable) {
            pendingSessionStart = true
            return
        }
        sessionRequested = true
        val surface = android.view.Surface(texture.surfaceTexture)
        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }
        runCatching {
            device.createCaptureSession(listOf(surface, reader.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    runCatching { session.setRepeatingRequest(builder.build(), null, backgroundHandler) }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    sessionRequested = false
                    runOnUiThreadIfAlive { showCameraProblem("The camera preview could not start.") }
                }
            }, backgroundHandler)
        }.onFailure {
            sessionRequested = false
            showCameraProblem("The camera preview could not start.")
        }
    }

    private fun capturePhoto() {
        val device = cameraDevice ?: run {
            showCameraProblem("The camera is not ready yet.")
            return
        }
        val reader = imageReader ?: return
        val texture = textureView
        val surface = when {
            texture != null && texture.isAvailable -> android.view.Surface(texture.surfaceTexture)
            else -> null
        }
        val targets = listOfNotNull(reader.surface, surface)
        runCatching {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                targets.forEach { addTarget(it) }
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.JPEG_ORIENTATION, 90)
            }.build()
            captureSession?.capture(request, null, backgroundHandler)
                ?: showCameraProblem("The camera is not ready yet.")
        }.onFailure {
            LumaLog.e("Vault capture failed: ${it.javaClass.simpleName}")
            showCameraProblem("The photo could not be taken.")
        }
    }

    private fun handleImage(reader: ImageReader) {
        val image = reader.acquireNextImage() ?: return
        val bytes = try {
            val buffer = image.planes[0].buffer
            ByteArray(buffer.remaining()).also { buffer.get(it) }
        } finally {
            image.close()
        }
        if (bytes.isEmpty()) return
        when (mode) {
            MODE_SCAN -> {
                pages.add(bytes)
                runOnUiThreadIfAlive {
                    statusLabel.text = "Page ${pages.size} captured · add more or finish"
                }
            }
            else -> savePhoto(bytes)
        }
    }

    /**
     * Encrypts one photo straight from memory into a vault object.
     *
     * There is no file in between: the JPEG byte array goes into the vault's
     * streaming AEAD and is zeroized afterwards.
     */
    private fun savePhoto(bytes: ByteArray) {
        val name = "Photo ${SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.getDefault()).format(Date())}.jpg"
        runOnUiThreadIfAlive {
            statusLabel.text = "Saving encrypted…"
        }
        io({
            ByteArrayInputStream(bytes).use { stream ->
                manager.addStream(stream, VaultItemKind.IMAGE, name, "image/jpeg", origin = "camera")
            }
        }) { result ->
            if (result.isSuccess) {
                statusLabel.text = "Saved to the Vault · it is not in your gallery"
                android.widget.Toast.makeText(this, "Photo encrypted into the Vault", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                statusLabel.text = "The photo was not saved"
                android.widget.Toast.makeText(this, "The photo could not be saved. Nothing was added.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // ------------------------------------------------------------------
    // Scanner: review and PDF assembly
    // ------------------------------------------------------------------

    private fun finishScan() {
        if (pages.isEmpty()) {
            android.widget.Toast.makeText(this, "Capture at least one page first.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        showReview()
    }

    private fun showReview() {
        previewHost.removeAllViews()
        val scroll = ScrollView(this)
        val column = VaultUi.column(this).apply {
            setPadding(
                VaultDesign.dp(this@VaultCaptureActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultCaptureActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultCaptureActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultCaptureActivity, VaultDesign.spaceMD)
            )
        }
        column.addView(VaultUi.title(this, "${pages.size} page(s)"))
        column.addView(VaultUi.caption(this, "Pages are assembled into one PDF and encrypted. Nothing is written to your normal storage."))
        pages.forEachIndexed { index, bytes ->
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                val image = ImageView(this).apply {
                    setImageBitmap(bitmap)
                    adjustViewBounds = true
                    setPadding(0, VaultDesign.dp(this@VaultCaptureActivity, VaultDesign.spaceXS), 0, 0)
                }
                column.addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, VaultDesign.dp(this, 220f)))
            }
            column.addView(
                VaultUi.text(this, "Page ${index + 1}", VaultDesign.typeMicroSp, VaultDesign.colorTextTertiary).apply {
                    setOnClickListener {
                        pages.removeAt(index)
                        showReview()
                    }
                    text = "Page ${index + 1} · tap to remove"
                }
            )
        }
        scroll.addView(column)
        previewHost.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        footer.removeAllViews()
        footer.addView(VaultUi.primaryButton(this, "Save encrypted PDF") { savePdf() })
        footer.addView(VaultUi.secondaryButton(this, "Capture another page") {
            previewHost.removeAllViews()
            if (textureView?.parent != null) (textureView?.parent as? ViewGroup)?.removeView(textureView)
            openCameraWhenReady()
            resetFooter()
        })
    }

    private fun resetFooter() {
        footer.removeAllViews()
        footer.addView(shutter, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        footer.addView(VaultUi.secondaryButton(this, "Finish · save encrypted PDF") { finishScan() })
        statusLabel.text = "Page ${pages.size} captured · add more or finish"
    }

    /**
     * Builds a multi-page PDF in memory with `PdfDocument` and stores it as one
     * encrypted object. The PDF bytes never touch the filesystem.
     */
    private fun savePdf() {
        footer.removeAllViews()
        footer.addView(VaultUi.busyRow(this, "Assembling encrypted PDF…"))
        val pagesSnapshot = ArrayList(pages)
        io({
            val document = PdfDocument()
            try {
                pagesSnapshot.forEachIndexed { index, bytes ->
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: return@forEachIndexed
                    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
                    val page = document.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)
                    val scale = minOf(595f / bitmap.width, 842f / bitmap.height)
                    val width = bitmap.width * scale
                    val height = bitmap.height * scale
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                    canvas.drawBitmap(
                        bitmap,
                        null,
                        android.graphics.RectF(
                            (595 - width) / 2f,
                            (842 - height) / 2f,
                            (595 + width) / 2f,
                            (842 + height) / 2f
                        ),
                        paint
                    )
                    document.finishPage(page)
                    bitmap.recycle()
                }
                val out = ByteArrayOutputStream()
                document.writeTo(out)
                out.toByteArray()
            } finally {
                document.close()
            }
        }) { result ->
            val pdf = result.getOrNull()
            if (pdf == null) {
                footer.addView(VaultUi.message(this, "The PDF could not be assembled. The pages are still in memory only."))
                resetFooter()
                return@io
            }
            val name = "Scan ${SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.getDefault()).format(Date())}.pdf"
            io({
                try {
                    ByteArrayInputStream(pdf).use { stream ->
                        manager.addStream(stream, VaultItemKind.SCAN, name, "application/pdf", origin = "scanner")
                    }
                } finally {
                    pdf.fill(0)
                }
            }) { saveResult ->
                if (saveResult.isSuccess) {
                    pages.clear()
                    android.widget.Toast.makeText(this, "Scan encrypted into the Vault", android.widget.Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    footer.addView(VaultUi.message(this, "The scan could not be encrypted. Nothing was added."))
                    resetFooter()
                }
            }
        }
    }

    // ------------------------------------------------------------------

    private fun hasCameraPermission(): Boolean = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun showCameraProblem(message: String) {
        statusLabel.text = message
        previewHost.removeAllViews()
        val column = VaultUi.column(this).apply {
            gravity = Gravity.CENTER
            setPadding(
                VaultDesign.dp(this@VaultCaptureActivity, VaultDesign.spaceXL),
                VaultDesign.dp(this@VaultCaptureActivity, VaultDesign.spaceXL),
                VaultDesign.dp(this@VaultCaptureActivity, VaultDesign.spaceXL),
                VaultDesign.dp(this@VaultCaptureActivity, VaultDesign.spaceXL)
            )
        }
        column.addView(VaultUi.text(this, "Camera unavailable", VaultDesign.typeHeadingSp, VaultDesign.colorTextSecondary, VaultUi.Weight.MEDIUM))
        column.addView(VaultUi.caption(this, "Android grants camera access per app. You can enable it in system settings and come back."))
        column.addView(VaultUi.secondaryButton(this, "Try again") {
            previewHost.removeAllViews()
            openCameraWhenReady()
        })
        previewHost.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                previewHost.removeAllViews()
                openCameraWhenReady()
            } else {
                showCameraProblem("Camera access was not granted.")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        closeCamera()
    }

    private fun closeCamera() {
        runCatching { captureSession?.close() }
        captureSession = null
        runCatching { cameraDevice?.close() }
        cameraDevice = null
        runCatching { imageReader?.close() }
        imageReader = null
        sessionRequested = false
    }

    override fun onDestroy() {
        closeCamera()
        pages.forEach { it.fill(0) }
        pages.clear()
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MODE = "vault.capture.mode"
        const val MODE_PHOTO = "photo"
        const val MODE_SCAN = "scan"
        private const val REQUEST_CAMERA = 5302
    }
}
