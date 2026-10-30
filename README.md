# Project LumaOS Pro

A standalone, lightweight Android launcher and performance platform for older
and low-resource phones.

LumaOS Pro runs its own launcher logic, resource monitoring and optimization
routines. It does not depend on Brave, another launcher, or a third-party
optimizer to function.

Its current visual identity is **Luma Glass**: a restrained Liquid Glass /
Glassmorphism system driven by shared semantic tokens and used by both the
Views home screen and the Compose settings surfaces. When the system wallpaper
backdrop is selected, it shows through a legibility scrim; effects fall back to
opaque, readable surfaces on devices without blur support. See [`docs/DESIGN_SYSTEM.md`](docs/DESIGN_SYSTEM.md).

---

## Quick start

```bash
./build.sh assembleDebug      # debuggable APK, installs alongside other launchers
./build.sh test               # JVM unit + Robolectric regression suite
./build.sh lint               # Android lint
./build.sh assembleRelease    # minified APK; release signing is never debug-key fallback
./build.sh bundleRelease      # Android App Bundle for Play (requires upload-key signing)
```

`build.sh` provisions the JDK 17 / Gradle / Android SDK toolchain into
`~/.cache/lumaos-toolchain` on first run (the sandbox ships only JDK 11 and no
Android SDK). The toolchain lives outside the repo so it never bloats the
project snapshot.

The Robolectric suite exercises the app's **real** Android-dependent code on
the JVM, including inflating the actual layouts and driving `HomeActivity`
through its lifecycle. No emulator is required for those tests. They do not
replace a fresh build, lint run, or testing on physical devices; see
§"Running the code without a device" below for the scope and limits.

On first launch LumaOS Pro offers to become the default launcher. Skipping is a
first-class outcome — it stays fully usable as an app, and the choice remains
available in Settings at any time.

### Install on a connected Android device

```bash
./build.sh assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.lumaos.pro.launcher.debug 1
```

The debug build uses the separate application ID `com.lumaos.pro.launcher.debug`.
For a release APK, run `./build.sh assembleRelease`; install
`app/build/outputs/apk/release/app-release.apk`. Release variants are unsigned
unless valid local upload-key credentials are configured; they are never signed
with the debug key. Google Play distribution uses the signed AAB from
`./build.sh bundleRelease` at `app/build/outputs/bundle/release/app-release.aab`.
See [keystore/README.md](keystore/README.md) for safe upload-key setup.

### Google Play release checklist

- The project targets Android 16 / API 36. Recheck the target-API requirement
  at submission time; Google Play's policy requires API 36 for submissions from
  August 31, 2026 ([current policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)).
- The Home activity has both HOME and LAUNCHER entry points: users can choose
  LumaOS Pro as their default launcher or open it normally from the app drawer/Play
  Store. Verify both flows from a fresh install.
- Build a signed AAB from the current source, enroll in Play App Signing, and
  verify the package identity and signature. `bundleRelease` never falls back
  to a debug key; without a valid upload key the output is not uploadable.
- Complete the Play Console Data safety form and provide a public privacy-policy
  URL ([Data safety requirements](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)).
  `PRIVACY_POLICY_DRAFT.md` is a publisher-review draft with placeholders,
  not a publishable policy. LumaOS Pro stores preferences, layouts, launch history
  and diagnostics locally; Android backup may transfer some of this data.
  Optional voice commands hand audio to the installed Android speech-recognition
  app, which may process it on-device or remotely under its own policy. Add the
  final public policy URL and publisher contact in Play Console and the app once
  supplied; reconcile the wording with the final Data safety answers.
- Complete the store listing (including publisher/support contact, app-content
  declarations and real screenshots captured from verified devices). Review
  the supplied 512 × 512 icon and 1024 × 500 feature graphic in `store-assets/`;
  those brand assets are not application screenshots.
- The publisher selected **LumaOS Pro** as the replacement brand; app labels,
  package namespace, and listing artwork have been updated in source. Confirm
  `com.lumaos.pro.launcher` is available and permanent, and obtain trademark
  clearance before first upload. `versionCode` follows
  `major * 100 + minor * 10 + patch` (1.0.0 → 100), so increment it on each
  later upload and follow any account-specific Play testing gates shown in
  Console. The new name and package have not been independently cleared.

---

## Project identity

