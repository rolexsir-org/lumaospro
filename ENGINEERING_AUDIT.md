# ROLEXM premium-launcher engineering inventory

Audit date: 2026-10-24
Baseline commit: `debc7ad6ae599b1f839470d3afc96f94d605f728`
Working branch: `arena/10367222-rolexm`

## 1. Existing functionality and source map

| Area | Existing implementation |
| --- | --- |
| Identity/build | Single Android app module, Kotlin, Views on the launcher hot path and Compose on secondary screens. `com.rolexm.launcher`, minSdk 26, target/compileSdk 36, version 0.1.0 (1). AGP 8.13.2, Kotlin 2.2.21, Gradle wrapper 8.13. Debug adds `.debug`; release uses R8 and has no debug-key fallback. |
| Android entry points | `AndroidManifest.xml`, `core/RolexApp.kt`, `launcher/HomeActivity.kt`, `settings/SettingsActivity.kt`, `onboarding/OnboardingActivity.kt`. HOME/DEFAULT activity is `singleTask`, edge-to-edge and excluded from recents. |
| State/persistence | `core/RolexCore.kt` owns process collaborators. `LauncherViewModel` exposes StateFlows. `core/prefs/RolexPrefs.kt` uses DataStore Preferences; layouts use the versioned `HomeLayoutCodec`. Context-specific layouts, visibility and schedules are present. |
| Installed apps | `core/apps/AppRepository.kt` uses `LauncherApps`, real labels/icons and launch intents; `LauncherApps.Callback` handles package changes. No `QUERY_ALL_PACKAGES`. `IconLoader`, `IconRenderer` and bounded `IconCache` render real adaptive/legacy icons off the main thread. |
| Home | `activity_home.xml`, `HomeActivity`, `HomeGridAdapter`, `DesktopLayout`, `ChronometerDialView`/`ChronoDialView`, `ReadoutSources`. Real locale-dependent `TextClock`s, configurable grid/dock, folders, smart folders, context readouts, responsive landscape layout and edit mode. |
| Drawer/search/gestures | `DrawerController`, `DrawerAdapter`, `DrawerModel`, `AppMatcher`, `AlphaIndexView`. Real app/shortcut search, sorting, categories, usage suggestions and configurable swipe/double-tap/long-press/pinch actions. The current drawer presentation is a grid. |
| Widgets/shortcuts | `WidgetHostController`, `WidgetPicker`, `ShortcutRepository`. Real AppWidgetHost allocation, bind consent, configuration, sizing and removal; launcher shortcuts are used when Android grants access. |
| Themes/effects/motion | `ui/theme/*`, `AmbientBackdropView`, `ThemedDrawables`, `settings/Theme.kt`, `effects/*`, `motion/*`. Six semantic presets, wallpaper-derived Chroma colours, contrast derivation, typography/icon treatments, light/dark scheduling, effect tiers and interruptible springs. |
| Settings/diagnostics | `SettingsScreen.kt`, `DashboardScreen.kt`, `SettingsViewModel.kt`. Home-role controls, context schedules, hidden apps, grid/label/text settings, performance profile, local diagnostics and optimizer actions. |
| Privacy/backup | Local launch history in SQLite, no internet permission/analytics. `BackupCodec` provides a typed, whitelisted JSON format and strips non-portable widget IDs. |
| Tests/release | Pure JVM tests plus Robolectric tests inflate the real home layout and exercise DataStore/SQLite. Signing is optional and local. Store artwork and historical logs are checked in; historical logs are not current-source verification. |

## 2. Architecture and data flow

`RolexApp` constructs `RolexCore`. DataStore and system repositories feed the theme,
effects and launcher StateFlows. `HomeActivity` observes those flows and binds the
View-based desktop/drawer. App and icon binder/decoding work is dispatched to IO or a
single icon worker. Layout mutations pass through pure `HomeLayoutOps`, then persist
through DataStore. Settings and diagnostics observe the same core through
`SettingsViewModel`; there is no duplicate settings database or mock app source.

## 3. Audit findings, technical constraints and regression risks

1. **Studio is not integrated.** Theme models, preview renderer, strings, the
   `openStudio()` route and `TAB_STUDIO` exist, but `SettingsActivity` exposes only
   Settings and Diagnostics and ignores the requested tab. Risk: adding the screen
   must apply drafts without rewriting home layouts or resetting arrangements.
