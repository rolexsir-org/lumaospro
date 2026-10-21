package com.rolexm.launcher.launcher

import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.LauncherApps
import android.speech.RecognizerIntent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.UserHandle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rolexm.launcher.R
import com.rolexm.launcher.core.RolexApp
import com.rolexm.launcher.core.apps.AppEntry
import com.rolexm.launcher.core.apps.AppMatcher
import com.rolexm.launcher.core.model.ComponentKey
import com.rolexm.launcher.core.prefs.GestureKind
import com.rolexm.launcher.core.prefs.RolexPrefs
import com.rolexm.launcher.core.util.RoleHelper
import com.rolexm.launcher.core.util.RolexLog
import com.rolexm.launcher.context.ContextProfile
import com.rolexm.launcher.context.VoiceCommand
import com.rolexm.launcher.context.VoiceCommandParser
import com.rolexm.launcher.intelligence.Profile
import com.rolexm.launcher.intelligence.Profiles
import com.rolexm.launcher.launcher.model.GestureAction
import com.rolexm.launcher.launcher.model.HomeItem
import com.rolexm.launcher.launcher.view.AlphaIndexView
import com.rolexm.launcher.onboarding.OnboardingActivity
import com.rolexm.launcher.settings.SettingsActivity
import com.rolexm.launcher.ui.Palette
import com.rolexm.launcher.ui.Palettes
import com.rolexm.launcher.ui.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * ROLEXM's home screen.
 *
 * Deliberately a View hierarchy rather than Compose. This screen is created and
 * drawn on every return to the desktop, on the weakest hardware ROLEXM targets;
 * a static XML tree with a RecyclerView binds in one pass and has no runtime
 * composition to schedule. Compose is used for the settings and diagnostics
 * screens, where the flexibility pays and the cost is paid once.
 *
 * No instance state is saved or restored (the manifest declares
 * stateNotNeeded). Everything derives from StateFlows held by
 * [LauncherViewModel] and DataStore, so orientation changes and process death
 * both rebuild the same screen from the same source.
 */
class HomeActivity : ComponentActivity(), DrawerItemActions, HomeItemActions {

    private val core by lazy { (application as RolexApp).core }
    private val viewModel by lazy { ViewModelProvider(this)[LauncherViewModel::class.java] }

