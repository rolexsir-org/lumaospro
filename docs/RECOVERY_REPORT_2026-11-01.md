# LumaOS Pro — Release Stabilization Report

Date: 2026-11-02 · Branch `arena/16dd279f-lumaospro` · Base `857e5e9` · HEAD `ff04af6`

> **This document replaces the 2026-11-01 version, which contained a false claim.**
> Section 2 states what was wrong and what the evidence actually shows.

---

## 0. Correction to the previous report

The previous report said the 79 compile errors were *"artifacts of the reconstructed
environment, not code defects — proven by being byte-identical on untouched `HEAD`"*,
and attributed them to *"library-version drift"*. **That claim was false.**

Being byte-identical on untouched `HEAD` proves only that the recovery work did not
*introduce* those errors. It says nothing about their cause. Reading each error site
disproved the drift explanation directly:

- `LumaShade.kt:553` had an orphan `else` with no matching `if` — a **syntax error**.
  No library version can cause that.
- `HomeActivity.kt` referenced `ClockStyle.NONE`, `.CHRONO` and `.EDITORIAL`. The enum
  in this tree is `{ DIAL, HERO, COMPACT }`. Those members **do not exist**.
- `NotificationExtractor.kt` called `MessagingStyle.Message.person`. The API 36
  accessor is `getSenderPerson()` — confirmed by extracting the class descriptor from
  `android.jar`.
- `MediaStore.INTENT_ACTION_STILL_CAMERA` does not exist; the constant is
  `INTENT_ACTION_STILL_IMAGE_CAMERA`.
- `RangeInfo.obtain(int,int,int,int)` was removed; API 36 exposes only `(IFFF)`.
- `GlassViews.drawable` takes four parameters and has no `radiusOverrideDp`.
- `ThemeColors` has no `dockSurface` or `dockRim`.

All 79 were in `app/src/main/java` — production source, in the release build path. The
baseline **could not compile**, so no release artifact could ever have been produced
from `857e5e9`. All 79 are now fixed and the module compiles clean (Section 5).

---

## 1. Baseline

Verified by command, not inferred.

| Item | Value | How verified |
|---|---|---|
| Base commit | `857e5e9` ("Merge pull request #26") | `git log` |
| Gradle wrapper | 8.13, `gradle-wrapper.jar` present (43,705 B) | `gradle/wrapper/` |
| AGP / Kotlin | 8.13.2 / 2.2.21 | `gradle/libs.versions.toml` |
| compileSdk / minSdk | 36 / 26 | `gradle/libs.versions.toml` |
| Modules | single `:app` | `settings.gradle.kts` |
| Release entry point | `./build.sh assembleRelease` (also `bundleRelease`) | `build.sh` |
| Signing | optional gitignored `keystore.properties`; unsigned release when absent; R8 on release | `app/build.gradle.kts` |
| Production sources | 142 files under `app/src/main/java` | `find` |
| Tests | 65 Kotlin files at base, 66 now (+1 added by this work); **all** in `app/src/test`, no `androidTest` source set | `find`, `git ls-tree` |
| Baseline compile | **79 errors** | kotlinc 2.2.21 against android-36 |
| Baseline DB-lifecycle guard | **6 violations, exit 1** | `toolchain/checks/db_lifecycle_check.py` |

### What this environment cannot do

Confirmed absent from `PATH`: `gradle`, `adb`, `sdkmanager`, `javac`, `java`,
`kotlinc`, `apksigner`, `zipalign`. All four toolchain hosts return HTTP `000`
(unreachable): `services.gradle.org`, `dl.google.com`, `api.adoptium.net`,
`repo1.maven.org`.

Therefore **the real Gradle release task was never executed here**, and neither were
the 66 unit tests, Robolectric, Android lint, resource packaging, APK/AAB assembly,
or signing. Everything below labelled "verified" was verified by direct compilation
or JVM execution of the real production classes, which is strong but is not the same
as a Gradle build.

