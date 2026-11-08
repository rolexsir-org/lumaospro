package com.lumaos.pro.launcher.vault.ui

import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.lumaos.pro.launcher.vault.core.VaultAutoLock
import com.lumaos.pro.launcher.vault.core.VaultEventType
import com.lumaos.pro.launcher.vault.core.VaultPinPolicy
import com.lumaos.pro.launcher.vault.core.VaultRecoveryCode
import com.lumaos.pro.launcher.vault.core.VaultSession
import com.lumaos.pro.launcher.vault.platform.VaultBiometricGate
import com.lumaos.pro.launcher.vault.platform.VaultDeviceKeyGuard
import com.lumaos.pro.launcher.vault.platform.VaultManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Vault Security Centre.
 *
 * Everything a user can change about Vault's protection lives here, and every line
 * reports what is *actually* true: whether a biometric wrapper still exists in the
 * Keystore, whether the recovery key was confirmed, and whether the last backup
 * completed and verified. There is no "Your vault is 100% secure" theatre - just
 * plain-language state and the actions that change it.
 */
class VaultSecurityActivity : VaultScreenActivity() {

    private lateinit var container: LinearLayout
    private val biometricGate: VaultBiometricGate by lazy { VaultBiometricGate(this) }
    private var pendingBackupUri: android.net.Uri? = null

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

    // ------------------------------------------------------------------

    private fun render() {
        container.removeAllViews()
        container.addView(header("Security Centre", "What protects this Vault, and what to do about it") { finish() })

        addPinSection()
        addBiometricSection()
        addRecoverySection()
        addAutoLockSection()
        addBackupSection()
        addLockNowSection()
        addEventSection()
    }

    private fun section(title: String, body: String? = null): LinearLayout {
        container.addView(VaultUi.spacer(this, VaultDesign.spaceLG))
        container.addView(VaultUi.sectionHeader(this, title))
        if (body != null) container.addView(VaultUi.caption(this, body))
        return VaultUi.card(this).also {
            container.addView(it)
            container.addView(VaultUi.spacer(this, VaultDesign.spaceSM))
        }
    }

    // ------------------------------------------------------------------

    private fun addPinSection() {
        val card = section("PIN", "Your PIN unlocks the vault key. Eight digits or more is strongly recommended.")
        card.addView(VaultUi.statusLine(this, "PIN", "Set", VaultDesign.colorPositive))
        card.addView(VaultUi.secondaryButton(this, "Change PIN") { promptChangePin() }.apply { text = "Change PIN" })
    }

