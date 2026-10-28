# LumaOS Pro production recovery — verification report

Branch: `arena/9ea97265-lumaospro`
Baseline: `707178fcf0b732716d6d781f6cf37a06e59f2e24` (tag `recovery-baseline-20261027`)
Date: 2026-10-28

## 1. Executive summary

The checked-out source did **not** contain the reported `LumaDesignTokens.kt`
“Unclosed comment”. That file is 359 lines, is byte-identical to `HEAD`, parses
and compiles cleanly with Kotlin 2.2.21. The real blocker was a cluster of 13
genuine unresolved symbols / API mismatches in the recent Luma Glass, onboarding,
settings, widget and theme code, plus a release-version regression
(`1 / 0.1.0` instead of the documented `100 / 1.0.0`). Those are now fixed with
small, source-compatible repairs and two focused regression tests.

A complete Android/Gradle toolchain is **not** available in this sandbox: the
normal Gradle, Google and Maven Central hosts are unreachable, and there is no
official Android SDK, `adb`, emulator or production keystore. To get real
compiler evidence, I reconstructed an offline verification toolchain from the
hosts that are reachable (JDK 17, Gradle-source caches on GitHub, Kotlin
compiler from npm, `aapt2` from npm, Android platform stubs, cached AndroidX /
AGP / bundletool jars). This is **not** the project’s Gradle wrapper build and
must not be mistaken for one.

After the fixes, all 120 main Kotlin sources compile with Kotlin 2.2.21 and the
Compose compiler plugin; 292 JVM unit tests pass; resources link; D8 produces
two dex files; an unsigned release AAB validates with bundletool 1.17.2; and an
unsigned APK reports version `100 / 1.0.0`. The release artifacts are
**unsigned** because no production keystore exists. The app is **not** ready for
Play upload and no store approval is implied.

## 2. Root causes and fixes

| # | Diagnostic / defect | Root cause | Fix |
|---|---|---|---|
| 1 | `LumaDesignTokens.kt: Unclosed comment` | Not reproducible at this `HEAD`; file is balanced and compiles. Likely a stale diagnostic from another source copy/toolchain. | No change. Verified with a Kotlin lexer and standalone `kotlinc`. |
| 2 | `TextAlign.CENTER` unresolved | Compose `TextAlign` uses `Center`, not an enum-style `CENTER`. | `OnboardingActivity.kt`: `TextAlign.Center`. |
| 3 | `selected` unresolved in onboarding semantics | Missing `androidx.compose.ui.semantics.selected` import. | Import added. |
| 4 | Six `BoxScope` errors in `DashboardScreen.kt` | Missing `androidx.compose.foundation.layout.BoxScope` import. | Import added. |
| 5 | `first()` unresolved in `ExperienceProfileController` | Missing `kotlinx.coroutines.flow.first` import. | Import added; regression test added. |
| 6 | `shadowColor` unresolved twice in `GlassComponents.kt` | `ThemeColors` never declared a shadow role, although glass surfaces requested one. | Added a **derived** `ThemeColors.shadowColor` getter backed by the themed `scrim`; no new persisted constructor field. |
| 7 | `minCellHeight` unresolved in `WidgetHostController.kt` | `AppWidgetProviderInfo` has no public `minCellHeight` field. The Android 12 cell minimum is expressed through `minResizeHeight` / `targetCellHeight`. | Pass `0` for the optional launcher-side cell minimum; `WidgetPlan` still derives row minima from the real pixel fields. Comment corrected in `WidgetPlan.ProviderFacts`. |
| 8 | `singleLine` unresolved in `WidgetPicker.kt` | Kotlin exposes `TextView.isSingleLine`, not a `singleLine` property. | Changed to `isSingleLine = true`. |
| 9 | Version mismatch | `app/build.gradle.kts` had reverted to `versionCode=1`, `versionName="0.1.0"` while every release document and QA checklist says `100 / 1.0.0`. | Restored `100 / 1.0.0`; updated the historical manual-build constants for consistency. |
| 10 | Stale Gradle user-home config | `gradle.properties` pointed at `/home/rolexsir/.gradle`, another machine. | Removed the dead keys; `build.sh` now defaults `GRADLE_USER_HOME` to `$HOME/.gradle`. |

