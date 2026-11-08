package com.lumaos.pro.launcher.vault.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.vault.core.VaultFiles
import com.lumaos.pro.launcher.vault.core.VaultItem
import com.lumaos.pro.launcher.vault.core.VaultItemKind
import com.lumaos.pro.launcher.vault.platform.VaultCapture
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Private voice recorder.
 *
 * The platform encoder cannot write into an encrypted blob directly, so recording
 * goes to a scratch file inside the vault's own staging directory - never to
 * `Music`, `Recordings` or any MediaStore collection - and is encrypted the moment
 * the user stops. If the process dies mid-recording, the leftover scratch file is
 * shredded at the next unlock; nothing half-written can be mistaken for a vault
 * object.
 */
class VaultRecorderActivity : VaultScreenActivity() {

    private lateinit var container: LinearLayout
    private var recorder: MediaRecorder? = null
    private var scratch: File? = null
    private var startedAtMs = 0L
    private var recording = false
    private lateinit var timerLabel: android.widget.TextView
    private lateinit var recordButton: android.widget.TextView

    override fun onCreateContent(): View {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        container = scrollColumn()
        scroll.addView(container)
        render()
        return scroll
    }

    private fun render() {
        container.removeAllViews()
        container.addView(header("Recorder", "Audio that never becomes a public file") { finish() })

        val status = VaultUi.card(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        timerLabel = VaultUi.hero(this, "00:00").apply { gravity = Gravity.CENTER }
        status.addView(timerLabel)
        status.addView(
            VaultUi.caption(this, if (recording) "Recording into the Vault…" else "Ready").apply { gravity = Gravity.CENTER }
        )
        container.addView(status)

        recordButton = if (recording) {
            VaultUi.dangerButton(this, "Stop and encrypt") { stopRecording() }
        } else {
            VaultUi.primaryButton(this, "Start recording") { startRecording() }
        }
        container.addView(recordButton)

        val items = manager.items(setOf(VaultItemKind.AUDIO))
        container.addView(VaultUi.spacer(this, VaultDesign.spaceLG))
        container.addView(VaultUi.sectionHeader(this, "Recordings · ${items.size}"))
        if (items.isEmpty()) {
            container.addView(VaultUi.emptyState(this, "No recordings yet", "Recordings are encrypted as soon as you stop, and are only playable inside the Vault."))
            return
        }
        for (item in items) {
            container.addView(
                VaultUi.tile(
                    this,
                    VaultIcons.recorder(this),
                    item.name,
                    "${VaultActions.formatBytes(item.sizeBytes)} · ${formatted(item.capturedAtMs)}"
                ) {
                    startActivity(Intent(this, VaultViewerActivity::class.java).putExtra(VaultViewerActivity.EXTRA_ID, item.id))
                }
            )
        }
    }

    // ------------------------------------------------------------------

    private fun startRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            return
        }
        val target = VaultCapture.scratch(manager.paths, ".m4a")
        val mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioEncodingBitRate(96_000)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setOutputFile(target.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
        } catch (e: Exception) {
            LumaLog.e("Vault recorder could not start: ${e.javaClass.simpleName}")
            runCatching { mediaRecorder.release() }
            VaultFiles.shred(target)
            android.widget.Toast.makeText(this, "The microphone is unavailable right now.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        recorder = mediaRecorder
        scratch = target
        startedAtMs = SystemClock.elapsedRealtime()
        recording = true
        render()
        startTicker()
    }

    private fun stopRecording() {
        val mediaRecorder = recorder ?: return
        val target = scratch
        recorder = null
        scratch = null
        recording = false
        val elapsed = SystemClock.elapsedRealtime() - startedAtMs
        val stoppedCleanly = runCatching { mediaRecorder.stop() }.isSuccess
        runCatching { mediaRecorder.release() }
        if (!stoppedCleanly || target == null || !target.isFile || target.length() == 0L) {
            // A recording shorter than a few hundred milliseconds cannot be
            // finalised by the platform encoder; treat it as no recording at all.
            target?.let { VaultFiles.shred(it) }
            render()
            if (elapsed < 800) {
                android.widget.Toast.makeText(this, "That was too short to keep.", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(this, "The recording could not be saved. Nothing was added.", android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }
        val name = "Recording ${SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.getDefault()).format(Date())}"
        io({
            try {
                FileInputStream(target).use { stream ->
                    manager.addStream(stream, VaultItemKind.AUDIO, name, "audio/mp4", origin = "recorder")
                }
            } finally {
                VaultFiles.shred(target)
            }
        }) { result ->
            if (result.isFailure) {
                android.widget.Toast.makeText(this, "The recording could not be encrypted into the Vault.", android.widget.Toast.LENGTH_LONG).show()
            }
            render()
        }
    }

    private fun startTicker() {
        if (!recording) return
        val elapsed = SystemClock.elapsedRealtime() - startedAtMs
        if (::timerLabel.isInitialized) {
            timerLabel.text = String.format(Locale.getDefault(), "%02d:%02d", elapsed / 60_000, (elapsed / 1000) % 60)
        }
        main.postDelayed({ startTicker() }, 500)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                android.widget.Toast.makeText(
                    this,
                    "The recorder needs microphone access. Android controls this permission; enable it in system settings.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Leaving the screen stops and encrypts: a recording never continues in
        // the background after the vault is hidden or locked.
        if (recording) stopRecording()
    }

    override fun onDestroy() {
        if (recording) {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            recorder = null
            scratch?.let { VaultFiles.shred(it) }
            scratch = null
        }
        super.onDestroy()
    }

    private fun formatted(timestampMs: Long): String =
        SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(timestampMs))

    companion object {
        private const val REQUEST_MIC = 5301
    }
}