    private fun promptChangePin() {
        val currentField = pinField("Current PIN")
        val newField = pinField("New PIN (${VaultPinPolicy.MIN_LENGTH}+ digits)")
        val confirmField = pinField("Repeat new PIN")
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                VaultDesign.dp(this@VaultSecurityActivity, VaultDesign.spaceXL),
                VaultDesign.dp(this@VaultSecurityActivity, VaultDesign.spaceXS),
                VaultDesign.dp(this@VaultSecurityActivity, VaultDesign.spaceXL),
                0
            )
            addView(currentField)
            addView(newField)
            addView(confirmField)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Change PIN")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val current = currentField.text.toString()
                val next = newField.text.toString()
                if (next != confirmField.text.toString()) {
                    toast("The new PINs do not match.")
                    return@setPositiveButton
                }
                io({ manager.changePin(current, next) }) { result ->
                    when (val outcome = result.getOrNull()) {
                        VaultManager.PinChangeResult.Success -> {
                            toast("PIN changed. Your files were not re-encrypted - only the key wrapper changed.")
                            render()
                        }
                        VaultManager.PinChangeResult.WrongCurrentPin -> toast("That current PIN is not right.")
                        is VaultManager.PinChangeResult.InvalidNewPin -> toast("That new PIN is too easy to guess. Try another.")
                        else -> toast("The PIN could not be changed.")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pinField(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        isSaveEnabled = false
        setTextColor(VaultDesign.colorTextPrimary)
        setHintTextColor(VaultDesign.colorTextTertiary)
        setPadding(0, VaultDesign.dp(this@VaultSecurityActivity, VaultDesign.spaceXS), 0, VaultDesign.dp(this@VaultSecurityActivity, VaultDesign.spaceXS))
    }

    // ------------------------------------------------------------------

    private fun addBiometricSection() {
        val card = section("Biometrics", "A fingerprint or face authorises the Keystore, which unseals your vault key. It never replaces the PIN.")
        val availability = manager.biometricAvailability()
        when {
            availability == VaultDeviceKeyGuard.Availability.AVAILABLE && manager.biometricNeedsSetup() ->
                card.addView(VaultUi.message(this, "Biometric unlock was reset (for example by a fingerprint change). Unlock with your PIN and set it up again.", VaultDesign.colorWarning))
            availability == VaultDeviceKeyGuard.Availability.AVAILABLE && manager.isBiometricEnabled() ->
                card.addView(VaultUi.statusLine(this, "Biometric unlock", "On", VaultDesign.colorPositive))
            availability == VaultDeviceKeyGuard.Availability.AVAILABLE ->
                card.addView(VaultUi.statusLine(this, "Biometric unlock", "Off", VaultDesign.colorTextSecondary))
            availability == VaultDeviceKeyGuard.Availability.NOT_ENROLLED ->
                card.addView(VaultUi.message(this, "No fingerprint or face is enrolled on this phone. Android asks you to enrol one in system settings.", VaultDesign.colorTextSecondary))
            availability == VaultDeviceKeyGuard.Availability.NO_HARDWARE ->
                card.addView(VaultUi.message(this, "This phone has no biometric hardware. Your PIN protects the Vault.", VaultDesign.colorTextSecondary))
            else ->
                card.addView(VaultUi.message(this, "Biometric unlock is not available on this Android version. Your PIN protects the Vault.", VaultDesign.colorTextSecondary))
        }

        if (availability == VaultDeviceKeyGuard.Availability.AVAILABLE) {
            if (manager.isBiometricEnabled()) {
                card.addView(VaultUi.secondaryButton(this, "Set up again") { enrollBiometric() })
                card.addView(VaultUi.dangerButton(this, "Turn off biometric unlock") {
                    manager.disableBiometric()
                    render()
                })
            } else {
                card.addView(VaultUi.primaryButton(this, "Enable biometric unlock") { enrollBiometric() })
            }
        }
    }

    private fun enrollBiometric() {
        val cipher = manager.prepareBiometricEnrollment()
        if (cipher == null) {
            toast("Biometric unlock could not be prepared on this device.")
            return
        }
        biometricGate.authenticate(
            cipher = cipher,
            title = "Enable biometric unlock",
            subtitle = "Confirm to seal your Vault key with this device's secure hardware."
        ) { outcome, authorised ->
            if (outcome == VaultBiometricGate.Outcome.SUCCESS && authorised != null) {
                io({ manager.completeBiometricEnrollment(authorised) }) { result ->
                    toast(if (result.getOrDefault(false)) "Biometric unlock enabled" else "Biometric unlock could not be enabled")
                    render()
                }
            } else if (outcome == VaultBiometricGate.Outcome.LOCKOUT) {
                toast("Biometrics are temporarily locked. Use your PIN.")
            }
        }
    }

    // ------------------------------------------------------------------

    private fun addRecoverySection() {
        val card = section("Recovery key", "The only way back in after a forgotten PIN, a new phone or a factory reset. LumaOS cannot recover your Vault without it.")
        val status = manager.recoveryStatus()
        card.addView(VaultUi.statusLine(this, "Recovery key", if (status.configured) "Set" else "Missing", if (status.configured) VaultDesign.colorPositive else VaultDesign.colorDanger))
        card.addView(VaultUi.statusLine(this, "Confirmed saved", if (status.confirmed) "Yes" else "Not yet", if (status.confirmed) VaultDesign.colorPositive else VaultDesign.colorWarning))
        if (status.rotatedAtMs > 0L) {
            card.addView(VaultUi.statusLine(this, "Last rotated", date(status.rotatedAtMs), VaultDesign.colorTextSecondary))
        }
        card.addView(VaultUi.caption(this, "Rotating issues a new key. Backups made before a rotation still need the previous key, so make a fresh backup afterwards."))
        card.addView(VaultUi.secondaryButton(this, "Show me a new recovery key") { confirmRotate() })
        card.addView(VaultUi.secondaryButton(this, "I lost my recovery key") { explainLoss() })
    }

    private fun confirmRotate() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Issue a new recovery key?")
            .setMessage("Your current recovery key will stop opening this Vault. Write the new one down; if you lose it and forget your PIN, the content is gone.")
            .setPositiveButton("Issue new key") {
                _, _ ->
                io({ manager.rotateRecoveryKey() }) { result ->
                    val code = result.getOrNull()
                    if (code == null) {
                        toast("A new recovery key could not be created.")
                    } else {
                        showRecoveryKey(code)
                    }
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRecoveryKey(code: String) {
        val field = VaultUi.text(this, code, VaultDesign.typeHeadingSp, VaultDesign.colorTextPrimary, VaultUi.Weight.MEDIUM).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(
                VaultDesign.dp(this@VaultSecurityActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultSecurityActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultSecurityActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultSecurityActivity, VaultDesign.spaceMD)
            )
            background = VaultDesign.surfaceDrawable(this@VaultSecurityActivity, VaultDesign.colorCardStrong, VaultDesign.radiusLarge)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Your new recovery key")
            .setView(field)
            .setMessage("Write this down now. It is shown once, and it is not stored anywhere in readable form.")
            .setPositiveButton("I have saved it", null)
            .show()
    }

    private fun explainLoss() {
        android.app.AlertDialog.Builder(this)
            .setTitle("If you lose your recovery key")
            .setMessage(
                "Your Vault is encrypted with a key that only your PIN, your biometrics and your recovery key can open. " +
                    "There is no master copy on any server, so if all three are gone the content cannot be recovered - by you or by us. " +
                    "Issue a new recovery key now and store it somewhere safe."
            )
            .setPositiveButton("Issue a new key") { _, _ -> confirmRotate() }
            .setNegativeButton("Close", null)
            .show()
    }

    // ------------------------------------------------------------------

    private fun addAutoLockSection() {
        val card = section("Auto-lock", "The Vault locks when the screen turns off, and after this much inactivity.")
        val status = manager.status()
        card.addView(VaultUi.statusLine(this, "Lock after", status.autoLock.label(), VaultDesign.colorTextPrimary))
        val options = VaultAutoLock.entries.map { it.label() }.toTypedArray()
        card.addView(VaultUi.secondaryButton(this, "Change timeout") {
            android.app.AlertDialog.Builder(this)
                .setTitle("Lock the Vault after")
                .setItems(options) { _, which ->
                    val autoLock = VaultAutoLock.entries[which]
                    io({ manager.updateLockSettings(autoLock, manager.status().lockOnLeave) }) { render() }
                }
                .show()
        })
        val toggle = android.widget.CheckBox(this).apply {
            text = "Lock immediately when I leave the Vault"
            isChecked = status.lockOnLeave
            setTextColor(VaultDesign.colorTextPrimary)
            buttonTintList = android.content.res.ColorStateList.valueOf(VaultDesign.colorAccent)
            setOnCheckedChangeListener { _, checked ->
                io({ manager.updateLockSettings(manager.status().autoLock, checked) }) { render() }
            }
        }
        card.addView(toggle)
    }

    private fun VaultAutoLock.label(): String = when (this) {
        VaultAutoLock.THIRTY_SECONDS -> "30 seconds"
        VaultAutoLock.ONE_MINUTE -> "1 minute"
        VaultAutoLock.THREE_MINUTES -> "3 minutes"
        VaultAutoLock.FIVE_MINUTES -> "5 minutes"
        VaultAutoLock.TEN_MINUTES -> "10 minutes"
    }

    // ------------------------------------------------------------------

    private fun addBackupSection() {
        val card = section(
            "Encrypted backup",
            "A backup contains only ciphertext. Restoring it needs your recovery key, not your PIN, so a new phone can open it."
        )
        val state = manager.backupState()
        val (label, color) = when (state.kind) {
            VaultManager.BackupState.Kind.PROTECTED -> "Latest backup verified ${date(state.lastSuccessAtMs)}" to VaultDesign.colorPositive
            VaultManager.BackupState.Kind.PENDING -> "Changes since the last verified backup are not protected" to VaultDesign.colorWarning
            VaultManager.BackupState.Kind.FAILED -> "The last backup did not complete or verify" to VaultDesign.colorDanger
            VaultManager.BackupState.Kind.DESTINATION_LOST -> "The backup location needs permission again" to VaultDesign.colorDanger
            VaultManager.BackupState.Kind.NOT_CONFIGURED -> "No backup yet - a lost phone would mean a lost Vault" to VaultDesign.colorWarning
        }
        card.addView(VaultUi.message(this, label, color))
        if (state.destinationLabel != null) {
            card.addView(VaultUi.statusLine(this, "Location", state.destinationLabel ?: "", VaultDesign.colorTextSecondary))
        }
        card.addView(VaultUi.primaryButton(this, if (state.kind == VaultManager.BackupState.Kind.NOT_CONFIGURED) "Set up encrypted backup" else "Back up now") {
            startActivityForResult(
                VaultActions.exportPicker("application/octet-stream", "lumaos-vault-backup.lvb"),
                REQUEST_BACKUP
            )
        })
        card.addView(VaultUi.secondaryButton(this, "Restore from a backup file") {
            startActivityForResult(VaultActions.importPicker(multiple = false), REQUEST_RESTORE)
        })
        card.addView(
            VaultUi.caption(
                this,
                "A backup on this phone does not protect against losing the phone. Choose a location that lives somewhere else - a computer, a USB drive, or your own cloud drive app."
            )
        )
    }

    @Deprecated("Platform activity result API; Vault avoids a lifecycle dependency for this.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_BACKUP -> runBackup(uri)
            REQUEST_RESTORE -> promptRestore(uri)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun runBackup(uri: android.net.Uri) {
        val busy = VaultUi.busyRow(this, "Encrypting and verifying your backup…")
        container.addView(busy)
        io({ manager.backupTo(uri, uri.lastPathSegment) }) { result ->
            container.removeView(busy)
            if (result.isSuccess) {
                val bytes = result.getOrNull()?.bytes ?: 0L
                toast("Backup written and verified (${VaultActions.formatBytes(bytes)}). It contains ciphertext only.")
            } else {
                toast("The backup did not complete. Nothing is claimed as protected.")
            }
            render()
        }
    }

    private fun promptRestore(uri: android.net.Uri) {
        io({ manager.inspectBackup(uri) }) { previewResult ->
            val preview = previewResult.getOrNull()
            if (preview == null) {
                toast("That file is not a LumaOS Vault backup.")
                return@io
            }
            val recoveryField = EditText(this).apply {
                hint = "Recovery key"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                isSaveEnabled = false
                setTextColor(VaultDesign.colorTextPrimary)
                setHintTextColor(VaultDesign.colorTextTertiary)
                typeface = android.graphics.Typeface.MONOSPACE
            }
            val pinField = pinField("New PIN for this phone")
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    VaultDesign.dp(this@VaultSecurityActivity, VaultDesign.spaceXL),
                    VaultDesign.dp(this@VaultSecurityActivity, VaultDesign.spaceXS),
                    VaultDesign.dp(this@VaultSecurityActivity, VaultDesign.spaceXL),
                    0
                )
                addView(VaultUi.caption(this@VaultSecurityActivity, "Backup contains ${preview.itemCount} encrypted item(s), created ${date(preview.createdAtMs)}."))
                addView(recoveryField)
                addView(pinField)
            }
            android.app.AlertDialog.Builder(this)
                .setTitle("Restore this Vault?")
                .setView(layout)
                .setMessage("Restoring replaces the Vault on this phone. The current content is kept until the restore has verified and your new PIN is saved.")
                .setPositiveButton("Restore") { _, _ ->
                    val code = recoveryField.text.toString()
                    val pin = pinField.text.toString()
                    if (!VaultRecoveryCode.isValid(code)) {
                        toast("That recovery key is not complete.")
                        return@setPositiveButton
                    }
                    val busy = VaultUi.busyRow(this, "Verifying and restoring…")
                    container.addView(busy)
                    io({ manager.restoreFrom(uri, code, pin) }) { restoreResult ->
                        container.removeView(busy)
                        when (val outcome = restoreResult.getOrNull()) {
                            is VaultManager.RestoreResult.Success ->
                                toast("Vault restored from backup. ${outcome.itemCount} item(s) are available again.")
                            VaultManager.RestoreResult.WrongRecoveryKey -> toast("That recovery key does not open this backup.")
                            VaultManager.RestoreResult.Corrupt -> toast("This backup is damaged and was not restored.")
                            else -> toast("The restore did not finish. The previous Vault is untouched.")
                        }
                        render()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ------------------------------------------------------------------

    private fun addLockNowSection() {
        container.addView(VaultUi.spacer(this, VaultDesign.spaceLG))
        container.addView(VaultUi.primaryButton(this, "Lock the Vault now") {
            manager.lock(VaultSession.LockReason.USER)
            finishAffinity()
        })
    }

    private fun addEventSection() {
        val card = section("Recent security activity", "Events only - never what you stored.")
        val events = manager.events().take(12)
        if (events.isEmpty()) {
            card.addView(VaultUi.caption(this, "Nothing recorded yet."))
            return
        }
        for (event in events) {
            val row = VaultUi.row(this)
            row.setPadding(0, VaultDesign.dp(this, VaultDesign.spaceXXS), 0, VaultDesign.dp(this, VaultDesign.spaceXXS))
            row.addView(VaultUi.text(this, event.type.label, VaultDesign.typeCaptionSp, VaultDesign.colorTextSecondary))
            row.addView(android.widget.Space(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
            row.addView(VaultUi.text(this, date(event.atMs), VaultDesign.typeMicroSp, VaultDesign.colorTextTertiary))
            card.addView(row)
        }
    }

    private fun date(timestampMs: Long): String =
        if (timestampMs <= 0L) "-"
        else SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(timestampMs))

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }

    override fun onPause() {
        super.onPause()
        biometricGate.cancel()
    }

    companion object {
        private const val REQUEST_BACKUP = 5401
        private const val REQUEST_RESTORE = 5402
    }
}