No package/application ID, persisted DataStore schema, storage key, navigation
contract, theme identity, billing/monetization code, or signing configuration was
changed. There is no billing/monetization dependency in the project.

## 3. Changed files

- `app/build.gradle.kts` — release version restored to `100 / 1.0.0`.
- `gradle.properties`, `build.sh` — remove stale absolute Gradle home; set a portable `GRADLE_USER_HOME` default.
- `app/src/main/java/com/lumaos/pro/launcher/onboarding/OnboardingActivity.kt` — `TextAlign.Center`, `selected` semantics import.
- `app/src/main/java/com/lumaos/pro/launcher/settings/DashboardScreen.kt` — `BoxScope` import.
- `app/src/main/java/com/lumaos/pro/launcher/settings/ExperienceProfileController.kt` — `first()` import.
- `app/src/main/java/com/lumaos/pro/launcher/ui/theme/ThemeModel.kt` — derived `shadowColor` role.
- `app/src/main/java/com/lumaos/pro/launcher/widgets/WidgetHostController.kt` — remove nonexistent platform field reference.
- `app/src/main/java/com/lumaos/pro/launcher/widgets/WidgetPicker.kt` — `isSingleLine`.
- `app/src/main/java/com/lumaos/pro/launcher/widgets/WidgetPlan.kt` — corrected `ProviderFacts` documentation.
- `app/src/test/java/com/lumaos/pro/launcher/ui/theme/ThemeEngineTest.kt` — new shadow-role regression test.
- `app/src/test/java/com/lumaos/pro/launcher/settings/SettingsPersistenceRegressionTest.kt` — new DataStore-backed profile/onboarding/layout/widget persistence test.
- `toolchain/manual-build/build.py` — version constants kept in sync (historical helper only).
- `dist/README.md`, `RECOVERY_REPORT.md` — release-output boundary and this report.

## 4. Verification table