| | |
|---|---|
| Application ID | `com.lumaos.pro.launcher` (provisional; verify availability before first upload) |
| minSdk | 26 (Android 8.0) |
| targetSdk / compileSdk | 36 (Android 16) |
| Build tools | JDK 17 · Gradle 8.13 · AGP 8.13.2 · Kotlin 2.2.21 |
| Version | 1.0.0 (versionCode 100) |
| UI | Views for the launcher hot path; Jetpack Compose for settings, Studio and diagnostics |
| Signing | Optional local upload key (`keystore.properties`, gitignored); unsigned release if absent |

### Move data from the older test build

The selected rebrand keeps `com.lumaos.pro.launcher`. Android treats it as a
different app from older `com.rolexm.launcher` and
`com.rolexm.launcher.debug` installs, so the old
app cannot be updated in place and Android does not let the new app read the old
app's private storage. To move settings and layouts:

1. In the old test app, open **Settings → Backup and restore → Export backup**
   and save the JSON file somewhere accessible (for example, Downloads or a
   cloud drive you control).
2. Install LumaOS Pro, open **Settings → Backup and restore → Restore backup**,
   and select that file. The new app accepts the old backup format.
3. Confirm the restored layout and settings before uninstalling the old test
   app.

Manual backup does not include widgets or launch history. This is the supported
migration path while retaining the new application ID; it is not automatic.

---

## Design system (v3.2)

The appearance layer is **LumaOS Design System v3.2**, adopted from the authored
bundle in [`docs/design/v3.2/`](docs/design/v3.2/): a written specification, one
token file per theme, and a parity test that fails if the Kotlin constants and the
bundle ever disagree. The bundle's studio build is kept in
[`design-src/v3.2/`](design-src/v3.2/).

Three identities, one grammar. They differ in composition, typography, icon
treatment, surface material and motion — not just in accent hue:

| | **Signature** | **Atmosphere** | **Monolith** |
| --- | --- | --- | --- |
| Character | Refined, timeless | Cinematic, adaptive | Architectural, exact |
| Accent | `#7C86FF` indigo-violet | `#46E0B0` aurora mint | `#FF5A2B` vermilion |
| Radii (xl→xs) | 34 / 26 / 16 / 10 / 6 | 38 / 30 / 20 / 12 / 8 | 8 / 4 / 2 / 2 / 2 |
| Material | blur 26 dp · sat 135 % | blur 34 dp · sat 165 % | **no blur** · solid |
| Clock | 64 sp light, caps date | 58 sp light, sentence date | 84 sp semibold, mono date |
| Label | 10.5 sp | 11 sp | 9 sp |

| Area | Where it lives | Summary |
| --- | --- | --- |
| Tokens | `ui/theme/LumaDesignTokens.kt` | Authored v3.2 colour, shape, material, type, spacing and motion; parity-tested against `docs/design/v3.2/tokens` |
| Colour | `ui/theme/ThemeModel.kt`, `SemanticColors.kt`, `ThemeEngine.kt` | Authored roles for v3.2 themes, seed-derived for retained palettes and custom colours; contrast enforced on both paths |
| Type | `ui/theme/TypeSystem.kt`, `res/font/*` | Inter and Space Grotesk (SIL OFL 1.1), one 10-role ladder, five voices |
| Icons | `ui/IconCatalog.kt`, `res/drawable/ic_rx_*.xml` | 45 glyphs on one 24 dp grid, filled only for selection; per-theme tile shape at 58 dp |
| Wallpaper | `ui/theme/WallpaperArt.kt`, `drawable-nodpi` | Authored art for the retained palettes; v3.2 paints its own backdrop or follows the system wallpaper |
| Shapes | `ResolvedTheme.radius(RadiusRole…)`, `ui/theme/Components.kt` | Authored per-theme radii, with the pre-3.2 scaled path kept for retained palettes |
| Motion | `ui/theme/MotionIdentity.kt`, `LumaDesignTokens.Motion` | Subtle / cinematic / exact (+ retained quiet, expressive) with hard invariants |
| Home | `res/layout/activity_home.xml`, `launcher/HomeActivity.kt` | One composition; blur, label size and radii come from the theme's tokens |
| Switching | `ui/theme/ThemeEngine.kt` | One resolver feeding View and Compose surfaces |

* [Design system reference](docs/DESIGN_SYSTEM.md)
* [Design QA checklist](docs/QA_CHECKLIST.md) — separates static checks performed
  here from the device verification that is still required
