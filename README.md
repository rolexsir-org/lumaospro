# Project ROLEXM

A standalone, lightweight Android launcher and performance platform for older
and low-resource phones.

ROLEXM runs its own launcher logic, resource monitoring and optimization
routines. It does not depend on Brave, another launcher, or a third-party
optimizer to function.

---

## Quick start

```bash
./build.sh assembleDebug      # debuggable APK, installs alongside other launchers
./build.sh test               # 133 tests (94 logic + 39 Robolectric runtime)
./build.sh lint               # Android lint
./build.sh assembleRelease    # minified APK; release signing is never debug-key fallback
./build.sh bundleRelease      # Android App Bundle for Play (requires upload-key signing)
```

`build.sh` provisions the JDK 17 / Gradle / Android SDK toolchain into
`~/.cache/rolexm-toolchain` on first run (the sandbox ships only JDK 11 and no
Android SDK). The toolchain lives outside the repo so it never bloats the
project snapshot.

The 39 runtime tests execute the app's **real** code on the JVM via Robolectric,
inflating the actual layouts and driving `HomeActivity` through its lifecycle.
No emulator is required and none is possible in this environment (no KVM, no
nested virtualisation). See §"Running the code without a device" below for
exactly what that proves and what it does not.

On first launch ROLEXM offers to become the default launcher. Skipping is a
first-class outcome — it stays fully usable as an app, and the choice remains
available in Settings at any time.

### Install on a connected Android device

```bash
./build.sh assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.rolexm.launcher.debug 1
```

The debug build uses the separate application ID `com.rolexm.launcher.debug`.
For a release APK, run `./build.sh assembleRelease`; install
`app/build/outputs/apk/release/app-release.apk`. Release variants are unsigned
unless valid local upload-key credentials are configured; they are never signed
with the debug key. Google Play distribution uses the signed AAB from
`./build.sh bundleRelease` at `app/build/outputs/bundle/release/app-release.aab`.
See [keystore/README.md](keystore/README.md) for safe upload-key setup.

### Google Play release checklist

- The project now targets Android 16 / API 36. Google Play requires new mobile
  apps and updates submitted from August 31, 2026 to target API 36 or higher
  ([current target API policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)).
- Upload the signed AAB and enroll in Play App Signing. `bundleRelease` does not
  sign with a debug key; without a valid upload key it will not produce an
  uploadable signed release.
- Complete the Play Console Data safety form and provide a public privacy-policy
  URL. Google requires both even for apps declaring no data collection
  ([Data safety requirements](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)).
  ROLEXM's in-app privacy summary describes local launcher/diagnostics data and
  Android backup behavior, but it is not a substitute for the publisher's
  reviewed policy and Play Console disclosures. This repository has no
  publisher contact details or public policy URL, so those must be supplied by
  the app owner; none are fabricated here.
- Review the supplied 512 × 512 icon and 1024 × 500 feature graphic in
  `store-assets/`. Capture real screenshots from verified devices, confirm the
  application ID, increment `versionCode` for each upload, and complete Play's
  review and testing requirements. These artwork files are not device
  screenshots, and a local build is not Play approval.

---

## Project identity

| | |
|---|---|
| Application ID | `com.rolexm.launcher` |
| minSdk | 26 (Android 8.0) |
| targetSdk / compileSdk | 36 (Android 16) |
| Build tools | JDK 17 · Gradle 8.13 · AGP 8.13.2 · Kotlin 2.2.21 |
| Version | 0.1.0 (versionCode 1) |
| UI | Views for the hot path, Jetpack Compose for settings & diagnostics |
| Signing | Optional local upload key (`keystore.properties`, gitignored); unsigned release if absent |

---

## Architecture

Single Gradle module with strict package separation. A multi-module split was
considered and rejected: it would add build overhead and inter-module API
surface for eight objects with no distinct scoping rules, which conflicts with
the project's own "minimise overhead" rule.

```
com.rolexm.launcher
├── core/            RolexApp, RolexCore, RolexLog, RoleHelper
│   ├── apps/        AppRepository (LauncherApps), IconCache, DefaultDockResolver
│   ├── prefs/       RolexPrefs — DataStore, every setting
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
├── settings/        SettingsActivity, SettingsScreen, DashboardScreen (Compose)
├── onboarding/      OnboardingActivity (Compose)
└── ui/              Palette, ThemeMode, RolexSpacing/RolexCorners
```

### Why Views for the home screen and Compose for settings

