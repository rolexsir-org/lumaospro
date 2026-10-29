package com.lumaos.pro.launcher.launcher

import android.app.ActivityOptions
import android.app.KeyguardManager
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
import android.os.Handler
import android.os.Looper
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
import androidx.core.graphics.Insets
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
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.context.VoiceCommand
import com.lumaos.pro.launcher.context.VoiceCommandParser
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.core.apps.AppEntry
import com.lumaos.pro.launcher.core.apps.AppMatcher
import com.lumaos.pro.launcher.core.apps.DrawerSort
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.core.prefs.GestureKind
import com.lumaos.pro.launcher.core.util.RoleHelper
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.effects.EffectsDecision
import com.lumaos.pro.launcher.effects.EffectsPolicy
import com.lumaos.pro.launcher.effects.EffectsPreference
import com.lumaos.pro.launcher.effects.EffectsSignals
import com.lumaos.pro.launcher.icons.IconRenderSpec
import com.lumaos.pro.launcher.intelligence.Profile
import com.lumaos.pro.launcher.intelligence.Profiles
import com.lumaos.pro.launcher.launcher.home.AmbientBackdropView
import com.lumaos.pro.launcher.launcher.home.ChronoDialView
import com.lumaos.pro.launcher.launcher.home.AppLaunchPortalView
import com.lumaos.pro.launcher.launcher.home.DesktopLayout
import com.lumaos.pro.launcher.launcher.home.DrawerAdapter
import com.lumaos.pro.launcher.launcher.home.DrawerController
import com.lumaos.pro.launcher.launcher.home.DrawerRowStyle
import com.lumaos.pro.launcher.launcher.home.EditModeController
import com.lumaos.pro.launcher.launcher.home.EditModeDesign
import com.lumaos.pro.launcher.launcher.home.FolderOverlay
import com.lumaos.pro.launcher.launcher.home.FolderStyle
import com.lumaos.pro.launcher.launcher.home.HomeGridAdapter
import com.lumaos.pro.launcher.launcher.home.LumaDepthStack
import com.lumaos.pro.launcher.launcher.home.MenuEntry
import com.lumaos.pro.launcher.launcher.home.NotificationPromptSheet
import com.lumaos.pro.launcher.launcher.home.PlacementGuideView
import com.lumaos.pro.launcher.launcher.home.ReadoutSources
import com.lumaos.pro.launcher.launcher.home.LumaMenu
import com.lumaos.pro.launcher.launcher.home.ThemedDrawables
import com.lumaos.pro.launcher.launcher.home.WallpaperArtView
import com.lumaos.pro.launcher.launcher.home.Typefaces
import com.lumaos.pro.launcher.launcher.model.Chronometer
import com.lumaos.pro.launcher.launcher.model.ClockStyle
import com.lumaos.pro.launcher.launcher.model.DockStyle
import com.lumaos.pro.launcher.launcher.model.DrawerAction
import com.lumaos.pro.launcher.launcher.model.DrawerFilter
import com.lumaos.pro.launcher.launcher.model.DrawerPresentation
import com.lumaos.pro.launcher.launcher.model.DrawerRow
import com.lumaos.pro.launcher.launcher.model.GestureAction
import com.lumaos.pro.launcher.launcher.model.GridRules
import com.lumaos.pro.launcher.launcher.model.HomeItem
import com.lumaos.pro.launcher.launcher.model.HomeLayout
import com.lumaos.pro.launcher.launcher.model.HomePreset
import com.lumaos.pro.launcher.launcher.model.SmartKind
import com.lumaos.pro.launcher.launcher.view.AlphaIndexView
import com.lumaos.pro.launcher.motion.LumaHaptics
import com.lumaos.pro.launcher.motion.LumaSpring
import com.lumaos.pro.launcher.motion.MotionPolicy
import com.lumaos.pro.launcher.motion.MotionTokens
import com.lumaos.pro.launcher.motion.SpringAnimator
import com.lumaos.pro.launcher.onboarding.OnboardingActivity
import com.lumaos.pro.launcher.settings.SettingsActivity
import com.lumaos.pro.launcher.ui.glass.GlassViews
import com.lumaos.pro.launcher.ui.theme.LumaTokens
import com.lumaos.pro.launcher.ui.theme.LumaComponents
import com.lumaos.pro.launcher.ui.theme.LumaTypeScale
import com.lumaos.pro.launcher.ui.theme.Backdrop
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.GlassPalette
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.IconShapes
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import com.lumaos.pro.launcher.ui.theme.ThemeColors
import com.lumaos.pro.launcher.ui.theme.ThemeEngine
import com.lumaos.pro.launcher.ui.theme.ThemeEnvironment
import com.lumaos.pro.launcher.ui.theme.ThemeSettings
import com.lumaos.pro.launcher.ui.theme.WallpaperArt
import com.lumaos.pro.launcher.widgets.InsertionPlan
import com.lumaos.pro.launcher.widgets.WidgetCapabilities
import com.lumaos.pro.launcher.widgets.WidgetHostController
import com.lumaos.pro.launcher.widgets.WidgetPicker
import com.lumaos.pro.launcher.widgets.WidgetPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * LumaOS Pro's home screen.
 *
 * Deliberately a View hierarchy rather than Compose. This screen is created and
 * drawn on every return to the desktop, on the weakest hardware LumaOS Pro targets;
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
 * The home layout derives from [LauncherViewModel] and DataStore. A small
 * instance-state record also keeps an in-flight widget bind/configuration
 * associated with its id if Android recreates this activity.
 */
class HomeActivity : ComponentActivity(), DrawerController.Host, EditModeController.Host, FolderOverlay.Host {

    private val core by lazy { (application as LumaApp).core }
    private val viewModel by lazy { ViewModelProvider(this)[LauncherViewModel::class.java] }