* [Design bundle provenance](docs/design/v3.2/README.md) — zip hash, what was and
  was not copied, why Inter stands in for "Luma Display/Text", and two measured
  token discrepancies in the authored colours

**Compatibility.** Nothing is removed. The pre-3.2 palettes — Aurora, Zen, Void,
Obsidian, Graphite and Chroma — keep their storage keys, seeds, geometry and
voices; a stored `aurora`, `porcelain` or `vector` still resolves to the same
palette, and the typography keys `precision`, `serene`, `grotesk`, `serif`, `mono`
all still load (joined by `ambient` and `monograph`). A fresh install follows the
system: **Signature** in dark mode, **Zen** (the retained paper palette) in light
mode, because the v3.2 trio is authored dark and the specification's light
*surface* variant is not implemented yet.

**Status:** the v3.2 adoption has not been compiled, linted or rendered in this
workspace (no JDK/Android SDK/device). What was checked mechanically: exact
token parity between the bundle JSON and Kotlin, and the measured contrast of every
authored pair (two authored roles fall short of the specification's own targets —
see the provenance page). Unit tests, lint and any on-device rendering remain
outstanding; do not present these screens as device-verified until section D of the
QA checklist is complete.

---

## Architecture

Single Gradle module with strict package separation. A multi-module split was
considered and rejected: it would add build overhead and inter-module API
surface for eight objects with no distinct scoping rules, which conflicts with
the project's own "minimise overhead" rule.

```
com.lumaos.pro.launcher
├── core/            LumaApp, LumaCore, LumaLog, RoleHelper
│   ├── apps/        AppRepository (LauncherApps), IconCache, DefaultDockResolver
│   ├── prefs/       LumaPrefs — DataStore, every setting
│   ├── model/       ComponentKey
│   └── util/
├── launcher/        HomeActivity, LauncherViewModel, adapters, IconLoader
│   ├── model/       HomeItem, HomeLayout, HomeLayoutOps, HomeLayoutCodec, GestureAction
│   └── view/        AlphaIndexView, FolderIconDrawable
├── intelligence/    DeviceCapability, Profile(+Resolver), Rules, Cooldown,
│                    IntelligenceEngine, FrameMonitor, MemorySampler, BatterySampler,
│                    StartupTracker, FrameStats
├── optimizer/       Optimizer — the four supported actions
├── diagnostics/     Reading<T>, DiagnosticsStore (SQLite), StartupStats, formatting
├── settings/        SettingsActivity, SettingsScreen, Studio, DashboardScreen (Compose)
├── onboarding/      OnboardingActivity (Compose)
└── ui/              DesignTokens, ThemeMode compatibility, shared theme engine
    └── theme/       ThemeRepository, ThemeEngine, LumaDesignTokens (v3.2),
                     contrast, atmosphere, presets
```

### Appearance and theme system

Signature, Atmosphere and Monolith are LumaOS Pro's signature themes (Design
System v3.2), resolved by one shared engine for the View-based home, drawer,
folders, menus, hosted-widget frames, and Compose settings/Studio:

- **Signature** — a midnight ramp (`#06070B → #0F1219`), indigo-violet accents on
  translucent surfaces, blur 26 dp, and a light 64 sp clock.
- **Atmosphere** — a wallpaper-led screen with aurora-mint and sky accents, the
  deepest blur (34 dp, 165 % saturation), a 58 sp clock and a cinematic motion
  profile.
- **Monolith** — stone neutrals, a vermilion signal, near-square geometry
  (8/4/2/2/2), **no blur at all**, an 84 sp Space Grotesk clock and mono labels.

Aurora, Zen, Void, Obsidian, Graphite and Chroma remain available as retained
palettes, with their historical storage keys (`porcelain` and `vector` included) so
no saved preference is rewritten or used to reset the home layout. Theme selection persists through DataStore and does not change
app placement, folders, widget IDs/configuration, icon-pack choice, or unrelated
preferences. The Studio keeps custom color/type/icon/backdrop overrides when a
preset changes. New installations start with the Liquid experience. System,
scheduled, and context-profile switching remain available. A custom accent or
background deliberately switches the palette to the seed-derived path, which can guarantee contrast for an arbitrary colour —
the authored v3.2 roles are used only while the theme's own colours are in force.

