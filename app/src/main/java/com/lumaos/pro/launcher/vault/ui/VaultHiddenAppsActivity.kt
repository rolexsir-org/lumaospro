package com.lumaos.pro.launcher.vault.ui

import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.lumaos.pro.launcher.core.apps.AppEntry
import com.lumaos.pro.launcher.core.apps.AppMatcher
import com.lumaos.pro.launcher.vault.core.VaultHiddenApp

/**
 * Hidden Apps, inside the Vault.
 *
 * This replaces the old launcher-level "Hide apps" screen. Two things changed and
 * one thing deliberately did not:
 *
 *  - the list, and the act of hiding, now live behind the Vault door, so nothing
 *    about which apps someone hid is readable without authenticating;
 *  - the selection is stored in the Vault's encrypted index rather than in plain
 *    preferences.
 *
 *  - what did *not* change is what "hidden" means: the app leaves the LumaOS Pro
 *    drawer and search, and keeps running. Vault does not uninstall, disable,
 *    suspend, force-stop or clear any application, because none of that is hiding -
 *    it is destruction - and a launcher cannot do most of it without device-owner
 *    privileges it does not have. The screen says exactly this, in these words,
 *    because "hidden in the Vault" must not be read as "isolated from Android".
 */
class VaultHiddenAppsActivity : VaultScreenActivity() {

    private lateinit var container: LinearLayout
    private lateinit var listHost: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var countLine: android.widget.TextView

    private var query: String = ""
    private var showingHiddenOnly: Boolean = true
    private var allApps: List<AppEntry> = emptyList()
    private var appsLoading: Boolean = true
    private var appsLoadFailed: Boolean = false

    /** The two filter chips, restyled in place (rebuilding would drop search focus). */
    private var hiddenChip: android.widget.TextView? = null
    private var allChip: android.widget.TextView? = null

    private val repository: com.lumaos.pro.launcher.core.apps.AppRepository
        get() = (application as com.lumaos.pro.launcher.core.LumaApp).core.appRepository

    override fun onCreateContent(): View {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        container = scrollColumn()
        scroll.addView(container)
        buildChrome()
        loadApps()
        return scroll
    }

    override fun onResume() {
        super.onResume()
        if (!manager.session.isUnlocked) return
        // Hidden state may have changed elsewhere; re-read it, and re-resolve the
        // installed set so an app uninstalled in the meantime disappears.
        loadApps()
    }

    // ------------------------------------------------------------------
    // Chrome (built once, so the search field keeps focus and text)
    // ------------------------------------------------------------------

