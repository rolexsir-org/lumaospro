package com.lumaos.pro.launcher.vault.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.lumaos.pro.launcher.vault.core.VaultItemKind
import com.lumaos.pro.launcher.vault.core.VaultSession
import com.lumaos.pro.launcher.vault.platform.VaultManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Vault home: the seven private spaces, plus an honest line about backup state.
 *
 * Nothing on this screen reveals content: counts are numbers, and there are no
 * thumbnails here at all, so a glance at the recent-apps card (which `FLAG_SECURE`
 * blanks anyway) or a shoulder-surf would not show what is inside.
 */
class VaultHomeActivity : VaultScreenActivity() {

    private lateinit var container: LinearLayout
    private lateinit var backupLine: LinearLayout
    private lateinit var summary: android.widget.TextView

    override fun onCreateContent(): View {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        container = scrollColumn()
        scroll.addView(container)
        render()
        return scroll
    }

    override fun onResume() {
        super.onResume()
        if (manager.session.isUnlocked) render()
    }

    private fun render() {
        container.removeAllViews()

        val topBar = VaultUi.row(this)
        topBar.addView(VaultUi.hero(this, "Vault"))
        topBar.addView(android.widget.Space(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        topBar.addView(
            VaultUi.text(this, "Lock", VaultDesign.typeCaptionSp, VaultDesign.colorAccent, VaultUi.Weight.SEMIBOLD)
                .apply {
                    isClickable = true
                    setPadding(
                        VaultDesign.dp(this@VaultHomeActivity, VaultDesign.spaceMD),
                        VaultDesign.dp(this@VaultHomeActivity, VaultDesign.spaceXS),
                        VaultDesign.dp(this@VaultHomeActivity, VaultDesign.spaceMD),
                        VaultDesign.dp(this@VaultHomeActivity, VaultDesign.spaceXS)
                    )
                    background = VaultDesign.surfaceDrawable(
                        this@VaultHomeActivity,
                        VaultDesign.colorAccentSoft,
                        VaultDesign.radiusFull,
                        android.graphics.Color.TRANSPARENT
                    )
                    setOnClickListener { lockAndReturn() }
                }
        )
        container.addView(topBar)
        container.addView(VaultUi.caption(this, "Encrypted on this device. Only you can open it."))
        container.addView(VaultUi.spacer(this, VaultDesign.spaceLG))

        summary = VaultUi.text(this, "", VaultDesign.typeCaptionSp, VaultDesign.colorTextSecondary)
        container.addView(summary)
        container.addView(VaultUi.spacer(this, VaultDesign.spaceMD))

        addTile("Camera", "Private photos, never in your gallery", VaultIcons.camera(this)) {
            open(VaultCaptureActivity::class.java, VaultCaptureActivity.EXTRA_MODE to VaultCaptureActivity.MODE_PHOTO)
        }
        addTile("Gallery", countFor(VaultItemKind.IMAGE, VaultItemKind.VIDEO), VaultIcons.gallery(this)) {
            open(VaultGalleryActivity::class.java)
        }
        addTile("Files", countFor(VaultItemKind.DOCUMENT, VaultItemKind.SCAN, VaultItemKind.OTHER), VaultIcons.files(this)) {
            open(VaultFilesActivity::class.java)
        }
        addTile("Notes", countFor(VaultItemKind.NOTE), VaultIcons.notes(this)) {
            open(VaultNotesActivity::class.java)
        }
        addTile("Recorder", countFor(VaultItemKind.AUDIO), VaultIcons.recorder(this)) {
            open(VaultRecorderActivity::class.java)
        }
        addTile("Scanner", "Documents to encrypted PDF", VaultIcons.scanner(this)) {
            open(VaultCaptureActivity::class.java, VaultCaptureActivity.EXTRA_MODE to VaultCaptureActivity.MODE_SCAN)
        }
        addTile("Security Centre", "PIN, biometrics, recovery key, backup", VaultIcons.security(this)) {
            open(VaultSecurityActivity::class.java)
        }

        container.addView(VaultUi.spacer(this, VaultDesign.spaceXL))
        container.addView(VaultUi.sectionHeader(this, "Backup"))
        backupLine = VaultUi.column(this)
        container.addView(backupLine)
        renderBackupLine()

        container.addView(VaultUi.spacer(this, VaultDesign.spaceXXL))
    }

    private fun renderBackupLine() {
        val status = manager.status()
        summary.text = buildString {
            append(if (status.itemCount == 1) "1 item" else "${status.itemCount} items")
            append(" · ")
            append(formatBytes(status.storageBytes))
            append(" encrypted")
        }

        val state = manager.backupState()
        val (label, color) = when (state.kind) {
            VaultManager.BackupState.Kind.PROTECTED -> "Protected · verified backup ${relative(state.lastSuccessAtMs)}" to VaultDesign.colorPositive
            VaultManager.BackupState.Kind.PENDING -> "Backup pending · recent changes are not in a verified backup" to VaultDesign.colorWarning
            VaultManager.BackupState.Kind.FAILED -> "Backup failed · open the Security Centre to retry" to VaultDesign.colorDanger
            VaultManager.BackupState.Kind.DESTINATION_LOST -> "Not protected · the backup location needs to be reconnected" to VaultDesign.colorDanger
            VaultManager.BackupState.Kind.NOT_CONFIGURED -> "Not protected · set up an encrypted backup so a lost phone is not a lost vault" to VaultDesign.colorTextSecondary
        }
        backupLine.removeAllViews()
        backupLine.addView(VaultUi.message(this, label, color))
        backupLine.addView(VaultUi.secondaryButton(this, "Open Security Centre") {
            open(VaultSecurityActivity::class.java)
        })
    }

    private fun addTile(title: String, subtitle: String, icon: android.graphics.drawable.Drawable?, onClick: () -> Unit) {
        container.addView(VaultUi.tile(this, icon, title, subtitle, onClick))
    }

    private fun countFor(vararg kinds: VaultItemKind): String {
        val items = manager.items(kinds.toSet())
        return when (items.size) {
            0 -> "Empty"
            1 -> "1 item"
            else -> "${items.size} items"
        }
    }

    private fun open(target: Class<*>, extra: Pair<String, String>? = null) {
        startActivity(Intent(this, target).apply { if (extra != null) putExtra(extra.first, extra.second) })
    }

    private fun lockAndReturn() {
        manager.lock(VaultSession.LockReason.USER)
        finishAffinity()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0)
        else -> String.format(Locale.getDefault(), "%.2f GB", bytes / 1073741824.0)
    }

    private fun relative(timestampMs: Long): String {
        if (timestampMs <= 0L) return "never"
        val delta = System.currentTimeMillis() - timestampMs
        return when {
            delta < 60_000 -> "just now"
            delta < 3_600_000 -> "${delta / 60_000} min ago"
            delta < 86_400_000 -> "${delta / 3_600_000} h ago"
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestampMs))
        }
    }
}
