# LumaOS Pro — Engineering Recovery Report

Branch `arena/16dd279f-lumaospro`, base `857e5e9`.
Commits: `c0b1a18` (crash + lifecycle), `97cc9c9` (package rebuilds + contrast).

## 0. What this environment could and could not do

Read this first; it bounds every claim below.

The repository's supported build path is `./build.sh`, which provisions JDK 17,
Gradle 8.13 and the Android SDK. **That path cannot run here**: this sandbox has
outbound access only to `github.com`, `codeload.github.com`, `api.github.com`,
`registry.npmjs.org`, `pypi.org` and `files.pythonhosted.org`.
`api.adoptium.net`, `services.gradle.org`, `dl.google.com` and
`repo1.maven.org` all returned `000`. So `assembleDebug`, `test`, `lint` and
`assembleRelease` were **not** run.

Rather than stop there, I reconstructed enough of a toolchain from the reachable
hosts to do real verification. The repository's own
`toolchain/manual-build/README.md` documents where each piece comes from, which
is what made this possible:

| Need | Source that worked | Result |
|---|---|---|
| JDK | PyPI `jdk4py==17.0.9.2` (manylinux wheel) | Temurin 17.0.9 — **JRE only**, no `javac` (`jdk.compiler` absent) |
| Kotlin compiler 2.2.21 + Compose plugin + stdlib + `kotlinx-coroutines-core-jvm` | npm `kotlin-compiler@2.2.21` | `kotlinc-jvm 2.2.21` runs |
| `android-36/android.jar` | `Sable/android-platforms` via codeload | 27 MB platform stubs |
| AndroidX / Compose / DataStore / lifecycle closure (55 artifacts) | Gradle caches committed in public repos, listed in `toolchain/manual-build/resolve.py` | pulled by blob SHA via `api.github.com` |
| JUnit 4.13.2 / Hamcrest / Robolectric `android-all` | — | **not obtainable.** Only POMs are cached; the real jars are Git-LFS objects and `media.githubusercontent.com` plus the LFS batch endpoint are blocked (403) |

Consequences, stated plainly:

- **Compilation of the real sources: yes.** All 142 files in
  `app/src/main/java` were compiled with kotlinc 2.2.21 + the Compose compiler
  plugin against `android-36` and the AndroidX closure.
- **Execution of real compiled classes: partially.** Pure-Kotlin/coroutine code
  was compiled and *run*. Android-framework-dependent code was not.
- **The project's JUnit/Robolectric suite: not run.** No JUnit jar, no
  Robolectric `android-all` jar.
- **No device, no emulator, no profiler.** No frame timings, no cold-start
  numbers, no Logcat. Every performance claim below is a code-path argument, not
  a measurement, and is labelled as such.

## 1. Confirmed root causes

### 1.1 The Settings crash — shared SQLite connection closed per operation

`DiagnosticsStore` extends `SQLiteOpenHelper`. A helper owns **one**
`SQLiteDatabase` for its whole lifetime; `readableDatabase` /
`writableDatabase` hand back that same cached instance. `LumaCore` holds a
single `DiagnosticsStore` for the process (`LumaCore.kt:57`), so the Settings
dashboard and the Optimizer genuinely share one connection.

Every operation was wrapped in `readableDatabase.use { … }` /
writableDatabase.use { … }. `SQLiteDatabase` implements `Closeable`, so `use`
**closed the shared connection after every single read and write.**

The failure needs two operations to overlap, and opening Settings reliably
creates that overlap:

- `SettingsViewModel.init` → `refreshHistory()` → `recentEvents(30)`
- `SettingsActivity.onResume` → `refreshHistory()` *again*, plus `refreshApps()`
  and `refreshIconPacks()` — a fresh `viewModelScope.launch(Dispatchers.IO)` on
  **every** resume
- meanwhile `Optimizer.run` → `diagnostics.record(…)` and `vacuumBlocking()`

Whichever finished first closed the connection under the other. The survivor
threw `IllegalStateException: attempt to re-open an already-closed object` or
`SQLiteException: Cannot perform this operation because the connection pool has
been closed`.

### 1.2 That exception was fatal, not recoverable

`refreshHistory()` was:

```kotlin
viewModelScope.launch(Dispatchers.IO) {
    _historyLoading.value = true
    try { … } finally { _historyLoading.value = false }
}
```