The Studio previews the user's installed app icons and Android wallpaper color
seeds. Android exposes wallpaper colors here, not the wallpaper image, so that
preview uses a clearly approximate gradient; the actual launcher continues to
show the system wallpaper when Wallpaper is selected. Home widgets are hosted
through Android's real `AppWidgetHost` and their provider apps. There is no
weather/calendar aggregation source in this build. LumaOS Pro cannot redesign
Android-controlled surfaces — the system notification shade, Quick Settings, the
lock screen or the navigation UI — so its own shade and controls panel are built
from real readings of those systems instead of replacing them.

### Wallpaper and widgets

Open **Wallpaper Studio** from Settings or Home edit mode to preview the existing
Luma art, favorite images, import a private photo copy, and set Android's home,
lock, or both wallpapers. Pure and Liquid may use their own backdrop; choose
**Show system wallpaper on Luma Home** to change only that setting. Chroma can
follow Android wallpaper colors on API 27+, and the system picker remains available
for live/OEM wallpapers. Android controls cropping and the secure lock screen.

The widget picker includes **Luma Glass Music** and **Luma Glass Battery**, hosted
through the existing Android widget system. Music requires the optional Android
notification-access grant described before the system permission screen. It
follows compatible active sessions and exposes only advertised controls. Battery
uses Android's real reading: it updates while Luma Home receives battery events,
and otherwise uses Android's 30-minute-or-longer widget refresh; the check time
is displayed. The system may delay updates and notification-listener access may
be restricted by the device. Existing clock/date UI and third-party providers
remain available. Weather, calendar, and photo widgets are not supplied by Luma.
See [docs/WALLPAPER_WIDGET_REPORT.md](docs/WALLPAPER_WIDGET_REPORT.md) for the
verification boundary and backup limitations.

### Notifications and quick controls

The launcher's own shade (`LumaShade`) lists the notifications Android reports to
a `NotificationListenerService` and a Quick Controls panel beneath them. It is
reachable only through the gesture the user binds (see **Gestures**), opens over
the home screen at the modal depth the widget picker already uses, and closes on
Back, on a tap of the scrim, or on an upward drag from the scrim.

- **Cards show what the app posted** — icon, app name, title, body, time, group
  count and the app's own actions — and nothing else. A group summary is never
  listed alongside the children it summarises, and content an app marked secret
  stays hidden.
- **Inline reply is the app's reply.** A reply field appears only when the
  posting app exposed a `RemoteInput` on one of its actions; sending it re-uses
  that action's own `PendingIntent`, so the message goes through the app exactly
  as it would from the system shade. Dismiss uses Android's listener API and
  opening a card fires the app's content intent.
- **Quick controls report real device state.** Wi-Fi, mobile data, Bluetooth,
  airplane mode and hotspot are read where Android allows a read and are changed
  through Android's own panels (`Settings.Panel`, Bluetooth/Airplane settings)
  wherever a third-party write is reserved to the system — a tile never claims a
  change it could not make. Brightness and rotation lock need the
  `WRITE_SETTINGS` app-op, Focus needs Notification Policy Access, and both are
  requested through the real system screens with an explanation, never silently.
  Torch is offered only when the device has a flash unit that is not held by a
  camera app; battery comes from Android's sticky broadcast; media comes from the
  active `MediaSession` and shows an empty state when nothing is playing.
- **No new permission is requested.** The shade reuses the optional
  notification-access grant the Luma Glass Music widget already asks for, and
  four `<queries>` intents so the camera and cast destinations can be resolved on
  Android 11+.

See [docs/SHADE_CONTROLS_REPORT.md](docs/SHADE_CONTROLS_REPORT.md) for the
per-control capability table, the verification boundary and what still needs a
physical device.

### Context profiles

The home screen has four independent contexts: Work, Study, Personal, and Sleep.
Each keeps its own shortcut/folder layout and app-visibility list in DataStore;
profiles without a customized layout inherit the existing launcher layout, so
upgrading does not blank or rearrange the current desktop. The drawer also
respects the selected profile's hidden-app list.

