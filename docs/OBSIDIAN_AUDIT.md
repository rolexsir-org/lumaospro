# OBSIDIAN — audit and preservation map

## Baseline and boundaries

The repository is a single Android application (AGP 8.13.2, Kotlin 2.2.21,
compile SDK 36, min SDK 26). Home and drawer are Android Views; Compose is
already used in Settings, Studio, onboarding, dashboard and Vault screens.
Replacing the live drawer with a second Compose state/gesture stack would risk
its existing back, drag, icon-cache and focus contracts. OBSIDIAN therefore
uses the existing RecyclerView and spring controller, not a parallel Compose
drawer or a new dependency. Theme typefaces and the shared effects/motion
policy continue to be respected. Other launcher surfaces are out of scope.

## Dependency map

- Home gesture/search/empty state → `HomeActivity` gesture dispatch →
  `DrawerController.open`, drag/release, close and back. Controller supplies
  progress to `LumaDepthStack` and honors `MotionPolicy`/effect capabilities.
- `LauncherApps` through `AppRepository` → `LauncherViewModel.apps` (coalesced
  refresh, package callbacks registered/unregistered by Activity). Failed or
  incomplete scans preserve the previous snapshot and don't prune user data.
- `LauncherViewModel` combines query, filter, persisted sort/columns and
  presentation, per-profile ordered pins (`LumaPrefs` DataStore), local launch
  history (`LaunchHistoryStore` SQLite), shortcuts and effective profile/Vault
  hidden keys → `DrawerModel` on a background dispatcher → `DrawerAdapter`.
- `AppMatcher` ranks actual installed-app matches; `AppCategories` maps declared
  platform categories, including Other. Search also retains platform shortcuts
  and available web/store actions. Hidden apps are excluded by the ViewModel.
- Adapter taps/long presses → `HomeActivity.onDrawerRowClick` /
  `onDrawerRowLongClick` → existing focus enforcement, app launcher/shortcut
  repository and app menu (including pin/unpin). Icons use `IconLoader`, cache,
  icon packs and the existing monogram fallback; no synthetic app entries.
- Sheet/back handling and keyboard remain in `DrawerController`; category chips,
  sorting menu, A–Z rail, settings and list/grid preference remain functional.
- Manifest permissions and package visibility declarations are unchanged. Usage
  recency is launcher-recorded launches, governed by the existing learning
  setting, not fabricated or inferred from a new permission.

## Design approach

Use a drawer-local black/graphite palette and blue accent with a high-contrast
light-theme fallback. Keep the existing live sources and list adapter, but
project the existing rows into an overview or a one-tap complete library. The
overview offers saved favourites, genuine recents and existing suggestions;
category chips continue to expose only categories present among visible apps.
Search and category views bypass the overview projection. No schema, intent,
permission, data migration or launch path changes.

## Baseline validation

Before this implementation both requested Gradle commands were attempted. The
wrapper cannot download Gradle from `services.gradle.org` in this restricted
sandbox (SSL handshake failure). There is no Android SDK, adb or emulator here;
therefore baseline Android/UI/device checks and screenshots cannot run. The
existing Luma Flow branch has a separate standalone Kotlin model harness; that
is not equivalent to the Gradle or Robolectric suite.

## Implementation and validation notes

- `ObsidianProjection` splits the *same* `DrawerModel.Output` into overview and
  library, rebases alphabetical positions, and passes searches and filtered
  collections through unchanged. It does not save or mutate apps or settings.
- `DrawerController` owns the transient page, keyboard, empty states, safe diff
  commits, and filter handoff. `HomeActivity` saves the selected page in the
  Activity instance state and retains its existing launch/menu callbacks. A
  completed close resets the page to Overview. The existing behavior of not
  automatically reopening the drawer after Activity recreation is unchanged.
- `ObsidianStyle`, the drawer layout, adapter and existing type/material systems
  implement the scoped visual language. Dark themes use an opaque graphite
  sheet and search surface; light themes retain their resolved glass materials.
  The original spring, desktop depth/blur capability policy, reduced-motion
  behavior, gesture and back entry points remain in control.
- Modified: `HomeActivity.kt`, `DrawerController.kt`, `DrawerAdapter.kt`,
  `DrawerModel.kt`, `activity_home.xml`, `item_drawer_app.xml`,
  `item_drawer_header.xml`, `strings.xml`, `strings_v2.xml`,
  `DrawerModelTest.kt` and `HomeActivityRuntimeTest.kt`.
