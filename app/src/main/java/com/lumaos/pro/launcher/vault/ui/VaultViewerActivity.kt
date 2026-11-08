package com.lumaos.pro.launcher.vault.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.vault.core.VaultFiles
import com.lumaos.pro.launcher.vault.core.VaultItem
import com.lumaos.pro.launcher.vault.core.VaultItemKind
import com.lumaos.pro.launcher.vault.platform.VaultMediaDataSource
import com.lumaos.pro.launcher.vault.platform.VaultTransientFiles
import java.io.File

/**
 * Full-screen private viewer for images, video, audio and scanned PDFs.
 *
 * Media is decrypted through a seekable, chunk-authenticated reader, so playback
 * needs no plaintext file. The one exception is PDF rendering, because the
 * platform's `PdfRenderer` insists on a file descriptor: that single case uses a
 * scratch file inside the vault's own staging directory, which is shredded when
 * this screen closes and again on lock.
 */
class VaultViewerActivity : VaultScreenActivity() {

    private var itemId: String = ""
    private var item: VaultItem? = null
    private var player: MediaPlayer? = null
    private var transientPdf: File? = null
    private var renderer: PdfRenderer? = null
    private var pdfDescriptor: ParcelFileDescriptor? = null
    private var pdfPage = 0

    private lateinit var stage: FrameLayout
    private lateinit var chrome: LinearLayout

    override fun onCreateContent(): View {
        itemId = intent.getStringExtra(EXTRA_ID) ?: ""
        item = manager.item(itemId)

        val rootColumn = VaultUi.column(this)
        rootColumn.setBackgroundColor(Color.BLACK)

        stage = FrameLayout(this)
        rootColumn.addView(stage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        chrome = VaultUi.column(this).apply {
            setBackgroundColor(0xE604060A.toInt())
            setPadding(
                VaultDesign.dp(this@VaultViewerActivity, VaultDesign.screenMarginHorizontal),
                VaultDesign.dp(this@VaultViewerActivity, VaultDesign.spaceSM),
                VaultDesign.dp(this@VaultViewerActivity, VaultDesign.screenMarginHorizontal),
                VaultDesign.dp(this@VaultViewerActivity, VaultDesign.spaceMD)
            )
        }
        rootColumn.addView(chrome)

        val current = item
        if (current == null) {
            chrome.addView(VaultUi.message(this, "This item is no longer in the Vault."))
            return rootColumn
        }
        renderChrome(current)
        renderStage(current)
        return rootColumn
    }

    private fun renderChrome(current: VaultItem) {
        val title = VaultUi.text(this, current.name, VaultDesign.typeHeadingSp, VaultDesign.colorTextPrimary, VaultUi.Weight.MEDIUM)
        chrome.addView(title)
        chrome.addView(
            VaultUi.caption(
                this,
                "${current.kind.name.lowercase().replaceFirstChar { it.uppercase() }} · " +
                    "${VaultActions.formatBytes(current.sizeBytes)} · encrypted at rest"
            )
        )
        val actions = VaultUi.row(this).apply {
            setPadding(0, VaultDesign.dp(this@VaultViewerActivity, VaultDesign.spaceSM), 0, 0)
        }
        actions.addView(actionChip("Close") { finish() })
        actions.addView(actionChip("Export") { startExport(current) })
        actions.addView(actionChip("Delete") {
            VaultActions.confirmDelete(this, manager, current) { finish() }
        })
        chrome.addView(actions)
    }

    private fun actionChip(label: String, onClick: () -> Unit): View {
        val chip = VaultUi.text(this, label, VaultDesign.typeCaptionSp, VaultDesign.colorTextPrimary, VaultUi.Weight.MEDIUM)
        chip.isClickable = true
        chip.setPadding(
            VaultDesign.dp(this, VaultDesign.spaceMD),
            VaultDesign.dp(this, VaultDesign.spaceXS),
            VaultDesign.dp(this, VaultDesign.spaceMD),
            VaultDesign.dp(this, VaultDesign.spaceXS)
        )
        chip.background = VaultDesign.surfaceDrawable(this, VaultDesign.colorCardStrong, VaultDesign.radiusFull, Color.TRANSPARENT)
        chip.setOnClickListener { onClick() }
        (chip.layoutParams as? LinearLayout.LayoutParams)?.marginEnd = VaultDesign.dp(this, VaultDesign.spaceXS)
        return chip
    }

    private fun renderStage(current: VaultItem) {
        when {
            current.kind == VaultItemKind.IMAGE -> showImage(current)
            current.kind == VaultItemKind.VIDEO -> showVideo(current)
            current.kind == VaultItemKind.AUDIO -> showAudio(current)
            current.kind == VaultItemKind.SCAN && current.mimeType == "application/pdf" -> showPdf(current)
            current.kind == VaultItemKind.NOTE -> showNote(current)
            else -> showFallback(current)
        }
    }

    private fun showImage(current: VaultItem) {
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }
        stage.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        io({ manager.bitmap(current, 2048) }) { result ->
            val bitmap: Bitmap? = result.getOrNull()
            if (bitmap != null) image.setImageBitmap(bitmap) else stage.addView(
                VaultUi.message(this, "This image could not be read. The file inside the Vault is unchanged.", VaultDesign.colorWarning)
            )
        }
    }

