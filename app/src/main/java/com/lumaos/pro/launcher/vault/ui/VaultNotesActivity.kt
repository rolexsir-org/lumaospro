package com.lumaos.pro.launcher.vault.ui

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.lumaos.pro.launcher.vault.core.VaultItem
import com.lumaos.pro.launcher.vault.core.VaultItemKind

/**
 * Private notes.
 *
 * The body is an encrypted object like any other file, and the editor is built to
 * keep it that way: no autofill, no personalised suggestions, no saving of the
 * field state into the activity bundle, and no preview text anywhere outside this
 * screen. Saving happens when the user leaves the note, and a failed save never
 * replaces the previous version - the old ciphertext is only deleted after the new
 * one is committed.
 */
class VaultNotesActivity : VaultScreenActivity() {

    private lateinit var container: LinearLayout
    private var editing: VaultItem? = null
    private var dirty = false

    override fun onCreateContent(): View {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        container = scrollColumn()
        scroll.addView(container)
        render()
        return scroll
    }

    override fun onResume() {
        super.onResume()
        if (manager.session.isUnlocked && editing == null) render()
    }

    private fun render() {
        editing = null
        container.removeAllViews()
        container.addView(header("Notes", "Text that never leaves the Vault") { finish() })
        container.addView(VaultUi.primaryButton(this, "New note") { openEditor(null) })

        val notes = manager.items(setOf(VaultItemKind.NOTE))
        container.addView(VaultUi.spacer(this, VaultDesign.spaceLG))
        container.addView(VaultUi.sectionHeader(this, "Notes · ${notes.size}"))
        if (notes.isEmpty()) {
            container.addView(
                VaultUi.emptyState(
                    this,
                    "No notes yet",
                    "Write down recovery codes, account details or anything you would rather keep inside the Vault."
                )
            )
            return
        }
        for (note in notes) {
            container.addView(
                VaultUi.tile(this, VaultIcons.notes(this), note.name, "Encrypted · tap to open") {
                    openEditor(note)
                }
            )
        }
    }

    private fun openEditor(note: VaultItem?) {
        editing = note
        dirty = false
        container.removeAllViews()
        container.addView(
            header(
                if (note == null) "New note" else "Edit note",
                "Saved by leaving this screen",
                onBack = { saveAndClose() }
            )
        )

        val titleField = privateField("Title").apply { setText(note?.name ?: "") }
        container.addView(titleField, matchWidth())

        val bodyField = privateField("Write something private…").apply {
            minLines = 8
            gravity = Gravity.TOP
            setSingleLine(false)
            setText(if (note == null) "" else runCatching { manager.readNote(note) }.getOrDefault(""))
            setPadding(
                VaultDesign.dp(this@VaultNotesActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultNotesActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultNotesActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultNotesActivity, VaultDesign.spaceMD)
            )
            background = VaultDesign.surfaceDrawable(this@VaultNotesActivity, VaultDesign.colorCard, VaultDesign.radiusLarge)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { topMargin = VaultDesign.dp(this@VaultNotesActivity, VaultDesign.spaceXS) }
        }
        container.addView(bodyField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        container.addView(VaultUi.primaryButton(this, "Save and close") { saveAndClose() })
        container.addView(VaultUi.secondaryButton(this, "Discard") { render() })
    }

    /**
     * A text field whose copy and cut actions mark the clip sensitive.
     *
     * The platform's own Copy action writes an ordinary clip, so it is intercepted
     * here: the selection is copied with `EXTRA_IS_SENSITIVE` set, and only then is a
     * cut applied. Everything else (selection, paste, select-all) falls through to the
     * platform, so copying still works exactly as the user expects.
     */
    private inner class SensitiveField(context: android.content.Context) : EditText(context) {

        override fun onTextContextMenuItem(id: Int): Boolean {
            if (id == android.R.id.copy || id == android.R.id.cut) {
                val start = selectionStart
                val end = selectionEnd
                val from = minOf(start, end)
                val to = maxOf(start, end)
                val content = text
                if (content != null && from >= 0 && to > from && to <= content.length) {
                    val selected = content.subSequence(from, to).toString()
                    val copied = VaultClipboard.copySensitive(
                        this@VaultNotesActivity,
                        "LumaOS Vault note",
                        selected
                    )
                    if (!copied) {
                        // Never fall back to an unmarked copy: it would silently give
                        // the platform an ordinary clip of Vault content.
                        android.widget.Toast.makeText(
                            this@VaultNotesActivity,
                            "This device did not accept the copy.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        return true
                    }
                    if (id == android.R.id.cut) text?.delete(from, to)
                    return true
                }
            }
            return super.onTextContextMenuItem(id)
        }
    }

    private fun privateField(hint: String): EditText = SensitiveField(this).apply {
        this.hint = hint
        // A private note must not be learned, autofilled or restored from a bundle.
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        isSaveEnabled = false
        setTextColor(VaultDesign.colorTextPrimary)
        setHintTextColor(VaultDesign.colorTextTertiary)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, VaultDesign.typeBodySp)
        setPadding(
            VaultDesign.dp(this@VaultNotesActivity, VaultDesign.spaceMD),
            VaultDesign.dp(this@VaultNotesActivity, VaultDesign.spaceSM),
            VaultDesign.dp(this@VaultNotesActivity, VaultDesign.spaceMD),
            VaultDesign.dp(this@VaultNotesActivity, VaultDesign.spaceSM)
        )
        background = VaultDesign.surfaceDrawable(this@VaultNotesActivity, VaultDesign.colorCard, VaultDesign.radiusMedium)
    }

    private fun matchWidth(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun saveAndClose() {
        // Read the fields from the current hierarchy, then re-render on the result.
        val titleField = findEditText(container, 0)
        val bodyField = findEditText(container, 1)
        val title = titleField?.text?.toString()?.trim().orEmpty().ifBlank { "Untitled note" }
        val body = bodyField?.text?.toString().orEmpty()
        val existing = editing
        if (existing == null && body.isBlank()) {
            render()
            return
        }
        io({
            if (existing == null) manager.addNote(title, body) else manager.updateNote(existing, title, body)
        }) { result ->
            if (result.isFailure) {
                android.widget.Toast.makeText(this, "The note could not be saved. Nothing was lost.", android.widget.Toast.LENGTH_LONG).show()
            }
            render()
        }
    }

    private fun findEditText(parent: ViewGroup, index: Int): EditText? {
        val found = ArrayList<EditText>()
        fun walk(view: View) {
            if (view is EditText) found.add(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(parent)
        return found.getOrNull(index)
    }
}