- Added: `ObsidianStyle.kt`, `ObsidianProjection.kt`,
  `ObsidianStyleTest.kt`, `ObsidianProjectionTest.kt`, this audit, and the
  earlier `LUMA_FLOW_AUDIT.md` retained as historical context.
- After implementation, 18 pure drawer/model/contrast tests passed under a
  standalone Kotlin 2.2.21 harness with a minimal JUnit shim. This is not the
  configured Gradle/JUnit suite. Android resource compilation with AAPT2 passed;
  controller and adapter compiled separately against Android API 36 and local
  dependency stubs, not the actual application dependency graph.
- The requested `:app:assembleDebug` and `:app:testDebugUnitTest` commands were
  retried after changes and both stopped while downloading Gradle, before build
  configuration or tests. The new Robolectric tests were therefore not run.
  No device/emulator, connected tests, performance measurements or genuine
  screenshots are available in this environment. Validate all interactions,
  font scales, orientations, light/dark themes, animations and package/profile
  changes on CI and a device before calling this production ready.

## Unified drawer revision (2026-11-20)

The actual live drawer is Views/RecyclerView rather than Compose. This revision
removes its page controls, category chips, alphabetical rail, sort and voice
buttons from the drawer layout. The existing model is projected to only its
`all` or search-result app rows, so each eligible component appears once, with
no curated sections, shortcuts or external-search rows in the drawer. Existing
visibility/hidden-search policy, persisted sort and column count, app discovery,
launch and long-press callbacks, and spring/gesture controller remain in place.
Stored list/category/suggestion preferences and pinned/history data are not
changed, but do not create alternate drawer views. Other surfaces are unchanged.

A bounded, asynchronous drawer icon cache uses the repository's native
LauncherApps icon (including work-profile badges), independent of home icon
styling; package changes invalidate it. Missing icons retain the existing
monogram fallback. The drawer remains a single RecyclerView with diffing.

The requested Gradle assembly and unit-test commands were attempted in this
sandbox and stopped before configuration: no Java executable or JAVA_HOME is
available. An attempt to obtain a JDK via GitHub was blocked at the release
asset host; there is also no Android SDK or adb/physical device exposed here.
XML parsing and `git diff --check` passed, but no build, test suite, physical
device inspection, performance validation, or genuine screenshots can be
claimed. Run both Gradle commands and validate the drawer on a physical device
before release.

## PR 42 correction review (2026-11-20)

This supersedes the previous revision's description of discarded curated rows
and shortcut results. Pins appear first in saved per-context order, followed by
real recents/suggestions when learning is enabled; remaining apps follow the
saved sort. Keys are deduplicated against the visible all-app collection, with
Android user serial included, so personal and work copies stay distinct. Search
keeps matching shortcuts as cells in the same grid, and the long-press app menu
continues to expose personal-profile shortcuts. The current shortcut repository
queries the personal Android user only; a work-profile drawer app is not offered
a personal user's shortcut. External web/store searches, sorting and voice
commands are available in a single drawer options menu. The home voice control
and configurable voice gesture also remain available.

The old list/category preferences and pinned/history data are left in storage;
the drawer settings no longer offer ineffective list/category controls and
explain the unified layout. Column, text scale and icon size settings still
apply. The drawer intentionally uses native icons (including LauncherApps work
badges), while icon treatments still apply to the home screen. Learning disabled
continues to disable history-derived recency and usage scoring. Sorting applies
after the curated leading apps. No database migration or permissions changed.

Validation in this sandbox: Kotlin 2.2.21 with a small JUnit-compatible assertion
shim ran 20 pure drawer-model/projection checks successfully. This is not the
Gradle unit suite, a UI test, or proof of work-profile rendering on a device.
Both requested Gradle tasks were attempted using a Java 17 runtime obtained from
PyPI, but the wrapper could not download Gradle from services.gradle.org (TLS
handshake blocked); Android SDK downloads from dl.google.com are also blocked.
No adb, physical device, or emulator is attached. The Robolectric additions,
real build, interaction checks, actual icon/badge rendering, and screenshots
remain unvalidated and must be completed on a properly provisioned device/CI.
