# LumaOS Pro build and release-readiness report

> **2026-10-30 update:** The notification shade, Quick Controls panel, widget
> slot geometry, saved-layout duplicate repair and Studio artwork decode are
> reported in [docs/SHADE_CONTROLS_REPORT.md](docs/SHADE_CONTROLS_REPORT.md),
> including the per-control capability table and the outstanding device matrix.
> For that work, **no build, unit test, lint run or device test was executed**:
> this workspace has no JDK, no Gradle, no Android SDK, no `adb` and no network
> route to the hosts that serve them. What was executed is listed in §8 of that
> report (tree-sitter parse of 141 main and 64 test sources, resource and
> manifest cross-check, independent re-derivation of the new arithmetic). No
> compile, test, lint or runtime result is claimed for it anywhere in this file.
>
> **2026-10-28 update:** The current branch's production-polish work and actual
> validation outcomes are recorded in [docs/POLISH_REPORT.md](docs/POLISH_REPORT.md).
> The subsequent wallpaper and widget work is reported separately in
> [docs/WALLPAPER_WIDGET_REPORT.md](docs/WALLPAPER_WIDGET_REPORT.md).
> The sections below describe earlier passes; their references to the "current"
> workspace are historical and are not evidence for this branch.

## Verification boundary

This report distinguishes prior historical verification from the current
source. The old build logs belonged to an earlier source/toolchain state and
have been removed so they cannot be mistaken for current release evidence. Per
the project owner's request, no build, test, lint, APK/AAB verification, or
device test was run for the current changes.

In the current workspace, the documented baseline command was attempted before
modification. `./build.sh test` initially could not start because the script did
not have its executable bit set; the build and setup scripts have since been
made executable. The command was then retried and reached provisioning, but
could not download JDK 17 (`api.adoptium.net` is not reachable from this
sandbox). No Java, Gradle, Android SDK, or `adb` executable is available in the
current PATH, and no local toolchain cache was present. Consequently,
Gradle/Robolectric tests, lint, debug/release APK builds, and device installation
could not be run here. A standalone Kotlin compiler and JRE were later
bootstrapped locally from npm/PyPI for pure-JVM validation only; no Android SDK
or Gradle distribution became available.

The current source still needs a fresh lint run; no lint result is claimed here.

## Luma Glass pass — executed verification (current branch)

The following checks were actually executed in this workspace:

- Standalone `kotlinc` 2.2.21 on JRE 17 compiled the pure Kotlin token, theme,
  motion, composition and model sources with `compile_exit=0`.
- A lightweight JUnit 4 shim and reflection runner executed **242 JVM-only unit
  tests** from 28 test classes: `RESULT passed=242 failed=0`. This includes all
  existing pure theme/design/parity tests and the new `GlassMaterialTest`.
  Android-dependent, Robolectric, coroutine and optimizer-dependent tests were
  excluded because their runtime dependencies are unavailable.
- 72 resource XML files parsed successfully; 130 Kotlin sources passed a
  comment/string/delimiter state-machine check.
- All XML and Kotlin resource references resolved: 61 IDs, 342 strings, 14
  styles, 60 drawables, 7 fonts, 6 layouts, 3 plurals and the remaining declared
  resources. `activity_home.xml` preserves every `HomeActivityRuntimeTest` ID
  and view type.
- Git diff was checked for unused imports in modified/untracked Kotlin files;
  none remain.

Not executed: `./build.sh test`, `./build.sh lint`, `assembleDebug`,
`assembleRelease`, Robolectric UI inflation, resource linking, screenshots,
contrast on a device, or touch-target verification on hardware. The standalone
JVM run does not prove that the Android/Compose code compiles.

## Design-system pass (current branch)

The visual identity is now implemented across typography, iconography, semantic
colour, wallpaper art, shapes/surfaces, the home composition, widgets,
micro-interactions and theme switching. See `docs/DESIGN_SYSTEM.md` for the
reference and `docs/QA_CHECKLIST.md` for verification status.

What was checked in this workspace without a toolchain:

- every resource reference in layouts/values resolves;
- every `com.lumaos.pro.launcher.*` import resolves to a declared symbol;
- every `R.*` reference in Kotlin resolves;
- all `activity_home.xml` ids used by `HomeActivityRuntimeTest` still exist;
- no reference to the deleted legacy glyph set remains;
- 45 glyphs, 7 font files, 2 licences and 6 wallpaper WebP files are present and
  well-formed.

What was **not** run: Kotlin compilation, resource linking, lint, unit tests,
Robolectric tests, APK/AAB assembly, signing, installation, or any on-device
visual/performance/accessibility check. This pass must not be described as
device-verified.

## Design System v3.2 adoption (current branch)

The owner supplied the LumaOS Design System v3.2 bundle (`design.zip`, Drive,
2026-10-26 14:43 UTC, SHA-256 `bc338391396d9c6784395ec33537681156e6032a28f8963aba52f1fc277b53a8`).
The specification and per-theme token JSON are now checked in at
`docs/design/v3.2/` with provenance, and the app's appearance layer is generated
from them:

- `ui/theme/LumaDesignTokens.kt` — authored colour, shape, material, type,
  spacing and motion tokens for the three identities.
- `ThemePresets.SIGNATURE_THEME / ATMOSPHERE_THEME / MONOLITH_THEME` — the v3.2
  identities; the six pre-3.2 palettes are retained unchanged.
- `ThemeEngine.authoredColors()` — draws the authored roles exactly (translucent
  roles composited over the authored background), falling back to the seed-derived
  path for custom colours and retained palettes.