`try/finally` with **no catch**. `viewModelScope` is
`SupervisorJob() + Dispatchers.Main.immediate` with no
`CoroutineExceptionHandler`, so an exception in the child goes to
`Thread.uncaughtExceptionHandler` — on Android, the process-killing handler.
A recoverable database read therefore took the launcher down. The same shape
existed in `refreshApps()` and, with no `try` at all, in `refreshIconPacks()`.

### 1.3 Why the existing test suite did not catch it

`DiagnosticsStoreRuntimeTest` exercises the real SQL — but **every operation in
it is sequential**. After a close, the next `getDatabaseLocked()` simply reopens,
so nothing fails. Only a connection-lifetime assertion or concurrent access
exposes the defect. That gap is what the new test closes.

### 1.4 `dividerStrong` broke its own documented contrast floor

`SemanticColors`' class contract promises "focus rings and dividers: >= 3.0:1 on
the surface they sit on". `SemanticPalette` passed `outlineStrong` straight
through. Measured through the real `ThemeEngine`, `dividerStrong` reached
**1.47:1** on Signature/dark and failed the 3.0:1 floor in 10 preset/mode
combinations — a "strong" divider that is effectively invisible.

### 1.5 Bulk package events rebuilt the whole home screen once per package

`LauncherApps.Callback.onPackagesAvailable/Unavailable` deliver a batch as one
callback per package. Each called `refreshAll()`, which does
`notifyItemRangeChanged` over the entire home grid, dock and drawer. A
work-profile sync or a batch of updates therefore rebinded everything N times on
the main thread.

### 1.6 Secondary defects found on the way

- `Optimizer.run` caught bare `Exception` around the action dispatch, swallowing
  `CancellationException` and then recording a "Failed" event for an action that
  had merely been cancelled.
- `Optimizer.compactDiagnostics` wrapped `databaseSizeBytesBlocking()` in
  `runCatching { }.getOrDefault(0L)`, hiding real failures.
- `SettingsViewModel.exportBackup` caught `_: Exception` with no log at all — a
  silent swallow with no diagnostic.
- `LaunchHistoryStore.retainOnly` had `runCatching { }` with no `onFailure`.
- `NotificationHub.publish()` computed the full card projection (filter, group,
  sort, map) **while holding the `entries` lock** that the main thread also takes
  for `fallbackIcon`, `dismiss` and `open`.
- `DiagnosticsStore.vacuum()` caught all `RuntimeException`, concealing genuine
  programming errors behind an expected-failure branch.
- `LumaCore.shutdown()` never closed the store it owns.

## 2. Every changed file, and why

| File | Change |
|---|---|
| `diagnostics/DiagnosticsStore.kt` | Root-cause fix. Never closes the helper's database; only `Cursor`s, via `.use`. Insert + row-cap trim in one transaction. `ReentrantLock` keeps `VACUUM` from colliding with live traffic. `vacuum()` catches only `SQLiteException` so programming errors still surface. `databaseSizeBytes()` stats the file via `getDatabasePath` instead of opening a connection. `close()` is the single release point. Row/table names hoisted to constants. Cursor mapping extracted to `Cursor.readEvents()`. |
| `diagnostics/DiagnosticsRepository.kt` | New error boundary. `loadHistory()` returns `DiagnosticsHistory.Loaded` / `.Unavailable` so an unreadable database is reported rather than misrepresented as empty history. `record` / `recordStartup` / `clearHistory` report success instead of throwing telemetry failures upward. Adds `recordBlocking`. |
| `settings/SettingsViewModel.kt` | `refreshHistory` and `refreshApps` now go through the repository's existing `RefreshCoalescer` — one pass at a time plus one trailing pass, so `onCreate` and every `onResume` cannot stack competing IO jobs on the shared connection, and a failure reaches `onFailure` instead of the process-killing handler. New `historyError` / `appsFailed` state. `runAction`, `emergencyReset`, `exportBackup`, `importBackup` rethrow `CancellationException` and log real causes. `runAction` ignores a second tap on an action already running, and clears its running flag in `finally`. `refreshIconPacks` keeps a previously good list instead of blanking it. |
| `settings/DashboardScreen.kt` | An unreadable history says so and offers a working retry, instead of claiming "No actions recorded yet". |
| `res/values/strings.xml` | `dash_history_error`, in the same voice as the existing `drawer_retry_apps`. |
| `optimizer/Optimizer.kt` | Rethrow `CancellationException` from the action dispatch. Drop the `runCatching` around `databaseSizeBytesBlocking`, which no longer throws. |
| `core/LumaCore.kt` | `shutdown()` closes `diagnosticsStore` — the one place the connection is released, which is what makes "never close it per operation" safe. |
| `core/usage/LaunchHistoryStore.kt` | A pruned-history failure is logged, not swallowed. |
| `notifications/NotificationHub.kt` | `publish()` snapshots the raw models under the `entries` lock and runs the projection unlocked, guarded by a separate `stateLock` for the read-modify-write on the `StateFlow`. |
| `launcher/HomeActivity.kt` | `schedulePackageRefresh()` coalesces the full rebuild into one trailing pass on the existing `undoHandler`; removed in `onDestroy`. Per-package icon invalidation stays immediate. |
| `ui/theme/SemanticColors.kt` | `dividerStrong` goes through `ColorMath.ensureContrast(…, 3.0)` — the same mechanism the neighbouring `focusRing` line already used. KDoc now states precisely which roles carry the floor and why the hairline `divider` deliberately does not. |
| `test/.../robolectric/DiagnosticsStoreConcurrencyTest.kt` | New. Regression coverage for §1.1/§1.3. |
| `toolchain/checks/db_lifecycle_check.py` | New. Static guard; Python 3 only, no dependencies. |