Work (weekdays, 09:00–17:00), Study (weekdays, 17:00–20:00), and Sleep
(daily, 22:00–07:00) have editable local-time schedules by default. Personal is
the fallback outside scheduled windows and can also be assigned its own window.
A single inexact `AlarmManager` alarm is recalculated at each schedule boundary
and after reboot or wall-clock/time-zone changes; there is no polling service or
exact-alarm permission. Overlaps resolve deterministically as Sleep, Work,
Study, then Personal. Context selection and all schedule/layout/visibility data
remain on-device. Optional voice commands use Android's installed recognition
activity to switch profiles or open apps; LumaOS Pro does not retain transcripts.

Optional context-change notifications are controlled by a master switch and a
per-profile preference, and request Android's notification permission only when
enabled. Per-profile Do Not Disturb is also opt-in: Android Notification Policy
Access is granted by the user in system Settings, and LumaOS Pro restores the prior
interruption filter when leaving a DND profile when it still owns that filter.
These features do not alter other apps' notification settings.

Location, calendar-event, and physical-activity triggers are not enabled yet.
No location or calendar data is accessed by the current implementation.

### Why Views for the home screen and Compose for settings

The home screen is created and drawn on every single return to the desktop, on
the weakest hardware LumaOS Pro targets. A static XML tree with a `RecyclerView`
binds in one pass with no runtime composition to schedule. Compose is used for
settings and diagnostics, where the screens are state-driven, opened
deliberately, and the flexibility is worth the runtime.

### State

No instance state is saved or restored — the manifest declares
`stateNotNeeded="true"`. Everything derives from `StateFlow`s in
`LauncherViewModel` and from DataStore, so orientation changes and process death
both rebuild the same screen from the same source.

---

## What LumaOS Pro measures (and what it refuses to fake)

Every reading on the diagnostics screen is a `Reading<T>` with one of three
states, rendered with a visible badge:

- **Measured** — from a real API, with the source named (`Reading.Measured`)
- **Estimate** — derived, with the basis stated (`Reading.Estimated`)
- **Unavailable** — the platform doesn't expose it, with the reason stated
  (`Reading.Unavailable`)

`Unavailable` is a first-class result, not a hidden error.

| Metric | Source | Notes |
|---|---|---|
| LumaOS Pro process memory | `ActivityManager.getProcessMemoryInfo` | PSS in KB |
| Startup | `SystemClock.uptimeMillis()` | `Application.onCreate` → first **populated** home frame. Not a cold-start figure: process fork happens before any app code can run, and there is no public API for it. |
| Frame rendering | `Choreographer.FrameCallback` | Slow = >1.5 refresh periods; frozen = >700 ms (Android vitals definitions). Period derived from the real panel refresh rate. |
| Battery | sticky `ACTION_BATTERY_CHANGED` | Current draw reported as absent when the device omits it, never as 0 |
| Device capability | `ActivityManager.MemoryInfo`, `isLowRamDevice()`, `Runtime.availableProcessors()` | Drives the default profile |

Optimization results separate `completed` from `measuredImprovement`. An action
can complete perfectly and show no improvement, and the dashboard says so
("no measurable change in PSS") rather than reporting a win.

---

## The optimizer: scope is stated, never implied

LumaOS Pro touches only its own process, caches and databases. Every action carries
a visible "Scope: …" line.

| Action | Scope |
|---|---|
| Trim icon cache | LumaOS Pro's own memory |
| Clear LumaOS Pro cache | LumaOS Pro's own files |
| Compact diagnostics database | LumaOS Pro's own database |
| Re-apply performance profile | LumaOS Pro's own settings |

**LumaOS Pro does not** — and cannot legitimately — stop other apps, clear other
apps' caches, control CPU governors, change system animation scale, or reclaim
system RAM. It also does not ask for the permissions that would be needed to
attempt any of that.

An emergency reset restores the default profile and clears LumaOS Pro's caches and
history. It deliberately keeps the home layout, dock, folders and appearance
settings: a reset that rearranged the user's home screen would be worse than
the problem it solves.

---

## Gestures

Swipe up, swipe down, double tap on empty space, long press on empty space —
each configurable.

**Open controls panel** is offered as a gesture action. It opens LumaOS Pro's own
shade — real notifications plus the Quick Controls panel — from inside the
launcher. It is *not* Android's system shade and it changes no gesture binding by
itself: bind it in **Settings → Gestures → Swipe down** (or any other slot) and
it becomes reachable. Nothing is reserved for it, so it cannot steal the drawer
swipe, the drag-to-move lift or the system's own edge gestures.