    private val voiceCommandLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val transcript = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (transcript != null) handleVoiceCommand(transcript)
        }
    }

    private val hiddenUnlockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pending = pendingHiddenApp
        pendingHiddenApp = null
        if (result.resultCode == RESULT_OK && pending != null) {
            lifecycleScope.launch {
                viewModel.prefs.setAppHidden(pending.profile, pending.key, true)
                toast(getString(R.string.toast_app_hidden, pending.label))
            }
        }
    }

    private val widgetBindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingWidget ?: return@registerForActivityResult
        if (result.resultCode == RESULT_OK) continueWidgetAdd(pending) else {
            releaseWidgetWhenUnreferenced(pending.appWidgetId)
            pendingWidget = null
        }
    }

    private lateinit var iconLoader: IconLoader
    private lateinit var widgets: WidgetHostController
    private lateinit var readouts: ReadoutSources

    private lateinit var homeRoot: FrameLayout
    private lateinit var wallpaperArt: WallpaperArtView
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
    // Edit-mode chrome (see EditModeController / EditModeDesign).
    private lateinit var editScrim: View
    private lateinit var editTopBar: LinearLayout
    private lateinit var editToolbar: LinearLayout
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
    private var notificationPromptSheet: NotificationPromptSheet? = null

    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleDetector: ScaleGestureDetector

    // --- live state -------------------------------------------------------
    private var theme: ResolvedTheme = ThemeEngine.resolve(ThemeSettings(), ThemeEnvironment(true, 0))
    private var appliedTheme: ResolvedTheme? = null
    private var effects: EffectsDecision = EffectsPolicy.decide(
        EffectsPreference.AUTO,
        EffectsSignals(com.lumaos.pro.launcher.intelligence.DeviceTier.MEDIUM, com.lumaos.pro.launcher.intelligence.ProfileId.BALANCED, Build.VERSION.SDK_INT)
    )
    private var glassPalette: GlassPalette = GlassViews.palette(theme, effects)
    private var motion: MotionPolicy = MotionPolicy.DEFAULT
    private var profile: Profile = Profiles.BALANCED
    private var appearance = Appearance()
    private var gestureActions: Map<GestureKind, GestureAction> = emptyMap()
    private var homeCellConfig = CellConfig(LumaTokens.iconAppInt, true, 1f, ColorMath.WHITE, true)
    private var dockCellConfig = homeCellConfig.copy(showLabels = false)
    private var drawerCellConfig = CellConfig(LumaTokens.iconAppInt, true, 1f, ColorMath.WHITE, false)
    private var folderCellConfig = drawerCellConfig
    private var widgetsEnabled = true
    private var hiddenLock = false
    private var pendingHiddenApp: PendingHiddenApp? = null
    private var cellHeightPx = 0
    private var gridColumns = 4
    private var drawerColumns = 4

    /** Window insets are stored once and re-used by every margin calculation. */
    private var safeInsets: Insets = Insets.NONE
    /** The header block's resting top margin, restored when edit mode ends. */
    private var headerTopMarginPx = -1
    private var editBackdropBlurred = false

    private var drawerProgress = 0f
    private var folderProgress = 0f
    private var editProgress = 0f
    private var modalProgress = 0f
    private var launchProgress = 0f
    private var settleProgress = 1f
    private var parallaxX = 0f
    private val parallaxSpring = SpringAnimator(0f, valueThreshold = 0.002f) { p ->
        parallaxX = p
        applyDepth()
    }
    private lateinit var launchPortal: AppLaunchPortalView
    private var lastBlurRadius = -1f
    private var hardwareLayersPromoted = false
    private var screenInteractive = true
    private var systemGuardsRegistered = false
    private val wallpaperRefreshDebounced = Runnable {
        core.themeManager.refreshFromSystemWallpaper()
        core.themes.refresh()
    }
    private val powerAndWallpaperReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenInteractive = false
                    parallaxSpring.cancel()
                    if (::launchPortal.isInitialized) launchPortal.cancelImmediately()
                    backdrop.setEffects(false, effects.ambientFps, effects.atmosphereDetail)
                    if (Build.VERSION.SDK_INT >= 31 && lastBlurRadius != 0f) {
                        lastBlurRadius = 0f
                        desktop.setRenderEffect(null)
                    }
                    setHardwareTransitionLayers(false)
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenInteractive = true
                    backdrop.setEffects(effects.ambientMotion, effects.ambientFps, effects.atmosphereDetail)
                    applyDepth()
                }
                Intent.ACTION_WALLPAPER_CHANGED -> {
                    undoHandler.removeCallbacks(wallpaperRefreshDebounced)
                    undoHandler.postDelayed(
                        wallpaperRefreshDebounced,
                        LumaTokens.wallpaperChangeDebounceMs
                    )
                }
            }
        }
    }
    private var returningFromApp = false
    private var startupMeasured = false
    private var appsLoadedOnce = false
    private var pendingWidget: WidgetHostController.Pending? = null
    private var pendingReconfigureWidgetId: Int? = null
    private var pendingWidgetRemoval: PendingWidgetRemoval? = null
    private val undoHandler = Handler(Looper.getMainLooper())
    private var undoBar: LinearLayout? = null
    private var themeAnimator: SpringAnimator? = null
    /** True while colour roles are interpolating; cells are re-bound once at the end. */
    private var themeAnimating = false

    private val density by lazy { resources.displayMetrics.densityDpi }

    private data class PendingHiddenApp(
        val profile: ContextProfile,
        val key: ComponentKey,
        val label: String
    )

    /** Appearance inputs from preferences. */
    private data class Appearance(
        val iconSizeDp: Int = LumaTokens.iconAppInt,
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
        restorePendingWidgets(savedInstanceState)
        readouts = ReadoutSources(this)

        setupAdapters()
        setupControllers()
        setupGestures()
        setupInsets()
        registerPowerAndWallpaperGuards()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // As the home screen, back must not finish the activity: doing
                // so would leave the device with no home at all.
                when {
                    notificationPromptSheet?.isShowing == true -> notificationPromptSheet?.dismiss()
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

    @Suppress("DEPRECATION")
    private fun restorePendingWidgets(state: Bundle?) {
        val info = state?.getParcelable<AppWidgetProviderInfo>(STATE_PENDING_WIDGET_INFO)
        val id = state?.getInt(STATE_PENDING_WIDGET_ID, -1) ?: -1
        if (info != null && id >= 0) pendingWidget = WidgetHostController.Pending(id, info)
        pendingReconfigureWidgetId = state?.getInt(STATE_RECONFIGURE_WIDGET_ID, -1)?.takeIf { it >= 0 }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingWidget?.let {
            outState.putInt(STATE_PENDING_WIDGET_ID, it.appWidgetId)
            outState.putParcelable(STATE_PENDING_WIDGET_INFO, it.info)
        }
        pendingReconfigureWidgetId?.let { outState.putInt(STATE_RECONFIGURE_WIDGET_ID, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        core.cancelBackgroundCacheRelease()
        core.appRepository.registerCallback(packageCallback)
        widgets.startListening()
        readouts.start()
    }

    override fun onStop() {
        core.appRepository.unregisterCallback(packageCallback)
        widgets.stopListening()
        readouts.stop()
        setHardwareTransitionLayers(false)
        core.scheduleBackgroundCacheRelease()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Refresh on every return to the desktop: an app installed while away
        // must appear without waiting for a broadcast round-trip.
        viewModel.refreshApps()
        core.themes.refresh()
        core.themeManager.refreshFromSystemWallpaper()
        core.effects.refresh()
        readouts.refresh()
        maybeLaunchOnboarding()
        if (returningFromApp) {
            returningFromApp = false
            playReturnSettle()
        }
    }

    override fun onDestroy() {
        undoHandler.removeCallbacks(widgetRemovalTimeout)
        undoHandler.removeCallbacks(wallpaperRefreshDebounced)
        if (systemGuardsRegistered) {
            runCatching { unregisterReceiver(powerAndWallpaperReceiver) }
            systemGuardsRegistered = false
        }
        // The pending layout write may still be in progress. Retaining an id is
        // safer than deleting a widget that a saved layout still references.
        pendingWidgetRemoval = null
        notificationPromptSheet?.shutdown()
        notificationPromptSheet = null
        widgetPicker?.shutdown()
        iconLoader.shutdown()
        widgets.clearViews()
        super.onDestroy()
    }

    private fun registerPowerAndWallpaperGuards() {
        if (systemGuardsRegistered) return
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_WALLPAPER_CHANGED)
        }
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                powerAndWallpaperReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            systemGuardsRegistered = true
        }
    }

    private fun setHardwareTransitionLayers(active: Boolean) {
        if (hardwareLayersPromoted == active) return
        hardwareLayersPromoted = active
        val layerType = if (active) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
        desktop.setLayerType(layerType, null)
        drawerSheet.setLayerType(layerType, null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home on the home screen returns to a clean desktop.
        when {
            notificationPromptSheet?.isShowing == true -> notificationPromptSheet?.dismiss()
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
        when (requestCode) {
            REQUEST_CONFIGURE_WIDGET -> {
                val pending = pendingWidget ?: return
                if (resultCode == RESULT_OK) finishWidgetAdd(pending) else {
                    releaseWidgetWhenUnreferenced(pending.appWidgetId)
                    pendingWidget = null
                }
            }
            // Re-configuring an already placed widget never changes its id or
            // placement; just refresh its reported size when the provider accepts.
            REQUEST_RECONFIGURE_WIDGET -> {
                val id = pendingReconfigureWidgetId
                pendingReconfigureWidgetId = null
                if (resultCode == RESULT_OK && id != null) {
                    val item = homeAdapter.items.filterIsInstance<HomeItem.Widget>()
                        .firstOrNull { it.appWidgetId == id }
                    item?.let { reportWidgetSize(it.appWidgetId, it.rows) }
                }
            }
        }
    }

    private fun maybeLaunchOnboarding() {
        lifecycleScope.launch {
            runCatching { viewModel.prefs.initializeDefaultExperienceIfUnconfigured() }
            val isDefaultHome = RoleHelper.isDefaultLauncher(this@HomeActivity)
            if (!isDefaultHome) {
                if (!viewModel.prefs.isOnboardingDoneOnce()) {
                    startActivity(Intent(this@HomeActivity, OnboardingActivity::class.java))
                }
                return@launch
            }
            if (!viewModel.prefs.isOnboardingDoneOnce()) {
                viewModel.prefs.setOnboardingDone(true)
            }
            if (!com.lumaos.pro.launcher.widgets.music.MusicBridge.hasAccess(this@HomeActivity)) {
                val alreadyPrompted = viewModel.prefs.isNotificationPromptShownOnce()
                if (!alreadyPrompted) {
                    viewModel.prefs.setNotificationPromptShown(true)
                    showOptionalNotificationSheet()
                }
            }
        }
    }

    private fun showOptionalNotificationSheet() {
        if (isFinishing || isDestroyed) return
        val sheet = notificationPromptSheet ?: NotificationPromptSheet(
            context = this,
            root = homeRoot,
            onDepthProgress = { p ->
                modalProgress = p
                applyDepth()
            }
        ).also { notificationPromptSheet = it }
        sheet.show(
            theme = theme,
            effects = effects,
            motion = motion,
            bottomInset = bottomInset,
            onSetUp = {},
            onSkip = {}
        )
    }

    // ------------------------------------------------------------------- setup

    private fun bindViews() {
        homeRoot = findViewById(R.id.home_root)
        wallpaperArt = findViewById(R.id.wallpaper_art)
        backdrop = findViewById(R.id.backdrop)
        wallpaperArt.onUnavailable = {
            // Restore the theme's own backdrop rather than showing an empty panel.
            if (theme.backdrop == Backdrop.LUMA) {
                wallpaperArt.visibility = View.GONE
                backdrop.setFallbackSolid(true)
            }
        }
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
        editScrim = findViewById(R.id.edit_scrim)
        editTopBar = findViewById(R.id.edit_top_bar)
        editToolbar = findViewById(R.id.edit_toolbar)
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
        homeGrid.itemAnimator = LumaSpring.ItemAnimator { motion }
        homeGrid.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            refreshResponsiveIcons()
            updateHomeCellSizing()
            // Cell widths/positions moved: re-anchor the dashed drop zones.
            if (::edit.isInitialized && edit.isActive) edit.updateGuideMetrics()
        }
        homeGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (::edit.isInitialized && edit.isActive) edit.updateGuideMetrics()
            }
        })
        dockRow.layoutManager = GridLayoutManager(this, gridColumns)
        dockRow.itemAnimator = LumaSpring.ItemAnimator { motion }
        drawerGrid.layoutManager = createDrawerLayoutManager(DrawerPresentation.GRID)
        drawerGrid.setHasFixedSize(true)
        drawerGrid.setItemViewCacheSize(LumaTokens.drawerItemViewCacheSize)
        drawerGrid.recycledViewPool.setMaxRecycledViews(0, LumaTokens.drawerRecycledPoolMax)
        drawerGrid.itemAnimator = null
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
            override fun onWidgetResizeOptions(item: HomeItem.Widget, position: Int, view: View) {
                // The ordinary long-press is reserved for dragging in edit mode.
                showHomeMenu(item, position, view, isDock = false)
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
                override fun frame() = widgetFrameDrawable()
                override fun frameRadiusPx() = theme.corner(LumaComponents.of(theme).shapes.widgetDp) * resources.displayMetrics.density
                override fun capabilitiesFor(item: HomeItem.Widget): WidgetCapabilities? =
                    widgets.infoFor(item.appWidgetId)?.let { widgets.capabilities(it, cellHeightPx) }
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
        launchPortal = AppLaunchPortalView(this).apply {
            onLaunchProgress = { p ->
                launchProgress = p
                applyDepth()
            }
        }
        val dScale = resources.displayMetrics.density
        LumaDepthStack.bindLayerElevation(wallpaperArt, LumaTokens.depthWallpaper, dScale)
        LumaDepthStack.bindLayerElevation(backdrop, LumaTokens.depthWallpaper, dScale)
        LumaDepthStack.bindLayerElevation(desktop, LumaTokens.depthHome, dScale)
        LumaDepthStack.bindLayerElevation(drawerContainer, LumaTokens.depthDrawer, dScale)
        LumaDepthStack.bindLayerElevation(launchPortal, LumaTokens.depthLaunchPortal, dScale)
        homeRoot.addView(
            launchPortal,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        findViewById<View>(R.id.drawer_close).setOnClickListener { drawer.close() }
        findViewById<View>(R.id.drawer_voice).setOnClickListener { launchVoiceCommand() }
        findViewById<View>(R.id.home_search).setOnClickListener { drawer.open(focusSearch = true) }
        findViewById<View>(R.id.home_settings).setOnClickListener { openSettings() }
        searchPill.setOnClickListener { drawer.open(focusSearch = true) }
        findViewById<View>(R.id.search_pill_voice).setOnClickListener { launchVoiceCommand() }
        findViewById<View>(R.id.search_pill_settings).setOnClickListener { openSettings() }
        homeEmpty.setOnClickListener { drawer.open(focusSearch = false) }
        homeContext.setOnClickListener { showContextMenu(homeClock) }
        // The clock is the single focal point. Long-press actions keep context
        // selection and settings reachable without header controls or chips.
        homeClock.setOnLongClickListener { showContextMenu(homeClock); true }
        homeDate.setOnLongClickListener { openSettings(); true }
        searchPill.setOnLongClickListener { openSettings(); true }
        homeClock.contentDescription = getString(R.string.home_clock_description) + ". " +
            getString(R.string.home_clock_long_press_hint)
        homeDate.contentDescription = getString(R.string.home_date_long_press_hint)
    }

    /**
     * System bars and cutouts: content is inset by hand because the window
     * draws edge to edge behind translucent bars.
     */
    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(homeRoot) { _, insets ->
            safeInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            applyChromeInsets()
            drawerSheet.setPadding(safeInsets.left, 0, safeInsets.right, 0)
            findViewById<View>(R.id.drawer_top_pad).layoutParams =
                findViewById<View>(R.id.drawer_top_pad).layoutParams.apply { height = safeInsets.top }
            findViewById<View>(R.id.drawer_bottom_pad).layoutParams =
                findViewById<View>(R.id.drawer_bottom_pad).layoutParams.apply { height = safeInsets.bottom }
            bottomInset = safeInsets.bottom
            insets
        }
        ViewCompat.requestApplyInsets(homeRoot)
        updateSideBySide()
    }

    /**
     * Single source of truth for the home composition's insets.
     *
     * At rest: the design system's 24dp screen margin and a 4dp optical inset.
     * While editing, the desktop reserves exactly the chrome's height —
     *   top    = safe area + header top margin + header card + 16dp clear gap
     *   bottom = safe area + toolbar bottom margin + toolbar
     * and the grid, drop zones, dock and chrome all share one 16dp margin, so
     * nothing overlaps and every left/right edge lines up.
     *
     * Idempotent and guarded: it is called from the insets listener, on every
     * edit-mode transition and whenever the chrome's measured height changes.
     */
    private fun applyChromeInsets() {
        if (!::desktop.isInitialized) return
        val safe = safeInsets
        val editing = ::edit.isInitialized && edit.isActive
        val marginPx = currentScreenMarginPx()

        val headerHeight = if (::edit.isInitialized) edit.headerHeightPx() else 0
        val toolbarHeight = if (::edit.isInitialized) edit.toolbarHeightPx() else 0
        val top = if (editing) {
            safe.top + dp(EditModeDesign.headerTopMarginDp.toInt()) + headerHeight +
                dp(EditModeDesign.headerToClockGapDp.toInt())
        } else {
            safe.top + dp(4)
        }
        val bottom = if (editing) {
            safe.bottom + dp(EditModeDesign.toolbarBottomMarginDp.toInt()) + toolbarHeight
        } else {
            safe.bottom + dp(4)
        }
        desktop.setPadding(safe.left, top, safe.right, bottom)

        // The clock/date keeps its own resting breath; in edit mode the header
        // block owns the gap above it, so its margin drops to keep the reserved
        // 16dp exact instead of stacking two gaps.
        val headerLp = homeHeader.layoutParams as LinearLayout.LayoutParams
        if (headerTopMarginPx < 0) headerTopMarginPx = headerLp.topMargin
        val headerTop = if (editing) 0 else headerTopMarginPx
        if (headerLp.topMargin != headerTop) {
            headerLp.topMargin = headerTop
            homeHeader.layoutParams = headerLp
        }

        // Grid and drop zones share one content box, so a dashed slot sits
        // exactly on its cell.
        if (homeGrid.paddingLeft != marginPx || homeGrid.paddingRight != marginPx) {
            homeGrid.setPadding(marginPx, homeGrid.paddingTop, marginPx, homeGrid.paddingBottom)
        }
        setHorizontalMargins(guide, marginPx)
        setHorizontalMargins(dockSurface, marginPx)

        // Chrome: horizontal margins match the grid, vertical margins clear the
        // status bar and the gesture bar.
        (editTopBar.layoutParams as FrameLayout.LayoutParams).apply {
            val start = marginPx + safe.left
            val end = marginPx + safe.right
            val topMarginWanted = safe.top + dp(EditModeDesign.headerTopMarginDp.toInt())
            if (marginStart != start || marginEnd != end || topMargin != topMarginWanted) {
                marginStart = start
                marginEnd = end
                topMargin = topMarginWanted
                editTopBar.layoutParams = this
            }
        }
        (editToolbar.layoutParams as FrameLayout.LayoutParams).apply {
            val start = marginPx + safe.left
            val end = marginPx + safe.right
            val bottomMarginWanted = safe.bottom + dp(EditModeDesign.toolbarBottomMarginDp.toInt())
            if (marginStart != start || marginEnd != end || bottomMargin != bottomMarginWanted) {
                marginStart = start
                marginEnd = end
                bottomMargin = bottomMarginWanted
                editToolbar.layoutParams = this
            }
        }
    }

    private fun setHorizontalMargins(view: View, marginPx: Int) {
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (lp.marginStart == marginPx && lp.marginEnd == marginPx) return
        lp.marginStart = marginPx
        lp.marginEnd = marginPx
        view.layoutParams = lp
    }

    /**
     * The edit screen's own 16dp margin while editing; the design system's
     * 24dp everywhere else. The dock reads this too, so it never disagrees
     * with the toolbar above it.
     */
    private fun currentScreenMarginPx(): Int =
        if (::edit.isInitialized && edit.isActive) dp(EditModeDesign.screenMarginDp.toInt())
        else dp(LumaTokens.screenMarginHorizontal.roundToInt())

    private var bottomInset = 0

    private fun updateSideBySide() {
        val cfg = resources.configuration
        desktop.sideBySide = GridRules.useSideBySide(cfg.screenWidthDp, cfg.screenHeightDp)
        homeHeader.gravity = if (desktop.sideBySide) Gravity.CENTER_VERTICAL else Gravity.NO_GRAVITY
    }

    // --------------------------------------------------------------- gestures

    private enum class DragMode { NONE, DRAWER, SEARCH_PULL }

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
                        if (swipeDownAction() !is GestureAction.OpenSearch) performGesture(GestureKind.SWIPE_DOWN)
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
                LumaHaptics.longPress(desktop)
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
    private fun swipeDownAction(): GestureAction = gestureActions[GestureKind.SWIPE_DOWN] ?: GestureKind.SWIPE_DOWN.default

    private fun overlayBlocksGestures(): Boolean =
        folders.isOpen || widgetPicker?.isShowing == true || notificationPromptSheet?.isShowing == true || edit.isActive

    /**
     * Events are seen at the activity level so a swipe anywhere is recognised
     * while children still receive taps. A vertical drag that should move the
     * drawer is claimed after the touch slop; children then get CANCEL.
     * Catching the drawer mid-spring immediately stops the spring and hands
     * 1:1 finger tracking back without any jump.
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
                if (drawer.isAnimating && drawer.progress > 0.04f && drawer.progress < 0.96f) {
                    dragMode = DragMode.DRAWER
                    drawer.startTracking()
                    return true
                }
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
                        val searchPull = !drawer.isOpen && dy > 0 && swipeDownAction() is GestureAction.OpenSearch &&
                            !(downOnScrollableGrid && homeGrid.canScrollVertically(-1))
                        if (opening || closing || searchPull) {
                            dragMode = if (searchPull) DragMode.SEARCH_PULL else DragMode.DRAWER
                            lastY = ev.rawY
                            drawer.startTracking()
                            if (closing) drawer.hideKeyboard()
                            val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                            super.dispatchTouchEvent(cancel)
                            cancel.recycle()
                        }
                    } else if (!drawer.isOpen && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.4f && homeRoot.width > 0) {
                        val normalized = (dx / homeRoot.width.toFloat()).coerceIn(-0.35f, 0.35f)
                        parallaxSpring.track(normalized)
                    }
                }
                if (dragMode == DragMode.DRAWER) {
                    drawer.trackBy(ev.rawY - lastY)
                    lastY = ev.rawY
                    return true
                }
                if (dragMode == DragMode.SEARCH_PULL) {
                    drawer.trackBy(-(ev.rawY - lastY))
                    lastY = ev.rawY
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (abs(parallaxX) > 0.002f) {
                    tracker.computeCurrentVelocity(1000)
                    val vx = if (homeRoot.width > 0) tracker.xVelocity / homeRoot.width else 0f
                    parallaxSpring.setVelocity(vx * 0.25f)
                    parallaxSpring.animateTo(0f, MotionTokens.LUMA, motion)
                }
                if (dragMode == DragMode.DRAWER) {
                    tracker.computeCurrentVelocity(1000)
                    drawer.release(tracker.yVelocity)
                    dragMode = DragMode.NONE
                    recycleTracker()
                    return true
                }
                if (dragMode == DragMode.SEARCH_PULL) {
                    tracker.computeCurrentVelocity(1000)
                    drawer.release(-tracker.yVelocity, focusSearchWhenOpened = true)
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
                        if (!loading && !viewModel.appRefreshFailed.value && !appsLoadedOnce) {
                            appsLoadedOnce = true
                            val apps = viewModel.apps.value
                            if (apps.isNotEmpty() && profile.preloadIcons) {
                                iconLoader.preload(apps.map { it.key }, density, maxCount = profile.maxCachedIcons)
                            }
                            // Empty/cold launches are real starts too; measuring
                            // only non-empty app lists hid the slowest edge case.
                            measureStartupWhenDrawn()
                        }
                    }
                }

                launch {
                    combine(
                        combine(viewModel.drawer, viewModel.isLoading, viewModel.query) { out, loading, q ->
                            Triple(out, loading, q)
                        },
                        viewModel.appRefreshFailed
                    ) { state, refreshFailed -> state to refreshFailed }
                        .collect { (state, refreshFailed) ->
                            drawer.submit(state.first, drawerAdapter, state.second, state.third, refreshFailed)
                        }
                }

                launch {
                    combine(viewModel.categories, viewModel.filter, viewModel.suggestionsEnabled, viewModel.categoriesEnabled,
                        core.launchHistory.records) { cats, filter, sugg, enabled, records ->
                        ChipState(cats, filter, sugg && records.isNotEmpty(), enabled)
                    }.distinctUntilChanged().collect { s -> drawer.bindChips(s.categories, s.filter, s.frequent, s.enabled) }
                }

                launch { viewModel.drawerSort.collect { drawer.currentSort = it } }
                launch { viewModel.prefs.widgetsEnabled.collect { widgetsEnabled = it } }
                launch { viewModel.prefs.hiddenLock.collect { hiddenLock = it } }

                launch {
                    combine(viewModel.activeContextProfile, readouts.state) { p, r -> p to r }.collect { (p, r) ->
                        dial.snapshot = r
                        // Reuse the existing foreground battery receiver; no extra poll/service.
                        com.lumaos.pro.launcher.widgets.battery.BatteryWidgetProvider.refresh(applicationContext)
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
                        val animate = appliedTheme != null &&
                            (appliedTheme?.id != t.id || appliedTheme?.colors != t.colors) &&
                            motion.enabled && !motion.reduced
                        theme = t
                        applyTheme(t, animate)
                        com.lumaos.pro.launcher.widgets.battery.BatteryWidgetProvider.refresh(applicationContext)
                    }
                }

                launch {
                    core.themes.activeWallpaper.collect {
                        if (appliedTheme != null) {
                            renderWallpaper(theme)
                        }
                    }
                }

                launch {
                    core.themeManager.palette.collect {
                        if (appliedTheme != null && !themeAnimating) {
                            applyTheme(theme, animate = false)
                        }
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
                            themeAnimating = false
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

                // A host id absent from a saved layout may be an in-flight bind,
                // an undo, or a layout write not yet committed. Do not sweep it
                // speculatively; only explicit cancel/removal releases an id.
            }
        }
    }

    private data class ChipState(
        val categories: List<com.lumaos.pro.launcher.core.apps.DrawerCategory>,
        val filter: DrawerFilter,
        val frequent: Boolean,
        val enabled: Boolean
    )

    private fun bindLayout(layout: com.lumaos.pro.launcher.launcher.model.HomeLayout) {
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
        // Cross-fade on the incoming theme's own clock, not a fixed duration.
        backdrop.setTheme(target, if (animate) target.motionProfile.themeChangeMs.toLong() else 0L)
        backdrop.setEffects(effects.ambientMotion, effects.ambientFps, effects.atmosphereDetail)
        updateWallpaperFlag(target)
        if (animate && from != null) {
            themeAnimator?.cancel()
            themeAnimating = true
            val animator = SpringAnimator(0f) { t ->
                // Re-binding every cell on every animation frame would cost more
                // than the cross-fade is worth; cells wait for the final frame.
                applyColors(target.copy(colors = ThemeColors.lerp(from.colors, target.colors, t.coerceIn(0f, 1f))))
            }
            themeAnimator = animator
            animator.animateTo(1f, MotionTokens.LUMA, motion) {
                themeAnimating = false
                finishTheme(target)
            }
        } else {
            themeAnimating = false
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
        if (::folders.isInitialized) folders.applyTheme()
        refreshAll()
    }

    /**
     * Applies every colour role to the home composition.
     *
     * All values come from the resolved theme's semantic palette: text, glyphs,
     * surfaces, borders, status colours, disabled states and overlays. Nothing
     * here is a hard-coded colour, so a theme switch (or a custom accent) updates
     * the whole screen coherently and legibly.
     */
    private fun applyColors(t: ResolvedTheme) {
        val c = t.colors
        val s = t.semantics
        val d = resources.displayMetrics.density
        val comps = LumaComponents.of(t)
        val shapes = comps.shapes
        val artwork = t.backdrop.usesArtwork
        val darkText = !ColorMath.isDark(ColorMath.opaque(c.homeForeground))
        glassPalette = GlassViews.palette(t, effects)
        val glass = glassPalette

        renderWallpaper(t)

        // One wallpaper veil; the theme engine chooses its tint from the
        // measured wallpaper/text relationship, not from a decorative gradient.
        findViewById<View>(R.id.glass_scrim).background = GlassViews.scrim(t)

        // System bars follow what is immediately behind them.
        val behindBars = when {
            drawerProgress > 0.5f -> c.surface0
            artwork -> if (darkText) ColorMath.WHITE else ColorMath.BLACK
            else -> c.background
        }
        val lightBars = !ColorMath.isDark(ColorMath.opaque(behindBars))
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }

        // One refined clock/date focal point. Shadows are deliberately small:
        // the legibility comes from the wallpaper veil, not letter halos.
        homeClock.setTextColor(c.homeForeground)
        homeClock.setShadowLayer(if (artwork) 4f else 0f, 0f, 1f, c.homeShadow)
        // Date line: 14sp at 80% white — readable on any wallpaper, still quiet.
        homeDate.setTextColor(
            if (artwork) ColorMath.withAlpha(c.homeForeground, EditModeDesign.dateAlpha) else c.homeMuted
        )
        homeDate.setShadowLayer(if (artwork) 3f else 0f, 0f, 1f, c.homeShadow)

        // The empty-state hint is the one floating pill on the desktop: fully
        // rounded, 14sp at 85%, lifted on its own soft shadow.
        val emptySpec = glass[GlassRole.OVERLAY]
        GlassViews.apply(
            homeEmpty, emptySpec, d, ripple = true,
            radiusOverrideDp = EditModeDesign.pillRadiusDp
        )
        homeEmpty.elevation = dp(EditModeDesign.hintElevationDp.toInt()).toFloat()
        homeEmpty.setTextColor(ColorMath.withAlpha(emptySpec.rest.content, EditModeDesign.hintTextAlpha))
        homeEmpty.compoundDrawableTintList = ColorStateList.valueOf(emptySpec.rest.content)

        // Compatibility controls remain bound to their actions but are no longer
        // part of the visual hierarchy. Search and settings have clear primary
        // entry points below and on the clock/date long-press.
        val actionTint = ColorStateList.valueOf(c.homeForeground)
        listOf(R.id.home_search, R.id.home_settings).forEach { id ->
            findViewById<ImageButton>(id).imageTintList = actionTint
        }
        homeContext.background = null
        homeContext.setTextColor(c.homeMuted)
        homeContext.compoundDrawableTintList = null

        // Compatibility chronometer: hidden, but its colours stay coherent for
        // any external code that still queries the view.
        dial.ringColor = ColorMath.withAlpha(c.homeForeground, 0.38f)
        dial.progressColor = if (artwork) c.homeForeground else c.accent
        dial.batteryColor = if (artwork) c.homeMuted else c.secondary
        dial.markerColor = c.homeForeground

        // The single primary search entry: one translucent surface, one fine
        // edge, one focus ring, one bounded ripple.
        val searchSpec = glass[GlassRole.SEARCH]
        GlassViews.apply(searchPill, searchSpec, d, ripple = true)
        val searchColors = searchSpec.rest
        findViewById<ImageView>(R.id.search_pill_icon).imageTintList = ColorStateList.valueOf(searchColors.muted)
        findViewById<TextView>(R.id.search_pill_text).setTextColor(searchColors.muted)
        findViewById<ImageButton>(R.id.search_pill_voice).imageTintList = ColorStateList.valueOf(searchColors.content)
        findViewById<ImageButton>(R.id.search_pill_settings).imageTintList = ColorStateList.valueOf(searchColors.content)

        applyDockStyle(t)
        drawer.applyTheme(t, glass)
        val sheetColors = glass[GlassRole.SHEET].rest
        findViewById<ImageButton>(R.id.drawer_close).imageTintList = ColorStateList.valueOf(sheetColors.content)
        findViewById<ImageButton>(R.id.drawer_voice).imageTintList = ColorStateList.valueOf(sheetColors.content)
        findViewById<ImageButton>(R.id.drawer_sort).imageTintList = ColorStateList.valueOf(sheetColors.content)

        val cellCorner = t.corner(shapes.cellDp) * d
        // Press feedback uses LumaTokens.pressScale (0.92f) and disappears under reduced motion.
        val pressScale = if (motion.enabled && !motion.reduced) LumaTokens.pressScale else 1f
        val pressMs = t.motionProfile.pressMs
        homeCellConfig = homeCellConfig.copy(
            labelColor = c.homeForeground, labelShadow = true, labelShadowColor = c.homeShadow,
            rippleColor = c.ripple, cornerPx = cellCorner,
            placeholderFill = ColorMath.withAlpha(c.homeForeground, 0.10f),
            placeholderText = ColorMath.withAlpha(c.homeForeground, 0.85f),
            pressScale = pressScale, pressMs = pressMs,
            labelAlpha = LumaTokens.typeAppLabelAlpha,
            iconShape = t.iconShape
        )
        dockCellConfig = dockCellConfig.copy(
            showLabels = false,
            labelColor = if (appearance.effectiveDock == DockStyle.MINIMAL) c.homeForeground else glass[GlassRole.DOCK].rest.content,
            labelShadow = false, labelShadowColor = c.homeShadow,
            rippleColor = c.ripple, cornerPx = cellCorner,
            placeholderFill = if (appearance.effectiveDock == DockStyle.MINIMAL) ColorMath.withAlpha(c.homeForeground, 0.10f) else s.disabledSurface,
            placeholderText = if (appearance.effectiveDock == DockStyle.MINIMAL) c.homeForeground else glass[GlassRole.DOCK].rest.content,
            pressScale = pressScale, pressMs = pressMs, iconShape = t.iconShape
        )
        drawerCellConfig = drawerCellConfig.copy(
            labelColor = sheetColors.content, labelShadow = false, rippleColor = c.ripple, cornerPx = cellCorner,
            placeholderFill = s.disabledSurface, placeholderText = sheetColors.muted,
            pressScale = pressScale, pressMs = pressMs,
            labelAlpha = LumaTokens.typeAppLabelAlpha,
            iconShape = t.iconShape
        )
        val folderColors = glass[GlassRole.FOLDER].rest
        folderCellConfig = drawerCellConfig.copy(
            labelColor = folderColors.content,
            placeholderText = folderColors.muted,
            labelAlpha = LumaTokens.typeAppLabelAlpha
        )
        undoBar?.let { styleUndoBar(it, t) }
        if (::homeAdapter.isInitialized && !themeAnimating) {
            homeAdapter.notifyItemRangeChanged(0, homeAdapter.itemCount)
            homeAdapter.refreshWidgetFrames()
            dockAdapter.notifyItemRangeChanged(0, dockAdapter.itemCount)
            drawerAdapter.notifyItemRangeChanged(0, drawerAdapter.itemCount)
        }
    }

    /**
     * Shows the selected Wallpaper Studio artwork (or bundled theme wallpaper)
     * when `Backdrop.LUMA` is active, or hides the layer and lets the system
     * wallpaper / atmosphere show through.
     *
     * If an artwork asset cannot be decoded, the home screen restores the theme's
     * own backdrop instead of leaving an empty black surface.
     */
    private fun renderWallpaper(t: ResolvedTheme) {
        val selectedEntry = core.themes.activeWallpaper.value
        val themeArt = WallpaperArt.forTheme(t.id)
        val crossfadeMs = if (motion.enabled && !motion.reduced) t.motionProfile.themeChangeMs.toLong() else 0L
        when {
            t.backdrop == Backdrop.LUMA && selectedEntry != null -> {
                wallpaperArt.visibility = View.VISIBLE
                backdrop.setFallbackSolid(false)
                wallpaperArt.showEntry(
                    entry = selectedEntry,
                    fallback = t.colors.background,
                    crossfadeMs = crossfadeMs
                )
            }
            t.backdrop == Backdrop.LUMA && themeArt != null -> {
                wallpaperArt.visibility = View.VISIBLE
                backdrop.setFallbackSolid(false)
                wallpaperArt.show(
                    artwork = themeArt,
                    fallback = t.colors.background,
                    crossfadeMs = crossfadeMs
                )
            }
            else -> {
                wallpaperArt.clear()
                wallpaperArt.visibility = View.GONE
                // THEME paints its own solid; WALLPAPER must stay transparent so the
                // system wallpaper is visible behind the scrims.
                backdrop.setFallbackSolid(false)
            }
        }
    }

    private fun applyTypography(t: ResolvedTheme) {
        val style = t.typography
        homeClock.typeface = Typefaces.clock(style)
        homeClock.letterSpacing = style.clockTracking
        homeDate.typeface = Typefaces.body(style)
        homeDate.letterSpacing = if (style.readoutCaps) style.readoutTracking * 0.50f else 0.01f
        homeDate.isAllCaps = false
        homeDate.textSize = EditModeDesign.dateSp
        homeContext.typeface = Typefaces.readout(style)
        homeContext.letterSpacing = style.readoutTracking
        homeContext.isAllCaps = style.readoutCaps
        homeContext.textSize = LumaTokens.typeMicroSp
        homeEmpty.typeface = Typefaces.body(style)
        homeEmpty.textSize = EditModeDesign.hintTextSp
        findViewById<TextView>(R.id.search_pill_text).apply {
            typeface = Typefaces.body(style)
            textSize = LumaTokens.typeBodySp
        }
        findViewById<TextView>(R.id.edit_title).typeface = Typefaces.title(style)
        findViewById<TextView>(R.id.edit_hint).typeface = Typefaces.body(style)
        findViewById<TextView>(R.id.edit_done).typeface = Typefaces.label(style)
        drawer.setSearchTypeface(Typefaces.body(style))
        // App labels: 11sp Regular (400), 0.85 alpha, single-line centered.
        val label = Typefaces.body(style)
        val labelSize = LumaTokens.typeAppLabelSp
        homeCellConfig = homeCellConfig.copy(
            typeface = label, labelTracking = 0f, labelSizeSp = labelSize, labelAlpha = LumaTokens.typeAppLabelAlpha
        )
        dockCellConfig = dockCellConfig.copy(
            showLabels = false, typeface = label, labelTracking = 0f, labelSizeSp = labelSize
        )
        drawerCellConfig = drawerCellConfig.copy(
            typeface = label, labelTracking = 0f, labelSizeSp = labelSize, labelAlpha = LumaTokens.typeAppLabelAlpha
        )
        val folderColors = glassPalette[GlassRole.FOLDER].rest
        folderCellConfig = drawerCellConfig.copy(
            labelColor = folderColors.content,
            placeholderText = folderColors.muted,
            labelAlpha = LumaTokens.typeAppLabelAlpha
        )
        applyPreset(appearance.preset)
    }

    private fun applyDockStyle(t: ResolvedTheme) {
        val c = t.colors
        val s = t.semantics
        val d = resources.displayMetrics.density
        val spec = glassPalette[GlassRole.DOCK].copy(radiusDp = LumaTokens.radiusXLarge)
        val lp = dockSurface.layoutParams as ViewGroup.MarginLayoutParams
        val marginPx = currentScreenMarginPx()
        lp.marginStart = marginPx
        lp.marginEnd = marginPx
        lp.height = dp(LumaTokens.dockHeight.roundToInt())
        when (appearance.effectiveDock) {
            DockStyle.MINIMAL -> {
                dockSurface.background = null
                dockSurface.elevation = 0f
                dockSurface.clipToOutline = false
            }
            else -> {
                GlassViews.apply(
                    view = dockSurface,
                    spec = spec,
                    density = d,
                    ripple = false,
                    depth = LumaTokens.depthHome,
                    radiusOverrideDp = LumaTokens.radiusXLarge
                )
            }
        }
        dockSurface.layoutParams = lp
        dockSurface.contentDescription = null
        dockCellConfig = dockCellConfig.copy(
            showLabels = false,
            labelColor = if (appearance.effectiveDock == DockStyle.MINIMAL) c.homeForeground else spec.rest.content,
            labelShadow = false,
            placeholderFill = if (appearance.effectiveDock == DockStyle.MINIMAL) ColorMath.withAlpha(c.homeForeground, 0.10f) else s.disabledSurface,
            placeholderText = if (appearance.effectiveDock == DockStyle.MINIMAL) c.homeForeground else spec.rest.content
        )
    }

    /** Hides the system wallpaper when LumaOS Pro paints its own backdrop (saves GPU and power). */
    private fun updateWallpaperFlag(t: ResolvedTheme) {
        if (t.backdrop == Backdrop.WALLPAPER) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
    }

    /**
     * The shared widget container: one glass role, one border radius, so
     * third-party widgets read as part of the home composition. Provider content
     * is clipped to the frame but never recoloured.
     */
    private fun widgetFrameDrawable() = GlassViews.drawable(
        glassPalette[GlassRole.WIDGET],
        resources.displayMetrics.density,
        ripple = false
    )

    private fun folderStyle(): FolderStyle {
        val folder = glassPalette[GlassRole.FOLDER].rest
        return FolderStyle(
            plate = folder.fillTop,
            rim = folder.edgeTop,
            outline = IconShapes.outline(theme.iconShape),
            smartMarker = folder.focusRing
        )
    }

    private fun drawerRowStyle(): DrawerRowStyle {
        val colors = glassPalette[GlassRole.SHEET].rest
        return DrawerRowStyle(
            headerColor = ColorMath.withAlpha(colors.content, LumaTokens.typeSectionHeaderAlpha),
            headerTypeface = Typefaces.title(theme.typography),
            headerCaps = false,
            headerTracking = 0.01f,
            titleColor = colors.content,
            titleTypeface = Typefaces.body(theme.typography),
            subtitleColor = colors.muted,
            subtitleTypeface = Typefaces.body(theme.typography),
            iconTint = colors.content,
            rippleColor = colors.ripple,
            cornerPx = LumaTokens.radiusSmall * resources.displayMetrics.density
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
        // Floating dock never shows text labels (Apple-precision Phase 2 B2).
        dockCellConfig = dockCellConfig.copy(showLabels = false, textScale = a.textScale, cellHeightPx = null)
        drawerCellConfig = drawerCellConfig.copy(showLabels = true, textScale = a.textScale)
        folderCellConfig = drawerCellConfig.copy(
            labelColor = glassPalette[GlassRole.FOLDER].rest.content,
            placeholderText = glassPalette[GlassRole.FOLDER].rest.muted
        )
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
        val voiceClock = theme.clockSp.coerceAtLeast(LumaTokens.typeDisplayClockSp)
        val clockSp = if (preset.clock == ClockStyle.COMPACT) LumaTokens.typeDisplayClockSp * 0.72f else voiceClock

        // Luma Glass has one clock/date focal point. The decorative chronometer,
        // header action cluster and context chip are compatibility-only views.
        dial.visibility = View.GONE
        headerActions.visibility = View.GONE
        homeContext.visibility = View.GONE
        homeHeader.gravity = Gravity.CENTER_HORIZONTAL
        clusterRow.gravity = Gravity.CENTER
        clockColumn.gravity = Gravity.CENTER_HORIZONTAL
        homeClock.gravity = Gravity.CENTER
        homeDate.gravity = Gravity.CENTER
        homeContext.gravity = Gravity.CENTER
        homeClock.setAutoSizeTextTypeUniformWithConfiguration(
            (clockSp * 0.55f).roundToInt(), clockSp.roundToInt(), 1, TypedValue.COMPLEX_UNIT_SP
        )
        findViewById<ImageButton>(R.id.search_pill_settings).visibility = View.GONE
        val gridVisibility = if (preset.showGrid) View.VISIBLE else View.GONE
        homeGrid.visibility = gridVisibility
        findViewById<View>(R.id.home_grid_frame).visibility = gridVisibility
        homeContext.requestLayout()
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
        val dockSize = iconSizeForGrid(requested, dockRow, LumaTokens.dockIconCount, 0)
        if (homeCellConfig.iconSizePx != homeSize || dockCellConfig.iconSizePx != dockSize) {
            homeCellConfig = homeCellConfig.copy(iconSizePx = homeSize)
            dockCellConfig = dockCellConfig.copy(iconSizePx = dockSize, showLabels = false)
            homeAdapter.notifyItemRangeChanged(0, homeAdapter.itemCount)
            dockAdapter.notifyItemRangeChanged(0, dockAdapter.itemCount)
        }
        val drawerSize = if (appearance.drawerPresentation == DrawerPresentation.LIST) requested else
            iconSizeForGrid(requested, drawerGrid, drawerColumns, drawerGrid.paddingLeft + drawerGrid.paddingRight)
        val folderSize = requested.coerceAtMost(dp(LumaTokens.iconAppInt))
        if (drawerCellConfig.iconSizePx != drawerSize || folderCellConfig.iconSizePx != folderSize) {
            drawerCellConfig = drawerCellConfig.copy(iconSizePx = drawerSize)
            folderCellConfig = drawerCellConfig.copy(
                iconSizePx = folderSize,
                labelColor = glassPalette[GlassRole.FOLDER].rest.content,
                placeholderText = glassPalette[GlassRole.FOLDER].rest.muted
            )
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
        val inMotion = dragMode != DragMode.NONE ||
            drawer.isAnimating ||
            parallaxSpring.isRunning ||
            (::launchPortal.isInitialized && launchPortal.isRunning) ||
            (drawerProgress > 0.01f && drawerProgress < 0.99f) ||
            (folderProgress > 0.01f && folderProgress < 0.99f) ||
            (modalProgress > 0.01f && modalProgress < 0.99f)

        setHardwareTransitionLayers(inMotion && screenInteractive)

        val frame = LumaDepthStack.solve(
            LumaDepthStack.Inputs(
                drawerProgress = drawerProgress,
                folderProgress = folderProgress,
                editProgress = editProgress,
                modalProgress = modalProgress,
                launchProgress = launchProgress,
                settleProgress = settleProgress,
                parallaxX = parallaxX,
                density = resources.displayMetrics.density,
                allowDepthTransforms = allow,
                allowBlur = screenInteractive && effects.blur && theme.usesBlur,
                inMotion = inMotion
            )
        )

        wallpaperArt.scaleX = frame.wallpaperScale
        wallpaperArt.scaleY = frame.wallpaperScale
        wallpaperArt.translationX = frame.wallpaperTranslationX
        wallpaperArt.translationY = frame.wallpaperTranslationY
        backdrop.scaleX = frame.wallpaperScale
        backdrop.scaleY = frame.wallpaperScale
        backdrop.translationX = frame.wallpaperTranslationX
        backdrop.translationY = frame.wallpaperTranslationY

        desktop.pivotX = desktop.width / 2f
        desktop.pivotY = if (editProgress > 0f) desktop.height * 0.42f else desktop.height / 2f
        desktop.scaleX = frame.desktopScale
        desktop.scaleY = frame.desktopScale
        desktop.translationX = frame.desktopTranslationX
        desktop.translationY = frame.desktopTranslationY
        desktop.alpha = frame.desktopAlpha

        drawerContainer.pivotX = drawerContainer.width / 2f
        drawerContainer.pivotY = drawerContainer.height / 2f
        drawerContainer.scaleX = frame.drawerScale
        drawerContainer.scaleY = frame.drawerScale
        drawerContainer.alpha = frame.drawerAlpha

        if (Build.VERSION.SDK_INT >= 31) {
            val stepped = frame.desktopBlurRadiusPx
            if (stepped != lastBlurRadius) {
                lastBlurRadius = stepped
                desktop.setRenderEffect(if (stepped >= 1f) RenderEffect.createBlurEffect(stepped, stepped, Shader.TileMode.CLAMP) else null)
            }
        }
        desktop.visibility = if (drawerProgress >= 0.999f && !effects.translucency) View.INVISIBLE else View.VISIBLE
    }

    /** Coming back from an app: morphs app portal back into its icon and settles desktop forward. */
    private fun playReturnSettle() {
        if (::launchPortal.isInitialized && launchPortal.lastLaunchedKey != null) {
            val targetIcon = findIconViewForKey(launchPortal.lastLaunchedKey)
            launchPortal.playReturn(homeRoot, targetIcon, theme.colors.surface1, motion)
        }
        if (!effects.depthTransforms || !motion.enabled || motion.reduced) return
        SpringAnimator(0.4f) { p ->
            settleProgress = p.coerceIn(0f, 1.1f)
            applyDepth()
        }.animateTo(1f, MotionTokens.LUMA, motion)
    }

    private fun findIconViewForKey(key: ComponentKey?): View? {
        if (key == null) return null
        dockAdapter.positionsFor(key).firstOrNull()?.let { pos ->
            dockRow.findViewHolderForAdapterPosition(pos)?.itemView?.let { v ->
                return v.findViewById(R.id.app_icon) ?: v
            }
        }
        homeAdapter.positionsFor(key).firstOrNull()?.let { pos ->
            homeGrid.findViewHolderForAdapterPosition(pos)?.itemView?.let { v ->
                return v.findViewById(R.id.app_icon) ?: v
            }
        }
        return null
    }

    // ------------------------------------------------------ DrawerController.Host

    override fun theme(): ResolvedTheme = theme
    override fun motion(): MotionPolicy = motion

    override fun onDrawerProgress(progress: Float) {
        drawerProgress = progress
        applyDepth()
        val c = theme.colors
        val behind = when {
            progress > 0.5f -> c.surface0
            theme.backdrop.usesArtwork -> if (ColorMath.isDark(ColorMath.opaque(c.homeForeground))) ColorMath.WHITE else ColorMath.BLACK
            else -> c.background
        }
        val lightBars = !ColorMath.isDark(ColorMath.opaque(behind))
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
    override fun retryApps() = viewModel.refreshApps()
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

    override fun glassPalette(): GlassPalette = glassPalette
    override fun depthAllowed(): Boolean = effects.depthTransforms

    /**
     * Edit mode touches the *wallpaper*, never the launcher's own content:
     * a 55% black veil plus a slight defocus pushes a bright or busy wallpaper
     * back while the clock, the apps and the dock stay fully crisp. The veil
     * peaks a busy pattern at ~16% effective opacity.
     */
    override fun onEditProgress(progress: Float) {
        editProgress = progress
        applyDepth()
        editScrim.alpha = progress.coerceIn(0f, 1f)
        wallpaperArt.alpha = 1f - (1f - EditModeDesign.wallpaperAlphaInEdit) * progress.coerceIn(0f, 1f)
        applyEditBackdropBlur(progress > 0.01f)
    }

    /** Slight wallpaper defocus while editing (API 31+), so glass edges read cleanly. */
    private fun applyEditBackdropBlur(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < 31) return
        val wanted = enabled && effects.blur
        if (wanted == editBackdropBlurred) return
        editBackdropBlurred = wanted
        if (wanted) {
            val radius = dp(EditModeDesign.wallpaperBlurDp.toInt()).toFloat()
            wallpaperArt.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
        } else {
            wallpaperArt.setRenderEffect(null)
        }
    }

    /**
     * [EditModeController.Host]: the chrome appeared, disappeared or changed
     * height. Re-reserve the desktop's space and re-anchor the drop zones.
     */
    override fun onEditChromeChanged() {
        applyChromeInsets()
        if (::edit.isInitialized && edit.isActive) edit.updateGuideMetrics()
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
            removeWidgetWithUndo(item)
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
        val iconSource = source?.findViewById<View>(R.id.app_icon) ?: source
        val options = if (motion.enabled && !motion.reduced && iconSource != null && iconSource.width > 0) {
            ActivityOptions.makeScaleUpAnimation(iconSource, 0, 0, iconSource.width, iconSource.height).toBundle()
        } else null
        val bounds = iconSource?.let { v -> Rect().also { r -> v.getGlobalVisibleRect(r) } }
        if (core.appRepository.launch(key, bounds, options)) {
            LumaHaptics.tap(iconSource ?: desktop)
            if (::launchPortal.isInitialized) {
                launchPortal.playLaunch(
                    root = homeRoot,
                    key = key,
                    sourceView = iconSource,
                    icon = iconLoader.get(key, density),
                    fillColor = theme.colors.surface1,
                    motion = motion
                )
            }
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
            is DrawerRow.Header, is DrawerRow.Empty -> Unit
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

    private fun menu() = LumaMenu(this, theme, effects, motion)

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
                else if (viewModel.hasDockSpace()) {
                    entries += MenuEntry(M_TO_DOCK, getString(R.string.action_move_to_dock), R.drawable.ic_rx_dock)
                }
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
                val info = widgets.infoFor(item.appWidgetId)
                val caps = info?.let { widgets.capabilities(it, cellHeightPx) }
                val entries = ArrayList<MenuEntry>()
                // Only providers that declare vertical resize get height options,
                // and only inside their own min/max row range.
                if (caps == null || caps.resizableVertical) {
                    val minRows = caps?.minRows ?: HomeLayout.MIN_WIDGET_ROWS
                    val maxRows = caps?.maxRows ?: HomeLayout.MAX_WIDGET_ROWS
                    for (rows in minRows..maxRows) {
                        entries += MenuEntry(M_RESIZE_BASE + rows,
                            resources.getQuantityString(R.plurals.widget_rows, rows, rows),
                            if (rows == item.rows) R.drawable.ic_rx_done else R.drawable.ic_rx_resize)
                    }
                }
                if (caps?.reconfigurable == true) {
                    entries += MenuEntry(M_CONFIGURE, getString(R.string.action_configure_widget), R.drawable.ic_rx_settings, group = 0)
                }
                if (!edit.isActive) entries += MenuEntry(M_EDIT_HOME, getString(R.string.action_edit_home), R.drawable.ic_rx_move, group = 1)
                entries += MenuEntry(M_REMOVE, getString(R.string.action_remove_widget), R.drawable.ic_rx_remove, destructive = true, group = 1)
                val label = info?.loadLabel(packageManager) ?: getString(R.string.widget_generic)
                menu().show(anchor, label, entries) { e ->
                    when {
                        e.id in (M_RESIZE_BASE + 1)..(M_RESIZE_BASE + 4) -> {
                            val rows = e.id - M_RESIZE_BASE
                            val safeRows = caps?.let { WidgetPlan.clampRows(rows, it) } ?: rows
                            viewModel.resizeWidget(item.appWidgetId, safeRows)
                            reportWidgetSize(item.appWidgetId, safeRows)
                        }
                        e.id == M_CONFIGURE -> {
                            pendingReconfigureWidgetId = item.appWidgetId
                            if (!widgets.startReconfiguration(this, item.appWidgetId, REQUEST_RECONFIGURE_WIDGET)) {
                                pendingReconfigureWidgetId = null
                                toast(getString(R.string.toast_widget_failed))
                            }
                        }
                        e.id == M_EDIT_HOME -> edit.enter()
                        e.id == M_REMOVE -> remove(item)
                    }
                }
            }
        }
    }

    /** App menu: the app's own shortcuts (when Android allows), then LumaOS Pro actions. */
    private fun showAppMenu(key: ComponentKey, label: String, anchor: View, context: MenuContext, position: Int) {
        val entries = ArrayList<MenuEntry>()
        val shortcuts = if (core.shortcuts.hasAccess()) core.shortcuts.shortcutsFor(key.packageName) else emptyList()
        shortcuts.forEachIndexed { i, sc ->
            entries += MenuEntry(M_SHORTCUT_BASE + i, sc.label, icon = core.shortcuts.icon(sc, density), group = 0)
        }
        when (context) {
            MenuContext.DRAWER -> {
                // The layout model keeps each app in one location. Don't offer
                // ignored duplicates or a dock action when the dock has no space.
                if (viewModel.canAddToHome(key)) {
                    entries += MenuEntry(M_ADD_HOME, getString(R.string.action_add_to_home), R.drawable.ic_rx_home_add, group = 1)
                }
                if (viewModel.canAddToDock(key)) {
                    entries += MenuEntry(M_ADD_DOCK, getString(R.string.action_add_to_dock), R.drawable.ic_rx_dock, group = 1)
                }
            }
            MenuContext.HOME -> {
                if (!edit.isActive) entries += MenuEntry(M_EDIT_HOME, getString(R.string.action_edit_home), R.drawable.ic_rx_move, group = 1)
                if (viewModel.hasDockSpace()) {
                    entries += MenuEntry(M_TO_DOCK, getString(R.string.action_move_to_dock), R.drawable.ic_rx_dock, group = 1)
                }
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
                e.id == M_HIDE -> requestHideApp(key, label)
                e.id == M_INFO -> appInfo(key)
                e.id == M_UNINSTALL -> uninstall(key)
            }
        }
    }

    private fun requestHideApp(key: ComponentKey, label: String) {
        val profile = viewModel.activeContextProfile.value
        if (!hiddenLock) {
            viewModel.hideApp(key)
            toast(getString(R.string.toast_app_hidden, label))
            return
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        val intent = keyguard?.createConfirmDeviceCredentialIntent(
            getString(R.string.hidden_unlock_title),
            getString(R.string.pref_hidden_lock_desc)
        )
        if (intent == null) {
            toast(getString(R.string.hidden_no_lock))
            return
        }
        pendingHiddenApp = PendingHiddenApp(profile, key, label)
        try {
            hiddenUnlockLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            pendingHiddenApp = null
            toast(getString(R.string.hidden_locked))
        } catch (_: SecurityException) {
            pendingHiddenApp = null
            toast(getString(R.string.hidden_locked))
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
        // Theme, motion and insets may have changed while Settings was open.
        widgetPicker?.dismiss()
        widgetPicker?.shutdown()
        val picker = WidgetPicker(
            this, homeRoot, theme, effects, motion, bottomInset,
            onDepthProgress = { p ->
                modalProgress = p
                applyDepth()
            },
            capabilities = { info -> widgets.capabilities(info, cellHeightPx) }
        ).also { widgetPicker = it }
        picker.show(
            state = widgets.providersState(),
            onPick = ::beginWidgetAdd,
            onRetry = ::showWidgetPicker
        )
    }

    private fun beginWidgetAdd(info: AppWidgetProviderInfo) {
        val (pending, consent) = try {
            widgets.begin(info)
        } catch (e: RuntimeException) {
            LumaLog.e("Widget add could not start", e)
            toast(getString(R.string.toast_widget_failed))
            return
        }
        pendingWidget = pending
        if (consent != null) {
            try {
                widgetBindLauncher.launch(consent)
            } catch (e: RuntimeException) {
                LumaLog.e("Widget consent could not start", e)
                releaseWidgetWhenUnreferenced(pending.appWidgetId)
                pendingWidget = null
                toast(getString(R.string.toast_widget_failed))
            }
        } else continueWidgetAdd(pending)
    }

    private fun continueWidgetAdd(pending: WidgetHostController.Pending) {
        if (widgets.needsConfiguration(pending)) {
            if (!widgets.startConfiguration(this, pending, REQUEST_CONFIGURE_WIDGET)) {
                releaseWidgetWhenUnreferenced(pending.appWidgetId)
                pendingWidget = null
                toast(getString(R.string.toast_widget_failed))
            }
        } else finishWidgetAdd(pending)
    }

    private fun finishWidgetAdd(pending: WidgetHostController.Pending) {
        pendingWidget = null
        val caps = widgets.capabilities(pending.info, cellHeightPx)
        val widget = HomeItem.Widget(
            pending.appWidgetId,
            pending.info.provider.flattenToString(),
            caps.initialRows
        )
        // The planner owns the only insertion decision: it refuses duplicates and
        // over-budget pages instead of displacing existing content.
        val page = viewModel.layout.value.page(0)
        when (val plan = WidgetPlan.planInsertion(page, widget)) {
            is InsertionPlan.AtIndex -> {
                viewModel.addWidget(widget, position = plan.index) { saved ->
                    toast(getString(if (saved) R.string.toast_widget_added else R.string.toast_widget_failed))
                }
            }
            InsertionPlan.AlreadyPresent -> {
                // A restored result can arrive after the placement was saved.
                // Never delete the id of the widget that's already on the page.
                toast(getString(R.string.toast_widget_already_added))
            }
            InsertionPlan.NoSpace -> {
                releaseWidgetWhenUnreferenced(pending.appWidgetId)
                toast(getString(R.string.toast_widget_no_space))
            }
        }
    }

    private fun reportWidgetSize(appWidgetId: Int, rows: Int) {
        val d = resources.displayMetrics.density
        val w = (homeGrid.width - homeGrid.paddingLeft - homeGrid.paddingRight) / d
        val h = (cellHeightPx * rows) / d
        if (w > 0 && h > 0) widgets.reportSize(appWidgetId, w, h)
    }

    /** A removed widget kept alive (host id included) until the undo window closes. */
    private data class PendingWidgetRemoval(val widget: HomeItem.Widget, val position: Int)

    private val widgetRemovalTimeout = Runnable {
        pendingWidgetRemoval?.let { releaseWidgetWhenUnreferenced(it.widget.appWidgetId) }
        pendingWidgetRemoval = null
        hideUndoBar()
    }

    private fun releaseWidgetWhenUnreferenced(id: Int) {
        lifecycleScope.launch {
            try {
                // A failed or incomplete write is not proof of removal. Check
                // in-memory placement and every profile before releasing an id.
                if (!viewModel.mayReferenceWidgetId(id)) widgets.delete(id)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                LumaLog.e("Could not verify widget removal", e)
            }
        }
    }

    /**
     * Removes a widget from the layout but keeps its host id until the undo
     * window closes, so Undo restores the exact same bound widget rather than
     * allocating a new id. If the process dies before removal is verified,
     * retaining an unreferenced id is safer than deleting a saved placement.
     */
    private fun removeWidgetWithUndo(item: HomeItem.Widget) {
        val position = viewModel.layout.value.page(0).indexOfFirst {
            it is HomeItem.Widget && it.appWidgetId == item.appWidgetId
        }
        viewModel.removeWidget(item.appWidgetId)
        // A second removal finalises the first one; only one undo slot is offered.
        pendingWidgetRemoval?.let { releaseWidgetWhenUnreferenced(it.widget.appWidgetId) }
        pendingWidgetRemoval = PendingWidgetRemoval(item, position.coerceAtLeast(0))
        undoHandler.removeCallbacks(widgetRemovalTimeout)
        undoHandler.postDelayed(widgetRemovalTimeout, WIDGET_UNDO_WINDOW_MS)
        showUndoBar()
    }

    private fun undoWidgetRemoval() {
        val pending = pendingWidgetRemoval ?: return
        undoHandler.removeCallbacks(widgetRemovalTimeout)
        pendingWidgetRemoval = null
        hideUndoBar()
        viewModel.addWidget(pending.widget, pending.position) { saved ->
            if (saved) {
                reportWidgetSize(pending.widget.appWidgetId, pending.widget.rows)
            } else {
                // Keep the same id and offer Undo again rather than claiming
                // success or deleting a binding after a failed layout write.
                if (pendingWidgetRemoval == null && !isFinishing && !isDestroyed) {
                    pendingWidgetRemoval = pending
                    undoHandler.postDelayed(widgetRemovalTimeout, WIDGET_UNDO_WINDOW_MS)
                    showUndoBar()
                }
                toast(getString(R.string.toast_widget_failed))
            }
        }
    }

    private fun showUndoBar() {
        val bar = undoBar ?: buildUndoBar().also { undoBar = it }
        styleUndoBar(bar, theme)
        if (bar.parent == null) {
            homeRoot.addView(
                bar,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                ).apply {
                    bottomMargin = bottomInset + dp(96)
                    marginStart = dp(20)
                    marginEnd = dp(20)
                }
            )
        }
        bar.alpha = 0f
        bar.translationY = dp(12).toFloat()
        if (motion.enabled && !motion.reduced) {
            SpringAnimator(0f) { p ->
                bar.alpha = p
                bar.translationY = (1f - p) * dp(12)
            }.animateTo(1f, MotionTokens.LUMA, motion)
        } else {
            bar.alpha = 1f
            bar.translationY = 0f
        }
        bar.announceForAccessibility(getString(R.string.widget_removed_announcement))
    }

    private fun hideUndoBar() {
        val bar = undoBar ?: return
        val done = {
            (bar.parent as? ViewGroup)?.removeView(bar)
        }
        if (motion.enabled && !motion.reduced) {
            SpringAnimator(1f) { p -> bar.alpha = p }.animateTo(0f, MotionTokens.LUMA, motion) { done() }
        } else done()
    }

    private fun buildUndoBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(10), dp(8), dp(10))
            isClickable = true
            addView(TextView(this@HomeActivity).apply {
                text = getString(R.string.widget_removed_message)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.bodyMediumSp)
            })
            addView(TextView(this@HomeActivity).apply {
                text = getString(R.string.widget_undo)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.labelLargeSp)
                setPadding(dp(14), dp(6), dp(10), dp(6))
                isClickable = true
                isFocusable = true
                contentDescription = getString(R.string.widget_undo_description)
                setOnClickListener { undoWidgetRemoval() }
            })
        }.also { styleUndoBar(it, theme) }
    }

    private fun styleUndoBar(bar: LinearLayout, t: ResolvedTheme) {
        val spec = glassPalette[GlassRole.MENU]
        val colors = spec.rest
        val density = resources.displayMetrics.density
        GlassViews.apply(bar, spec, density, ripple = false)
        (bar.getChildAt(0) as? TextView)?.apply {
            setTextColor(colors.content)
            typeface = Typefaces.body(t.typography)
        }
        (bar.getChildAt(1) as? TextView)?.apply {
            setTextColor(colors.focusRing)
            typeface = Typefaces.label(t.typography)
            background = GlassViews.drawable(glassPalette[GlassRole.SETTINGS_CONTROL], density, ripple = true)
        }
    }

    // ---------------------------------------------------------- navigation

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    private fun openStudio() = startActivity(
        Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_TAB, SettingsActivity.TAB_STUDIO)
    )

    private fun openWallpaperPicker() {
        startActivity(Intent(this, com.lumaos.pro.launcher.wallpaper.WallpaperActivity::class.java))
    }

    private fun appInfo(key: ComponentKey) {
        runCatching { startActivity(core.appRepository.appDetailsIntent(key)) }
            .onFailure { LumaLog.w("App details unavailable for ${key.packageName}") }
    }

    private fun uninstall(key: ComponentKey) {
        runCatching { startActivity(core.appRepository.uninstallIntent(key)) }
            .onFailure { LumaLog.w("Uninstall unavailable for ${key.packageName}") }
    }

    /** Speech is delegated to Android's selected recognition service; LumaOS Pro records nothing. */
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
        private const val STATE_PENDING_WIDGET_ID = "pending_widget_id"
        private const val STATE_PENDING_WIDGET_INFO = "pending_widget_info"
        private const val STATE_RECONFIGURE_WIDGET_ID = "reconfigure_widget_id"

        private const val SWIPE_MIN_DISTANCE_DP = 60
        private const val REQUEST_CONFIGURE_WIDGET = 4101
        private const val REQUEST_RECONFIGURE_WIDGET = 4102
        private const val WIDGET_UNDO_WINDOW_MS = 6000L

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
        private const val M_CONFIGURE = 150
        private const val M_PRESET_BASE = 200
        private const val M_SMART_BASE = 300
        private const val M_SHORTCUT_BASE = 1000
    }
}
