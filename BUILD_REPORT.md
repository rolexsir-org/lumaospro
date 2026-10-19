# Project ROLEXM — Build Report

**Date:** 2026-10-10
**Scope delivered:** Phases 1–5 (audit, stabilise, launcher, core/intelligence, measure & optimise)
**Result:** `assembleDebug` + `testDebugUnitTest` + `lintDebug` + `assembleRelease` all pass in a single run.

---

## 1. Phase 1 — Audit finding: there was no project

The brief instructed me to inspect the existing project and preserve its
architecture, package ID, signing config and working features. **None of that
existed.** `/home/user` was empty: 0 files, no git repository, no
`settings.gradle`, no `AndroidManifest.xml`, no keystore.

So Phase 1 could not be executed as written, and the "preserve X" rules were
moot. This was confirmed with the user before any code was written; the answer
was to treat ROLEXM as greenfield. Consequences, stated plainly:

- The application ID, minSdk, module layout and signing config are **my
  choices**, documented below, not inherited values.
- There is no baseline build to compare against; this report establishes one.
- Nothing was "preserved" because nothing was there to preserve.

**Environment provisioned** (sandbox shipped JDK 11 and no Android SDK):
JDK 17, Gradle 8.7, Android SDK 34 + build-tools 34.0.0, installed to
`~/.cache/rolexm-toolchain` by `toolchain/setup_toolchain.sh` so the toolchain
never inflates the project snapshot.

---

## 2. Project identity

| | |
|---|---|
| Application ID | `com.rolexm.launcher` |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 |
| Version | 0.1.0 (versionCode 1) |
| Kotlin / AGP | 2.0.21 / 8.5.2 |
| UI | Views (home screen, drawer) + Jetpack Compose (settings, diagnostics) |
| Signing | `keystore.properties` → `keystore/rolexm.jks`; falls back to debug key if absent |

---

## 3. Verification results

Run: `./build.sh assembleDebug testDebugUnitTest lintDebug assembleRelease`
→ **BUILD SUCCESSFUL** (104 tasks, 25 executed), and re-run after the Robolectric
work: `testDebugUnitTest` → 127/127 in 32s, `assembleRelease` → BUILD SUCCESSFUL
in 2m 36s.

### Unit tests — 127 passed, 0 failed, 0 errors (17 suites)

Two tiers. **Logic tests (89)** run on the JVM against pure Kotlin, as before.
**Runtime tests (38)** — added in response to "run it in the sandbox" — execute
the *real* Android code paths on the JVM under **Robolectric 4.16.1**, SDK 34,
with `isIncludeAndroidResources = true` so genuine merged resources are
inflated. See §3.1 for why this was necessary and what it does and does not
prove.

| Suite | Tests | Tier | Covers |
|---|---|---:|---|
| `HomeActivityRuntimeTest` | 8 | runtime | **the real `HomeActivity` inflated and driven through `onCreate → onStart → onResume`**; every `R.id` binding resolves with the expected view type; adapters and layout managers attach; drawer starts hidden; cold start with zero apps; configuration change re-binds grids; back press does not finish; `RolexApp` wiring |
| `PreferencesAndSamplersRuntimeTest` | 14 | runtime | real DataStore round-trips: theme, profile override, numeric clamping, gestures, home layout persist/reload, action history; `DeviceCapabilityProvider.snapshot`; `MemorySampler`/`BatterySampler` return real `Reading`s; `RoleHelper` default-launcher query |
| `OptimizerRuntimeTest` | 9 | runtime | internal-cache clear deletes real files and reports exact bytes; icon-cache trim respects the on-screen floor; profile apply; compact diagnostics; history recording; unknown action fails instead of throwing; **emergency reset scope** |
| `DiagnosticsStoreRuntimeTest` | 8 | runtime | real SQLite: full round trip, null PSS preserved, `MAX_EVENTS` cap keeps newest, startup-sample cap and ordering, `StartupStats`, `vacuum()`, `clearHistory()`, non-zero db size |
| `AppMatcherTest` | 10 | logic | sorting, stability, bucketing, relevance tiers |
| `HomeLayoutTest` | 13 | logic | add/remove/merge/prune, folder lifecycle |
| `HomeLayoutCodecTest` | 9 | logic | round-trip, escaping, malformed input |
| `DeviceCapabilityTest` | 8 | logic | tier thresholds, low-RAM cap, extremes |
| `FrameStatsAccumulatorTest` | 8 | logic | slow/frozen/jank, refresh-rate relativity |
| `CooldownTest` | 7 | logic | rate limiting, backwards clock, formatting |
| `RulesTest` | 8 | logic | each rule fires and stays quiet correctly |
| `ProfileResolverTest` | 5 | logic | override wins, pressure steps down |
| `ComponentKeyTest` | 5 | logic | round-trip, malformed input |
| `StartupStatsTest` | 5 | logic | median/latest/best |
| `ReadingTest` | 4 | logic | measured vs estimated vs unavailable |
| `ProfilesTest` | 3 | logic | ladder stepping, ordering |
| `FormattingTest` | 3 | logic | unit formatting |

