# Project LumaOS Pro

A standalone, lightweight Android launcher and performance platform for older
and low-resource phones.

LumaOS Pro runs its own launcher logic, resource monitoring and optimization
routines. It does not depend on Brave, another launcher, or a third-party
optimizer to function.

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
  clearance before first upload. Increment `versionCode` on each later update
  and follow any account-specific Play testing gates shown in Console. The new
  name and package have not been independently cleared.

---

## Project identity

| | |
|---|---|
| Application ID | `com.lumaos.pro.launcher` (provisional; verify availability before first upload) |
| minSdk | 26 (Android 8.0) |
| targetSdk / compileSdk | 36 (Android 16) |
| Build tools | JDK 17 · Gradle 8.13 · AGP 8.13.2 · Kotlin 2.2.21 |
| Version | 0.1.0 (versionCode 1) |
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
    └── theme/       ThemeRepository, ThemeEngine, contrast, atmosphere, presets
```

### Appearance and theme system

Aurora, Zen, and Void are LumaOS Pro's signature themes, resolved by one
shared engine for the View-based home, drawer, folders, menus, hosted-widget
frames, and Compose settings/Studio:

- **Aurora** — midnight navy and obsidian surfaces, icy blue accents, and a
  slow, restrained polar glow.
- **Zen** — quiet paper neutrals, sage and blue-grey accents, clear text
  hierarchy, and a soft daylight lift.
- **Void** — indigo depth with cyan and magenta signals, fine grid detail, and
  reduced highlight intensity.

Obsidian, Graphite, and Chroma remain available as additional palettes. To
preserve existing settings and backups, Zen and Void retain the historical
`porcelain` and `vector` storage keys; the IDs are not rewritten or used to reset
the home layout. Theme selection persists through DataStore and does not change
app placement, folders, widget IDs/configuration, icon-pack choice, or unrelated
preferences. The Studio keeps custom color/type/icon/backdrop overrides when a
preset changes. New installs follow Android's system appearance, choosing
Aurora for dark mode and Zen for light. Scheduled and context-profile switching
remain available.

The Studio previews the user's installed app icons and Android wallpaper color
seeds. Android exposes wallpaper colors here, not the wallpaper image, so that
preview uses a clearly approximate gradient; the actual launcher continues to
show the system wallpaper when Wallpaper is selected. Home widgets are hosted
through Android's real `AppWidgetHost` and their provider apps. There is no
weather/calendar aggregation source in this build, and LumaOS Pro cannot
redesign Android-controlled surfaces such as the notification shade, Quick
Settings, lock screen, or navigation UI.

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

**Not offered, on purpose:** opening the notification shade. Android exposes no
public API for it; the only route is an `AccessibilityService`, which would mean
asking for a permission that can observe and act on everything on screen. The
same reasoning excludes "show recent apps" and "lock the screen" (the latter
needs Device Admin). Rather than ship a button that silently does nothing, the
option is absent — see the note in `GestureAction.kt`.

---

## Privacy

- No account, backend, analytics, advertising SDK, or app network permission.
  LumaOS Pro itself does not upload app-list or diagnostics data.
- Installed app labels/icons are read from Android for launcher display and
  search. Preferences, home layout, launch history and diagnostics are stored
  locally.
- Android cloud backup may transfer preferences and layout. Device-to-device
  transfer also includes diagnostics history; cloud backup excludes it.
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

They do **not** prove appearance, performance, or OEM-specific behaviour.
Robolectric draws no pixels and measures no jank. Build the current source and
validate it on supported physical devices before release; no verified APK/AAB
is checked into this repository.

## Known limitations

LumaOS Pro Shield, a Brave integration, and location/calendar/physical-activity
context triggers are not part of this release. Home-screen widgets are
implemented, but their provider consent, configuration and interaction still
need real-device validation. See [BUILD_REPORT.md](BUILD_REPORT.md) for the
release verification boundary.
