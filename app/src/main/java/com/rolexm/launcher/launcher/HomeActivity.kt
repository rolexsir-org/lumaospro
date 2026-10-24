package com.rolexm.launcher.launcher

import android.app.ActivityOptions
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.speech.RecognizerIntent
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rolexm.launcher.R
import com.rolexm.launcher.context.ContextProfile
import com.rolexm.launcher.context.VoiceCommand
import com.rolexm.launcher.context.VoiceCommandParser
import com.rolexm.launcher.core.RolexApp
import com.rolexm.launcher.core.apps.AppEntry
import com.rolexm.launcher.core.apps.AppMatcher
import com.rolexm.launcher.core.apps.DrawerSort
import com.rolexm.launcher.core.model.ComponentKey
import com.rolexm.launcher.core.prefs.GestureKind
import com.rolexm.launcher.core.util.RoleHelper
import com.rolexm.launcher.core.util.RolexLog
import com.rolexm.launcher.effects.EffectsDecision
import com.rolexm.launcher.effects.EffectsPolicy
import com.rolexm.launcher.effects.EffectsPreference
import com.rolexm.launcher.effects.EffectsSignals
import com.rolexm.launcher.icons.IconRenderSpec
import com.rolexm.launcher.intelligence.Profile
import com.rolexm.launcher.intelligence.Profiles
import com.rolexm.launcher.launcher.home.AmbientBackdropView
import com.rolexm.launcher.launcher.home.BezelDrawable
import com.rolexm.launcher.launcher.home.ChronoDialView
import com.rolexm.launcher.launcher.home.DesktopLayout
import com.rolexm.launcher.launcher.home.DrawerAdapter
import com.rolexm.launcher.launcher.home.DrawerController
import com.rolexm.launcher.launcher.home.DrawerRowStyle
import com.rolexm.launcher.launcher.home.EditModeController
import com.rolexm.launcher.launcher.home.FolderOverlay
import com.rolexm.launcher.launcher.home.FolderStyle
import com.rolexm.launcher.launcher.home.HomeGridAdapter
import com.rolexm.launcher.launcher.home.MenuEntry
import com.rolexm.launcher.launcher.home.PlacementGuideView
import com.rolexm.launcher.launcher.home.ReadoutSources
import com.rolexm.launcher.launcher.home.RolexMenu
import com.rolexm.launcher.launcher.home.ThemedDrawables
import com.rolexm.launcher.launcher.home.Typefaces
import com.rolexm.launcher.launcher.model.Chronometer
import com.rolexm.launcher.launcher.model.ClockStyle
import com.rolexm.launcher.launcher.model.DockStyle
import com.rolexm.launcher.launcher.model.DrawerAction
import com.rolexm.launcher.launcher.model.DrawerFilter
import com.rolexm.launcher.launcher.model.DrawerPresentation
import com.rolexm.launcher.launcher.model.DrawerRow
import com.rolexm.launcher.launcher.model.GestureAction
import com.rolexm.launcher.launcher.model.GridRules
import com.rolexm.launcher.launcher.model.HomeItem
import com.rolexm.launcher.launcher.model.HomePreset
import com.rolexm.launcher.launcher.model.SmartKind
import com.rolexm.launcher.launcher.view.AlphaIndexView
import com.rolexm.launcher.motion.MotionPolicy
import com.rolexm.launcher.motion.MotionTokens
import com.rolexm.launcher.motion.SpringAnimator
import com.rolexm.launcher.onboarding.OnboardingActivity
import com.rolexm.launcher.settings.SettingsActivity
import com.rolexm.launcher.ui.theme.Backdrop
import com.rolexm.launcher.ui.theme.ColorMath
import com.rolexm.launcher.ui.theme.IconShapes
import com.rolexm.launcher.ui.theme.ResolvedTheme
import com.rolexm.launcher.ui.theme.ThemeColors
import com.rolexm.launcher.ui.theme.ThemeEngine
import com.rolexm.launcher.ui.theme.ThemeEnvironment
import com.rolexm.launcher.ui.theme.ThemeSettings
import com.rolexm.launcher.widgets.WidgetHostController
import com.rolexm.launcher.widgets.WidgetPicker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ROLEXM's home screen.
 *
 * Deliberately a View hierarchy rather than Compose. This screen is created and
 * drawn on every return to the desktop, on the weakest hardware ROLEXM targets;
 * a static XML tree with RecyclerViews binds in one pass and has no runtime
 * composition to schedule. Compose is used for Settings and the Studio, where
 * the flexibility pays and the cost is paid once.
 *
 * Responsibilities are split: [DrawerController] (drawer sheet, search,
 * categories), [EditModeController] (drag-and-drop editing), [FolderOverlay]
 * (folders), [WidgetHostController] (AppWidgetHost) and [ReadoutSources]
 * (battery/alarm/focus). This class wires them together, applies the theme,
 * composes depth (the desktop receding behind drawer, folders and edit mode),
 * routes gestures and launches apps.
 *
 * No instance state is saved or restored (the manifest declares
 * stateNotNeeded). Everything derives from StateFlows held by
 * [LauncherViewModel], the theme repository and DataStore, so orientation
 * changes and process death both rebuild the same screen from the same source.
 */
class HomeActivity : ComponentActivity(), DrawerController.Host, EditModeController.Host, FolderOverlay.Host {

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