- Blur (26/34/0 dp), saturation (135/165/100 %), corner geometry, app-label size
  and the type ladder are now data the UI reads, not per-screen constants.
- `TypeStyle` gained `ambient` and `monograph`; `MotionCharacter` gained
  `cinematic` and `exact`. No existing storage key changed.
- Fresh installs default to Signature (dark) / Zen (light); the v3.2 trio is
  authored dark and the specification's light *surface* variant is not implemented.
- Authored `ok` / `warn` seeds flow into `SemanticPalette`; the per-theme clock
  size and app-label size are tokens; `LumaSpacing` now draws every value from the
  bundle's 2 · 4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 56 · 72 dp scale.
- Configured version for this release: **1.0.0** (`versionCode` 100,
  `major * 100 + minor * 10 + patch`). The 0.1.0 pre-release was never published.

### What was checked here, without a toolchain

| Check | Method | Result |
| --- | --- | --- |
| Token parity, Kotlin ↔ bundle JSON | Scripted comparison (same parsing/rounding as `DesignTokensParityTest`): 18 colour roles × 3 themes, radii, tile, blur, saturation, type scale, spacing, motion | Exact match, 0 differences |
| Authored contrast | Scripted WCAG maths over composited roles | Every engine floor met; two authored roles are below the *specification's* own targets and are shipped as authored (Monolith text-2 6.8:1 vs 7:1; text-3 4.1/4.4/3.4:1 vs 4.6:1) |
| Preset coverage | Scripted enum/preset set comparison | 9 identities, exactly one preset each |
| Edited Kotlin files | Bracket balance with strings/comments stripped, backticked test names handled | Balanced (19 files) |
| Status roles | `ThemeColors.successSeed` / `warningSeed` → `SemanticPalette.of`, retained palettes null | Wired; asserted by `DesignSystemV32Test` |
| Spacing | `LumaSpacing` values checked against `SPACING_DP` | No off-scale value (4/8/12/16/20/24/32/40/56/72) |
| Resources | `R.string` cross-check | All resolve (`android.R.string.cancel` is framework) |

### What was **not** run

Kotlin compilation, resource linking, lint, unit tests (including the new
`DesignTokensParityTest` and `DesignSystemV32Test`), Robolectric tests, APK/AAB
assembly, signing, installation, or any on-device visual, performance and
accessibility check. The v3.2 pass must not be described as compiled or
device-verified. `docs/QA_CHECKLIST.md` §D0 lists the device rows that remain.

## Release configuration and Play readiness

- Application identity for this release: `com.lumaos.pro.launcher`, version
  `1.0.0` (`versionCode` 100). Configured in `app/build.gradle.kts`; no build of
  this identity has been produced in this workspace.

- The app is configured for Android 16 / API 36. Google Play's new-app and
  update target requirement became API 36 on August 31, 2026; the previous API
  34 target was a submission blocker. This configuration still needs a fresh
  build and a policy check at submission time.
- AGP/Kotlin/Gradle and the provisioned SDK packages were aligned to versions
  that support API 36. This configuration has not been built in this workspace.
- Release signing no longer falls back to the debug key. Without a complete,
  existing local upload keystore, release outputs are unsigned and not suitable
  for Play upload. No private signing material is present here.
- The Play AAB task is `bundleRelease`; Play App Signing must be configured in
  Play Console. No current-source AAB was generated or is present in this
  checkout.
- The in-app privacy statement and documentation now reflect local app-list and
  diagnostics use plus Android's cloud/device-transfer backup behavior and the
  optional external Android speech-recognition flow. The publisher still needs
  a reviewed, public privacy-policy URL and must complete an accurate Play
  Console Data safety form. Neither publisher details nor a public policy URL
  are available in this repository, so these have not been fabricated.

## Outstanding real-device verification

1. Build and install the updated debug APK on a supported Android device; verify
   that both the app-drawer/Play Store LAUNCHER entry and the Android HOME role
   resolve to LumaOS Pro. Test first launch/onboarding, Home-role selection,
   default-launcher return behavior, app discovery, app launch, search, A–Z
   navigation, drawer dismissal, gestures, folders, dock actions, and saved
   layout after process recreation.
2. Check the clock, wallpaper contrast, edge-to-edge/system-bar icons, icon grid,
   and dock in both themes, across compact and tall displays and larger system
   font scales. Robolectric does not render pixels or validate these details.
3. Confirm theme and appearance settings update immediately and persist, in
   particular that the home-row count changes visible row sizing while all home
   items remain scroll-accessible.
4. Run unit tests, Android lint, `assembleDebug`, and `assembleRelease` from the
   updated source. Confirm release shrinking and signing configuration on a
   machine with the documented Android toolchain.
5. Measure startup, frame pacing, and memory on a real low-memory phone. The
   widget host is implemented, but widget-provider consent, configuration,
   resizing, and interaction need physical-device coverage. LumaOS Pro Shield and
   the optional Brave bridge are outside this project version's scope.

## Release blockers

Release remains blocked pending owner-run current-source build/test/lint,
physical-device validation, a correctly signed AAB/upload key, Play Console App
Signing and Data safety setup, a reviewed and hosted privacy policy with the
publisher's contact details, real-device store screenshots, and trademark/package
availability checks for LumaOS Pro and `com.lumaos.pro.launcher`. The icon and
feature graphic are supplied, but have not been reviewed in Play Console. These
are unverified/product-owner prerequisites, not claims of Play approval or
evidence of a specific code failure. No signing secrets, AAB, or APK are stored
or included in this repository.
