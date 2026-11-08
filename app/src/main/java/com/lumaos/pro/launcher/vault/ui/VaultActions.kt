package com.lumaos.pro.launcher.vault.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.lumaos.pro.launcher.vault.core.VaultItem
import com.lumaos.pro.launcher.vault.platform.VaultManager

/**
 * Shared, explicit actions on a vault item.
 *
 * Export is the only operation in Vault that deliberately makes content *less*
 * private, so it always goes through a system document picker chosen by the user,
 * and the confirmation text says plainly that the exported copy is an ordinary
 * file from that moment on. Nothing in Vault ever exports silently.
 */
object VaultActions {

    data class PickedFile(val uri: Uri, val name: String, val mimeType: String, val sizeBytes: Long)

    /** Reads the display name, MIME type and size a SAF picker reported. */
    fun describe(context: Context, uri: Uri): PickedFile? {
        val resolver = context.contentResolver
        var name: String? = null
        var size = 0L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                    }
                }
        }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val resolvedName = name ?: uri.lastPathSegment ?: "Imported file"
        return PickedFile(uri, resolvedName, mime, size)
    }

    fun promptRename(activity: Activity, manager: VaultManager, item: VaultItem, onDone: () -> Unit) {
        val input = EditText(activity).apply {
            setText(item.name)
            setSelection(text.length)
            // A name is private metadata: no personalised keyboard learning.
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            isSaveEnabled = false
            setTextColor(VaultDesign.colorTextPrimary)
        }
        val container = LinearLayout(activity).apply {
            setPadding(
                VaultDesign.dp(activity, VaultDesign.spaceXL),
                VaultDesign.dp(activity, VaultDesign.spaceXS),
                VaultDesign.dp(activity, VaultDesign.spaceXL),
                0
            )
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        android.app.AlertDialog.Builder(activity)
            .setTitle("Rename")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    manager.ioExecutor.execute {
                        runCatching { manager.rename(item, name) }
                        activity.runOnUiThread { onDone() }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun confirmDelete(activity: Activity, manager: VaultManager, item: VaultItem, onDone: () -> Unit) {
        android.app.AlertDialog.Builder(activity)
            .setTitle("Delete this item?")
            .setMessage("\"${item.name}\" will be removed from the Vault. This cannot be undone, and it will be absent from future backups.")
            .setPositiveButton("Delete") { _, _ ->
                manager.ioExecutor.execute {
                    runCatching { manager.delete(item) }
                    activity.runOnUiThread { onDone() }
                }
            }
            .setNegativeButton("Keep", null)
            .show()
    }

    /** Opens the system "save a copy" picker for an explicit export. */
    fun startExport(activity: Activity, item: VaultItem) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = item.mimeType.ifBlank { "application/octet-stream" }
            putExtra(Intent.EXTRA_TITLE, item.name)
        }
        runCatching { activity.startActivityForResult(intent, BaseRequest.EXPORT) }
            .onFailure { Toast.makeText(activity, "No file manager is available to export to", Toast.LENGTH_LONG).show() }
    }

    fun importPicker(multiple: Boolean): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun exportPicker(mimeType: String, suggestedName: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, suggestedName)
        }

    /** Request codes shared by every Vault screen. */
    object BaseRequest {
        const val EXPORT = 4201
        const val IMPORT = 4202
        const val DOCUMENT = 4203
    }

    fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / 1048576.0)
        else -> String.format(java.util.Locale.getDefault(), "%.2f GB", bytes / 1073741824.0)
    }
}
