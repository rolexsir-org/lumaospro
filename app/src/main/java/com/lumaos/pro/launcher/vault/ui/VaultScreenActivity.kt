package com.lumaos.pro.launcher.vault.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.vault.core.VaultItem
import com.lumaos.pro.launcher.vault.core.VaultSession
import com.lumaos.pro.launcher.vault.platform.VaultLifecycleGuard
import com.lumaos.pro.launcher.vault.platform.VaultManager
import com.lumaos.pro.launcher.vault.platform.vault

/**
 * Base class for every Vault screen.
 *
 * It carries the rules that must never be forgotten on a single screen:
 *
 *  - `FLAG_SECURE`, so the OS excludes the window from screenshots, screen
 *    recording and the recent-apps thumbnail. Vault content never appears in the
 *    task switcher, even for a moment;
 *  - a session listener that closes the screen the instant the vault locks, so no
 *    decrypted pixels outlive the keys;
 *  - redirect to the lock screen when the activity is resumed while the vault is
 *    locked (process death, timeout, screen-off);
 *  - touch forwarding, so the idle timer measures real interaction;
 *  - a single background lane for storage work ([io]) that keeps disk and crypto
 *    off the main thread without a coroutine scope that outlives the activity.
 */
abstract class VaultScreenActivity : Activity(), VaultLifecycleGuard.VaultScreen {

    protected val manager: VaultManager by lazy { vault() }
    protected val main: Handler = Handler(Looper.getMainLooper())

    private val sessionListener = object : VaultSession.Listener {
        override fun onLocked(reason: VaultSession.LockReason) {
            if (reason == VaultSession.LockReason.PROCESS_DEATH) return
            runOnUiThreadIfAlive { onVaultLocked() }
        }
    }