What was reconstructed instead, outside the repository so nothing is committed:
Temurin 17 JRE (`jdk4py`), kotlinc 2.2.21 (npm `kotlin-compiler`), `android-36/android.jar`,
and 55 AndroidX/Compose/coroutines artifacts pulled by blob SHA from Gradle caches
committed in public repositories. **These artifact versions differ from
`libs.versions.toml`** (Compose 1.7.0 vs 1.7.8, material3 1.3.0 vs 1.3.2,
coroutines 1.9.0 vs 1.8.1), so a clean typecheck here proves *shape*, not version
parity.

---

## 2. Compiler errors — all 79 classified

Every one is in production source. None is a test, generated-code, dependency,
SDK/JDK-config or unsupported-task artifact.

| File | Count | Root cause | Fix |
|---|---|---|---|
| `launcher/home/LumaShade.kt` | 29 | **2 causes.** (a) `:553` orphan `else`, no matching `if` — syntax error that also poisoned the rest of the file. (b) 6 bare `LayoutParams(...)` at `addView` sites; `LumaShade` is not a `ViewGroup`, so the unqualified name resolved to nothing. | Restored the branch (`refreshAccessState` when granted, else `requestRebindIfGranted` — both already exist on `NotificationAccess`). Qualified the 6 as `LinearLayout.LayoutParams` (both parents are `LinearLayout`). |
| `launcher/HomeActivity.kt` | 36 | **6 causes.** Missing imports `ThemeId`/`TypeStyle`/`RadiusRole`; `ClockStyle.NONE/CHRONO/EDITORIAL` absent; `HomePreset.showDate`/`showContextLine` absent; `playReturn` called with a signature it does not have; `GlassViews.drawable` given a `radiusOverrideDp` it does not accept; `dockSurface`/`dockRim` read off `ThemeColors`, which has neither. | Added the imports. Mapped `EDITORIAL`→`HERO`, `CHRONO`→`DIAL` (see note). Added the two `HomePreset` fields. Trimmed the `playReturn` call to its real signature. Dropped the redundant argument. Read the dock roles off `SemanticColors` via the `s` binding the enclosing function already had. |
| `controls/DeviceControls.kt` | 6 | Smart cast on an open-getter property; `Boolean?` passed where `Boolean` required; `MediaStore.INTENT_ACTION_STILL_CAMERA` (typo) plus 2 cascades. | Local `val` for the connectivity manager; nullable interruption-filter state now reports `unavailable()` rather than being coerced to a state the panel cannot know; corrected constant. |
| `settings/studio/HomePreview.kt` | 3 | `EDITORIAL` ×2, `showContextLine`. | Same mapping and field as above. |
| `controls/GlassSlider.kt` | 3 | `RangeInfo.obtain` given `Int`s; only `(IFFF)` exists in API 36. | Floats, with `RANGE_TYPE_INT` retained so TalkBack still announces a whole percentage. |
| `controls/MediaSessionSource.kt` | 1 | `MediaController.Callback.onAudioInfoChanged` overridden with no parameter. | Real signature, non-null `PlaybackInfo` (the parameter is `@NonNull`). |
| `notifications/NotificationExtractor.kt` | 1 | `MessagingStyle.Message.person`. | `senderPerson`; the existing `SDK_INT >= P` guard already matched that accessor. |
| **Total** | **79** | | **All fixed** |

**Note on the `ClockStyle` mapping.** `ClockStyle` is `{ DIAL, HERO, COMPACT }`. The
call sites named members that never existed, so intent had to be reconstructed from
evidence rather than assumed. `EDITORIAL`→`HERO` because `HERO`'s own KDoc reads
"centred time with date beneath" and every preset described as a centred clock uses
`HERO`. `CHRONO`→`DIAL` because `DIAL` is the only dial member and `ChronoDialView` is
its view. No preset selects `DIAL`, so that view stays hidden — **this is an open
product question, not a settled decision.** `ClockStyle` is never persisted (only
`HomePreset.storageKey` is), so no stored value or schema changed.