No application ID, signing identity, release configuration, dependency version,
database schema or persisted preference was changed. No secret was touched,
created or committed. There is no billing or licensing implementation in this
codebase — the only "licenses" code is the open-source-attributions dialog in
`SettingsScreen.kt:1114`, backed by `app/src/main/assets/licenses/`.

## 3. Features preserved

Home screen, app drawer, app search, icons and labels, app launching, dock,
widgets, shortcuts, gestures, wallpapers, all 9 themes, notification shade and
replies, context profiles and automation, launch history/frecency, backup
export/import via SAF, icon packs, living icons, the Optimizer and its
diagnostics, onboarding, and the Home-role flow.

Concretely, the invariants I was careful not to break:

- The diagnostics **schema is untouched** (`SCHEMA_VERSION = 1`, same tables,
  same columns, same indexes, same row caps). Existing history survives.
- `DiagnosticsStore`'s public method signatures are unchanged except that
  `insertEvent` still returns `Long`, and repository methods now return
  `Boolean`/`DiagnosticsHistory` where they returned `Unit`/`Long` — every
  caller in the tree discards those values, verified by grep before the change.
- The `IconLoader` design (single background thread, in-flight tracker, negative
  cache, density and spec invalidation) was **left alone**. It is already
  correct: one decode thread by design for a 2-core target, bounded LRU cache
  sized by profile, `invalidatePackage` on package change.
- `NotificationHub`'s ordering guarantees (single ordered worker, connection
  generation guards, bounded 60 entries) are unchanged; only the lock scope moved.
- `LaunchHistoryStore` already closed only its cursors. It was the correct
  pattern in the codebase and was used as the model for the fix.

## 4. Tests executed and actual results

### 4.1 `toolchain/checks/db_lifecycle_check.py` — ran, real before/after

```
$ python3 toolchain/checks/db_lifecycle_check.py /tmp/tc/baseline   # HEAD, unmodified
6 violation(s):
  …/diagnostics/DiagnosticsStore.kt:109: closes the SQLiteOpenHelper's shared connection via `use`. …
  …/diagnostics/DiagnosticsStore.kt:122: …
  …/diagnostics/DiagnosticsStore.kt:163: …
  …/diagnostics/DiagnosticsStore.kt:182: …
  …/diagnostics/DiagnosticsStore.kt:197: …
  …/diagnostics/DiagnosticsStore.kt:209: …
EXIT=1

$ python3 toolchain/checks/db_lifecycle_check.py /home/user/lumaospro
OK: no database-lifecycle violations.
EXIT=0
```

Six violations, exactly the six `.use` sites in the original `DiagnosticsStore`.
This is a linter — it inspects the shipped sources, it does not execute them, and
it is labelled that way in the script's own docstring.

### 4.2 Crash-path harness — ran, real before/after