    /** Root container every screen fills. */
    protected lateinit var root: FrameLayout
        private set

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set before the first frame: the window is secure for its whole life.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        root = FrameLayout(this)
        root.setBackgroundColor(VaultDesign.colorBackgroundTop)
        setContentView(root)
        manager.session.addListener(sessionListener)
        val content = onCreateContent()
        root.addView(
            content,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    /** Build the screen; called once, before the first frame. */
    protected abstract fun onCreateContent(): View

    /** Called when the vault locks while this screen is showing. */
    protected open fun onVaultLocked() {
        finish()
        startActivity(
            Intent(this, VaultLockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    override fun onResume() {
        super.onResume()
        if (requiresUnlockedVault() && !manager.session.isUnlocked) {
            // Never show a screen whose keys are gone.
            startActivity(
                Intent(this, VaultLockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
            return
        }
        manager.session.touch()
    }

    /** Lock screens and setup override this; every content screen keeps it true. */
    protected open fun requiresUnlockedVault(): Boolean = true

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) manager.session.touch()
        return super.dispatchTouchEvent(event)
    }

    override fun onDestroy() {
        manager.session.removeListener(sessionListener)
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Import and export (Storage Access Framework)
    // ------------------------------------------------------------------

    private var pendingExportItem: VaultItem? = null

    /**
     * Explicit export: the user picks the destination, and Vault tells them what
     * that means. Nothing is ever exported without this call.
     */
    protected fun startExport(item: VaultItem) {
        pendingExportItem = item
        android.app.AlertDialog.Builder(this)
            .setTitle("Export a copy?")
            .setMessage(
                "The exported file will be an ordinary file outside the Vault: other apps, galleries " +
                    "and cloud sync can see it from that moment on. The copy inside the Vault stays encrypted."
            )
            .setPositiveButton("Choose location") { _, _ ->
                startActivityForResult(VaultActions.exportPicker(item.mimeType, item.name), VaultActions.BaseRequest.EXPORT)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Opens the system picker to import files; results are encrypted immediately. */
    protected fun startImport(multiple: Boolean = true) {
        runCatching { startActivityForResult(VaultActions.importPicker(multiple), VaultActions.BaseRequest.IMPORT) }
            .onFailure { Toast.makeText(this, "No file manager is available to import from", Toast.LENGTH_LONG).show() }
    }

    /** Called after an import finished, with the number of imported items. */
    protected open fun onImportFinished(count: Int) {}

    /** Called after an export finished. */
    protected open fun onExportFinished(item: VaultItem) {}

    @Deprecated("Platform activity result API; Vault avoids a lifecycle dependency for this.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            pendingExportItem = null
            return
        }
        when (requestCode) {
            VaultActions.BaseRequest.EXPORT -> {
                val item = pendingExportItem ?: return
                val uri = data?.data ?: return
                pendingExportItem = null
                io({ manager.exportTo(item, uri) }) { result ->
                    if (result.isSuccess) {
                        Toast.makeText(this, "Exported a copy of \"${item.name}\"", Toast.LENGTH_LONG).show()
                        onExportFinished(item)
                    } else {
                        Toast.makeText(this, "The export did not finish. The Vault copy is unchanged.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            VaultActions.BaseRequest.IMPORT -> {
                val uris = collectUris(data)
                if (uris.isEmpty()) return
                io({
                    var imported = 0
                    for (uri in uris) {
                        val described = VaultActions.describe(this, uri) ?: continue
                        manager.importUri(described.uri, described.name, described.mimeType)
                        imported++
                    }
                    imported
                }) { result ->
                    val count = result.getOrNull() ?: 0
                    if (count == 0) {
                        Toast.makeText(this, "The import did not finish. Nothing was added to the Vault.", Toast.LENGTH_LONG).show()
                    } else {
                        onImportFinished(count)
                    }
                }
            }
        }
    }

    private fun collectUris(data: Intent?): List<Uri> {
        if (data == null) return emptyList()
        val clip = data.clipData
        if (clip != null && clip.itemCount > 0) {
            return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it)?.uri }
        }
        return listOfNotNull(data.data)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Runs [work] on the vault's single background lane and hands the result back
     * on the main thread, unless the activity is already gone.
     */
    protected fun <T> io(work: () -> T, then: (Result<T>) -> Unit) {
        manager.ioExecutor.execute {
            val result = runCatching(work)
            main.post { if (!isFinishing && !isDestroyed) then(result) }
        }
    }

    protected fun runOnUiThreadIfAlive(action: () -> Unit) {
        if (isFinishing || isDestroyed) return
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action)
    }

    protected fun scrollColumn(): LinearLayout = VaultUi.column(this).apply {
        setPadding(
            VaultDesign.dp(this@VaultScreenActivity, VaultDesign.screenMarginHorizontal),
            VaultDesign.dp(this@VaultScreenActivity, VaultDesign.spaceXL),
            VaultDesign.dp(this@VaultScreenActivity, VaultDesign.screenMarginHorizontal),
            VaultDesign.dp(this@VaultScreenActivity, VaultDesign.spaceXXL)
        )
    }

    /** Standard screen chrome: back affordance, title and optional subtitle. */
    protected fun header(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null): LinearLayout =
        VaultUi.column(this).apply {
            setPadding(0, 0, 0, VaultDesign.dp(this@VaultScreenActivity, VaultDesign.spaceMD))
            val top = VaultUi.row(this@VaultScreenActivity)
            if (onBack != null) {
                top.addView(
                    VaultUi.text(
                        this@VaultScreenActivity,
                        "‹",
                        VaultDesign.typeTitleSp + 8f,
                        VaultDesign.colorTextSecondary,
                        VaultUi.Weight.REGULAR
                    ).apply {
                        isClickable = true
                        setPadding(
                            0,
                            0,
                            VaultDesign.dp(this@VaultScreenActivity, VaultDesign.spaceMD),
                            0
                        )
                        setOnClickListener { onBack() }
                    }
                )
            }
            top.addView(
                VaultUi.text(
                    this@VaultScreenActivity,
                    title,
                    VaultDesign.typeDisplayHeroSp - 12f,
                    VaultDesign.colorTextPrimary,
                    VaultUi.Weight.SEMIBOLD
                )
            )
            addView(top)
            if (!subtitle.isNullOrBlank()) {
                addView(VaultUi.caption(this@VaultScreenActivity, subtitle))
            }
        }
}