`HomePreset.showDate` and `.showContextLine` were added as real fields, valued from
each preset's own description. `showContextLine` is `true` only for `HORIZON`; **that
one value has no direct evidence in the repository** and should be confirmed by
product.

---

## 3. Changes

Four commits on top of `857e5e9`; 26 files changed, +2299/−165.

| Commit | Content |
|---|---|
| `63a5fe9` | Recovery work: Settings/database crash fix, lifecycle hardening, contrast floor, package-rebuild coalescing |
| `93ff458` | All 79 pre-existing compile errors in `app/src/main` |
| `b3e3f68` | Executable regression harnesses for the two protected fixes |
| `ff04af6` | Signing password removed from `keystore/README.md` |

### 3a. Database lifecycle (the crash fix — preserved)

**Root cause.** `DiagnosticsStore` extends `SQLiteOpenHelper` and wrapped every
operation in `writableDatabase.use { }` / `readableDatabase.use { }` (lines 109, 122,
163, 182, 197, 209). `SQLiteOpenHelper` returns a **shared cached connection**; `.use`
closed it. `LumaCore` holds one store per process shared by Settings and Optimizer, so
the next caller hit `IllegalStateException: attempt to re-open an already-closed
object` or `connection pool has been closed`. The amplifier was `refreshHistory()`,
whose `try/finally` had no `catch`, on a `viewModelScope` with no handler — so the
exception killed the process.

**Fix.** Only `Cursor`s get `.use { }`. A `ReentrantLock` serialises whole-database
operations so `VACUUM` cannot hit `SQLITE_BUSY`. Insert and row-cap share one
transaction. `vacuum()` catches only `SQLiteException`. `databaseSizeBytes()` stats
the file. Schema untouched: `lumaos_diagnostics.db`, `SCHEMA_VERSION=1`, tables
`events` + `startup_samples`, `MAX_EVENTS=200`, `MAX_STARTUP_SAMPLES=30`.

Supporting changes: `DiagnosticsRepository` became the error boundary (sealed
`DiagnosticsHistory` = `Loaded`/`Unavailable(cause)`, `Boolean` mutators);
`SettingsViewModel` rethrows `CancellationException`, ignores a second tap on a
running action, and keeps a good icon-pack list on failure; `DashboardScreen` shows
`dash_history_error` with retry; `LumaCore.shutdown()` closes the store.

### 3b. Refresh coalescing (preserved)

`HomeActivity` package-broadcast handling now coalesces into a single trailing
`refreshAll()`, and `onDestroy()` calls `removeCallbacks`.

### 3c. Contrast floor

`SemanticColors.dividerStrong = ColorMath.ensureContrast(c.outlineStrong, c.surface1, 3.0)`.
**Scope caveat found while writing Section 4:** `dividerStrong` is read zero times
outside `ui/theme`, so this change is correct but has **no shipped-UI effect today**.
The previous report presented it as the resolution of 18 live violations; it was not.

---

## 4. Regression results

### 4a. Database-lifecycle guard — executed

```
$ python3 toolchain/checks/db_lifecycle_check.py .            -> OK, exit 0
$ python3 toolchain/checks/db_lifecycle_check.py <857e5e9>    -> 6 violations, exit 1
```
The 6 are all `DiagnosticsStore.kt` `.use` closures at lines 109/122/163/182/197/209.

### 4b. RefreshCoalescer — executed on a real JVM

`toolchain/harness/RefreshCoalescerHarness.kt`, run via
`toolchain/harness/run_coalescer_harness.sh`. It is compiled in the same kotlinc
module as the **real** `core/util/RefreshCoalescer.kt` (so `internal` is visible) and
executed on a JVM. The production class is not re-implemented, stubbed or replaced;
only the `CoroutineScope` and `CoroutineDispatcher` are supplied, because
`kotlinx-coroutines-test` cannot be downloaded here.