Compiled the repository's **actual** `RefreshCoalescer`, `DiagnosticsStore`,
`DiagnosticsRepository`, `Samplers` and `LumaLog` from `app/src/main/java`
(compile exit 0), then executed a harness against those real classes:

```
PASS  OLD pattern lets a DB failure reach the uncaught-exception handler
PASS  NEW pattern reports the failure instead of escaping
PASS  NEW pattern never reaches the uncaught-exception handler
PASS  NEW pattern clears the loading flag after a failure
PASS  repeated requests collapse rather than stacking
PASS  20 requests produce a bounded number of passes
PASS  the trailing pass still runs so data is never stale
PASS  no exception escapes a healthy pass
PASS  StartupStats.from(emptyList()) is null
PASS  median of 3 samples
PASS  best of 3 samples
PASS  latest is the last sample, not the largest
PASS  median of 4 samples averages the middle pair
PASS  formatBytes switches to MB at 1 MiB
PASS  formatBytes of a negative value is a dash, not a lie

ALL CHECKS PASSED
```

The first line is the important one: a bare `viewModelScope.launch` whose body
throws **does** reach the `CoroutineExceptionHandler` — which on Android is the
process-killing handler. Lines 2–3 show the replacement does not. Lines 5–6 show
20 requests collapse to ≤3 passes, which is the `onCreate` + repeated-`onResume`
case.

### 4.3 Theme contrast audit — ran, real before/after

Compiled the real `ThemeEngine`, `SemanticPalette`, `ColorMath`, `ThemeModel`,
`TypeSystem`, `WallpaperArt`, `LumaTokens`, `LumaDesignTokens`, `MotionIdentity`
and the `motion` package, then measured every documented guarantee over all 9
presets in light and dark plus 4 accent overrides:

```
BEFORE:  18 GUARANTEE(S) VIOLATED   lowest measured ratio: 1.47:1
         dividerStrong failed >= 3.0:1 in 10 distinct preset/mode combinations
AFTER:   ALL CONTRAST GUARANTEES HOLD   lowest measured ratio: 3.00:1
```

Every other guarantee already held and still holds: `onSurface` ≥ 7:1,
`onSurfaceMuted` ≥ 4.5:1, `onSurfaceFaint` ≥ 3:1, `accent` ≥ 3:1,
`onAccent` ≥ 4.5:1, `onBackground` ≥ 7:1, all status/container pairs ≥ 4.5:1,
`disabledContent` ≥ 3:1, `focusRing` ≥ 3:1, plus selection, widget, search and
inverse pairs.

### 4.4 Full-module compilation — ran, baseline-diffed

All 142 files in `app/src/main/java`, kotlinc 2.2.21, Compose compiler plugin,
`-jvm-target 17`, `-opt-in=ExperimentalMaterial3Api`:

```
baseline (HEAD, unmodified): 79 errors
this branch:                 79 errors
normalised diff (line numbers stripped): IDENTICAL
```

The 79 are artifacts of the reconstructed environment, not code defects — proven
by being byte-identical on untouched `HEAD`. They are library-version drift
(cached Compose 1.7.0 vs catalog 1.7.8, RecyclerView 1.2.1 vs 1.3.2, lifecycle
2.9.4 vs 2.8.7) and gaps in the generated-`R` stand-in (`INTENT_ACTION_STILL_CAMERA`,
`addCategory`, `dockSurface`). **This is a compilation check, not a Gradle build**:
no AGP, no R8, no resource shrinking, no manifest merge, no lint, no APK.

### 4.5 Not executed

- `DiagnosticsStoreConcurrencyTest` — needs Robolectric's `android-all` jar.
- All 65 files in `app/src/test` — no JUnit jar obtainable.
- `./build.sh test`, `lint`, `assembleDebug`, `assembleRelease` — no Gradle, no
  Android SDK, no AGP.
- Anything on a device or emulator.

## 5. Performance

No measurements were possible: no device, no profiler, no Perfetto, no frame
timings. **I am not reporting benchmark numbers, because there are none.** What
follows is code-path reasoning, and it is labelled as such.

Changes that reduce work, with the mechanism:

- **Package bursts**: N full grid rebinds → 1. Previously each of N package
  callbacks ran `notifyItemRangeChanged` across the entire home grid, dock and
  drawer on the main thread. Now one trailing pass. This is the clearest win and
  the easiest to observe on a device (install or update several apps at once).
