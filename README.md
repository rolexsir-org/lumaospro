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
./build.sh test               # 127 tests (89 logic + 38 Robolectric runtime)
./build.sh lint               # Android lint
./build.sh assembleRelease    # minified, R8-shrunk, signed APK
```

`build.sh` provisions the JDK 17 / Gradle / Android SDK toolchain into
`~/.cache/rolexm-toolchain` on first run (the sandbox ships only JDK 11 and no
Android SDK). The toolchain lives outside the repo so it never bloats the
project snapshot.

The 38 runtime tests execute the app's **real** code on the JVM via Robolectric,
inflating the actual layouts and driving `HomeActivity` through its lifecycle.
No emulator is required and none is possible in this environment (no KVM, no
nested virtualisation). See §"Running the code without a device" below for
exactly what that proves and what it does not.

On first launch ROLEXM offers to become the default launcher. Skipping is a
first-class outcome — it stays fully usable as an app, and the choice remains
available in Settings at any time.

---

## Project identity

| | |
|---|---|
| Application ID | `com.rolexm.launcher` |
| minSdk | 26 (Android 8.0) |
| targetSdk / compileSdk | 34 (Android 14) |
| Version | 0.1.0 (versionCode 1) |
| UI | Views for the hot path, Jetpack Compose for settings & diagnostics |
| Signing | `keystore.properties` (gitignored), falls back to debug key |

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
└── ui/              Palette, ThemeMode
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

- No account, no backend, no analytics, no advertising SDK, no telemetry.
- All data stays on the device.
- **One permission** in the merged manifest: `REQUEST_DELETE_PACKAGES`, used
  only by the user-initiated uninstall item, which launches the system
  confirmation dialog.
- `QUERY_ALL_PACKAGES` is not requested: the app list comes from
  `LauncherApps`, the official launcher API, which is not subject to
  package-visibility filtering.
- Backup includes settings and layout; diagnostics are excluded from cloud
  backup by omission (`res/xml/data_extraction_rules.xml`).

---

## Running the code without a device

A real Android emulator is **not possible** in this workspace: there is no
`/dev/kvm`, no nested virtualisation, no `qemu` and no `adb`. Rather than accept
"it has never run", 38 of the tests run the app's genuine code on the JVM under
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