    private val widgetBindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingWidget ?: return@registerForActivityResult
        if (result.resultCode == RESULT_OK) continueWidgetAdd(pending) else {
            widgets.delete(pending.appWidgetId)
            pendingWidget = null
        }
    }

    private lateinit var iconLoader: IconLoader
    private lateinit var widgets: WidgetHostController
    private lateinit var readouts: ReadoutSources

    private lateinit var homeRoot: FrameLayout
    private lateinit var backdrop: AmbientBackdropView
    private lateinit var desktop: DesktopLayout
    private lateinit var homeHeader: LinearLayout
    private lateinit var clusterRow: LinearLayout
    private lateinit var clockColumn: LinearLayout
    private lateinit var headerActions: LinearLayout
    private lateinit var dial: ChronoDialView
    private lateinit var homeClock: TextClock
    private lateinit var homeDate: TextClock
    private lateinit var homeContext: TextView
    private lateinit var homeGrid: RecyclerView
    private lateinit var guide: PlacementGuideView
    private lateinit var homeEmpty: TextView
    private lateinit var searchPill: LinearLayout
    private lateinit var dockSurface: FrameLayout
    private lateinit var dockRow: RecyclerView
    private lateinit var drawerContainer: FrameLayout
    private lateinit var drawerSheet: LinearLayout
    private lateinit var drawerGrid: RecyclerView
    private lateinit var searchField: EditText

    private lateinit var homeAdapter: HomeGridAdapter
    private lateinit var dockAdapter: HomeGridAdapter
    private lateinit var drawerAdapter: DrawerAdapter

    private lateinit var drawer: DrawerController
    private lateinit var edit: EditModeController
    private lateinit var folders: FolderOverlay
    private var widgetPicker: WidgetPicker? = null

    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleDetector: ScaleGestureDetector

    // --- live state -------------------------------------------------------
    private var theme: ResolvedTheme = ThemeEngine.resolve(ThemeSettings(), ThemeEnvironment(true, 0))
    private var appliedTheme: ResolvedTheme? = null
    private var effects: EffectsDecision = EffectsPolicy.decide(
        EffectsPreference.AUTO,
        EffectsSignals(com.rolexm.launcher.intelligence.DeviceTier.MEDIUM, com.rolexm.launcher.intelligence.ProfileId.BALANCED, Build.VERSION.SDK_INT)
    )
    private var motion: MotionPolicy = MotionPolicy.DEFAULT
    private var profile: Profile = Profiles.BALANCED
    private var appearance = Appearance()
    private var gestureActions: Map<GestureKind, GestureAction> = emptyMap()
    private var homeCellConfig = CellConfig(48, true, 1f, ColorMath.WHITE, true)
    private var dockCellConfig = homeCellConfig
    private var drawerCellConfig = CellConfig(48, true, 1f, ColorMath.WHITE, false)
    private var folderCellConfig = drawerCellConfig
    private var widgetsEnabled = true
    private var cellHeightPx = 0
    private var gridColumns = 4
    private var drawerColumns = 4

    private var drawerProgress = 0f
    private var folderProgress = 0f
    private var editProgress = 0f
    private var settleProgress = 1f
    private var lastBlurRadius = -1f
    private var returningFromApp = false
    private var startupMeasured = false
    private var appsLoadedOnce = false
    private var orphanSweepDone = false
    private var pendingWidget: WidgetHostController.Pending? = null
    private var themeAnimator: SpringAnimator? = null

    private val density by lazy { resources.displayMetrics.densityDpi }

    /** Appearance inputs from preferences. */
    private data class Appearance(
        val iconSizeDp: Int = 48,
        val showLabels: Boolean = true,
        val textScale: Float = 1f,
        val gridColumns: Int = 4,
        val gridRows: Int = 4,
        val drawerColumns: Int = 4,
        val drawerPresentation: DrawerPresentation = DrawerPresentation.LIST,
        val preset: HomePreset = HomePreset.DEFAULT,
        val dockStyle: DockStyle? = null,
        val iconPack: String? = null
    ) {
        val effectiveDock: DockStyle get() = dockStyle ?: preset.dock
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        core.themes.onConfiguration(resources.configuration)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_home)
        bindViews()

        iconLoader = IconLoader(core.appRepository, core.iconCache, core.iconPacks, resources)
        iconLoader.onIconReady = ::onIconReady
        widgets = WidgetHostController(this)
        readouts = ReadoutSources(this)

        setupAdapters()
        setupControllers()
        setupGestures()
        setupInsets()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // As the home screen, back must not finish the activity: doing
                // so would leave the device with no home at all.
                when {
                    widgetPicker?.isShowing == true -> widgetPicker?.dismiss()
                    folders.isOpen -> folders.close()
                    drawer.isOpen -> drawer.close()
                    edit.isActive -> edit.exit()
                }
            }
        })

        theme = core.themes.theme.value
        effects = core.effects.decision.value
        motion = core.effects.motion.value
        applyTheme(theme, animate = false)
        observeState()
    }

    override fun onStart() {
        super.onStart()
        core.appRepository.registerCallback(packageCallback)
        widgets.startListening()
        readouts.start()
    }

    override fun onStop() {
        core.appRepository.unregisterCallback(packageCallback)
        widgets.stopListening()
        readouts.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Refresh on every return to the desktop: an app installed while away
        // must appear without waiting for a broadcast round-trip.
        viewModel.refreshApps()
        core.themes.refresh()
        core.effects.refresh()
        readouts.refresh()
        maybeLaunchOnboarding()
        if (returningFromApp) {
            returningFromApp = false
            playReturnSettle()
        }
    }

    override fun onDestroy() {
        iconLoader.shutdown()
        widgets.clearViews()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home on the home screen returns to a clean desktop.
        when {
            widgetPicker?.isShowing == true -> widgetPicker?.dismiss()
            folders.isOpen -> folders.close()
            drawer.isOpen -> drawer.close()
            edit.isActive -> edit.exit()
            else -> homeGrid.smoothScrollToPosition(0)
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            val pending = pendingWidget ?: return
            if (resultCode == RESULT_OK) finishWidgetAdd(pending) else {
                widgets.delete(pending.appWidgetId)
                pendingWidget = null
            }
        }
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
        backdrop = findViewById(R.id.backdrop)
        desktop = findViewById(R.id.desktop)
        homeHeader = findViewById(R.id.home_header)
        clusterRow = findViewById(R.id.cluster_row)
        clockColumn = findViewById(R.id.clock_column)
        headerActions = findViewById(R.id.header_actions)
        dial = findViewById(R.id.home_dial)
        homeClock = findViewById(R.id.home_clock)
        homeDate = findViewById(R.id.home_date)
        homeContext = findViewById(R.id.home_context)
        homeGrid = findViewById(R.id.home_grid)
        guide = findViewById(R.id.placement_guide)
        homeEmpty = findViewById(R.id.home_empty)
        searchPill = findViewById(R.id.home_search_pill)
        dockSurface = findViewById(R.id.dock_surface)
        dockRow = findViewById(R.id.dock_row)
        drawerContainer = findViewById(R.id.drawer_container)
        drawerSheet = findViewById(R.id.drawer_sheet)
        drawerGrid = findViewById(R.id.drawer_grid)
        searchField = findViewById(R.id.search_field)

        homeGrid.layoutManager = GridLayoutManager(this, gridColumns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (::homeAdapter.isInitialized && homeAdapter.isWidget(position)) spanCount else 1
            }
        }
        homeGrid.itemAnimator?.changeDuration = 120
        homeGrid.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            refreshResponsiveIcons()
            updateHomeCellSizing()
        }
        homeGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (::edit.isInitialized && edit.isActive) edit.updateGuideMetrics()
            }
        })
        dockRow.layoutManager = GridLayoutManager(this, gridColumns)
        drawerGrid.layoutManager = createDrawerLayoutManager(DrawerPresentation.GRID)
        drawerGrid.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refreshResponsiveIcons() }
    }

    private fun createDrawerLayoutManager(presentation: DrawerPresentation): RecyclerView.LayoutManager {
        if (presentation == DrawerPresentation.LIST) return LinearLayoutManager(this)
        return GridLayoutManager(this, drawerColumns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (::drawerAdapter.isInitialized && drawerAdapter.isFullSpan(position)) spanCount else 1
            }
        }
    }

    private fun setupAdapters() {
        val gridListener = object : HomeGridAdapter.Listener {
            override fun onItemClick(item: HomeItem, position: Int, view: View) = onHomeItemClick(item, position, view, isDock = false)
            override fun onItemLongClick(item: HomeItem, position: Int, view: View): Boolean {
                if (edit.isActive) return false // ItemTouchHelper takes the long press to drag
                showHomeMenu(item, position, view, isDock = false)
                return true
            }
            override fun onWidgetResize(item: HomeItem.Widget, rows: Int, commit: Boolean) {
                if (commit) {
                    viewModel.resizeWidget(item.appWidgetId, rows)
                    reportWidgetSize(item.appWidgetId, rows)
                }
            }
        }
        homeAdapter = HomeGridAdapter(
            config = { homeCellConfig },
            iconLoader = iconLoader,
            density = density,
            folderStyle = ::folderStyle,
            labelFor = viewModel::labelFor,
            smartMembers = { viewModel.smartMembers(it) },
            widgetHost = object : HomeGridAdapter.WidgetViews {
                override fun viewFor(item: HomeItem.Widget): View? = widgets.viewFor(this@HomeActivity, item.appWidgetId)
                    ?.also { reportWidgetSize(item.appWidgetId, item.rows) }
                override fun rowHeightPx(): Int = cellHeightPx
                override fun editOutline() = ThemedDrawables.surface(
                    ColorMath.withAlpha(theme.colors.accent, 0.08f), theme.corner(18f) * resources.displayMetrics.density,
                    theme.colors.accent, dp(1)
                )
                override fun handle() = ThemedDrawables.surface(theme.colors.accent, dp(12).toFloat())
            },
            listener = gridListener
        )
        homeGrid.adapter = homeAdapter

        dockAdapter = HomeGridAdapter(
            config = { dockCellConfig },
            iconLoader = iconLoader,
            density = density,
            folderStyle = ::folderStyle,
            labelFor = viewModel::labelFor,
            smartMembers = { viewModel.smartMembers(it) },
            widgetHost = null,
            listener = object : HomeGridAdapter.Listener {
                override fun onItemClick(item: HomeItem, position: Int, view: View) = onHomeItemClick(item, position, view, isDock = true)
                override fun onItemLongClick(item: HomeItem, position: Int, view: View): Boolean {
                    showHomeMenu(item, position, view, isDock = true)
                    return true
                }
            }
        )
        dockRow.adapter = dockAdapter

        drawerAdapter = DrawerAdapter(
            cellConfig = { drawerCellConfig },
            presentation = { appearance.drawerPresentation },
            rowStyle = ::drawerRowStyle,
            iconLoader = iconLoader,
            density = density,
            shortcutIcon = ::shortcutIcon,
            onClick = ::onDrawerRowClick,
            onLongClick = ::onDrawerRowLongClick
        )
        drawerGrid.adapter = drawerAdapter
    }

    private fun setupControllers() {
        drawer = DrawerController(
            context = this,
            container = drawerContainer,
            sheet = drawerSheet,
            scrim = findViewById(R.id.drawer_scrim),
            grid = drawerGrid,
            searchField = searchField,
            clearButton = findViewById(R.id.search_clear),
            alphaIndex = findViewById<AlphaIndexView>(R.id.alpha_index),
            emptyView = findViewById(R.id.empty_view),
            chipsScroll = findViewById<HorizontalScrollView>(R.id.drawer_chips_scroll),
            chips = findViewById(R.id.drawer_chips),
            title = findViewById(R.id.drawer_title),
            subtitle = findViewById(R.id.drawer_subtitle),
            handle = findViewById(R.id.drawer_handle),
            sortButton = findViewById(R.id.drawer_sort),
            host = this
        )
        edit = EditModeController(
            context = this,
            grid = homeGrid,
            adapter = homeAdapter,
            guide = guide,
            topBar = findViewById(R.id.edit_top_bar),
            toolbar = findViewById(R.id.edit_toolbar),
            doneButton = findViewById(R.id.edit_done),
            host = this
        )
        folders = FolderOverlay(this, homeRoot, iconLoader, density, this)

        findViewById<View>(R.id.drawer_close).setOnClickListener { drawer.close() }
        findViewById<View>(R.id.drawer_voice).setOnClickListener { launchVoiceCommand() }
        findViewById<View>(R.id.home_search).setOnClickListener { drawer.open(focusSearch = true) }
        findViewById<View>(R.id.home_settings).setOnClickListener { openSettings() }
        searchPill.setOnClickListener { drawer.open(focusSearch = true) }
        findViewById<View>(R.id.search_pill_voice).setOnClickListener { launchVoiceCommand() }
        findViewById<View>(R.id.search_pill_settings).setOnClickListener { openSettings() }
        homeEmpty.setOnClickListener { drawer.open(focusSearch = false) }
        homeContext.setOnClickListener { showContextMenu(it) }
    }

    /**
     * System bars and cutouts: content is inset by hand because the window
     * draws edge to edge behind translucent bars.
     */
    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(homeRoot) { _, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            desktop.setPadding(safe.left, safe.top + dp(4), safe.right, safe.bottom + dp(4))
            findViewById<View>(R.id.drawer_top_pad).layoutParams =
                findViewById<View>(R.id.drawer_top_pad).layoutParams.apply { height = safe.top }
            findViewById<View>(R.id.drawer_bottom_pad).layoutParams =
                findViewById<View>(R.id.drawer_bottom_pad).layoutParams.apply { height = safe.bottom }
            findViewById<View>(R.id.edit_top_bar).setPadding(dp(20) + safe.left, safe.top + dp(8), dp(12) + safe.right, dp(8))
            (findViewById<View>(R.id.edit_toolbar).layoutParams as FrameLayout.LayoutParams).bottomMargin = safe.bottom + dp(12)
            bottomInset = safe.bottom
            insets
        }
        ViewCompat.requestApplyInsets(homeRoot)
        updateSideBySide()
    }

    private var bottomInset = 0

    private fun updateSideBySide() {
        val cfg = resources.configuration
        desktop.sideBySide = GridRules.useSideBySide(cfg.screenWidthDp, cfg.screenHeightDp)
        homeHeader.gravity = if (desktop.sideBySide) Gravity.CENTER_VERTICAL else Gravity.NO_GRAVITY
    }

    // --------------------------------------------------------------- gestures

    private enum class DragMode { NONE, DRAWER }

    private var dragMode = DragMode.NONE
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var downOnScrollableGrid = false
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }
    private var pinchScale = 1f

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val from = e1 ?: return false
                val dx = e2.x - from.x
                val dy = e2.y - from.y
                val threshold = dp(SWIPE_MIN_DISTANCE_DP)
                if (abs(dy) > threshold && abs(dy) > abs(dx)) {
                    if (dy < 0) {
                        // Swipe up is handled by the finger-tracked drawer when
                        // bound to it; any other binding fires here.
                        if (swipeUpAction() !is GestureAction.OpenDrawer) performGesture(GestureKind.SWIPE_UP)
                    } else if (!homeGrid.canScrollVertically(-1) || !downOnScrollableGrid) {
                        performGesture(GestureKind.SWIPE_DOWN)
                    }
                    return true
                }
                if (abs(dx) > threshold && abs(dx) > abs(dy) * 1.5f) {
                    performGesture(if (dx < 0) GestureKind.SWIPE_LEFT else GestureKind.SWIPE_RIGHT)
                    return true
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Ignored over an icon: the first tap has already launched it.
                if (isOverItem(e.rawX, e.rawY)) return false
                performGesture(GestureKind.DOUBLE_TAP)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (isOverItem(e.rawX, e.rawY)) return
                performGesture(GestureKind.LONG_PRESS)
            }
        })
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                pinchScale = 1f
                return true
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                pinchScale *= detector.scaleFactor
                return true
            }
            override fun onScaleEnd(detector: ScaleGestureDetector) {
                if (pinchScale < 0.78f) performGesture(GestureKind.PINCH_IN)
            }
        })
    }

    private fun swipeUpAction(): GestureAction = gestureActions[GestureKind.SWIPE_UP] ?: GestureKind.SWIPE_UP.default

    private fun overlayBlocksGestures(): Boolean =
        folders.isOpen || widgetPicker?.isShowing == true || edit.isActive

    /**
     * Events are seen at the activity level so a swipe anywhere is recognised
     * while children still receive taps. A vertical drag that should move the
     * drawer is claimed after the touch slop; children then get CANCEL.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (overlayBlocksGestures()) return super.dispatchTouchEvent(ev)
        val tracker = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
        tracker.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                lastY = ev.rawY
                dragMode = DragMode.NONE
                downOnScrollableGrid = isInside(homeGrid, ev.rawX, ev.rawY) &&
                    (homeGrid.canScrollVertically(1) || homeGrid.canScrollVertically(-1))
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragMode == DragMode.NONE && ev.pointerCount == 1) {
                    val dy = ev.rawY - downY
                    val dx = ev.rawX - downX
                    if (abs(dy) > touchSlop * 1.5f && abs(dy) > abs(dx) * 1.3f) {
                        val opening = !drawer.isOpen && dy < 0 && swipeUpAction() is GestureAction.OpenDrawer &&
                            !(downOnScrollableGrid && homeGrid.canScrollVertically(1))
                        val closing = drawer.isOpen && dy > 0 && drawer.canDragClose() &&
                            !isInside(findViewById(R.id.alpha_index), downX, downY)
                        if (opening || closing) {
                            dragMode = DragMode.DRAWER
                            lastY = ev.rawY
                            drawer.startTracking()
                            if (closing) drawer.hideKeyboard()
                            val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                            super.dispatchTouchEvent(cancel)
                            cancel.recycle()
                        }
                    }
                }
                if (dragMode == DragMode.DRAWER) {
                    drawer.trackBy(ev.rawY - lastY)
                    lastY = ev.rawY
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragMode == DragMode.DRAWER) {
                    tracker.computeCurrentVelocity(1000)
                    drawer.release(tracker.yVelocity)
                    dragMode = DragMode.NONE
                    recycleTracker()
                    return true
                }
                recycleTracker()
            }
        }
        if (!drawer.isOpen) {
            gestureDetector.onTouchEvent(ev)
            scaleDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun recycleTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun isInside(view: View, rawX: Float, rawY: Float): Boolean {
        if (view.visibility != View.VISIBLE) return false
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        return rawX >= loc[0] && rawY >= loc[1] && rawX <= loc[0] + view.width && rawY <= loc[1] + view.height
    }

    /** True when the point is over an actual cell or control rather than empty space. */
    private fun isOverItem(rawX: Float, rawY: Float): Boolean {
        for (control in arrayOf(headerActions, homeEmpty, searchPill, homeContext, dial)) {
            if (isInside(control, rawX, rawY)) return true
        }
        for (recycler in arrayOf(homeGrid, dockRow)) {
            if (!isInside(recycler, rawX, rawY)) continue
            val loc = IntArray(2)
            recycler.getLocationOnScreen(loc)
            if (recycler.findChildViewUnder(rawX - loc[0], rawY - loc[1]) != null) return true
        }
        return false
    }

    private fun performGesture(kind: GestureKind) {
        when (val action = gestureActions[kind] ?: kind.default) {
            GestureAction.None -> Unit
            GestureAction.OpenDrawer -> drawer.open(focusSearch = false)
            GestureAction.OpenSearch -> drawer.open(focusSearch = true)
            GestureAction.OpenSettings -> openSettings()
            GestureAction.OpenWallpaperPicker -> openWallpaperPicker()
            GestureAction.EnterEditMode -> edit.enter()
            GestureAction.OpenStudio -> openStudio()
            GestureAction.NextContextProfile -> cycleContextProfile()
            GestureAction.VoiceCommand -> launchVoiceCommand()
            is GestureAction.OpenApp -> action.key?.let { launchApp(it, null) }
        }
    }

    // ------------------------------------------------------------ observation

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.isLoading.collect { loading ->
                        if (!loading && !appsLoadedOnce) {
                            appsLoadedOnce = true
                            val apps = viewModel.apps.value
                            if (profile.preloadIcons && apps.isNotEmpty()) {
                                iconLoader.preload(apps.map { it.key }, density)
                            }
                            // Empty/cold launches are real starts too; measuring
                            // only non-empty app lists hid the slowest edge case.
                            measureStartupWhenDrawn()
                        }
                    }
                }

                launch {
                    combine(viewModel.drawer, viewModel.isLoading, viewModel.query) { out, loading, q -> Triple(out, loading, q) }
                        .collect { (out, loading, q) -> drawer.submit(out, drawerAdapter, loading, q) }
                }

                launch {
                    combine(viewModel.categories, viewModel.filter, viewModel.suggestionsEnabled, viewModel.categoriesEnabled,
                        core.launchHistory.records) { cats, filter, sugg, enabled, records ->
                        ChipState(cats, filter, sugg && records.isNotEmpty(), enabled)
                    }.distinctUntilChanged().collect { s -> drawer.bindChips(s.categories, s.filter, s.frequent, s.enabled) }
                }

                launch { viewModel.drawerSort.collect { drawer.currentSort = it } }
                launch { viewModel.prefs.widgetsEnabled.collect { widgetsEnabled = it } }

                launch {
                    combine(viewModel.activeContextProfile, readouts.state) { p, r -> p to r }.collect { (p, r) ->
                        dial.snapshot = r
                        homeContext.text = Chronometer.readoutLine(
                            r, p.title, System.currentTimeMillis(), DateFormat.is24HourFormat(this@HomeActivity),
                            theme.typography.readoutCaps
                        )
                        homeContext.contentDescription = getString(R.string.home_context_description, homeContext.text)
                    }
                }

                launch {
                    var previous: ContextProfile? = null
                    viewModel.activeContextProfile.collect { contextProfile ->
                        if (previous != null && previous != contextProfile) playReturnSettle()
                        previous = contextProfile
                    }
                }

                launch {
                    viewModel.layout.collect { layout -> bindLayout(layout) }
                }

                launch {
                    viewModel.historyVersion.collect {
                        // Smart folders show live members.
                        homeAdapter.items.forEachIndexed { i, item -> if (item is HomeItem.SmartFolder) homeAdapter.notifyItemChanged(i) }
                    }
                }

                launch { core.intelligence.state.collect { state -> profile = state.profile } }

                launch {
                    core.themes.theme.collect { t ->
                        val animate = appliedTheme != null && appliedTheme?.id != t.id && motion.enabled && !motion.reduced
                        theme = t
                        applyTheme(t, animate)
                    }
                }

                launch {
                    core.effects.decision.collect { d ->
                        val changed = d != effects
                        effects = d
                        if (changed) {
                            applyTheme(theme, animate = false)
                            applyDepth()
                        }
                    }
                }

                launch {
                    core.effects.motion.collect { policy ->
                        motion = policy
                        if (policy.reduced) {
                            themeAnimator?.cancel()
                            themeAnimator = null
                            settleProgress = 1f
                            finishTheme(theme)
                            applyDepth()
                        }
                    }
                }

                launch {
                    val flows: List<Flow<Any?>> = listOf(
                        viewModel.prefs.iconSizeDp, viewModel.prefs.showLabels, viewModel.prefs.textScale,
                        viewModel.prefs.gridColumns, viewModel.prefs.gridRows, viewModel.prefs.drawerColumns,
                        viewModel.prefs.drawerPresentation, viewModel.prefs.homePreset,
                        viewModel.prefs.dockStyleOverride, viewModel.prefs.iconPack
                    )
                    combine(flows) { v ->
                        Appearance(
                            iconSizeDp = v[0] as Int, showLabels = v[1] as Boolean, textScale = v[2] as Float,
                            gridColumns = v[3] as Int, gridRows = v[4] as Int, drawerColumns = v[5] as Int,
                            drawerPresentation = v[6] as DrawerPresentation,
                            preset = v[7] as HomePreset, dockStyle = v[8] as DockStyle?, iconPack = v[9] as String?
                        )
                    }.distinctUntilChanged().collect { a ->
                        appearance = a
                        applyAppearance()
                    }
                }

                for (kind in GestureKind.entries) {
                    launch { viewModel.prefs.gesture(kind).collect { action -> gestureActions = gestureActions + (kind to action) } }
                }

                if (!orphanSweepDone) {
                    launch {
                        orphanSweepDone = true
                        val referenced = viewModel.allWidgetIds()
                        val pending = pendingWidget?.appWidgetId
                        widgets.releaseOrphans(if (pending != null) referenced + pending else referenced)
                    }
                }
            }
        }
    }

    private data class ChipState(
        val categories: List<com.rolexm.launcher.core.apps.DrawerCategory>,
        val filter: DrawerFilter,
        val frequent: Boolean,
        val enabled: Boolean
    )

    private fun bindLayout(layout: com.rolexm.launcher.launcher.model.HomeLayout) {
        val page = layout.page(0)
        // Zen shows widgets only; everything else lives in the drawer.
        val shown = if (appearance.preset.showGrid) page else page.filterIsInstance<HomeItem.Widget>()
        homeAdapter.submit(shown)
        homeEmpty.visibility = if (shown.isEmpty() && appearance.preset.showGrid) View.VISIBLE else View.GONE
        val dockItems = layout.dock
        val span = dockItems.size.coerceIn(1, gridColumns.coerceAtLeast(dockItems.size.coerceAtLeast(1)))
        (dockRow.layoutManager as GridLayoutManager).spanCount = span
        dockSurface.visibility = if (dockItems.isEmpty()) View.GONE else View.VISIBLE
        dockAdapter.submit(dockItems)
    }

    // ------------------------------------------------------------- theming

    /**
     * Applies the theme to every home surface. Theme changes animate: all
     * colour roles interpolate together on a gentle spring while the backdrop
     * cross-fades, then type and icons switch once at the end.
     */
    private fun applyTheme(target: ResolvedTheme, animate: Boolean) {
        val from = appliedTheme
        backdrop.setTheme(target, if (animate) 420L else 0L)
        backdrop.setEffects(effects.ambientMotion, effects.ambientFps, effects.atmosphereDetail)
        updateWallpaperFlag(target)
        if (animate && from != null) {
            themeAnimator?.cancel()
            val animator = SpringAnimator(0f) { t ->
                applyColors(target.copy(colors = ThemeColors.lerp(from.colors, target.colors, t.coerceIn(0f, 1f))))
            }
            themeAnimator = animator
            animator.animateTo(1f, MotionTokens.GENTLE, motion) { finishTheme(target) }
        } else {
            finishTheme(target)
        }
    }

    private fun finishTheme(target: ResolvedTheme) {
        appliedTheme = target
        applyColors(target)
        applyTypography(target)
        updateIconSpec()
        drawer.bindChips(viewModel.categories.value, viewModel.filter.value,
            viewModel.suggestionsEnabled.value && core.launchHistory.size > 0, viewModel.categoriesEnabled.value)
        if (::edit.isInitialized) edit.applyTheme()
        refreshAll()
    }

    private fun applyColors(t: ResolvedTheme) {
        val c = t.colors
        val d = resources.displayMetrics.density
        val wallpaper = t.backdrop == Backdrop.WALLPAPER

        // System bars follow what is behind them.
        val lightBars = !ColorMath.isDark(ColorMath.opaque(if (drawerProgress > 0.5f) c.surface0 else homeBaseColor(t)))
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }

        homeClock.setTextColor(c.homeForeground)
        homeClock.setShadowLayer(if (wallpaper) 8f else 4f, 0f, 1f, c.homeShadow)
        homeDate.setTextColor(ColorMath.withAlpha(c.homeForeground, 0.92f))
        homeDate.setShadowLayer(5f, 0f, 1f, c.homeShadow)
        homeContext.setTextColor(c.homeMuted)
        homeContext.setShadowLayer(4f, 0f, 1f, c.homeShadow)
        homeEmpty.setTextColor(c.homeForeground)
        homeEmpty.setShadowLayer(4f, 0f, 1f, c.homeShadow)
        val actionTint = ColorStateList.valueOf(c.homeForeground)
        findViewById<ImageButton>(R.id.home_search).imageTintList = actionTint
        findViewById<ImageButton>(R.id.home_settings).imageTintList = actionTint

        dial.ringColor = ColorMath.withAlpha(c.homeForeground, 0.42f)
        dial.progressColor = if (wallpaper) c.homeForeground else c.accent
        dial.batteryColor = if (wallpaper) c.homeMuted else c.secondary
        dial.markerColor = c.homeForeground

        // Search pill: a theme surface with a fine outline.
        val pillRadius = t.corner(24f) * d
        searchPill.background = ThemedDrawables.ripple(
            c.ripple,
            ThemedDrawables.surface(ColorMath.withAlpha(c.surface1, t.surfaceAlpha(effects.translucency) * 0.96f), pillRadius, c.outline, 1),
            pillRadius
        )
        findViewById<ImageView>(R.id.search_pill_icon).imageTintList = ColorStateList.valueOf(c.onSurfaceMuted)
        findViewById<TextView>(R.id.search_pill_text).setTextColor(c.onSurfaceMuted)
        findViewById<ImageButton>(R.id.search_pill_voice).imageTintList = ColorStateList.valueOf(c.onSurfaceMuted)
        findViewById<ImageButton>(R.id.search_pill_settings).imageTintList = ColorStateList.valueOf(c.onSurfaceMuted)

        applyDockStyle(t)
        drawer.applyTheme(t, effects.translucency)
        findViewById<ImageButton>(R.id.drawer_close).imageTintList = ColorStateList.valueOf(c.onSurface)
        findViewById<ImageButton>(R.id.drawer_voice).imageTintList = ColorStateList.valueOf(c.onSurface)
        findViewById<ImageButton>(R.id.drawer_sort).imageTintList = ColorStateList.valueOf(c.onSurface)

        homeCellConfig = homeCellConfig.copy(
            labelColor = c.homeForeground, labelShadow = true, labelShadowColor = c.homeShadow,
            rippleColor = c.ripple, cornerPx = t.corner(16f) * d
        )
        dockCellConfig = dockCellConfig.copy(
            labelColor = if (appearance.effectiveDock == DockStyle.MINIMAL) c.homeForeground else c.onSurface,
            labelShadow = appearance.effectiveDock == DockStyle.MINIMAL, labelShadowColor = c.homeShadow,
            rippleColor = c.ripple, cornerPx = t.corner(16f) * d
        )
        drawerCellConfig = drawerCellConfig.copy(labelColor = c.onSurface, labelShadow = false, rippleColor = c.ripple, cornerPx = t.corner(16f) * d)
        folderCellConfig = drawerCellConfig
    }

    private fun homeBaseColor(t: ResolvedTheme): Int =
        if (t.backdrop == Backdrop.THEME) t.colors.background
        else if (ColorMath.isDark(ColorMath.opaque(t.colors.homeShadow))) ColorMath.BLACK else ColorMath.WHITE

    private fun applyTypography(t: ResolvedTheme) {
        val style = t.typography
        homeClock.typeface = Typefaces.clock(style)
        homeClock.letterSpacing = style.clockTracking
        homeDate.typeface = Typefaces.readout(style)
        homeDate.letterSpacing = if (style.readoutCaps) style.readoutTracking * 0.6f else style.readoutTracking
        homeDate.isAllCaps = style.readoutCaps
        homeContext.typeface = Typefaces.readout(style)
        homeContext.letterSpacing = style.readoutTracking
        homeEmpty.typeface = Typefaces.label(style)
        findViewById<TextView>(R.id.search_pill_text).typeface = Typefaces.label(style)
        val label = Typefaces.label(style)
        homeCellConfig = homeCellConfig.copy(typeface = label, labelTracking = style.labelTracking)
        dockCellConfig = dockCellConfig.copy(typeface = label, labelTracking = style.labelTracking)
        drawerCellConfig = drawerCellConfig.copy(typeface = label, labelTracking = style.labelTracking)
        folderCellConfig = drawerCellConfig
    }

    private fun applyDockStyle(t: ResolvedTheme) {
        val c = t.colors
        val d = resources.displayMetrics.density
        val lp = dockSurface.layoutParams as ViewGroup.MarginLayoutParams
        when (appearance.effectiveDock) {
            DockStyle.BEZEL -> {
                lp.marginStart = dp(14); lp.marginEnd = dp(14)
                dockSurface.background = BezelDrawable(
                    ColorMath.withAlpha(c.surface1, t.surfaceAlpha(effects.translucency) * 0.94f),
                    ColorMath.withAlpha(ColorMath.WHITE, if (t.isDark) 0.16f else 0.7f),
                    ColorMath.withAlpha(c.outlineStrong, 0.6f),
                    t.corner(26f) * d, d
                )
                dockSurface.elevation = if (effects.iconDepth) dp(3).toFloat() else 0f
            }
            DockStyle.FLOAT -> {
                lp.marginStart = dp(34); lp.marginEnd = dp(34)
                dockSurface.background = BezelDrawable(
                    ColorMath.withAlpha(c.surface1, t.surfaceAlpha(effects.translucency)),
                    ColorMath.withAlpha(ColorMath.WHITE, if (t.isDark) 0.18f else 0.8f),
                    ColorMath.withAlpha(c.outlineStrong, 0.5f),
                    dp(40).toFloat(), d
                )
                dockSurface.elevation = if (effects.iconDepth) dp(10).toFloat() else 0f
            }
            DockStyle.MINIMAL -> {
                lp.marginStart = dp(14); lp.marginEnd = dp(14)
                dockSurface.background = null
                dockSurface.elevation = 0f
            }
        }
        dockSurface.layoutParams = lp
    }

    /** Hides the wallpaper from composition when ROLEXM paints its own backdrop (saves GPU and power). */
    private fun updateWallpaperFlag(t: ResolvedTheme) {
        if (t.backdrop == Backdrop.THEME) window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        else window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
    }

    private fun folderStyle(): FolderStyle {
        val c = theme.colors
        return FolderStyle(
            plate = ColorMath.withAlpha(if (theme.isDark) c.surface2 else c.surface1, 0.86f),
            rim = ColorMath.withAlpha(c.outlineStrong, 0.7f),
            outline = IconShapes.outline(theme.iconShape),
            smartMarker = c.accent
        )
    }

    private fun drawerRowStyle(): DrawerRowStyle {
        val c = theme.colors
        return DrawerRowStyle(
            headerColor = c.onSurfaceMuted,
            headerTypeface = Typefaces.readout(theme.typography),
            headerCaps = true,
            headerTracking = 0.12f,
            titleColor = c.onSurface,
            subtitleColor = c.onSurfaceMuted,
            iconTint = c.onSurface,
            rippleColor = c.ripple,
            cornerPx = theme.corner(14f) * resources.displayMetrics.density
        )
    }

    // ---------------------------------------------------------- appearance

    private fun applyAppearance() {
        val a = appearance
        val widthDp = resources.configuration.screenWidthDp
        val cols = GridRules.effectiveColumns(a.gridColumns, if (desktop.sideBySide) (widthDp * 0.6f).toInt() else widthDp)
        if (cols != gridColumns) {
            gridColumns = cols
            (homeGrid.layoutManager as GridLayoutManager).spanCount = cols
        }
        val currentDrawerManager = drawerGrid.layoutManager
        val presentationChanged = when (a.drawerPresentation) {
            DrawerPresentation.LIST -> currentDrawerManager !is LinearLayoutManager
            DrawerPresentation.GRID -> currentDrawerManager !is GridLayoutManager
        }
        if (presentationChanged) {
            drawerGrid.layoutManager = createDrawerLayoutManager(a.drawerPresentation)
            drawerGrid.recycledViewPool.clear()
            @Suppress("NotifyDataSetChanged")
            drawerAdapter.notifyDataSetChanged()
        }
        drawerColumns = GridRules.effectiveColumns(a.drawerColumns, widthDp)
        if (a.drawerPresentation == DrawerPresentation.GRID) {
            (drawerGrid.layoutManager as GridLayoutManager).spanCount = drawerColumns
        }
        homeCellConfig = homeCellConfig.copy(showLabels = a.showLabels, textScale = a.textScale)
        dockCellConfig = dockCellConfig.copy(showLabels = a.showLabels && a.preset.dockLabels, textScale = a.textScale, cellHeightPx = null)
        drawerCellConfig = drawerCellConfig.copy(showLabels = true, textScale = a.textScale)
        folderCellConfig = drawerCellConfig
        applyPreset(a.preset)
        applyDockStyle(theme)
        updateIconSpec()
        refreshResponsiveIcons()
        updateHomeCellSizing()
        bindLayout(viewModel.layout.value)
        refreshAll()
    }

    /** Spatial arrangement for the chosen composition preset. */
    private fun applyPreset(preset: HomePreset) {
        val clockSp: Float
        when (preset.clock) {
            ClockStyle.DIAL -> {
                dial.visibility = View.VISIBLE
                clockSp = 64f
                clockColumn.gravity = Gravity.START
                homeClock.gravity = Gravity.START
                homeHeader.gravity = Gravity.START
                (homeContext.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.START
            }
            ClockStyle.HERO -> {
                dial.visibility = View.GONE
                clockSp = 92f
                clockColumn.gravity = Gravity.CENTER_HORIZONTAL
                homeClock.gravity = Gravity.CENTER_HORIZONTAL
                (homeContext.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER_HORIZONTAL
            }
            ClockStyle.COMPACT -> {
                dial.visibility = View.GONE
                clockSp = 44f
                clockColumn.gravity = Gravity.START
                homeClock.gravity = Gravity.START
                (homeContext.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.START
            }
        }
        homeClock.setAutoSizeTextTypeUniformWithConfiguration(
            (clockSp * 0.55f).roundToInt(), clockSp.roundToInt(), 1, TypedValue.COMPLEX_UNIT_SP
        )
        // Hero centres the clock; header actions move into the search pill.
        headerActions.visibility = if (preset.clock == ClockStyle.HERO) View.GONE else View.VISIBLE
        findViewById<ImageButton>(R.id.search_pill_settings).visibility =
            if (preset.clock == ClockStyle.HERO) View.VISIBLE else View.GONE
        homeContext.requestLayout()
        homeGrid.visibility = View.VISIBLE
    }

    private fun updateIconSpec() {
        if (!::iconLoader.isInitialized) return
        val sizePx = dp(appearance.iconSizeDp)
        val changed = iconLoader.updateSpec(IconRenderSpec.from(theme, sizePx, effects.iconDepth, appearance.iconPack))
        if (changed) refreshAll()
    }

    /**
     * Keeps the requested icon size when it fits, and scales down at narrow
     * widths so high column counts do not clip icons inside their cells.
     */
    private fun iconSizeForGrid(preferredPx: Int, grid: RecyclerView, columns: Int, insetsPx: Int): Int =
        responsiveIconSizePx(preferredPx, grid.width, insetsPx, columns, dp(8), dp(24))

    private fun refreshResponsiveIcons() {
        if (!::homeAdapter.isInitialized) return
        val requested = dp(appearance.iconSizeDp)
        val homeSize = iconSizeForGrid(requested, homeGrid, gridColumns, homeGrid.paddingLeft + homeGrid.paddingRight)
        if (homeCellConfig.iconSizePx != homeSize) {
            homeCellConfig = homeCellConfig.copy(iconSizePx = homeSize)
            dockCellConfig = dockCellConfig.copy(iconSizePx = homeSize)
            homeAdapter.notifyItemRangeChanged(0, homeAdapter.itemCount)
            dockAdapter.notifyItemRangeChanged(0, dockAdapter.itemCount)
        }
        val drawerSize = if (appearance.drawerPresentation == DrawerPresentation.LIST) requested else
            iconSizeForGrid(requested, drawerGrid, drawerColumns, drawerGrid.paddingLeft + drawerGrid.paddingRight)
        if (drawerCellConfig.iconSizePx != drawerSize) {
            drawerCellConfig = drawerCellConfig.copy(iconSizePx = drawerSize)
            folderCellConfig = drawerCellConfig
            drawerAdapter.notifyItemRangeChanged(0, drawerAdapter.itemCount)
        }
    }

    /**
     * The row preference controls how many rows fit in the visible grid; rows
     * beyond that scroll. A minimum based on icon and label size avoids
     * clipping on compact screens.
     */
    private fun updateHomeCellSizing() {
        if (homeGrid.height <= 0) return
        val labelHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, AppEntryAdapter.BASE_LABEL_SP * homeCellConfig.textScale * 2f, resources.displayMetrics
        ).toInt()
        val minimum = homeCellConfig.iconSizePx + dp(25) + if (homeCellConfig.showLabels) labelHeight else 0
        val target = homeCellHeightPx(homeGrid.height, appearance.gridRows, minimum)
        if (cellHeightPx != target) {
            cellHeightPx = target
            homeCellConfig = homeCellConfig.copy(cellHeightPx = target)
            homeAdapter.notifyItemRangeChanged(0, homeAdapter.itemCount)
            if (::edit.isInitialized && edit.isActive) edit.updateGuideMetrics()
        }
    }

    private fun refreshAll() {
        if (!::homeAdapter.isInitialized) return
        homeAdapter.notifyItemRangeChanged(0, homeAdapter.itemCount)
        dockAdapter.notifyItemRangeChanged(0, dockAdapter.itemCount)
        drawerAdapter.notifyItemRangeChanged(0, drawerAdapter.itemCount)
    }

    private fun onIconReady(key: ComponentKey) {
        homeAdapter.positionsFor(key).forEach { homeAdapter.notifyItemChanged(it) }
        dockAdapter.positionsFor(key).forEach { dockAdapter.notifyItemChanged(it) }
        drawerAdapter.currentList.forEachIndexed { i, row ->
            if (row is DrawerRow.App && row.entry.key == key) drawerAdapter.notifyItemChanged(i)
        }
        folders.refreshIcon(key)
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

    // ------------------------------------------------------------- depth

    /**
     * Spatial depth: the desktop recedes (scale, fade and - on capable devices
     * with blur enabled - a real-time blur) behind the drawer, folders and edit
     * mode. All three progress values combine, so overlapping transitions
     * never fight.
     */
    private fun applyDepth() {
        val allow = effects.depthTransforms && !motion.reduced
        val scale = if (allow) 1f - 0.06f * drawerProgress - 0.04f * folderProgress - 0.08f * editProgress -
            0.04f * (1f - settleProgress) else 1f
        desktop.pivotX = desktop.width / 2f
        desktop.pivotY = if (editProgress > 0f) desktop.height * 0.42f else desktop.height / 2f
        desktop.scaleX = scale
        desktop.scaleY = scale
        desktop.alpha = (1f - 0.45f * drawerProgress) * (1f - 0.25f * folderProgress) * (0.6f + 0.4f * settleProgress)
        if (Build.VERSION.SDK_INT >= 31) {
            val radius = if (effects.blur) maxOf(20f * drawerProgress, 14f * folderProgress) * resources.displayMetrics.density else 0f
            val stepped = (radius * 2f).roundToInt() / 2f
            if (stepped != lastBlurRadius) {
                lastBlurRadius = stepped
                desktop.setRenderEffect(if (stepped >= 1f) RenderEffect.createBlurEffect(stepped, stepped, Shader.TileMode.CLAMP) else null)
            }
        }
        desktop.visibility = if (drawerProgress >= 0.999f && !effects.translucency) View.INVISIBLE else View.VISIBLE
    }

    /** Coming back from an app: the desktop settles forward into place. */
    private fun playReturnSettle() {
        if (!effects.depthTransforms || !motion.enabled || motion.reduced) return
        SpringAnimator(0.4f) { p ->
            settleProgress = p.coerceIn(0f, 1.1f)
            applyDepth()
        }.animateTo(1f, MotionTokens.SNAPPY, motion)
    }

    // ------------------------------------------------------ DrawerController.Host

    override fun theme(): ResolvedTheme = theme
    override fun motion(): MotionPolicy = motion

    override fun onDrawerProgress(progress: Float) {
        drawerProgress = progress
        applyDepth()
        val c = theme.colors
        val lightBars = !ColorMath.isDark(ColorMath.opaque(if (progress > 0.5f) c.surface0 else homeBaseColor(theme)))
        WindowCompat.getInsetsController(window, window.decorView).apply {
            if (isAppearanceLightStatusBars != lightBars) {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    override fun onDrawerOpened() {
        if (core.shortcuts.hasAccess()) viewModel.loadShortcuts()
        drawerSheet.announceForAccessibility(getString(R.string.drawer_title))
    }

    override fun onDrawerClosed() = Unit
    override fun onQueryChanged(query: String) = viewModel.setQuery(query)
    override fun onFilterSelected(filter: DrawerFilter) = viewModel.setFilter(filter)
    override fun onSortSelected(sort: DrawerSort) = viewModel.setSort(sort)

    override fun launchFirst(rows: List<DrawerRow>): Boolean {
        val first = rows.firstOrNull { it is DrawerRow.App || it is DrawerRow.Shortcut } ?: return false
        onDrawerRowClick(first, null)
        return true
    }

    override fun showSortMenu(anchor: View, current: DrawerSort) {
        val entries = listOf(
            MenuEntry(DrawerSort.ALPHABETICAL.ordinal, getString(R.string.sort_alpha), if (current == DrawerSort.ALPHABETICAL) R.drawable.ic_rx_done else null),
            MenuEntry(DrawerSort.MOST_USED.ordinal, getString(R.string.sort_usage), if (current == DrawerSort.MOST_USED) R.drawable.ic_rx_done else null),
            MenuEntry(DrawerSort.NEWEST.ordinal, getString(R.string.sort_newest), if (current == DrawerSort.NEWEST) R.drawable.ic_rx_done else null)
        )
        menu().show(anchor, getString(R.string.sort_title), entries) { viewModel.setSort(DrawerSort.entries[it.id]) }
    }

    // --------------------------------------------------- EditModeController.Host

    override fun depthAllowed(): Boolean = effects.depthTransforms

    override fun onEditProgress(progress: Float) {
        editProgress = progress
        applyDepth()
        searchPill.alpha = 1f - progress
        homeHeader.alpha = 1f - 0.5f * progress
    }

    override fun commitOrder(arranged: List<HomeItem>) = viewModel.reorderHome(arranged)

    override fun dropOnto(source: HomeItem, target: HomeItem) {
        val page = homeAdapter.items
        val s = page.indexOfFirst { HomeGridIdentity.of(it) == HomeGridIdentity.of(source) }
        val t = page.indexOfFirst { HomeGridIdentity.of(it) == HomeGridIdentity.of(target) }
        val display = viewModel.layout.value.page(0)
        val ds = display.indexOfFirst { HomeGridIdentity.of(it) == HomeGridIdentity.of(source) }
        val dt = display.indexOfFirst { HomeGridIdentity.of(it) == HomeGridIdentity.of(target) }
        if (s >= 0 && t >= 0 && ds >= 0 && dt >= 0) viewModel.dropOnto(ds, dt)
    }

    override fun remove(item: HomeItem) {
        if (item is HomeItem.Widget) {
            widgets.delete(item.appWidgetId)
            viewModel.removeWidget(item.appWidgetId)
            return
        }
        val display = viewModel.layout.value.page(0)
        val index = display.indexOfFirst { HomeGridIdentity.of(it) == HomeGridIdentity.of(item) }
        if (index >= 0) viewModel.removeFromHome(index)
    }

    override fun onToolbarAction(action: EditModeController.ToolbarAction, anchor: View) {
        when (action) {
            EditModeController.ToolbarAction.WIDGETS -> showWidgetPicker()
            EditModeController.ToolbarAction.LAYOUT -> showLayoutMenu(anchor)
            EditModeController.ToolbarAction.STUDIO -> openStudio()
            EditModeController.ToolbarAction.WALLPAPER -> openWallpaperPicker()
            EditModeController.ToolbarAction.SETTINGS -> openSettings()
        }
    }

    override fun gridColumns(): Int = gridColumns
    override fun gridRows(): Int = appearance.gridRows
    override fun cellHeight(): Int = cellHeightPx

    // -------------------------------------------------------- FolderOverlay.Host

    override fun translucency(): Boolean = effects.translucency
    override fun folderCellConfig(): CellConfig = folderCellConfig

    override fun onFolderDepth(progress: Float) {
        folderProgress = progress
        applyDepth()
    }

    override fun launchFromFolder(entry: AppEntry, view: View) {
        launchApp(entry.key, view)
        folders.close()
    }

    override fun showFolderMemberMenu(entry: AppEntry, anchor: View, folderId: String?) {
        val entries = ArrayList<MenuEntry>()
        if (folderId != null) entries += MenuEntry(M_REMOVE_FROM_FOLDER, getString(R.string.action_remove_from_folder_short), R.drawable.ic_rx_remove)
        entries += MenuEntry(M_INFO, getString(R.string.action_app_info), R.drawable.ic_rx_info, group = 1)
        entries += MenuEntry(M_UNINSTALL, getString(R.string.action_uninstall), R.drawable.ic_rx_uninstall, destructive = true, group = 1)
        menu().show(anchor, entry.label, entries) { e ->
            when (e.id) {
                M_REMOVE_FROM_FOLDER -> folderId?.let {
                    viewModel.removeFromFolder(it, entry.key)
                    folders.close()
                }
                M_INFO -> appInfo(entry.key)
                M_UNINSTALL -> uninstall(entry.key)
            }
        }
    }

    override fun renameFolder(folderId: String, name: String) = viewModel.renameFolderById(folderId, name)

    // ------------------------------------------------------------- launching

    private fun launchApp(key: ComponentKey, source: View?) {
        val options = if (motion.enabled && !motion.reduced && source != null && source.width > 0) {
            ActivityOptions.makeClipRevealAnimation(source, 0, 0, source.width, source.height).toBundle()
        } else null
        val bounds = source?.let { v -> Rect().also { r -> v.getGlobalVisibleRect(r) } }
        if (core.appRepository.launch(key, bounds, options)) {
            viewModel.recordLaunch(key)
            returningFromApp = true
        } else {
            // The app vanished between the list update and the tap.
            toast(getString(R.string.toast_app_unavailable))
            viewModel.refreshApps()
        }
    }

    private fun onHomeItemClick(item: HomeItem, position: Int, view: View, isDock: Boolean) {
        if (edit.isActive && !isDock) {
            showHomeMenu(item, position, view, isDock = false)
            return
        }
        when (item) {
            is HomeItem.App -> launchApp(item.key, view)
            is HomeItem.Folder -> folders.open(item.name, item.id, item.children.mapNotNull(viewModel::entryFor), smart = false, anchor = view)
            is HomeItem.SmartFolder -> folders.open(
                item.name, item.id, viewModel.smartMembers(item.kind).mapNotNull(viewModel::entryFor), smart = true, anchor = view
            )
            is HomeItem.Widget -> Unit
        }
    }

    private fun onDrawerRowClick(row: DrawerRow, view: View?) {
        when (row) {
            is DrawerRow.App -> {
                launchApp(row.entry.key, view)
                drawer.close()
            }
            is DrawerRow.Shortcut -> {
                val sc = viewModel.shortcutFor(row.ref)
                val bounds = view?.let { v -> Rect().also { r -> v.getGlobalVisibleRect(r) } }
                if (sc == null || !core.shortcuts.launch(sc, bounds, null)) toast(getString(R.string.toast_shortcut_unavailable))
                drawer.close()
            }
            is DrawerRow.Action -> {
                val intent = when (row.action) {
                    DrawerAction.WEB_SEARCH -> Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, row.query)
                    DrawerAction.PLAY_STORE -> Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(row.query)))
                }
                runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { toast(getString(R.string.toast_no_handler)) }
                drawer.close()
            }
            is DrawerRow.Header -> Unit
        }
    }

    private fun onDrawerRowLongClick(row: DrawerRow, anchor: View) {
        val entry = (row as? DrawerRow.App)?.entry ?: return
        showAppMenu(entry.key, entry.label, anchor, context = MenuContext.DRAWER, position = -1)
    }

    private fun shortcutIcon(row: DrawerRow.Shortcut): android.graphics.drawable.Drawable? {
        val sc = viewModel.shortcutFor(row.ref) ?: return null
        return core.shortcuts.icon(sc, density)
    }

    // ----------------------------------------------------------------- menus

    private enum class MenuContext { HOME, DOCK, DRAWER }

    private fun menu() = RolexMenu(this, theme, effects, motion)

    private fun showHomeMenu(item: HomeItem, position: Int, anchor: View, isDock: Boolean) {
        when (item) {
            is HomeItem.App -> showAppMenu(item.key, viewModel.labelFor(item.key), anchor,
                if (isDock) MenuContext.DOCK else MenuContext.HOME, position)
            is HomeItem.Folder, is HomeItem.SmartFolder -> {
                val name = (item as? HomeItem.Folder)?.name ?: (item as HomeItem.SmartFolder).name
                val entries = ArrayList<MenuEntry>()
                entries += MenuEntry(M_OPEN, getString(R.string.action_open_folder), R.drawable.ic_rx_folder)
                if (!isDock) entries += MenuEntry(M_EDIT_HOME, getString(R.string.action_edit_home), R.drawable.ic_rx_move)
                if (isDock) entries += MenuEntry(M_TO_HOME, getString(R.string.action_move_to_home), R.drawable.ic_rx_home_add)
                else entries += MenuEntry(M_TO_DOCK, getString(R.string.action_move_to_dock), R.drawable.ic_rx_dock)
                entries += MenuEntry(M_REMOVE, getString(R.string.action_remove_folder), R.drawable.ic_rx_remove, destructive = true, group = 1)
                menu().show(anchor, name, entries) { e ->
                    when (e.id) {
                        M_OPEN -> onHomeItemClick(item, position, anchor, isDock = false)
                        M_EDIT_HOME -> edit.enter()
                        M_TO_DOCK -> viewModel.moveHomeToDock(position)
                        M_TO_HOME -> viewModel.moveDockToHome(position)
                        M_REMOVE -> if (isDock) viewModel.removeFromDock(position) else viewModel.removeFromHome(position)
                    }
                }
            }
            is HomeItem.Widget -> {
                val entries = ArrayList<MenuEntry>()
                for (rows in 1..4) {
                    entries += MenuEntry(M_RESIZE_BASE + rows,
                        resources.getQuantityString(R.plurals.widget_rows, rows, rows),
                        if (rows == item.rows) R.drawable.ic_rx_done else R.drawable.ic_rx_resize)
                }
                if (!edit.isActive) entries += MenuEntry(M_EDIT_HOME, getString(R.string.action_edit_home), R.drawable.ic_rx_move, group = 1)
                entries += MenuEntry(M_REMOVE, getString(R.string.action_remove_widget), R.drawable.ic_rx_remove, destructive = true, group = 1)
                val label = widgets.infoFor(item.appWidgetId)?.loadLabel(packageManager) ?: getString(R.string.widget_generic)
                menu().show(anchor, label, entries) { e ->
                    when {
                        e.id > M_RESIZE_BASE && e.id <= M_RESIZE_BASE + 4 -> {
                            val rows = e.id - M_RESIZE_BASE
                            viewModel.resizeWidget(item.appWidgetId, rows)
                            reportWidgetSize(item.appWidgetId, rows)
                        }
                        e.id == M_EDIT_HOME -> edit.enter()
                        e.id == M_REMOVE -> remove(item)
                    }
                }
            }
        }
    }

    /** App menu: the app's own shortcuts (when Android allows), then ROLEXM actions. */
    private fun showAppMenu(key: ComponentKey, label: String, anchor: View, context: MenuContext, position: Int) {
        val entries = ArrayList<MenuEntry>()
        val shortcuts = if (core.shortcuts.hasAccess()) core.shortcuts.shortcutsFor(key.packageName) else emptyList()
        shortcuts.forEachIndexed { i, sc ->
            entries += MenuEntry(M_SHORTCUT_BASE + i, sc.label, icon = core.shortcuts.icon(sc, density), group = 0)
        }
        when (context) {
            MenuContext.DRAWER -> {
                entries += MenuEntry(M_ADD_HOME, getString(R.string.action_add_to_home), R.drawable.ic_rx_home_add, group = 1)
                entries += MenuEntry(M_ADD_DOCK, getString(R.string.action_add_to_dock), R.drawable.ic_rx_dock, group = 1)
            }
            MenuContext.HOME -> {
                if (!edit.isActive) entries += MenuEntry(M_EDIT_HOME, getString(R.string.action_edit_home), R.drawable.ic_rx_move, group = 1)
                entries += MenuEntry(M_TO_DOCK, getString(R.string.action_move_to_dock), R.drawable.ic_rx_dock, group = 1)
                entries += MenuEntry(M_REMOVE, getString(R.string.action_remove_from_home), R.drawable.ic_rx_remove, group = 1)
            }
            MenuContext.DOCK -> {
                entries += MenuEntry(M_TO_HOME, getString(R.string.action_move_to_home), R.drawable.ic_rx_home_add, group = 1)
                entries += MenuEntry(M_REMOVE, getString(R.string.action_remove_from_dock), R.drawable.ic_rx_remove, group = 1)
            }
        }
        if (context == MenuContext.DRAWER) {
            val pinned = viewModel.isPinned(key)
            entries += MenuEntry(
                if (pinned) M_UNPIN else M_PIN,
                getString(if (pinned) R.string.action_unpin_app else R.string.action_pin_app),
                R.drawable.ic_rx_pin,
                group = 2
            )
        }
        entries += MenuEntry(M_HIDE, getString(R.string.action_hide_app, viewModel.activeContextProfile.value.title), R.drawable.ic_rx_hide, group = 2)
        entries += MenuEntry(M_INFO, getString(R.string.action_app_info), R.drawable.ic_rx_info, group = 2)
        entries += MenuEntry(M_UNINSTALL, getString(R.string.action_uninstall), R.drawable.ic_rx_uninstall, destructive = true, group = 2)

        menu().show(anchor, label, entries) { e ->
            when {
                e.id >= M_SHORTCUT_BASE -> shortcuts.getOrNull(e.id - M_SHORTCUT_BASE)?.let { sc ->
                    val bounds = Rect().also { anchor.getGlobalVisibleRect(it) }
                    if (!core.shortcuts.launch(sc, bounds, null)) toast(getString(R.string.toast_shortcut_unavailable))
                    else if (drawer.isOpen) drawer.close()
                }
                e.id == M_ADD_HOME -> { viewModel.addToHome(key); toast(getString(R.string.toast_added_to_home)) }
                e.id == M_ADD_DOCK -> { viewModel.addToDock(key); toast(getString(R.string.toast_added_to_dock)) }
                e.id == M_EDIT_HOME -> edit.enter()
                e.id == M_TO_DOCK -> viewModel.moveHomeToDock(position)
                e.id == M_TO_HOME -> viewModel.moveDockToHome(position)
                e.id == M_REMOVE -> if (context == MenuContext.DOCK) viewModel.removeFromDock(position) else viewModel.removeFromHome(position)
                e.id == M_PIN -> viewModel.setAppPinned(key, true)
                e.id == M_UNPIN -> viewModel.setAppPinned(key, false)
                e.id == M_HIDE -> {
                    viewModel.hideApp(key)
                    toast(getString(R.string.toast_app_hidden, label))
                }
                e.id == M_INFO -> appInfo(key)
                e.id == M_UNINSTALL -> uninstall(key)
            }
        }
    }

    private fun showLayoutMenu(anchor: View) {
        val current = appearance.preset
        val entries = ArrayList<MenuEntry>()
        HomePreset.entries.forEach { p ->
            entries += MenuEntry(M_PRESET_BASE + p.ordinal, p.displayName,
                if (p == current) R.drawable.ic_rx_done else R.drawable.ic_rx_layout, group = 0)
        }
        entries += MenuEntry(M_SMART_BASE + SmartKind.FREQUENT.ordinal, getString(R.string.smart_add_frequent), R.drawable.ic_rx_spark, group = 1)
        entries += MenuEntry(M_SMART_BASE + SmartKind.RECENT.ordinal, getString(R.string.smart_add_recent), R.drawable.ic_rx_spark, group = 1)
        entries += MenuEntry(M_SMART_BASE + SmartKind.NEW_APPS.ordinal, getString(R.string.smart_add_new), R.drawable.ic_rx_spark, group = 1)
        menu().show(anchor, getString(R.string.edit_layout), entries) { e ->
            when {
                e.id >= M_SMART_BASE -> {
                    val kind = SmartKind.entries[e.id - M_SMART_BASE]
                    val name = when (kind) {
                        SmartKind.FREQUENT -> getString(R.string.smart_frequent)
                        SmartKind.RECENT -> getString(R.string.smart_recent)
                        SmartKind.NEW_APPS -> getString(R.string.smart_new)
                    }
                    viewModel.addSmartFolder(kind, name)
                }
                e.id >= M_PRESET_BASE -> lifecycleScope.launch {
                    viewModel.prefs.setHomePreset(HomePreset.entries[e.id - M_PRESET_BASE])
                }
            }
        }
    }

    private fun showContextMenu(anchor: View) {
        val active = viewModel.activeContextProfile.value
        val entries = ContextProfile.entries.map { p ->
            MenuEntry(p.ordinal, p.title, if (p == active) R.drawable.ic_rx_done else null)
        }
        menu().show(anchor, getString(R.string.context_menu_title), entries) { e ->
            lifecycleScope.launch { viewModel.prefs.setActiveContextProfile(ContextProfile.entries[e.id]) }
        }
    }

    private fun cycleContextProfile() {
        val all = ContextProfile.entries
        val next = all[(viewModel.activeContextProfile.value.ordinal + 1) % all.size]
        lifecycleScope.launch { viewModel.prefs.setActiveContextProfile(next) }
        toast(getString(R.string.toast_context_switched, next.title))
    }

    // --------------------------------------------------------------- widgets

    private fun showWidgetPicker() {
        if (!widgetsEnabled) {
            toast(getString(R.string.toast_widgets_disabled))
            return
        }
        val picker = widgetPicker ?: WidgetPicker(this, homeRoot, theme, effects, motion, bottomInset).also { widgetPicker = it }
        picker.show(widgets.providers()) { info -> beginWidgetAdd(info) }
    }

    private fun beginWidgetAdd(info: AppWidgetProviderInfo) {
        val (pending, consent) = widgets.begin(info)
        pendingWidget = pending
        if (consent != null) {
            try {
                widgetBindLauncher.launch(consent)
            } catch (_: ActivityNotFoundException) {
                widgets.delete(pending.appWidgetId)
                pendingWidget = null
                toast(getString(R.string.toast_widget_failed))
            }
        } else continueWidgetAdd(pending)
    }

    private fun continueWidgetAdd(pending: WidgetHostController.Pending) {
        if (widgets.needsConfiguration(pending)) {
            if (!widgets.startConfiguration(this, pending, REQUEST_CONFIGURE_WIDGET)) {
                widgets.delete(pending.appWidgetId)
                pendingWidget = null
                toast(getString(R.string.toast_widget_failed))
            }
        } else finishWidgetAdd(pending)
    }

    private fun finishWidgetAdd(pending: WidgetHostController.Pending) {
        pendingWidget = null
        val rows = widgets.defaultRows(pending.info, cellHeightPx)
        viewModel.addWidget(HomeItem.Widget(pending.appWidgetId, pending.info.provider.flattenToString(), rows), position = 0)
    }

    private fun reportWidgetSize(appWidgetId: Int, rows: Int) {
        val d = resources.displayMetrics.density
        val w = (homeGrid.width - homeGrid.paddingLeft - homeGrid.paddingRight) / d
        val h = (cellHeightPx * rows) / d
        if (w > 0 && h > 0) widgets.reportSize(appWidgetId, w, h)
    }

    // ---------------------------------------------------------- navigation

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    private fun openStudio() = startActivity(
        Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_TAB, SettingsActivity.TAB_STUDIO)
    )

    private fun openWallpaperPicker() {
        runCatching { startActivity(core.appRepository.wallpaperPickerIntent()) }
            .onFailure { RolexLog.w("No wallpaper picker available") }
    }

    private fun appInfo(key: ComponentKey) {
        runCatching { startActivity(core.appRepository.appDetailsIntent(key)) }
            .onFailure { RolexLog.w("App details unavailable for ${key.packageName}") }
    }

    private fun uninstall(key: ComponentKey) {
        runCatching { startActivity(core.appRepository.uninstallIntent(key)) }
            .onFailure { RolexLog.w("Uninstall unavailable for ${key.packageName}") }
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
                drawer.close()
            }
            is VoiceCommand.OpenApp -> {
                val matches = AppMatcher.filter(viewModel.apps.value, command.query)
                val entry = matches.firstOrNull { it.label.equals(command.query, ignoreCase = true) } ?: matches.firstOrNull()
                if (entry == null) toast(getString(R.string.voice_command_app_not_found))
                else {
                    launchApp(entry.key, null)
                    drawer.close()
                }
            }
            VoiceCommand.Unrecognized -> toast(getString(R.string.voice_command_unrecognized))
        }
    }

    // -------------------------------------------------------- package changes

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh(packageName)
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach(::refresh)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach(::refresh)

        private fun refresh(packageName: String) {
            runOnUiThread {
                viewModel.apps.value.asSequence().filter { it.key.packageName == packageName }
                    .forEach { iconLoader.invalidate(it.key) }
                viewModel.refreshApps()
            }
        }
    }

    // ----------------------------------------------------------------- helpers

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val SWIPE_MIN_DISTANCE_DP = 60
        private const val REQUEST_CONFIGURE_WIDGET = 4101

        private const val M_ADD_HOME = 1
        private const val M_ADD_DOCK = 2
        private const val M_INFO = 3
        private const val M_UNINSTALL = 4
        private const val M_REMOVE = 5
        private const val M_HIDE = 6
        private const val M_EDIT_HOME = 7
        private const val M_TO_DOCK = 8
        private const val M_TO_HOME = 9
        private const val M_OPEN = 10
        private const val M_REMOVE_FROM_FOLDER = 11
        private const val M_PIN = 12
        private const val M_UNPIN = 13
        private const val M_RESIZE_BASE = 100
        private const val M_PRESET_BASE = 200
        private const val M_SMART_BASE = 300
        private const val M_SHORTCUT_BASE = 1000
    }
}