    private fun showVideo(current: VaultItem) {
        val surface = SurfaceView(this)
        stage.addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        var source: VaultMediaDataSource? = null
        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                source = manager.mediaSource(current)
                val dataSource = source ?: return
                val mediaPlayer = MediaPlayer()
                player = mediaPlayer
                runCatching {
                    mediaPlayer.setDataSource(dataSource)
                    mediaPlayer.setDisplay(holder)
                    mediaPlayer.setOnPreparedListener { it.start() }
                    mediaPlayer.setOnErrorListener { _, _, _ ->
                        stage.addView(VaultUi.message(this@VaultViewerActivity, "This video could not be played."))
                        true
                    }
                    mediaPlayer.prepareAsync()
                }.onFailure { LumaLog.e("Vault video playback failed: ${it.javaClass.simpleName}") }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceDestroyed(holder: SurfaceHolder) = releasePlayer()
        })
    }

    private fun showAudio(current: VaultItem) {
        val container = VaultUi.column(this).apply {
            gravity = Gravity.CENTER
            setPadding(
                VaultDesign.dp(this@VaultViewerActivity, VaultDesign.spaceXL),
                VaultDesign.dp(this@VaultViewerActivity, VaultDesign.spaceXXL),
                VaultDesign.dp(this@VaultViewerActivity, VaultDesign.spaceXL),
                VaultDesign.dp(this@VaultViewerActivity, VaultDesign.spaceXXL)
            )
        }
        val label = VaultUi.text(this, current.name, VaultDesign.typeHeadingSp, VaultDesign.colorTextPrimary, VaultUi.Weight.MEDIUM)
        container.addView(label)
        val playButton = VaultUi.secondaryButton(this, "Play") {}
        container.addView(playButton)
        val seek = SeekBar(this)
        container.addView(seek)
        stage.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        var source: VaultMediaDataSource? = null
        playButton.setOnClickListener {
            val existing = player
            if (existing != null && existing.isPlaying) {
                existing.pause()
                playButton.text = "Play"
                return@setOnClickListener
            }
            if (existing != null) {
                existing.start()
                playButton.text = "Pause"
                return@setOnClickListener
            }
            source = manager.mediaSource(current)
            val dataSource = source ?: return@setOnClickListener
            val mediaPlayer = MediaPlayer()
            player = mediaPlayer
            runCatching {
                mediaPlayer.setDataSource(dataSource)
                mediaPlayer.setOnPreparedListener {
                    it.start()
                    playButton.text = "Pause"
                    seek.max = it.duration.coerceAtLeast(1)
                    seek.progress = 0
                }
                mediaPlayer.setOnCompletionListener { playButton.text = "Play" }
                seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (fromUser) player?.seekTo(progress)
                    }
                    override fun onStartTrackingTouch(bar: SeekBar) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar) = Unit
                })
                mediaPlayer.prepareAsync()
            }.onFailure {
                VaultUi.message(this, "This recording could not be played.")
            }
        }
    }

    /**
     * PDF rendering through a transient plaintext file.
     *
     * `PdfRenderer` cannot read from a stream, so the encrypted document is
     * decrypted into the vault's staging directory for the lifetime of this screen
     * and shredded on exit. Bounded, private, and gone - not a cache.
     */
    private fun showPdf(current: VaultItem) {
        val container = VaultUi.column(this).apply { gravity = Gravity.CENTER }
        val pageView = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        container.addView(pageView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val controls = VaultUi.row(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, VaultDesign.dp(this@VaultViewerActivity, VaultDesign.spaceXS), 0, 0)
        }
        val indicator = VaultUi.caption(this, "Page 1")
        controls.addView(actionChip("‹") { turnPdfPage(-1, pageView, indicator) })
        controls.addView(indicator)
        controls.addView(actionChip("›") { turnPdfPage(1, pageView, indicator) })
        container.addView(controls)
        stage.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        io({
            val scratch = VaultTransientFiles.createForPdf(manager.paths)
            java.io.FileOutputStream(scratch).use { out -> manager.copyTo(current, out) }
            scratch
        }) { result ->
            val scratch = result.getOrNull()
            if (scratch == null) {
                stage.addView(VaultUi.message(this, "This document could not be opened."))
                return@io
            }
            transientPdf = scratch
            renderPdf(scratch, current, pageView, indicator)
        }
    }

    private fun renderPdf(scratch: File, current: VaultItem, pageView: ImageView, indicator: android.widget.TextView) {
        runCatching {
            pdfDescriptor = ParcelFileDescriptor.open(scratch, ParcelFileDescriptor.MODE_READ_ONLY)
            val rendererInstance = PdfRenderer(pdfDescriptor!!)
            renderer = rendererInstance
            pdfPage = 0
            indicator.text = "Page 1 of ${rendererInstance.pageCount}"
            showPdfPage(rendererInstance, pageView)
        }.onFailure {
            LumaLog.e("Vault PDF render failed: ${it.javaClass.simpleName}")
            stage.addView(VaultUi.message(this, "This document could not be rendered. Export it to view outside the Vault."))
        }
    }

    private fun showPdfPage(rendererInstance: PdfRenderer, pageView: ImageView) {
        rendererInstance.openPage(pdfPage).use { page ->
            val scale = 2
            val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pageView.setImageBitmap(bitmap)
        }
    }

    private fun turnPdfPage(delta: Int, pageView: ImageView, indicator: android.widget.TextView) {
        val rendererInstance = renderer ?: return
        val next = (pdfPage + delta).coerceIn(0, rendererInstance.pageCount - 1)
        if (next == pdfPage) return
        pdfPage = next
        renderPdfPage(rendererInstance, pageView)
        indicator.text = "Page ${next + 1} of ${rendererInstance.pageCount}"
    }

    private fun renderPdfPage(rendererInstance: PdfRenderer, pageView: ImageView) {
        runCatching { showPdfPage(rendererInstance, pageView) }
    }

    private fun showNote(current: VaultItem) {
        val text = runCatching { manager.readNote(current) }.getOrNull()
        val scroll = android.widget.ScrollView(this)
        val body = VaultUi.column(this).apply {
            setPadding(
                VaultDesign.dp(this@VaultViewerActivity, VaultDesign.screenMarginHorizontal),
                VaultDesign.dp(this@VaultViewerActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultViewerActivity, VaultDesign.screenMarginHorizontal),
                VaultDesign.dp(this@VaultViewerActivity, VaultDesign.spaceXXL)
            )
        }
        body.addView(
            VaultUi.text(
                this,
                text ?: "This note could not be opened.",
                VaultDesign.typeBodySp,
                VaultDesign.colorTextPrimary
            ).apply { setTextIsSelectable(false) }
        )
        scroll.addView(body)
        stage.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun showFallback(current: VaultItem) {
        val container = VaultUi.column(this).apply {
            gravity = Gravity.CENTER
            setPadding(
                VaultDesign.dp(this@VaultViewerActivity, VaultDesign.spaceXL),
                VaultDesign.dp(this@VaultViewerActivity, VaultDesign.spaceXL),
                VaultDesign.dp(this@VaultViewerActivity, VaultDesign.spaceXL),
                VaultDesign.dp(this@VaultViewerActivity, VaultDesign.spaceXL)
            )
        }
        container.addView(VaultUi.text(this, "No in-Vault preview", VaultDesign.typeHeadingSp, VaultDesign.colorTextSecondary, VaultUi.Weight.MEDIUM))
        container.addView(VaultUi.caption(this, "This file type opens in its own app. Exporting a copy is the only way to view it, and the copy leaves Vault protection."))
        container.addView(VaultUi.secondaryButton(this, "Export a copy") { startExport(current) })
        stage.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    private fun releasePlayer() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    override fun onPause() {
        super.onPause()
        // Locking the screen, switching apps or the auto-lock timeout all pause
        // playback: nothing private keeps playing in the background.
        releasePlayer()
    }

    override fun onVaultLocked() {
        super.onVaultLocked()
    }

    override fun onDestroy() {
        releasePlayer()
        runCatching { renderer?.close() }
        renderer = null
        runCatching { pdfDescriptor?.close() }
        pdfDescriptor = null
        transientPdf?.let { VaultFiles.shred(it) }
        transientPdf = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "vault.item.id"
    }
}