The home screen is created and drawn on every single return to the desktop, on
the weakest hardware ROLEXM targets. A static XML tree with a `RecyclerView`
binds in one pass with no runtime composition to schedule. Compose is used for
settings and diagnostics, where the screens are state-driven, opened
deliberately, and the flexibility is worth the runtime.

### State

No instance state is saved or restored — the manifest declares
`stateNotNeeded="true"`. Everything derives from `StateFlow`s in
`LauncherViewModel` and from DataStore, so orientation changes and process death
both rebuild the same screen from the same source.

---

## What ROLEXM measures (and what it refuses to fake)

Every reading on the diagnostics screen is a `Reading<T>` with one of three
states, rendered with a visible badge:

- **Measured** — from a real API, with the source named (`Reading.Measured`)
- **Estimate** — derived, with the basis stated (`Reading.Estimated`)
- **Unavailable** — the platform doesn't expose it, with the reason stated
  (`Reading.Unavailable`)

`Unavailable` is a first-class result, not a hidden error.

| Metric | Source | Notes |
|---|---|---|
| ROLEXM process memory | `ActivityManager.getProcessMemoryInfo` | PSS in KB |
| Startup | `SystemClock.uptimeMillis()` | `Application.onCreate` → first **populated** home frame. Not a cold-start figure: process fork happens before any app code can run, and there is no public API for it. |
| Frame rendering | `Choreographer.FrameCallback` | Slow = >1.5 refresh periods; frozen = >700 ms (Android vitals definitions). Period derived from the real panel refresh rate. |
| Battery | sticky `ACTION_BATTERY_CHANGED` | Current draw reported as absent when the device omits it, never as 0 |
| Device capability | `ActivityManager.MemoryInfo`, `isLowRamDevice()`, `Runtime.availableProcessors()` | Drives the default profile |

Optimization results separate `completed` from `measuredImprovement`. An action
can complete perfectly and show no improvement, and the dashboard says so
("no measurable change in PSS") rather than reporting a win.

---

## The optimizer: scope is stated, never implied

ROLEXM touches only its own process, caches and databases. Every action carries
a visible "Scope: …" line.

| Action | Scope |
|---|---|
| Trim icon cache | ROLEXM's own memory |
| Clear ROLEXM cache | ROLEXM's own files |
| Compact diagnostics database | ROLEXM's own database |
| Re-apply performance profile | ROLEXM's own settings |

**ROLEXM does not** — and cannot legitimately — stop other apps, clear other
apps' caches, control CPU governors, change system animation scale, or reclaim
system RAM. It also does not ask for the permissions that would be needed to
attempt any of that.

An emergency reset restores the default profile and clears ROLEXM's caches and
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
  ROLEXM itself does not upload app-list or diagnostics data.
- Installed app labels/icons are read from Android for launcher display and
  search. Preferences, home layout, and diagnostics are stored locally.
- Android cloud backup may transfer preferences and layout. Device-to-device
  transfer also includes diagnostics history; cloud backup excludes it.
- The merged manifest requests `REQUEST_DELETE_PACKAGES` for the user-confirmed
  uninstall action. `QUERY_ALL_PACKAGES` is not requested; app discovery uses
  Android's `LauncherApps` API and the declared launcher-intent query.

---

## Running the code without a device

A real Android emulator is **not possible** in this workspace: there is no
`/dev/kvm`, no nested virtualisation, no `qemu` and no `adb`. Rather than accept
"it has never run", 39 of the tests run the app's genuine code on the JVM under
Robolectric 4.16.1 (SDK 34), with `isIncludeAndroidResources = true` so the real
merged resources are used.

They prove:

- `activity_home.xml` is valid, and every `R.id` that Kotlin touches exists in
  the layout **and is the expected view type** — the most common launcher crash
- `HomeActivity` completes `onCreate → onStart → onResume`, survives a
  configuration change, and does not finish on back press
- a cold start with zero installed apps does not crash
- adapters and layout managers attach to the grids
- DataStore round-trips (theme, profile, gestures, home layout) and real SQLite
  queries behave, including the history cap and `vacuum()`
- the optimizer deletes real files and reports the real byte count

They do **not** prove appearance, performance, or real-device behaviour.
Robolectric runs the code but draws no pixels and measures no jank. **Install
`app-release.apk` on a real phone to validate those.**

## Known limitations

See [BUILD_REPORT.md](BUILD_REPORT.md) for the full list, including features
deferred from this pass (home-screen widgets, ROLEXM Shield, Brave bridge) and
the verification boundary.