**Result: 37 assertions, 37 pass, 0 fail, exit 0.** Stable across 5 consecutive runs.

| Required property | Result |
|---|---|
| Rapid repeat requests | 21 requests → **2 passes** (all passes gated, so exact) |
| Queue bound | 41 requests across three gated passes → **3 passes**, not 41 |
| Mid-work arrival | requests during a pass produce exactly **one** trailing pass; no refresh lost |
| Concurrency | 25 requests from 8 threads → 2 passes, **0 overlaps** |
| Serialization | **0 overlapping passes** in every run |
| Failure + retry | failure reported once with its message; loading cleared; retry runs a 2nd pass |
| Sustained failure | 5 consecutive failures then success; coalescer never wedges |
| Cancellation | `CancellationException` reaches the operation and is **not** reported as a failure |
| Lifecycle termination | 10 requests after scope cancellation start **no** new work |
| No uncontrolled background work | all work runs on the supplied dispatcher, verified by thread name |

Two measurement corrections were needed and are recorded here because they matter:
the first draft asserted `started == passes` as the idle condition, which cannot hold
for a pass that throws, and an early draft asserted "241 requests ≤ 3 passes", which
passed only by timing luck — an unsynchronised 241-request burst measured **2, 3, 3,
17 and 18 passes** over five runs. That is correct behaviour (a request may land
during each successive pass), so the stress case now asserts the real invariants
(zero overlaps, ≥10× coalescing, no lost refresh) and reports the count as
timing-dependent.

### 4c. Theme contrast — executed against the real `ThemeEngine`

`toolchain/harness/ThemeContrastAudit.kt`, run via
`toolchain/harness/run_contrast_audit.sh`. Drives `ThemeEngine.resolve` and
`SemanticPalette.of`; contrast comes from `ColorMath.contrast`. **1260 combinations**
(9 presets × 2 polarities × 14 accent states × 5 wallpaper seed sets), **47 pairings**,
**59,220 measurements**.

Pairings are tiered, because one flat threshold misreports in both directions: WCAG
1.4.3 exempts inactive UI, and SC 1.4.11 governs only non-text elements needed to
identify a component or state. So decorative separators and disabled content are
measured and reported but not failed. Each pairing is flagged **live** when its roles
are actually read outside `ui/theme`; a violation on a role nothing reads is a
design-system defect, not a shipped one.

**Shipped-UI violations: 113 — all on one pairing, `accent / background`.**

| Split | Count | Worst |
|---|---|---|
| Theme's curated accent | **1** | 2.76:1 — `chroma/light`, no user override |
| User-supplied accent override | **112** | 2.75:1 — `porcelain/light`, accent `#FFFF00` |
| Latent (roles no view reads) | **0** | — |

Minimum ratio on any enforced pairing: **2.75:1**, from `accent / background`
(`porcelain/light`, user accent `#FFFF00` → `#999900` on `#F2F4F1`).

Context that keeps this in proportion:

- `accent / surface1` — the guarantee `ThemeEngine` actually documents — **passes**,
  at exactly **3.00:1**. So do `focusRing / surface1` and `trackOn / surface1`, also
  at exactly 3.00:1. **All three have zero headroom**, which is fragile: any future
  rounding or palette tweak tips them under.
- `accent / background` is *not* covered by the documented contract, which names
  `surface1` only. Whether the 113 are user-visible depends on components that draw
  accent-coloured UI directly on bare `background` rather than on a surface — **that
  needs device inspection, which could not be done here.**
- Every text pairing passes 4.5:1. Tightest: `onAccent / accent` and `onInfo / info`,
  both 4.58:1.
- Home-screen text (`homeForeground`, `homeMuted`) is composited over the wallpaper
  plus a legibility scrim, not over `ThemeColors.background`. Measured against the
  wallpaper seed actually behind it, the worst case is 1.09:1 (`chroma/light`,
  `#FFFFFF` on `#F3F5F8`) — **a flat-colour measurement would be meaningless here**,
  and the real result depends on the scrim, which is a runtime compositing question.

