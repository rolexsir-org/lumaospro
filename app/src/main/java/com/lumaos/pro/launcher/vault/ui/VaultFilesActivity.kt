package com.lumaos.pro.launcher.vault.ui

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.lumaos.pro.launcher.vault.core.VaultItem
import com.lumaos.pro.launcher.vault.core.VaultItemKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Private files: imports, scans and documents.
 *
 * Import goes through the Storage Access Framework and is encrypted as it streams
 * in - the original file the user picked is left exactly where it was, because
 * Vault never silently deletes or moves something outside its own storage.
 */
class VaultFilesActivity : VaultScreenActivity() {

    private lateinit var container: LinearLayout
    private lateinit var listHost: LinearLayout

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

    override fun onImportFinished(count: Int) {
        render()
        android.widget.Toast.makeText(
            this,
            if (count == 1) "1 file encrypted into the Vault" else "$count files encrypted into the Vault",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    override fun onExportFinished(item: VaultItem) = render()

    private fun render() {
        container.removeAllViews()
        container.addView(header("Files", "Imported and captured documents, encrypted at rest") { finish() })
        container.addView(VaultUi.primaryButton(this, "Import from this phone") { startImport() })

        val items = manager
            .items(setOf(VaultItemKind.DOCUMENT, VaultItemKind.SCAN, VaultItemKind.OTHER))
        container.addView(VaultUi.spacer(this, VaultDesign.spaceLG))
        container.addView(VaultUi.sectionHeader(this, "Files · ${items.size}"))

        listHost = VaultUi.column(this)
        container.addView(listHost)

        if (items.isEmpty()) {
            listHost.addView(
                VaultUi.emptyState(
                    this,
                    "No files yet",
                    "Import a document and it is encrypted straight into the Vault. The original stays where it was."
                )
            )
            return
        }
        for (item in items) listHost.addView(fileRow(item))
    }

    private fun fileRow(item: VaultItem): View {
        val row = VaultUi.row(this).apply {
            background = VaultDesign.surfaceDrawable(this@VaultFilesActivity, VaultDesign.colorCard, VaultDesign.radiusLarge)
            isClickable = true
            setPadding(
                VaultDesign.dp(this@VaultFilesActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultFilesActivity, VaultDesign.spaceSM),
                VaultDesign.dp(this@VaultFilesActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultFilesActivity, VaultDesign.spaceSM)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = VaultDesign.dp(this@VaultFilesActivity, VaultDesign.spaceXS) }
            setOnClickListener { open(item) }
            setOnLongClickListener { itemOptions(item); true }
        }
        val icon = ImageView(this).apply {
            setImageDrawable(if (item.kind == VaultItemKind.SCAN) VaultIcons.document(this@VaultFilesActivity) else VaultIcons.files(this@VaultFilesActivity))
            val size = VaultDesign.dp(this@VaultFilesActivity, 26f)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                rightMargin = VaultDesign.dp(this@VaultFilesActivity, VaultDesign.spaceMD)
            }
        }
        row.addView(icon)
        row.addView(VaultUi.column(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(VaultUi.text(this@VaultFilesActivity, item.name, VaultDesign.typeBodySp, VaultDesign.colorTextPrimary, VaultUi.Weight.MEDIUM))
            addView(
                VaultUi.caption(
                    this@VaultFilesActivity,
                    "${VaultActions.formatBytes(item.sizeBytes)} · ${formatted(item.capturedAtMs)}"
                )
            )
        })
        row.addView(
            VaultUi.text(this, "⋯", VaultDesign.typeTitleSp, VaultDesign.colorTextTertiary).apply {
                isClickable = true
                setPadding(
                    VaultDesign.dp(this@VaultFilesActivity, VaultDesign.spaceSM),
                    0,
                    0,
                    0
                )
                setOnClickListener { itemOptions(item) }
            }
        )
        return row
    }

    private fun open(item: VaultItem) {
        startActivity(Intent(this, VaultViewerActivity::class.java).putExtra(VaultViewerActivity.EXTRA_ID, item.id))
    }

    private fun itemOptions(item: VaultItem) {
        val options = arrayOf("Open", "Rename", "Export a copy", "Delete")
        android.app.AlertDialog.Builder(this)
            .setTitle(item.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> open(item)
                    1 -> VaultActions.promptRename(this, manager, item) { render() }
                    2 -> startExport(item)
                    3 -> VaultActions.confirmDelete(this, manager, item) { render() }
                }
            }
            .show()
    }

    private fun formatted(timestampMs: Long): String =
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestampMs))
}