- **Notification bursts**: the card projection no longer runs under the lock the
  main thread needs for `fallbackIcon` / `dismiss` / `open`. Main-thread callers
  now wait only for a `LinkedHashMap` copy, not for filter + group + sort + map.
- **Repeated Settings visits**: `onCreate` plus every `onResume` previously
  started a fresh IO job each for apps and history; now one pass plus one
  trailing pass each.
- **Diagnostics reads**: no longer pay a full `SQLiteDatabase` reopen per
  operation, which the `.use` pattern forced every time.

Deliberately **not** changed, with reasons:

- **Icon loading.** `IconLoader` is already the right design: one decode thread
  (documented as deliberate for 2-core targets), in-flight dedup, negative cache,
  density/spec invalidation, bounded LRU sized by profile, `invalidatePackage` on
  package change. The brief says not to raise thread counts blindly and to
  measure contention first; I could not measure, so I changed nothing.
- **Notification projection coalescing.** Considered and rejected. The expensive
  case (listener connect/resync, dozens of notifications) is already batched into
  a single `publish`. Coalescing individual posts would need to separate
  "apply to entries" from "publish" and cannot be done correctly with a FIFO
  single-thread executor, because the drain task interleaves 1:1 with the posts.
  Adding that complexity for an unmeasurable gain was the wrong trade.

## 6. Outstanding defects, ranked

1. **HIGH — the Gradle build is unverified here.** No `assembleRelease`, no R8,
   no lint, no device test. `DiagnosticsStoreConcurrencyTest` has never been
   executed. Everything in §7 must be run by someone with the toolchain.
2. **MEDIUM — `NotificationHub` is a process-lifetime `object`** holding
   `application`, a listener reference and up to 60 extracted notifications. It
   clears correctly on disconnect/destroy, but the singleton makes the lifetime
   hard to test and impossible to reset between tests.
3. **MEDIUM — `IconCache` is sized in entries, not bytes.** `ENTRY_APPROX_KB = 40`
   assumes 96×96 ARGB_8888. On a high-density device with larger icons the real
   ceiling is higher than the diagnostics screen reports.
4. **LOW — `NotificationPromptSheet` is dead code.** Its own KDoc says it "is not
   wired into startup"; grep confirms no construction site. Candidate for
   deletion, but that is a product decision, not a stability fix.
5. **LOW — `LumaCore.shutdown()` is never called.** No `onTerminate` override and
   no other caller, so the new `diagnosticsStore.close()` will not run in
   production. It is still correct ownership and matters for tests.
6. **LOW — the visual layer is unverified.** No screenshots, no device. The
   contrast fix is measurable; nothing else about appearance is.

## 7. Manual verification instructions

Needs a device or emulator with the real toolchain.

**7.1 The crash (highest priority).**
```
./build.sh test --tests '*DiagnosticsStoreConcurrencyTest*'
./build.sh test --tests '*DiagnosticsStoreRuntimeTest*'
```
Then on a device: open Settings → Diagnostics tab, tap "Trim icon cache" and
"Compact diagnostics database" repeatedly and rapidly, and background/foreground
Settings while a run is in flight. Before the fix this was the reliable repro;
after it, nothing should crash and `adb logcat -d | grep FATAL` should be empty.

**7.2 Repeated Settings open/close.** Open and close Settings 20 times, including
via the home screen's edit mode (`EXTRA_TAB=studio`). Expect no crash, no
duplicated rows in Recent activity, and the loading spinner to clear.

**7.3 History-load failure path.** With the dashboard open, fill storage or
corrupt `lumaos_diagnostics.db`, then pull-to-refresh. Expect "Couldn't read
recent activity. Tap to try again." in the error colour — **not**
"No actions recorded yet", and not a crash. Tapping it must retry.

**7.4 Notification burst.** Grant notification access, then post 30+ notifications
from a test app in quick succession. The shade must stay scrollable and the home
screen must not stutter. Then revoke access in Android Settings and return:
expect the revoked state, not stale cards.

**7.5 Package changes.** Install, update and uninstall apps, including a bulk
event (work-profile sync, or `adb install-multiple`). The grid should update
once, not flicker N times. Icons must be correct after an update, and a removed
app must disappear from home, dock and drawer.