**Conclusion: the previous report's "0 violations" is not correct as a statement about
shipped UI.** With correct tiering there is one genuine curated-accent failure and a
systemic gap for user-chosen accents.

---

## 5. Build status

**Source compilation: PASS.** At HEAD `ff04af6`:

```
kotlinc 2.2.21 + Compose compiler plugin, against android-36 + AndroidX closure
142 sources -> exit 0, 0 errors, 28 warnings, 1074 class files
```
Baseline for comparison: **79 errors**. The 28 warnings are pre-existing platform
deprecations (`ACTION_WALLPAPER_CHANGED`, `TRIM_MEMORY_*`, the deprecated
`RangeInfo.obtain` overload) and annotation-target notices — not defects.

**Release task: NOT RUN.** `./build.sh assembleRelease` was never executed, because
Gradle, the Android SDK and a JDK are all absent and all four download hosts are
unreachable (Section 1). **The project must not be described as buildable.** A clean
typecheck of the main source set is necessary but not sufficient: it does not exercise
resource linking, manifest merging, R8, desugaring, or the Compose compiler's Gradle
integration, and the classpath versions here differ from `libs.versions.toml`.

No APK or AAB exists. No lint report exists.

---

## 6. Device verification

**None. Not performed, and not possible here** — no `adb`, no emulator, no SDK.

Static analysis and JVM execution of real production classes is what Section 4
provides. It is not a substitute for the ten device scenarios. Every one of these
remains unverified:

cold start · home-screen launch · app drawer · notification shade · quick controls ·
Settings → diagnostics refresh under rapid taps · theme switching across all presets ·
icon-pack change · memory pressure / `onTrimMemory` · process death and restore.

The Settings/diagnostics crash in particular has a plausible root cause and a
mechanically sound fix, but **it has never been observed failing or passing on a
device**. `app/src/test/.../robolectric/DiagnosticsStoreConcurrencyTest.kt` (5 `@Test`
methods, added by this work) **has never been executed** — Robolectric and `android-all` are
unobtainable here.

---

## 7. Outstanding issues, ranked

| # | Severity | Issue | Smallest next action |
|---|---|---|---|
| 1 | **High** | The signing password `Rolexsir911@` was committed in `keystore/README.md` and is still in git history at `857e5e9:keystore/README.md`. Removed from the working tree in `ff04af6`, but the edit does not retire the credential. | Treat it as exposed and **rotate the upload key** via Play Console's supported upload-key reset. History rewrite is separate and needs coordination. I did not touch signing identity, key alias or keystore path. |
| 2 | **High** | Release task never run — no APK/AAB, no lint, no R8. | Run `./build.sh assembleRelease` on a machine with network access to `services.gradle.org` and `dl.google.com`. |
| 3 | **High** | All 66 unit tests unexecuted, including the 5 in `DiagnosticsStoreConcurrencyTest`. | `./build.sh test`. |
| 4 | **Medium** | No device verification of any of the 10 scenarios (Section 6). | Install a debug build and walk the list, starting with Settings → diagnostics rapid refresh. |
| 5 | **Medium** | `accent / background` below 3:1 in 113/1260 combinations (1 curated: `chroma/light` at 2.76:1). Whether user-visible depends on components drawn on bare `background`. | Inspect on device; if visible, apply `ColorMath.ensureContrast(accent, background, 3.0)` alongside the existing `surface1` guarantee. **This will shift CHROMA's light accent hue**, so it is a design decision, not a stabilization edit — deliberately not made here. |
| 6 | **Medium** | `accent / surface1`, `focusRing / surface1` and `trackOn / surface1` all sit at exactly 3.00:1 — zero headroom. | Raise the floor to 3.1:1 in `ColorMath.ensureContrast` calls so a future palette tweak cannot silently break AA. |
| 7 | **Low** | `ClockStyle.CHRONO`→`DIAL` is an inference. No preset selects `DIAL`, so `ChronoDialView` never renders. | Confirm with product which preset should offer the dial. |
| 8 | **Low** | `HomePreset.showContextLine = true` only for `HORIZON` has no supporting evidence in the repository. | Confirm the intended per-preset values. |
| 9 | **Low** | 43 Android SDK cache files under `.android/` are tracked, including `debug.keystore.lock`. No key material, but build cruft that `.gitignore` does not cover. | `git rm -r --cached .android && echo '.android/' >> .gitignore`. Not done here: unrelated to stability and touches 43 files. |
| 10 | **Low** | **30 of 44** `SemanticColors` roles are read zero times outside `ui/theme` — the whole `widget*`, `search*`, `selected*`, `unselected*`, `chip*` and `track*` families, plus `divider`, `dividerStrong`, `dialogBorder`, `dialogScrim`, `disabledContent` and the `on*` partners of `success`/`warning`/`info`. Six `ThemeColors` roles are likewise unread (`onSurfaceFaint`, `glow`, `pressedOverlay`, `selectedFill`, `successSeed`, `warningSeed`). | Either wire them up or remove them; until then the contrast audit's "latent" bucket is the safety net. |