2. **Legacy theme control can become ineffective.** `setThemeMode()` writes only the
   legacy key, while `ThemeRepository` resolves the newer `ThemeSettings`. Once a new
   theme ID exists, changing that control need not change the rendered theme. Risk:
   compatibility writes must preserve existing preset pairs and explicit user choices.
3. **Several persisted capabilities have no UI.** Drawer categories/suggestions,
   hidden-app search, effects/motion, icon packs, backup and related strings exist but
   are not all reachable. Controls will be added only where the underlying behavior is
   real; unimplemented credential protection will not be exposed as working.
4. **App discovery is grid-only.** There is no vertical list or pinned-app section.
   Risk: list/grid switching must retain A–Z offsets, full-span headers, stable row
   identities, search and the existing drawer-column preference for grid users.
5. **Search/accessibility polish gaps.** Search has no explicit clear button; several
   controls are below a comfortable 48dp target. Risk: changing row geometry can affect
   icon sizing and A–Z hit areas, so existing responsive calculations must be retained.
6. **Onboarding denial can repeat.** The role result callback records completion only
   when accepted, despite the documented skip/denial behavior. Risk: completion must
   not be used to infer that the role was granted; role status remains separately read.
7. **Refresh/icon edge cases.** Rapid resumes can overlap app refreshes; an unavailable
   icon can be retried after each failure callback; package changes do not explicitly
   invalidate that package's cached icon. Risk: generation handling must not drop newer
   icon requests or cause global cache clear/redecode on every resume.
8. **Reduced motion is incomplete.** The preference removes overshoot but large
   springs/transforms still run. Risk: reduced motion must keep state changes and back
   behavior deterministic rather than leaving a transition half-finished.
9. **Performance evidence is not portable.** The repository contains historical logs,
   not measurements for this source/device. No physical device, emulator, adb or KVM is
   available here. No startup, jank, memory or battery number will be inferred.
10. **Large coordinator.** `HomeActivity` is about 80 KB and wires many controllers.
    A wholesale rewrite would create unnecessary lifecycle and gesture regressions;
    changes will remain incremental and use the existing controllers.

## 4. Baseline checks actually run

Command attempted before source modifications:

```text
./build.sh test
```

Result: **blocked before Gradle started** (exit 35). `toolchain/setup_toolchain.sh`
could not download JDK 17 from `api.adoptium.net` because that host is unreachable from
this sandbox. `java`, `javac`, `gradle`, `adb` and a local Android SDK are not installed;
`/dev/kvm` is absent. The repository's old successful logs are therefore **not**
counted as baseline passes. Current-source unit tests, lint, APK/AAB builds, device
installation and visual/performance verification remain unexecuted at this point.

## 5. Features requiring external validation or additional product input

- Real widget provider consent/configuration/interaction, launcher-role behavior,
  predictive/system navigation and icon-pack compatibility require Android devices.
- Wallpaper pixels cannot be read merely for a preview without user-selected content;
  the existing implementation correctly uses platform wallpaper colours instead.
- Weather/calendar/notification-content features have no real data source and will not
  be fabricated or given placeholder controls.
- Play upload, signing, Data safety declarations, public privacy policy and store
  approval require publisher credentials/input and are not claimed by source changes.

## 6. Milestones and verifiable exit criteria

| Milestone | Implementation | Exit criterion |
| --- | --- | --- |
| M1 baseline | This inventory and failed provisioning record | Every planned change maps to an existing component or a justified new Studio/list component. |
| M2 design/motion | Reuse semantic themes/tokens; complete reduced-motion semantics | New Studio/list UI consumes the same theme, spacing, shapes and motion policy; no parallel palette. |
| M3–M4 home/discovery | Preserve desktop; add vertical/grid drawer presentation, pinned apps, clear search, package/icon reliability | Real apps remain launchable; pinned state and presentation persist; A–Z/search/headers work in both layouts; saved home layouts are untouched. |
| M5–M6 Studio/settings/integration | Wire Studio drafts/previews/apply/reset, theme compatibility, real drawer/privacy controls, onboarding fix | Every newly visible control calls implemented persistence/system behavior; Studio route opens the correct tab; invalid values keep safe fallbacks. |
| M7 verification | Add pure and Robolectric regression coverage; static source/resource checks; run available toolchain checks | Exact executed checks and failures are recorded. No visual, performance or device claim without evidence. |
| M8 release | Attempt debug/test/lint/release using supported build script when toolchain permits | Report actual outputs and signing state; distinguish source completion from device/release verification. |