    private fun buildChrome() {
        container.addView(
            header("Hidden Apps", "Apps removed from the LumaOS drawer and search") { finish() }
        )
        container.addView(
            VaultUi.message(
                this,
                "Hidden here means hidden from the LumaOS Pro launcher - nothing more. The app is not " +
                    "uninstalled, disabled or cleared, it keeps running, its Android data is untouched, " +
                    "and any other launcher or the system settings can still open it. This is not the " +
                    "Android work-profile isolation feature.",
                VaultDesign.colorTextSecondary
            )
        )
        container.addView(VaultUi.spacer(this, VaultDesign.spaceMD))

        countLine = VaultUi.text(this, "", VaultDesign.typeCaptionSp, VaultDesign.colorTextSecondary)
        container.addView(countLine)
        container.addView(VaultUi.spacer(this, VaultDesign.spaceSM))

        searchField = EditText(this).apply {
            hint = "Search installed apps"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            isSaveEnabled = false
            setTextColor(VaultDesign.colorTextPrimary)
            setHintTextColor(VaultDesign.colorTextTertiary)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(
                VaultDesign.dp(this@VaultHiddenAppsActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultHiddenAppsActivity, VaultDesign.spaceSM),
                VaultDesign.dp(this@VaultHiddenAppsActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultHiddenAppsActivity, VaultDesign.spaceSM)
            )
            background = VaultDesign.surfaceDrawable(
                this@VaultHiddenAppsActivity,
                VaultDesign.colorCard,
                VaultDesign.radiusMedium
            )
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString().orEmpty()
                    renderList()
                }
            })
        }
        container.addView(
            searchField,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        container.addView(VaultUi.spacer(this, VaultDesign.spaceSM))

        container.addView(
            VaultUi.row(this).apply {
                hiddenChip = modeChip("Hidden", hiddenOnly = true)
                allChip = modeChip("All apps", hiddenOnly = false)
                addView(
                    hiddenChip,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                addView(
                    allChip,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
            }
        )
        container.addView(VaultUi.spacer(this, VaultDesign.spaceMD))

        listHost = VaultUi.column(this)
        container.addView(listHost)
        container.addView(VaultUi.spacer(this, VaultDesign.spaceXXL))
    }

    private fun modeChip(label: String, hiddenOnly: Boolean): android.widget.TextView =
        VaultUi.text(
            this,
            label,
            VaultDesign.typeCaptionSp,
            VaultDesign.colorTextSecondary,
            VaultUi.Weight.MEDIUM
        ).apply {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            minHeight = VaultDesign.dp(this@VaultHiddenAppsActivity, VaultDesign.minTouchTarget)
            setOnClickListener {
                if (this@VaultHiddenAppsActivity.showingHiddenOnly == hiddenOnly) return@setOnClickListener
                this@VaultHiddenAppsActivity.showingHiddenOnly = hiddenOnly
                styleChips()
                renderList()
            }
        }

    /**
     * Applies the selected/unselected look to both chips.
     *
     * Restyling beats rebuilding the chrome: a rebuild clears the search field,
     * drops its focus and closes the keyboard every time the user switches lists.
     */
    private fun styleChips() {
        styleChip(hiddenChip, selected = showingHiddenOnly)
        styleChip(allChip, selected = !showingHiddenOnly)
    }

    private fun styleChip(chip: android.widget.TextView?, selected: Boolean) {
        // A chip may not exist yet when the very first render runs.
        chip ?: return
        chip.setTextColor(if (selected) VaultDesign.colorAccent else VaultDesign.colorTextSecondary)
        chip.background = VaultDesign.surfaceDrawable(
            this,
            if (selected) VaultDesign.colorAccentSoft else VaultDesign.colorCard,
            VaultDesign.radiusFull,
            android.graphics.Color.TRANSPARENT
        )
        chip.isSelected = selected
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    private fun loadApps() {
        appsLoading = true
        renderList()
        io({
            // LauncherApps enumeration is binder-heavy; the Vault's single I/O lane
            // already keeps it off the main thread. The repository is LumaCore's
            // shared instance so this screen never builds a second app cache.
            repository.refresh()
        }) { result ->
            appsLoading = false
            result.getOrNull()?.let { refreshed ->
                allApps = refreshed
                appsLoadFailed = false
            } ?: run { appsLoadFailed = true }
            renderList()
        }
    }

    private fun hiddenKeys(): Set<String> = manager.hiddenComponentKeys()

    private fun renderList() {
        if (!::listHost.isInitialized) return
        styleChips()
        listHost.removeAllViews()

        if (appsLoading) {
            listHost.addView(VaultUi.busyRow(this, "Reading installed apps…"))
            countLine.text = ""
            return
        }

        val hidden = manager.hiddenApps()
        // One snapshot of the index for this render; membership is checked against
        // it, not by re-reading the Vault state once per row.
        val hiddenKeys = hidden.mapTo(HashSet(hidden.size)) { it.componentKey }

        if (showingHiddenOnly) {
            countLine.text = if (hidden.isEmpty()) {
                "Nothing is hidden yet"
            } else {
                if (hidden.size == 1) "1 app hidden from the launcher" else "${hidden.size} apps hidden from the launcher"
            }
            if (hidden.isEmpty()) {
                listHost.addView(
                    VaultUi.message(
                        this,
                        "No apps are hidden. Switch to \"All apps\" and choose the ones you want removed " +
                            "from the LumaOS drawer.",
                        VaultDesign.colorTextSecondary
                    )
                )
                return
            }
            hidden.sortedBy { it.label.ifBlank { it.packageName }.lowercase() }.forEach { entry ->
                listHost.addView(hiddenRow(entry))
            }
            return
        }

        val byKey = allApps.associateBy { it.key.serialize() }
        val candidates = if (query.isBlank()) allApps else AppMatcher.filter(allApps, query)
        countLine.text = if (appsLoadFailed) {
            "${allApps.size} installed · ${hidden.size} hidden · list may be incomplete"
        } else {
            "${allApps.size} installed · ${hidden.size} hidden"
        }

        if (appsLoadFailed && allApps.isEmpty()) {
            // A failed enumeration is not an empty device. Say so, and offer a retry
            // rather than implying the user has no apps installed.
            listHost.addView(
                VaultUi.message(
                    this,
                    "The installed app list could not be read just now. Your hidden apps are safe and " +
                        "still hidden; reload to try again.",
                    VaultDesign.colorTextSecondary
                )
            )
            listHost.addView(VaultUi.secondaryButton(this, "Reload") { loadApps() })
            return
        }

        if (candidates.isEmpty()) {
            listHost.addView(
                VaultUi.message(
                    this,
                    if (query.isBlank()) "No launchable apps were found on this device." else "No installed app matches that search.",
                    VaultDesign.colorTextSecondary
                )
            )
            return
        }

        candidates.forEach { app ->
            listHost.addView(appRow(app, app.key.serialize() in hiddenKeys))
        }
        // An app that is hidden but no longer installed still needs its entry to be
        // removable; otherwise it would be stuck in the list forever.
        hidden.filterNot { it.componentKey in byKey }.forEach { stale ->
            listHost.addView(hiddenRow(stale))
        }
    }

    // ------------------------------------------------------------------
    // Rows
    // ------------------------------------------------------------------

    private fun appRow(app: AppEntry, isHidden: Boolean): View {
        val row = VaultUi.row(this).apply {
            background = VaultDesign.surfaceDrawable(this@VaultHiddenAppsActivity, VaultDesign.colorCard, VaultDesign.radiusLarge)
            setPadding(
                VaultDesign.dp(this@VaultHiddenAppsActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultHiddenAppsActivity, VaultDesign.spaceSM),
                VaultDesign.dp(this@VaultHiddenAppsActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultHiddenAppsActivity, VaultDesign.spaceSM)
            )
            minimumHeight = VaultDesign.dp(this@VaultHiddenAppsActivity, 64f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = VaultDesign.dp(this@VaultHiddenAppsActivity, VaultDesign.spaceXS) }
        }
        row.addView(iconFor(app.key.packageName, app.key.className))
        val labels = VaultUi.column(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = VaultDesign.dp(this@VaultHiddenAppsActivity, VaultDesign.spaceSM)
            }
            addView(VaultUi.text(this@VaultHiddenAppsActivity, app.label, VaultDesign.typeBodySp, VaultDesign.colorTextPrimary, VaultUi.Weight.MEDIUM))
            addView(
                VaultUi.text(
                    this@VaultHiddenAppsActivity,
                    if (app.key.userSerial != 0L) "${app.key.packageName} · work profile" else app.key.packageName,
                    VaultDesign.typeMicroSp,
                    VaultDesign.colorTextTertiary
                )
            )
        }
        row.addView(labels)
        row.addView(
            if (isHidden) {
                VaultUi.secondaryButton(this, "Restore") {
                    restore(app.key.serialize(), app.key.packageName, app.label)
                }
            } else {
                VaultUi.primaryButton(this, "Hide") { hide(app) }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        )
        return row
    }

    private fun hiddenRow(entry: VaultHiddenApp): View {
        val row = VaultUi.row(this).apply {
            background = VaultDesign.surfaceDrawable(this@VaultHiddenAppsActivity, VaultDesign.colorCard, VaultDesign.radiusLarge)
            setPadding(
                VaultDesign.dp(this@VaultHiddenAppsActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultHiddenAppsActivity, VaultDesign.spaceSM),
                VaultDesign.dp(this@VaultHiddenAppsActivity, VaultDesign.spaceMD),
                VaultDesign.dp(this@VaultHiddenAppsActivity, VaultDesign.spaceSM)
            )
            minimumHeight = VaultDesign.dp(this@VaultHiddenAppsActivity, 64f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = VaultDesign.dp(this@VaultHiddenAppsActivity, VaultDesign.spaceXS) }
        }
        row.addView(iconFor(entry.packageName, entry.className))
        val labels = VaultUi.column(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = VaultDesign.dp(this@VaultHiddenAppsActivity, VaultDesign.spaceSM)
            }
            addView(
                VaultUi.text(
                    this@VaultHiddenAppsActivity,
                    entry.label.ifBlank { entry.packageName },
                    VaultDesign.typeBodySp,
                    VaultDesign.colorTextPrimary,
                    VaultUi.Weight.MEDIUM
                )
            )
            addView(
                VaultUi.text(
                    this@VaultHiddenAppsActivity,
                    entry.packageName,
                    VaultDesign.typeMicroSp,
                    VaultDesign.colorTextTertiary
                )
            )
        }
        row.addView(labels)
        row.addView(
            VaultUi.secondaryButton(this, "Restore") {
                restore(entry.componentKey, entry.packageName, entry.label)
            }
        )
        return row
    }

    /**
     * The app's own icon, resolved through the launcher's shared cache.
     *
     * `PackageManager.getActivityIcon` is the fallback, not the first choice: it is
     * unbuffered, it does not badge work-profile apps, and it re-decodes a drawable
     * for every row on every render.
     */
    private fun iconFor(packageName: String, className: String): ImageView {
        val size = VaultDesign.dp(this, 34f)
        val key = com.lumaos.pro.launcher.core.model.ComponentKey(packageName, className)
        val icon: Drawable? = runCatching { repository.iconFor(key, resources.displayMetrics.densityDpi) }
            .getOrNull()
            ?: runCatching {
                packageManager.getActivityIcon(android.content.ComponentName(packageName, className))
            }.getOrNull()
        return ImageView(this).apply {
            setImageDrawable(icon)
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private fun hide(app: AppEntry) {
        val label = app.label
        io({
            manager.hideApp(
                VaultHiddenApp(app.key.packageName, app.key.className, app.key.userSerial, label)
            )
        }) { result ->
            val hidden = result.getOrDefault(false)
            when {
                hidden -> toast("\"$label\" is now hidden from the LumaOS drawer")
                // A lock that happens mid-write is a real cause, not a mystery
                // failure: say so instead of "could not be hidden".
                result.exceptionOrNull() is IllegalStateException -> {
                    toast("The Vault locked before the change was saved. Nothing was hidden.")
                    openLockScreen()
                    finish()
                }
                else -> toast("That app could not be hidden. Nothing was changed.")
            }
            renderList()
        }
    }

    private fun restore(componentKey: String, packageName: String, label: String) {
        io({ manager.unhideApp(componentKey) }) { result ->
            toast(
                if (result.getOrDefault(false)) "\"${label.ifBlank { packageName }}\" is back in the drawer"
                else "That app was not hidden"
            )
            renderList()
        }
    }
}