---

## 8. Release verdict

# BLOCKED

**Why not `BUILDABLE, NOT VERIFIED ON DEVICE`:** that verdict requires the release
task to have run. It has not, and cannot here. Source-level compilation passing is a
real result but it is not the release build — it does not cover resource linking,
manifest merging, R8, or packaging, and the reconstructed classpath does not match
`libs.versions.toml`.

**What specifically blocks release:**

1. No toolchain. Gradle, the Android SDK and a JDK are absent; `services.gradle.org`,
   `dl.google.com`, `api.adoptium.net` and `repo1.maven.org` are all unreachable.
2. No test execution. JUnit and Robolectric could not be obtained, so all 66 unit
   tests are unrun.
3. No device verification of any scenario.
4. An exposed signing credential in git history (Issue 1).
5. One unresolved curated-accent contrast failure (Issue 5).

**What has genuinely improved and is verified:**

- The baseline **did not compile** — 79 errors in production source. It now compiles
  clean: 142 sources, 0 errors, 1074 class files, exit 0.
- The database-lifecycle guard went from **6 violations to 0**, executed against both
  the fixed tree and the pristine baseline.
- `RefreshCoalescer` is verified by **37 passing assertions run on a real JVM against
  the production class**, covering every required property.
- The contrast position is now measured honestly: **113 shipped-UI violations on one
  pairing**, 112 of them only under user-supplied accents, plus a zero-headroom
  finding on three pairings — replacing an earlier "0 violations" claim that was wrong.
- A committed signing password was removed from the working tree.

The shortest path to a defensible verdict is Issues 2 and 3 on a networked machine,
then Issue 4 on a device. None of them require further code changes.

---

## Appendix — reproducing these results

```bash
# Database-lifecycle guard (runs anywhere with Python 3)
python3 toolchain/checks/db_lifecycle_check.py .

# RefreshCoalescer harness — needs a JDK + kotlinc + coroutines jars
KOTLIN_HOME=… CP=… ./toolchain/harness/run_coalescer_harness.sh

# Theme contrast audit — also needs android.jar, AndroidX/Compose jars and
# generated R.kt / BuildConfig.kt from a Gradle build
KOTLIN_HOME=… CP=… GEN_SRCS=… COMPOSE_PLUGIN=… ./toolchain/harness/run_contrast_audit.sh

# The real build (requires network access this sandbox does not have)
./build.sh assembleDebug && ./build.sh test && ./build.sh assembleRelease
```

Both harnesses are deliberately **outside** `app/src/test`: each is a `main()`, not a
JUnit test, and placing them there would make them look like tests the Gradle runner
silently skips.