| Check | Result | Evidence / boundary |
|---|---|---|
| Baseline reproduction | **13 real errors** after classpath reconstruction | `kotlinc 2.2.21` against all main sources, Android 36 stub, reconstructed AndroidX/Compose classpath. Earlier classpath gaps produced 1253 cascading errors; those were toolchain artifacts, not source defects. |
| `LumaDesignTokens.kt` parse | **Pass** | Standalone `kotlinc` compiled it; no unterminated comment/string at `HEAD`. |
| Main Kotlin compile | **Pass** | 120 sources, `-jvm-target 17`, Compose compiler plugin, `-opt-in=androidx.compose.material3.ExperimentalMaterial3Api`; 0 errors. |
| JVM unit tests | **292 passed, 0 failed** | JUnit 4.12 + Hamcrest 1.3 on the JVM; 36 test classes. Includes the two new regression tests. |
| Robolectric tests | **Not run (5 files)** | `AppRepositoryTest.kt` plus four files under `.../robolectric/` remain in the repo unchanged; Robolectric and `android-all-instrumented` jars are unavailable offline. |
| Coroutine test support | **Local shim only** | One offline-only `kotlinx.coroutines.test` shim was used to execute `RefreshCoalescerTest`; it is not shipped and is not a product dependency. |
| Resource compile/link | **Pass** | `aapt2` from npm compiled/linked 548 resource flats; generated `R` for app + 14 libraries. |
| Dex | **Pass** | D8/R8 8.13.2 produced `classes.dex` and `classes2.dex`. |
| AAB validation | **Pass (unsigned)** | `bundletool 1.17.2 validate` succeeded on `dist/app-release-unsigned.aab`. |
| APK badging | **Pass (unsigned)** | `aapt2 dump badging`: `com.lumaos.pro.launcher`, `versionCode='100'`, `versionName='1.0.0'`, `minSdk 26`, `targetSdk 36`, compileSdk 36. |
| Signature check | **Unsigned, as expected** | 0 signature blocks in both AAB and APK. No production keystore or `keystore.properties` exists; none was created. |
| Install / device test | **Not performed** | `adb` not installed, `/dev/kvm` absent, no emulator or physical device. |
| Gradle / AGP / lint / instrumentation | **Not performed** | Official Gradle/SDK/Maven repositories unreachable; exact catalog versions could not be resolved through the wrapper. Offline verification used nearest cached artifacts and a local-only toolchain. |
| Security / platform static review | **Pass by inspection** | Manifest requests only `REQUEST_DELETE_PACKAGES`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`; no `QUERY_ALL_PACKAGES`, accessibility, or device-admin. `PendingIntent`s are immutable; dynamic receivers use `RECEIVER_NOT_EXPORTED` on API 33+; widget binding uses the platform consent flow; DataStore reads catch `IOException`. |

### Offline dependency substitutions (verification only)

The repository’s version catalog pins were **not changed**. Because Maven/Google
artifacts were unreachable, the offline verification classpath used the nearest
cached binaries available on reachable GitHub mirrors, e.g. Compose UI
`1.7.0` instead of `1.7.8`, Material3 `1.3.0` instead of `1.3.2`, Lifecycle
`2.8.3 / 2.6.2 / 2.9.4` instead of `2.8.7`, Activity `1.9.0` instead of
`1.9.2`, and coroutines `1.9.0` instead of `1.8.1`. This proves the source
compiles against compatible APIs, but it is **not** a substitute for a real
Gradle build with the exact catalog versions.

## 5. Release artifacts

Generated during verification, **not committed**. The AAB and APK are covered
by the repository’s `*.aab` / `*.apk` ignore rules; the sandbox does not persist
generated `dist/` artifacts between turns.

| Artifact | Size | SHA-256 |
|---|---:|---|
| `dist/app-release-unsigned.aab` | 21.51 MB | `0d0d12b740fb2ac77b6d61ff6fde99638698bb494a74b9741ed45998d2d1dd93` |
| `dist/app-release-unsigned.apk` | 21.89 MB | `35876805181f10b56b697398dd5234d325a0fc8f4994d586f7183d1da45fed16` |
| `dist/SHA256SUMS` | — | recorded the two hashes above |

Signing: **none**. Install result: **not attempted** (no `adb`/device). These
files are release-shaped but not Play-uploadable. The historical
`toolchain/manual-build` pipeline is explicitly not an approved production
release path; it was used here only for compiler, unit-test and packaging
evidence under network restrictions.

## 6. Verified vs assumed

**Verified by execution:** Kotlin 2.2.21 compilation of all main sources; 292
JVM unit tests; resource linking; D8; unsigned AAB/APK generation; bundletool
validation; aapt2 badging; absence of signature blocks; absence of `adb`/KVM.

**Verified by inspection:** manifest permission set, exported components,
PendingIntent flags, dynamic receiver flags, widget binding flow, DataStore
error handling, absence of billing/monetization code, preservation of the three
v3.2 theme identities and their token parity tests.

**Assumed / not proven:** exact Gradle/AGP dependency resolution with the
catalog versions; lint; Robolectric and instrumentation tests; real-device
startup, rendering, performance, gesture and widget behaviour; Play Store
approval; production signing.

## 7. Remaining work and next action

1. Run the real toolchain from a networked CI environment:
   `./gradlew clean assembleDebug testDebugUnitTest lintDebug bundleRelease`.
2. Resolve the exact `gradle/libs.versions.toml` artifacts from Google/Maven
   Central; remove the offline substitution workaround.
3. Execute the 5 skipped Robolectric tests and any instrumentation tests on an
   emulator/device.
4. Owner must supply the production upload keystore and a gitignored
   `keystore.properties`; then build a signed release AAB and verify with
   `apksigner` / bundletool.
5. Perform manual device QA (launcher role, onboarding, three themes, widgets,
   drawer, gestures, settings, backup/restore, permission-denial paths).

**Next action:** provision a normal Android SDK/Gradle environment with network
access and the owner’s release signing credentials. Until then the branch is
source-repaired and compiler/test-verified, but **not release-ready**.