Six tests failed across the Robolectric rollout. **All six were defects in my
tests, not in the product** — each was diagnosed to a specific line of product
code before anything was changed, and in no case was product code altered to
satisfy a bad test:

1. `IllegalArgumentException: Path is not a file` ×3 — setting
   `robolectric.dependency.dir` puts Robolectric in *offline* mode, so the
   151 MB framework jar must be pre-staged. `build.sh` now fetches it
   idempotently.
2. Home layout persist/reload — I added the *same* app to both dock and home.
   `HomeLayoutOps.addToHome` correctly refuses it, because
   `HomeLayoutOps.contains` (`HomeLayout.kt:69`) spans dock **and** pages. The
   product was right.
3. Emergency reset profile override ×2 — I had parked the test's preferences
   file inside `cacheDir`, the one directory `emergencyReset()` is designed to
   wipe, so the file was deleted mid-test. The real app stores preferences via
   the `preferencesDataStore` delegate (`RolexPrefs.kt:24`) under
   `filesDir/datastore`, which emergency reset never touches. I also initially
   wrote a regression test using *profile override* as the "must survive"
   sentinel — self-contradictory, since resetting the override to automatic is
   precisely what an emergency reset is for. The sentinel is now home layout,
   matching the feature's own promise that "your home layout, dock and settings
   were kept".

Three tests failed on first run. All three were **wrong expectations in my
tests**, not product bugs — verified individually before fixing:
jank mean arithmetic, a cooldown rounding case, and an assertion on
`Collator`'s locale-defined ordering of `1Weather` vs `@mailreader`.

### 3.1 Running the app's real code in the sandbox

I was challenged on the claim that the app could not be run here. The challenge
was fair, and the honest answer turned out to be *partly* runnable:

- **A real emulator is genuinely impossible.** I verified this rather than
  assuming it: `/dev/kvm` is absent, there is no `kvm` group, no
  `qemu-system-*`, no `adb`, no `emulator`; `lsmod` shows no KVM modules; CPU
  flags expose only `hypervisor`, meaning this sandbox is itself a container
  with no nested virtualisation (2 cores, 1.9 GB RAM). So there is no path to
  booting Android, and therefore **no instrumentation tests, no screenshots, no
  performance measurement**.
- **But most of the app is not emulator-only.** Robolectric runs the *real*
  Android framework classes on the JVM. That means the actual
  `activity_home.xml` is inflated, the actual `HomeActivity.onCreate` runs, the
  actual SQLite database is opened, and the actual DataStore files are written.

What the 38 runtime tests therefore **do** prove: layout XML is valid; every
`R.id` referenced in Kotlin exists in the layout *and* is the expected view
type (a mismatch here is the single most common launcher crash); adapters and
layout managers attach; the drawer starts hidden; a cold start with zero apps
does not crash; a configuration change re-binds correctly; Flows emit and
persist; file deletion reports accurate byte counts.

What they **cannot** prove: how it looks, how fast it is, or how it behaves on
real hardware. Rendering, jank, touch handling, OEM launcher integration and the
Phase 5 before/after measurement all still require the user's device. Robolectric
narrows the "never been run" gap; it does not close it.

### Lint — 0 errors, 47 warnings