**7.6 Permissions.** Deny `POST_NOTIFICATIONS`, then deny again (permanently),
then grant from Android Settings and return. No loops, no duplicate dialogs.
Same for the Home role via `RoleHelper`.

**7.7 Themes.** Switch through all 9 presets in light and dark, plus a custom
accent (`#FF4581` is the documented mid-tone edge case) and wallpaper-derived
colours. Dividers that separate sections should now be visibly present where they
were previously near-invisible — most noticeably Signature, Obsidian and
Atmosphere in dark mode.

**7.8 Configuration change.** Rotate, and force a process death
(`adb shell am kill com.lumaos.pro.launcher.debug`) then return. Layout, dock,
folders, theme and preferences must survive.

**7.9 Release.** `./build.sh bundleRelease` with a real `keystore.properties`.
Confirm R8 keeps everything in `app/proguard-rules.pro` and that the bundle
verifies.

## 8. Decisions that need the developer

1. **Run the real suite.** §7.1 is the gate. Until `DiagnosticsStoreConcurrencyTest`
   passes under Robolectric, the fix is *implemented and reasoned*, not *proven*.
2. **`dividerStrong` is now visibly stronger** in 10 preset/mode combinations.
   That is the documented contract being honoured, but it *is* a visual change.
   Confirm it reads correctly on a device; if a preset was relying on the
   invisible divider, the right fix is that preset's `outlineStrong`, not the
   floor.
3. **`NotificationPromptSheet`** — delete or wire up?
4. **Wire `LumaCore.shutdown()`** to something real, or drop the method?
5. **`IconCache` byte sizing** — worth switching to a byte-budgeted `LruCache`
   with `sizeOf`, or is entry-count good enough for the target devices?
6. **Toolchain provenance.** `toolchain/manual-build/README.md` already warns its
   pipeline is not for release. My verification reused the same *sources*
   (npm `kotlin-compiler`, public Gradle caches) at **different versions** from
   the catalog pins. That is fine for a regression diff, and must never be
   treated as a release check.

## 9. Status

**CONFIRMED FIXED** (root cause identified, code changed, mechanism verified by
execution or by a real before/after):
- Shared SQLite connection closed per operation → no longer closed; single
  release point in `LumaCore.shutdown()`. Guard: 6 violations at `HEAD`, 0 after.
- Recoverable history-read failure crashing the process → routed to `onFailure`;
  harness shows the old shape reaching the uncaught-exception handler and the new
  shape not.
- Competing IO jobs from repeated Settings open/resume → coalesced; harness shows
  20 requests → ≤3 passes.
- `CancellationException` swallowed in `Optimizer.run`, `exportBackup`,
  `importBackup` → rethrown.
- Silent swallows in `LaunchHistoryStore.retainOnly`, `compactDiagnostics`,
  `exportBackup` → logged or removed.
- `dividerStrong` below its documented floor → 18 violations → 0; lowest ratio
  1.47:1 → 3.00:1.
- Bulk package events rebuilding the grid N times → one trailing pass.
- Notification projection under the main thread's lock → moved out.
- `diagnosticsStore` never closed → closed at shutdown.

**TESTED AND PASSING** (actually executed here):
- `toolchain/checks/db_lifecycle_check.py` — 0 violations after, 6 before.
- Crash-path harness, 15/15 checks, against real compiled production classes.
- Theme contrast audit, 23 guarantees × 9 presets × light/dark + 4 accents — all
  hold after, 18 violated before.
- Full-module compilation: 79 errors, byte-identical to the untouched `HEAD`
  baseline → zero new errors introduced.

**IMPLEMENTED BUT NOT YET VERIFIED**:
- `DiagnosticsStoreConcurrencyTest` — written, never run (no Robolectric).
- Every behavioural change on a device: the Settings flow end to end, the
  dashboard error/retry UI, the package-rebuild coalescing, the notification lock
  change, and the visual effect of the divider fix.
- The Gradle build in all variants, lint, and R8 output.

**STILL OPEN**: everything in §6, and §8.

**This is not production-ready.** The confirmed Settings/database crash has a
root-cause fix and a regression test, and the changed sources compile with zero
new errors — but the project's own build and test suite have not been run in
this environment, and no change here has been observed on a device.