**Not offered, on purpose:** opening *Android's* notification shade. Android
exposes no public API for it; the only route is an `AccessibilityService`, which
would mean asking for a permission that can observe and act on everything on
screen. The same reasoning excludes "show recent apps" and "lock the screen"
(the latter needs Device Admin). Rather than ship a button that silently does
nothing, those options are absent — see the note in `GestureAction.kt`.

---

## Privacy

- No account, backend, analytics, advertising SDK, or app network permission.
  LumaOS Pro itself does not upload app-list or diagnostics data.
- Installed app labels/icons are read from Android for launcher display and
  search. Preferences, home layout, launch history and diagnostics are stored
  locally.
- Android cloud backup may transfer preferences and layout. Device-to-device
  transfer also includes diagnostics history; cloud backup excludes it.
- Wallpaper Studio copies only user-selected photos into private storage. The
  copies may contain embedded metadata and are excluded from Android backup and
  device transfer. The manual Luma backup excludes photos and wallpaper favorites.
- The optional Luma Glass Music widget and the launcher shade share one Android
  notification-access grant. The grant can expose all notifications; the Music
  widget queries media sessions without reading or storing notification contents,
  and the Android widget host may cache the displayed media details. The shade
  reads notification content in memory to render a card and to send a reply
  through the posting app's own `PendingIntent`; it does not store, log or
  transmit titles, bodies or replies, and it keeps at most the newest cards in
  RAM for as long as the shade exists. The grant can be revoked in Android
  Settings at any time, and the shade then shows its revoked state instead of
  cards. No new permission is requested by either feature.
- Voice commands are optional and user-triggered. LumaOS Pro delegates recognition
  to the installed Android speech-recognition app; that provider may process
  audio locally or remotely under its own privacy policy. LumaOS Pro receives the
  recognized text to carry out the command and does not retain transcripts.
- The merged manifest requests `REQUEST_DELETE_PACKAGES` for the user-confirmed
  uninstall action. `QUERY_ALL_PACKAGES` is not requested; app discovery uses
  Android's `LauncherApps` API and the declared launcher-intent query.

---

## Running the code without a device

The JVM test suite uses Robolectric 4.16.1 (SDK 34), with
`isIncludeAndroidResources = true` so tests can use the merged Android
resources. These automated checks are useful but are not a substitute for a
fresh current-source build, lint run, or physical-device validation.

When run, they cover:

- `activity_home.xml` is valid, and every `R.id` that Kotlin touches exists in
  the layout **and is the expected view type** — the most common launcher crash
- `HomeActivity` completes `onCreate → onStart → onResume`, survives a
  configuration change, and does not finish on back press
- a cold start with zero installed apps does not crash
- adapters and layout managers attach to the grids
- DataStore round-trips (theme, profile, gestures, home layout) and real SQLite
  queries behave, including the history cap and `vacuum()`
- the optimizer deletes real files and reports the real byte count

Pure decisions are covered by plain JVM tests that need no device and no
Robolectric: widget slot geometry (`WidgetSlotGeometryTest`), what a notification
card shows and which notifications are listed at all (`NotificationPolicyTest`),
the quick-control tile set, slider mapping, battery reading, torch verdict and
the Do-Not-Disturb handover with context profiles (`QuickControlsPolicyTest`),
and the repair of a saved layout that holds the same item twice
(`HomeLayoutDedupeTest`).

They do **not** prove appearance, performance, or OEM-specific behaviour.
Robolectric draws no pixels and measures no jank. Build the current source and
validate it on supported physical devices before release; no verified APK/AAB
is checked into this repository.

## Known limitations

LumaOS Pro Shield, a Brave integration, and location/calendar/physical-activity
context triggers are not part of this release. Home-screen widgets are
implemented, but their provider consent, configuration and interaction still
need real-device validation. Android widget host IDs are not remapped after
system backup/restore, so restored placements may need to be added again. Imported
wallpaper photos are device-local and are not backed up. Some quick controls are
limited by Android itself rather than by this code — a tile that Android will not
let a launcher write opens the real system page, hotspot state is not readable
without carrier privileges, and Bluetooth state reads are restricted on API 33+.
Each limitation, and which controls are still unverified without a device, is
listed in [docs/SHADE_CONTROLS_REPORT.md](docs/SHADE_CONTROLS_REPORT.md). See
[BUILD_REPORT.md](BUILD_REPORT.md) for the release verification boundary.