39 warnings are `GradleDependency` / `AndroidGradlePluginVersion` ("a newer
version is available"). The remaining 8:

| Warning | Count | Disposition |
|---|---|---|
| `PluralsCandidate` | 3 | Accepted — would need `<plurals>` for "1 cores" vs "2 cores" |
| `NotifyDataSetChanged` | 3 | Accepted with justification in code; an appearance change alters every row's size/colour, so no cheaper invalidation is correct. Runs only on a settings change, never per frame |
| `ObsoleteSdkInt` | 1 | **False positive** — reverted. The suggestion to drop `-v26` from `mipmap-anydpi-v26` breaks adaptive icons: AAPT then cannot find `mipmap/ic_launcher` |
| `Overdraw` | 1 | Accepted — `selectableItemBackground` state drawable |

### Build output

| Artifact | Size | Notes |
|---|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 9.90 MB | debuggable, `.debug` application ID suffix |
| `app/build/outputs/apk/release/app-release.apk` | **1.28 MB** | R8 minified + resource-shrunk, signed (rebuilt after the Robolectric changes — same size, still signed) |

Release APK verified independently of the build:

```
apksigner verify
  Verifies
  Verified using v2 scheme (APK Signature Scheme v2): true
  Number of signers: 1
  Signer #1 certificate DN: CN=ROLEXM, OU=Development, O=ROLEXM, ...

aapt2 dump badging
  package: name='com.rolexm.launcher'  sdkVersion:'26'  targetSdkVersion:'34'
  uses-permission: 'android.permission.REQUEST_DELETE_PACKAGES'   ← the only one ROLEXM asks for
  uses-permission: 'com.rolexm.launcher.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
                                       ^ not requested by ROLEXM: androidx.core injects this
                                         automatically (protectionLevel 0x2 = signature) for its
                                         internal non-exported receiver. Harmless and standard.

aapt2 dump xmltree --file AndroidManifest.xml   (re-checked after the Robolectric changes)
  activity ... .HomeActivity
    android:launchMode=singleTask  stateNotNeeded=true  excludeFromRecents=true
    intent-filter
      action  android.intent.action.MAIN
      category android.intent.category.HOME      <- still present after R8
      category android.intent.category.DEFAULT
```

Merged manifest confirms the Home intent filter survived R8 and manifest
merging: `ACTION_MAIN` + `CATEGORY_HOME` + `CATEGORY_DEFAULT` on
`HomeActivity`, with `singleTask`, `stateNotNeeded`, `excludeFromRecents`.

---

## 4. What was implemented (Phases 1–5)

### ROLEXM Launcher
Home screen grid + dock, app drawer with search and an A–Z fast scroller,
favourites, folders (create by merging, rename, add/remove members,
auto-collapse when emptied), custom icon size, grid/rows/columns, label
visibility, text scale, light/dark/system themes, four configurable gestures,
first-run dock seeded by *intent resolution* (the device's real dialler,
browser, camera, settings — not hard-coded package names), package
add/remove/change via `LauncherApps.Callback`, and a Home-role flow with a safe
switch-away path.

### ROLEXM Core
`RolexCore` service holder, DataStore-backed `RolexPrefs` (every read
`catch`-wrapped so a corrupt preferences file degrades to defaults instead of
crashing the launcher at startup), `AppRepository` over `LauncherApps`.

### ROLEXM Intelligence
Process-lifecycle-bound engine: starts sampling when visible, **stops
completely** when not. Memory, battery and frame sampling on a profile-derived
interval; three rules with cooldowns; automatic profile step-down under
sustained jank, which never overrides an explicit user choice.

### ROLEXM Optimizer
Four actions with before/after PSS measurement and separate
`completed` / `measuredImprovement` flags (see §5).

### ROLEXM Diagnostics
Real measurements with provenance badges, startup history, action history in a
row-capped SQLite database.

### ROLEXM Settings (Compose)
Default launcher, appearance, gestures, performance, privacy, about, plus the
diagnostics dashboard.

---

## 5. Honesty measures built into the code

These are the points where this product category usually lies, and what ROLEXM
does instead:

1. **`Reading<T>` is a 3-state sealed class** — `Measured(source)`,
   `Estimated(basis)`, `Unavailable(reason)`. The UI renders a badge for each.
   Unavailable is first-class, not a hidden error.
2. **Startup is labelled for what it is.** It measures
   `Application.onCreate` → first *populated* frame. It is explicitly **not** a
   cold-start number, because the process fork happens before any app code runs
   and there is no public API for it. The dashboard says so.
3. **Optimization results distinguish completing from improving.** "No
   measurable change in PSS" is a normal, displayed outcome.
4. **Every action states its scope** in the UI: "ROLEXM's own memory",
   "ROLEXM's own files" — never an implied system-wide clean.
5. **Gestures that can't work honestly are absent, not faked.** No notification
   shade, no recents, no screen lock. Rationale documented in
   `GestureAction.kt`.
6. **No claim of peer-app control.** No cache clearing of other apps, no
   process killing, no governor changes, no "free X% RAM".

### A deliberate dependency removal

WorkManager was integrated, then removed. Its nightly `VACUUM` looked like
textbook "deferrable work" — but the merged manifest showed it pulling in
**four transitive permissions**: `FOREGROUND_SERVICE`,
`RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `ACCESS_NETWORK_STATE`. Four permissions
and a runtime for one `VACUUM` contradicts ROLEXM's own rule. The vacuum now
rides the intelligence engine, which only runs while ROLEXM is visible, and is
skipped under memory pressure. Final manifest: **one** permission.

---

## 6. Known limitations

### The verification boundary — read this before trusting the APK

**No device and no emulator is available** (2 cores, 1.9 GB RAM, no KVM, no
nested virtualisation — audited, see §3.1). What **is** verified:

| Verified | How |
|---|---|
| Compilation, R8 shrinking, resource shrinking, APK signing | `assembleRelease` + `apksigner verify` + `aapt2 dump badging` |
| Layout XML validity and view-id/type correctness | real inflation under Robolectric |
| `HomeActivity` lifecycle (create → start → resume → config change → back) | `Robolectric.buildActivity(...).setup()` |
| DataStore persistence, SQLite schema and queries | real implementations, temp files per test |
| Optimizer byte accounting and cache deletion | real filesystem operations |
| Lint | `lintDebug` — 0 errors |

What is **still not verified: appearance, performance, and real-device
behaviour.** Nothing has been rendered, tapped by a human, rotated on real
hardware, or installed. Robolectric drives the code, but it does not draw
pixels or measure jank. Claims like "smooth on a 1 GB phone" remain *design
intent*, not measurement, and the brief's Phase 5 before/after comparison on
real hardware has not happened. **The next step is genuinely on-device: install
`app-release.apk`, set it as default launcher, and confirm it looks and feels
right.**

### Features deferred from this pass

| Feature | Status | Why |
|---|---|---|
| Home-screen widgets (Phase 3) | **Not implemented** | Feasible via `AppWidgetHost` + `ACTION_APPWIDGET_BIND`, but it is the single most stateful, device-dependent surface in a launcher and cannot be validated without a device. Adding it blind risks destabilising the home screen. Highest-value next item. |
| ROLEXM Shield (Phase 6) | Not started | Out of the agreed Phase 1–5 scope |
| Brave bridge (Phase 7) | Not started | Out of the agreed Phase 1–5 scope |

### Other limitations

- **Single home page.** The data model (`HomeLayout.pages`) supports multiple
  pages; the UI renders page 0 only. No migration will be needed to add paging.
- **No drag-and-drop.** Folder creation is via long-press → "Merge into folder
  with…". Reordering by drag is not implemented.
- **Dependency versions are behind.** Compose is pinned to 1.7.8 / material3
  1.3.2 because that pairing is compatible with Kotlin 2.0.21; lint reports
  Compose 1.12.1 and core-ktx 1.19.1 as available. Upgrading means moving
  Kotlin forward too — worth doing, but it is a coordinated change, not a
  version bump.
- **R8 full mode is disabled** (`android.enableR8.fullMode=false`). Full mode
  shrinks harder but OOM-killed the build on this 2 GB machine. The explicit
  keep rules in `app/proguard-rules.pro` cover what full mode would infer. On
  a normal build machine, re-enabling it is safe and will shrink further.
- **Build tuning is sandbox-specific.** `gradle.properties` sets
  `-Xmx700m` and `workers.max=1`. A developer machine should raise these.

---

## 7. Files changed / created

Complete inventory — everything here is new (there was no prior state).

**Build (8):**
`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`,
`gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro`,
`build.sh`, `.gitignore`, `toolchain/setup_toolchain.sh`

**Manifest & resources (14):**
`app/src/main/AndroidManifest.xml`, `values/strings.xml`, `values/themes.xml`,
`values/colors.xml`, `layout/activity_home.xml`, `layout/item_app.xml`,
`drawable/ic_launcher_foreground.xml`, `drawable/ic_launcher_background.xml`,
`drawable/bg_icon_placeholder.xml`, `drawable/bg_search_field.xml`,
`drawable/bg_drawer_sheet.xml`, `mipmap-anydpi-v26/ic_launcher.xml`,
`mipmap-anydpi-v26/ic_launcher_round.xml`, `xml/backup_rules.xml`,
`xml/data_extraction_rules.xml`

**Kotlin sources — 42 files, 6,159 lines** across `core/`, `launcher/`,
`intelligence/`, `optimizer/`, `diagnostics/`, `settings/`, `onboarding/`,
`ui/` (full list in `README.md`).

**Tests — 6 files, 813 lines** under `app/src/test/`.

**Signing & docs:** `keystore/rolexm.jks`, `keystore/README.md`,
`keystore.properties` (gitignored), `README.md`, `BUILD_REPORT.md`

---

## 8. Recommended next steps

1. **Install the release APK on a real low-RAM device** and confirm: default
   launcher prompt, drawer scroll, app launch, rotation, and process-death
   recovery. This is the gap that matters most.
2. **Run the Phase 5 measurement for real** — capture startup, jank and PSS
   before/after each profile on that device.
3. **Add home-screen widgets**, now that there is a device to verify against.
4. **Then** Shield (Phase 6) and the Brave bridge (Phase 7); both are
   self-contained additions.