    private val voiceCommandLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val transcript = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (transcript != null) handleVoiceCommand(transcript)
        }
    }

    private lateinit var iconLoader: IconLoader

    private lateinit var homeRoot: FrameLayout
    private lateinit var desktop: LinearLayout
    private lateinit var homeHeader: View
    private lateinit var homeGrid: RecyclerView
    private lateinit var homeEmpty: TextView
    private lateinit var homeClock: TextView
    private lateinit var homeDate: TextView
    private lateinit var homeContext: TextView
    private lateinit var dockSurface: FrameLayout
    private lateinit var dockRow: RecyclerView
    private lateinit var drawerContainer: FrameLayout
    private lateinit var drawerSheet: LinearLayout
    private lateinit var drawerScrim: View
    private lateinit var drawerGrid: RecyclerView
    private lateinit var searchField: EditText
    private lateinit var emptyView: TextView
    private lateinit var alphaIndex: AlphaIndexView
    private lateinit var drawerTopPad: View
    private lateinit var drawerBottomPad: View

    private lateinit var drawerAdapter: AppEntryAdapter
    private lateinit var homeAdapter: HomeItemAdapter
    private lateinit var dockAdapter: HomeItemAdapter

    private lateinit var homeGestureDetector: GestureDetector
    private lateinit var drawerGestureDetector: GestureDetector

    private var palette: Palette = Palettes.LIGHT
    private var profile: Profile = Profiles.BALANCED
    private var homeCellConfig = DEFAULT_HOME_CELL
    private var drawerCellConfig = DEFAULT_DRAWER_CELL
    private var gestureActions: Map<GestureKind, GestureAction> = emptyMap()

    private var gridColumns = 4
    private var requestedIconSizePx = 48
    private var homeRows = RolexPrefs.DEFAULT_GRID_ROWS
    private var drawerColumns = 4
    private var drawerOpen = false
    private var startupMeasured = false
    private var appsLoadedOnce = false

    private val density by lazy { resources.displayMetrics.densityDpi }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        bindViews()

        iconLoader = IconLoader(core.appRepository, core.iconCache)
        iconLoader.onIconReady = ::onIconReady

        setupAdapters()
        setupGestures()
        setupInsets()
        setupSearch()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerOpen) closeDrawer()
                // As the home screen, back must not finish the activity: doing so
                // would leave the device with no home at all.
            }
        })

        observeState()
    }

    override fun onStart() {
        super.onStart()
        core.appRepository.registerCallback(packageCallback)
    }

    override fun onStop() {
        core.appRepository.unregisterCallback(packageCallback)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Refresh on every return to the desktop: an app installed while away
        // must appear without waiting for a broadcast round-trip.
        viewModel.refreshApps()
        maybeLaunchOnboarding()
    }

    override fun onDestroy() {
        iconLoader.shutdown()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Returning home should always land on a clean desktop, never on a
        // leftover open drawer.
        if (drawerOpen) closeDrawer()
    }

    private fun maybeLaunchOnboarding() {
        lifecycleScope.launch {
            if (!viewModel.prefs.isOnboardingDoneOnce() && !RoleHelper.isDefaultLauncher(this@HomeActivity)) {
                startActivity(Intent(this@HomeActivity, OnboardingActivity::class.java))
            }
        }
    }

    // ------------------------------------------------------------------- setup

    private fun bindViews() {
        homeRoot = findViewById(R.id.home_root)
        desktop = findViewById(R.id.desktop)
        homeHeader = findViewById(R.id.home_header)
        homeGrid = findViewById(R.id.home_grid)
        homeEmpty = findViewById(R.id.home_empty)
        homeClock = findViewById(R.id.home_clock)
        homeDate = findViewById(R.id.home_date)
        homeContext = findViewById(R.id.home_context)
        dockSurface = findViewById(R.id.dock_surface)
        dockRow = findViewById(R.id.dock_row)
        drawerContainer = findViewById(R.id.drawer_container)
        drawerSheet = findViewById(R.id.drawer_sheet)
        drawerScrim = findViewById(R.id.drawer_scrim)
        drawerGrid = findViewById(R.id.drawer_grid)
        searchField = findViewById(R.id.search_field)
        emptyView = findViewById(R.id.empty_view)
        alphaIndex = findViewById(R.id.alpha_index)
        drawerTopPad = findViewById(R.id.drawer_top_pad)
        drawerBottomPad = findViewById(R.id.drawer_bottom_pad)

        homeGrid.layoutManager = GridLayoutManager(this, gridColumns)
        homeGrid.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            refreshResponsiveIcons()
            updateHomeCellSizing(homeRows)
        }
        dockRow.layoutManager = GridLayoutManager(this, gridColumns)
        drawerGrid.layoutManager = GridLayoutManager(this, drawerColumns)
        drawerGrid.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            refreshResponsiveIcons()
        }
    }

    private fun setupAdapters() {
        drawerAdapter = AppEntryAdapter(
            config = { drawerCellConfig },
            iconLoader = iconLoader,
            density = density,
            onClick = { entry ->
                launch(entry.key)
                closeDrawer()
            },
            onLongClick = { entry, view -> AppMenus.showDrawerMenu(this, view, entry, this) }
        )
        drawerGrid.adapter = drawerAdapter

        homeAdapter = HomeItemAdapter(
            config = { homeCellConfig },
            iconLoader = iconLoader,
            density = density,
            onClick = { item, _ -> onHomeItemClick(item) },
            onLongClick = { item, position, view -> showHomeMenu(item, position, view, isDock = false) }
        )
        homeAdapter.labelResolver = { viewModel.labelFor(it) }
        homeGrid.adapter = homeAdapter

        dockAdapter = HomeItemAdapter(
            config = { homeCellConfig.copy(cellHeightPx = null) },
            iconLoader = iconLoader,
            density = density,
            onClick = { item, _ -> onHomeItemClick(item) },
            onLongClick = { item, position, view -> showHomeMenu(item, position, view, isDock = true) }
        )
        dockAdapter.labelResolver = { viewModel.labelFor(it) }
        dockRow.adapter = dockAdapter
    }

    /**
     * Applies system-bar insets.
     *
     * The status and navigation bars are translucent in this theme, so content
     * must be inset by hand or the top row of icons sits under the clock.
     */
    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(homeRoot) { _, insets ->
            val safeInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            desktop.setPadding(
                safeInsets.left, safeInsets.top + dp(8),
                safeInsets.right, safeInsets.bottom + dp(8)
            )
            drawerTopPad.layoutParams = drawerTopPad.layoutParams.apply { height = safeInsets.top }
            drawerBottomPad.layoutParams = drawerBottomPad.layoutParams.apply { height = safeInsets.bottom }
            insets
        }
        ViewCompat.requestApplyInsets(homeRoot)
    }

    private fun setupSearch() {
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                viewModel.setQuery(query)
                // The A–Z offsets refer to the unfiltered list; hide the strip
                // while filtered results use a different set of positions.
                alphaIndex.visibility = if (query.isBlank()) View.VISIBLE else View.GONE
            }
        })
        searchField.setOnEditorActionListener { _, _, _ ->
            // Enter launches the top result, which is the point of search.
            viewModel.drawerApps.value.firstOrNull()?.let { entry ->
                launch(entry.key)
                closeDrawer()
            }
            true
        }
    }

    // --------------------------------------------------------------- gestures

    private fun setupGestures() {
        homeGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val from = e1 ?: return false
                val dx = e2.x - from.x
                val dy = e2.y - from.y
                val distanceThreshold = dp(SWIPE_MIN_DISTANCE_DP)
                if (abs(dy) > distanceThreshold && abs(dy) > abs(dx)) {
                    performGesture(if (dy < 0) GestureKind.SWIPE_UP else GestureKind.SWIPE_DOWN)
                    return true
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Ignored over an icon: the first tap has already launched it.
                if (isOverItem(e.x, e.y)) return false
                performGesture(GestureKind.DOUBLE_TAP)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (isOverItem(e.x, e.y)) return
                performGesture(GestureKind.LONG_PRESS)
            }
        })

        drawerGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val from = e1 ?: return false
                val dy = e2.y - from.y
                val dx = e2.x - from.x
                if (dy > dp(SWIPE_MIN_DISTANCE_DP) && abs(dy) > abs(dx)) {
                    closeDrawer()
                    return true
                }
                return false
            }
        })

        drawerScrim.setOnClickListener { closeDrawer() }
        findViewById<View>(R.id.drawer_close).setOnClickListener { closeDrawer() }
        findViewById<View>(R.id.drawer_voice).setOnClickListener { launchVoiceCommand() }
        findViewById<View>(R.id.home_search).setOnClickListener {
            openDrawer()
            searchField.requestFocus()
            showKeyboard()
        }
        findViewById<View>(R.id.home_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        homeEmpty.setOnClickListener { openDrawer() }

        alphaIndex.onLetterTouched = { letter ->
            val index = viewModel.buckets.value.firstOrNull { it.first == letter }?.second
            if (index != null) {
                (drawerGrid.layoutManager as GridLayoutManager)
                    .scrollToPositionWithOffset(index, 0)
            }
        }
    }

    /**
     * Events are fed to the gesture detector at the activity level so that a
     * swipe anywhere on the desktop is recognised, while children still receive
     * taps and long presses normally.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (drawerOpen) {
            drawerGestureDetector.onTouchEvent(ev)
        } else {
            homeGestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    /** True when the point is over an actual grid cell rather than empty space. */
    private fun isOverItem(x: Float, y: Float): Boolean {
        val location = IntArray(2)
        for (control in arrayOf(homeHeader, homeEmpty)) {
            if (control.visibility != View.VISIBLE) continue
            control.getLocationOnScreen(location)
            if (x >= location[0] && y >= location[1] &&
                x <= location[0] + control.width && y <= location[1] + control.height
            ) return true
        }
        for (recycler in arrayOf(homeGrid, dockRow)) {
            recycler.getLocationOnScreen(location)
            val localX = x - location[0]
            val localY = y - location[1]
            if (localX < 0 || localY < 0 || localX > recycler.width || localY > recycler.height) continue
            if (recycler.findChildViewUnder(localX, localY) != null) return true
        }
        return false
    }

    private fun performGesture(kind: GestureKind) {
        when (val action = gestureActions[kind] ?: kind.default) {
            GestureAction.None -> Unit
            GestureAction.OpenDrawer -> openDrawer()
            GestureAction.OpenSearch -> {
                openDrawer()
                searchField.requestFocus()
                showKeyboard()
            }
            GestureAction.OpenSettings -> startActivity(Intent(this, SettingsActivity::class.java))
            GestureAction.OpenWallpaperPicker -> runCatching {
                startActivity(core.appRepository.wallpaperPickerIntent())
            }.onFailure {
                RolexLog.w("No wallpaper picker available")
            }
            is GestureAction.OpenApp -> action.key?.let { launch(it) }
        }
    }

    // ------------------------------------------------------------ observation

    private fun updateDrawerEmptyState() {
        val apps = viewModel.drawerApps.value
        val query = viewModel.query.value
        val loading = viewModel.isLoading.value
        emptyView.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        emptyView.text = when {
            loading -> getString(R.string.drawer_loading_apps)
            query.isNotBlank() -> getString(R.string.empty_no_results, query)
            else -> getString(R.string.empty_no_apps)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.apps.collect { apps ->
                        if (apps.isNotEmpty() && !appsLoadedOnce) {
                            appsLoadedOnce = true
                            if (profile.preloadIcons) {
                                iconLoader.preload(apps.map { it.key }, density)
                            }
                            measureStartupWhenDrawn()
                        }
                    }
                }

                launch {
                    viewModel.drawerApps.collect { list ->
                        drawerAdapter.submitList(list)
                        updateDrawerEmptyState()
                    }
                }

                launch {
                    viewModel.isLoading.collect { updateDrawerEmptyState() }
                }

                launch {
                    viewModel.buckets.collect { buckets ->
                        alphaIndex.letters = buckets.map { it.first }
                    }
                }

                launch {
                    var previous: ContextProfile? = null
                    viewModel.activeContextProfile.collect { contextProfile ->
                        homeContext.text = contextProfile.title.uppercase()
                        if (previous != null && previous != contextProfile && profile.transitionsEnabled) {
                            desktop.animate().cancel()
                            desktop.alpha = 0.86f
                            desktop.animate().alpha(1f)
                                .setDuration((220L * profile.animationScale).toLong().coerceAtLeast(1L))
                                .setInterpolator(DecelerateInterpolator())
                                .start()
                        }
                        previous = contextProfile
                    }
                }

                launch {
                    viewModel.layout.collect { layout ->
                        val homeItems = layout.page(0)
                        homeAdapter.submitList(homeItems)
                        homeEmpty.visibility = if (homeItems.isEmpty()) View.VISIBLE else View.GONE
                        updateDock(layout.dock)
                    }
                }

                launch {
                    core.intelligence.state.collect { state ->
                        profile = state.profile
                    }
                }

                // Appearance: recompute only when an input actually changes.
                launch {
                    // Seven inputs exceed the arity of the typed combine()
                    // overloads, so the Iterable form is used with explicit casts.
                    val appearanceFlows: List<Flow<Any>> = listOf(
                        viewModel.prefs.themeMode,
                        viewModel.prefs.iconSizeDp,
                        viewModel.prefs.showLabels,
                        viewModel.prefs.textScale,
                        viewModel.prefs.gridColumns,
                        viewModel.prefs.gridRows,
                        viewModel.prefs.drawerColumns
                    )
                    combine(appearanceFlows) { values ->
                        @Suppress("UNCHECKED_CAST")
                        Appearance(
                            themeMode = values[0] as ThemeMode,
                            iconSizeDp = values[1] as Int,
                            showLabels = values[2] as Boolean,
                            textScale = values[3] as Float,
                            gridColumns = values[4] as Int,
                            gridRows = values[5] as Int,
                            drawerColumns = values[6] as Int
                        )
                    }.distinctUntilChanged().collect { appearance ->
                        applyAppearance(appearance)
                    }
                }

                for (kind in GestureKind.entries) {
                    launch {
                        viewModel.prefs.gesture(kind)
                            .collect { action -> gestureActions += (kind to action) }
                    }
                }
            }
        }
    }

    /** Holder for the four bindable gestures. */
    private data class Appearance(
        val themeMode: ThemeMode,
        val iconSizeDp: Int,
        val showLabels: Boolean,
        val textScale: Float,
        val gridColumns: Int,
        val gridRows: Int,
        val drawerColumns: Int
    )

    private fun applyAppearance(appearance: Appearance) {
        val systemNight = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        palette = Palettes.forMode(appearance.themeMode, systemNight)

        val iconPx = dp(appearance.iconSizeDp)
        requestedIconSizePx = iconPx

        // Keep wallpaper labels legible in either chosen theme; the soft
        // opposite-colour shadow adds contrast without placing plates behind icons.
        val homeForeground = if (palette.isDark) Color.WHITE else Color.rgb(26, 31, 29)
        val homeShadow = if (palette.isDark) 0x99000000.toInt() else 0x66FFFFFF
        homeCellConfig = CellConfig(
            iconSizePx = iconSizeForGrid(iconPx, homeGrid, appearance.gridColumns, 0),
            showLabels = appearance.showLabels,
            textScale = appearance.textScale,
            labelColor = homeForeground,
            labelShadow = true,
            labelShadowColor = homeShadow
        )
        // The drawer is opaque: themed text, no shadow.
        drawerCellConfig = CellConfig(
            iconSizePx = iconSizeForGrid(
                iconPx,
                drawerGrid,
                appearance.drawerColumns,
                drawerGrid.paddingLeft + drawerGrid.paddingRight
            ),
            showLabels = appearance.showLabels,
            textScale = appearance.textScale,
            labelColor = palette.onSurface,
            labelShadow = false
        )
        homeRows = appearance.gridRows
        updateHomeCellSizing(homeRows)

        if (appearance.gridColumns != gridColumns) {
            gridColumns = appearance.gridColumns
            (homeGrid.layoutManager as GridLayoutManager).spanCount = gridColumns
        }
        if (appearance.drawerColumns != drawerColumns) {
            drawerColumns = appearance.drawerColumns
            (drawerGrid.layoutManager as GridLayoutManager).spanCount = drawerColumns
        }

        // Drawer chrome
        (drawerSheet.background as? GradientDrawable)?.setColor(palette.drawerBackground)
        drawerScrim.setBackgroundColor(palette.scrim)
        (searchField.background as? GradientDrawable)?.setColor(palette.fieldBackground)
        searchField.setTextColor(palette.onSurface)
        searchField.setHintTextColor(palette.onSurfaceVariant)
        emptyView.setTextColor(palette.onSurfaceVariant)
        alphaIndex.setAccentColor(palette.accent)
        findViewById<TextView>(R.id.drawer_title).setTextColor(palette.onSurface)

        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !palette.isDark
            isAppearanceLightNavigationBars = !palette.isDark
        }
        homeClock.setTextColor(homeForeground)
        homeDate.setTextColor(if (palette.isDark) 0xF2FFFFFF.toInt() else 0xF21A1F1D.toInt())
        homeContext.setTextColor(if (palette.isDark) 0xCCFFFFFF.toInt() else 0xCC1A1F1D.toInt())
        homeEmpty.setTextColor(if (palette.isDark) 0xE6FFFFFF.toInt() else 0xE61A1F1D.toInt())
        findViewById<android.widget.ImageButton>(R.id.home_search).imageTintList =
            android.content.res.ColorStateList.valueOf(homeForeground)
        findViewById<android.widget.ImageButton>(R.id.home_settings).imageTintList =
            android.content.res.ColorStateList.valueOf(homeForeground)
        findViewById<android.widget.ImageButton>(R.id.drawer_close).imageTintList =
            android.content.res.ColorStateList.valueOf(palette.onSurface)
        findViewById<android.widget.ImageButton>(R.id.drawer_voice).imageTintList =
            android.content.res.ColorStateList.valueOf(palette.onSurface)
        dockSurface.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(if (palette.isDark) 0xCC0B0F0D.toInt() else 0xE6FFFFFF.toInt())
        }

        // A full rebind is correct here: icon size, label visibility, text
        // scale and colours change every visible row at once, so there is no
        // cheaper invalidation that is also correct. This runs only when the
        // user changes an appearance setting, never per frame.
        homeAdapter.notifyDataSetChanged()
        dockAdapter.notifyDataSetChanged()
        drawerAdapter.notifyDataSetChanged()
    }

    /**
     * Keeps the requested icon size when it fits, and scales down at narrow
     * widths so high column counts do not clip icons inside their cells.
     */
    private fun iconSizeForGrid(
        preferredPx: Int,
        grid: RecyclerView,
        columns: Int,
        horizontalInsetsPx: Int
    ): Int {
        return responsiveIconSizePx(
            preferredPx = preferredPx,
            gridWidthPx = grid.width,
            horizontalInsetsPx = horizontalInsetsPx,
            columns = columns,
            cellGutterPx = dp(8),
            minimumPx = dp(24)
        )
    }

    private fun refreshResponsiveIcons() {
        if (!::homeAdapter.isInitialized) return
        val homeSize = iconSizeForGrid(requestedIconSizePx, homeGrid, gridColumns, 0)
        if (homeCellConfig.iconSizePx != homeSize) {
            homeCellConfig = homeCellConfig.copy(iconSizePx = homeSize)
            homeAdapter.notifyDataSetChanged()
            dockAdapter.notifyDataSetChanged()
        }
        val drawerSize = iconSizeForGrid(
            requestedIconSizePx,
            drawerGrid,
            drawerColumns,
            drawerGrid.paddingLeft + drawerGrid.paddingRight
        )
        if (drawerCellConfig.iconSizePx != drawerSize) {
            drawerCellConfig = drawerCellConfig.copy(iconSizePx = drawerSize)
            drawerAdapter.notifyDataSetChanged()
        }
    }

    /**
     * The home-row preference controls how many rows fit in the visible grid;
     * rows beyond that remain available by scrolling. A minimum based on the
     * current icon and scaled label size avoids clipping on compact screens.
     */
    private fun updateHomeCellSizing(rows: Int) {
        if (homeGrid.height <= 0) return
        val labelHeight = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            AppEntryAdapter.BASE_LABEL_SP * homeCellConfig.textScale * 2f,
            resources.displayMetrics
        ).toInt()
        val minimumHeight = homeCellConfig.iconSizePx + dp(25) + labelHeight
        val targetHeight = homeCellHeightPx(homeGrid.height, rows, minimumHeight)
        if (homeCellConfig.cellHeightPx != targetHeight) {
            homeCellConfig = homeCellConfig.copy(cellHeightPx = targetHeight)
            homeAdapter.notifyDataSetChanged()
        }
    }

    private fun updateDock(items: List<HomeItem>) {
        val span = items.size.coerceIn(1, gridColumns)
        (dockRow.layoutManager as GridLayoutManager).spanCount = span
        dockSurface.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        dockAdapter.submitList(items)
    }

    private fun onIconReady(key: ComponentKey) {
        drawerAdapter.currentList.indexOfFirst { it.key == key }
            .takeIf { it >= 0 }
            ?.let { drawerAdapter.notifyItemChanged(it) }
        homeAdapter.positionsFor(key).forEach { homeAdapter.notifyItemChanged(it) }
        dockAdapter.positionsFor(key).forEach { dockAdapter.notifyItemChanged(it) }
    }

    /**
     * Anchors the startup measurement to the first frame that actually shows
     * apps, not just the first frame of an empty layout.
     */
    private fun measureStartupWhenDrawn() {
        if (startupMeasured) return
        startupMeasured = true
        homeGrid.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                homeGrid.viewTreeObserver.removeOnPreDrawListener(this)
                core.intelligence.onHomeFirstFrame()
                return true
            }
        })
    }

    // ---------------------------------------------------------------- drawer

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        drawerContainer.visibility = View.VISIBLE
        drawerContainer.alpha = 1f
        // The sheet has no measured height while its overlay is GONE; wait for
        // the first layout so the entry transition starts at the real height.
        drawerSheet.post {
            if (drawerOpen) animateDrawerSheet(show = true)
        }
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        hideKeyboard()
        animateDrawerSheet(show = false)
    }

    /**
     * Drawer transition, gated by the active profile.
     *
     * On the Lightweight profile (animationScale 0) the drawer appears
     * instantly: on a slow device a slide animation is itself a source of jank,
     * and instant is more responsive than a stuttering slide.
     */
    private fun animateDrawerSheet(show: Boolean) {
        val duration = (DRAWER_ANIM_MS * profile.animationScale).toLong()
        if (!profile.transitionsEnabled || duration <= 0L) {
            drawerSheet.translationY = if (show) 0f else drawerSheet.height.toFloat()
            drawerScrim.alpha = if (show) 1f else 0f
            if (!show) drawerContainer.visibility = View.GONE
            return
        }

        drawerSheet.animate().cancel()
        drawerScrim.animate().cancel()

        if (show) {
            drawerSheet.translationY = drawerSheet.height.toFloat()
            drawerSheet.animate()
                .translationY(0f)
                .setDuration(duration)
                .setInterpolator(DecelerateInterpolator())
                .start()
            drawerScrim.animate().alpha(1f).setDuration(duration).start()
        } else {
            drawerSheet.animate()
                .translationY(drawerSheet.height.toFloat())
                .setDuration(duration)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { drawerContainer.visibility = View.GONE }
                .start()
            drawerScrim.animate().alpha(0f).setDuration(duration).start()
        }
    }

    private fun showKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(searchField.windowToken, 0)
        searchField.clearFocus()
    }

    // -------------------------------------------------------------- launching

    private fun launch(key: ComponentKey) {
        if (!core.appRepository.launch(key)) {
            // The app vanished between the list update and the tap.
            toast(getString(R.string.toast_app_unavailable))
            viewModel.refreshApps()
        }
    }

    /** Speech is delegated to Android's selected recognition service; ROLEXM records nothing. */
    private fun launchVoiceCommand() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_command_prompt))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            voiceCommandLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            toast(getString(R.string.voice_command_unavailable))
        } catch (_: SecurityException) {
            toast(getString(R.string.voice_command_unavailable))
        }
    }

    private fun handleVoiceCommand(transcript: String) {
        when (val command = VoiceCommandParser.parse(transcript)) {
            is VoiceCommand.SwitchProfile -> lifecycleScope.launch {
                viewModel.prefs.setActiveContextProfile(command.profile)
                closeDrawer()
            }
            is VoiceCommand.OpenApp -> {
                val matches = AppMatcher.filter(viewModel.drawerApps.value, command.query)
                val entry = matches.firstOrNull { it.label.equals(command.query, ignoreCase = true) }
                    ?: matches.firstOrNull()
                if (entry == null) toast(getString(R.string.voice_command_app_not_found))
                else {
                    launch(entry.key)
                    closeDrawer()
                }
            }
            VoiceCommand.Unrecognized -> toast(getString(R.string.voice_command_unrecognized))
        }
    }

    private fun onHomeItemClick(item: HomeItem) {
        when (item) {
            is HomeItem.App -> launch(item.key)
            is HomeItem.Folder -> showFolderPopup(item)
        }
    }

    /** Folder contents in a popup anchored to the folder icon. */
    private fun showFolderPopup(folder: HomeItem.Folder) {
        val anchor = homeGrid.findViewWithTag<View>(folder.id) ?: homeGrid
        val recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@HomeActivity, 3)
        }
        val adapter = AppEntryAdapter(
            config = { drawerCellConfig },
            iconLoader = iconLoader,
            density = density,
            onClick = { entry ->
                launch(entry.key)
                folderPopup?.dismiss()
            },
            onLongClick = { entry, view ->
                AppMenus.showDrawerMenu(this@HomeActivity, view, entry, this@HomeActivity)
            }
        )
        adapter.submitList(folder.children.map { AppEntry(it, viewModel.labelFor(it)) })
        recycler.adapter = adapter

        val popup = android.widget.PopupWindow(
            recycler,
            resources.displayMetrics.widthPixels * 3 / 4,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(palette.surface))
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
        }
        folderPopup = popup
        popup.showAsDropDown(anchor, 0, -dp(8))
    }

    private var folderPopup: android.widget.PopupWindow? = null

    private fun showHomeMenu(item: HomeItem, position: Int, anchor: View, isDock: Boolean) {
        currentMenuIsDock = isDock
        val candidates = if (isDock) emptyList() else buildMergeCandidates(position)
        AppMenus.showHomeItemMenu(this, anchor, item, position, candidates, this)
    }

    /** Other single apps on the home page that this item could merge with. */
    private fun buildMergeCandidates(position: Int): List<Pair<Int, String>> {
        val items = viewModel.layout.value.page(0)
        return items.mapIndexedNotNull { index, item ->
            if (index == position || item !is HomeItem.App) return@mapIndexedNotNull null
            index to viewModel.labelFor(item.key)
        }
    }

    // ------------------------------------------------------ DrawerItemActions

    override fun addToHome(entry: AppEntry) {
        viewModel.addToHome(entry.key)
        toast(getString(R.string.toast_added_to_home))
    }

    override fun addToDock(entry: AppEntry) {
        viewModel.addToDock(entry.key)
        toast(getString(R.string.toast_added_to_dock))
    }

    override fun appInfo(entry: AppEntry) {
        runCatching {
            startActivity(core.appRepository.appDetailsIntent(entry.key))
        }.onFailure { RolexLog.w("App details unavailable for ${entry.key.packageName}") }
    }

    override fun uninstall(entry: AppEntry) {
        runCatching {
            startActivity(core.appRepository.uninstallIntent(entry.key))
        }.onFailure { RolexLog.w("Uninstall unavailable for ${entry.key.packageName}") }
    }

    // -------------------------------------------------------- HomeItemActions

    override fun remove(position: Int) {
        if (currentMenuIsDock) viewModel.removeFromDock(position) else viewModel.removeFromHome(position)
    }

    override fun renameFolder(position: Int) {
        val items = viewModel.layout.value.page(0)
        val folder = items.getOrNull(position) as? HomeItem.Folder ?: return
        val input = EditText(this).apply {
            setText(folder.name)
            setTextColor(palette.onSurface)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.action_rename_folder)
            .setView(input)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.common_ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) viewModel.renameFolder(position, name)
            }
            .show()
    }

    override fun mergeIntoFolder(position: Int, targetPosition: Int) {
        viewModel.createFolder(position, targetPosition)
    }

    override fun removeFromFolder(position: Int, childKey: ComponentKey) {
        viewModel.removeFromFolder(position, childKey)
    }

    override fun appInfo(key: ComponentKey) {
        runCatching { startActivity(core.appRepository.appDetailsIntent(key)) }
            .onFailure { RolexLog.w("App details unavailable for ${key.packageName}") }
    }

    override fun uninstall(key: ComponentKey) {
        runCatching { startActivity(core.appRepository.uninstallIntent(key)) }
            .onFailure { RolexLog.w("Uninstall unavailable for ${key.packageName}") }
    }

    /**
     * Whether the currently open menu belongs to the dock.
     *
     * The dock and the home grid share [HomeItemActions] but mutate different
     * lists; this flag is set when the menu opens. It is a field rather than a
     * parameter because the framework callbacks ([HomeItemActions]) are fixed.
     */
    private var currentMenuIsDock: Boolean = false

    // -------------------------------------------------------- package changes

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()

        private fun refresh() {
            runOnUiThread { viewModel.refreshApps() }
        }
    }

    // ----------------------------------------------------------------- helpers

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val SWIPE_MIN_DISTANCE_DP = 60
        private const val DRAWER_ANIM_MS = 220f
        private val DEFAULT_HOME_CELL = CellConfig(48, true, 1f, Color.WHITE, true)
        private val DEFAULT_DRAWER_CELL = CellConfig(48, true, 1f, Color.WHITE, false)
    }
}
