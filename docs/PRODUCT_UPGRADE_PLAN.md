# LumaOS Pro — product upgrade plan & stage status (master spec)

Baseline (2026-11-04): branch `arena/013857a3-lumaospro`, clean tree at `bd0abbb`.
No JDK/Android SDK in this sandbox (toolchain hosts unreachable), so Gradle
build/test and device verification remain owner-run; every stage below states
what was actually executed. No commit/push without explicit user authorization.

## Already delivered (previous stages)
- Pillar 2/4: Luma Glass + Luma Mono first-class themes, unified icon treatment,
  drawer casing fix (`4bf96bb`). Report: `docs/LUMA_GLASS_MONO_REPORT.md`.
- Pillar 3: Studio preview-before-apply made real (draft-only controls, single
  Apply path), undo/redo (`StudioHistory`), session preset, live config
  summary, single-line tabs (`bd0abbb`). Report: `docs/STUDIO_SETTINGS_REPORT.md`.

## This stage (smallest safe increments, no schema/API changes)
1. Diagnostics completeness (Pillar 3H): surface real launcher version,
   Android version, display metrics and resolved theme — all read from
   existing sources (`BuildConfig`, `Build.VERSION`, window metrics,
   `themes.theme`), nothing fabricated, nothing sensitive.
   Files: `settings/DashboardScreen.kt`, strings.
2. Preview fidelity (Pillar 3E): Studio preview icons now render at the user's
   actual `iconSizeDp` preference instead of a fixed 52 dp.
   Files: `SettingsViewModel.loadPreviewIcons` (new defaulted parameter),
   `studio/StudioScreen.kt`.
3. Per-section reset (Pillar 3F): each main Studio card gains "Reset section",
   restoring that section's draft fields from the *applied* configuration
   (draft-only; Apply still persists). No invented defaults.
   Files: `studio/StudioScreen.kt`, strings.

## Explicitly not done this stage (and why)
- Dock icon-count/height/labels as new prefs: dock composition is layout-driven
  (`HomeLayout.dock`, page grid columns); inventing parallel prefs would create
  a second configuration system, which the spec forbids.
- Custom preset persistence across restarts: would need a new stored schema;
  session preset + Apply + backup export already cover it compatibly.
- Full Gradle/device verification: impossible in this sandbox (see above).

## This-stage results
- Implemented: Diagnostics System & LumaOS card (real BuildConfig/Android/display/theme
  values); Studio preview icons render at the user's real `iconSizeDp`; per-section
  draft reset (Composition&Dock / Material&Motion / Iconography) restoring applied values.
- Executed verification: tree-sitter parse (0 errors in all modified Kotlin files),
  strings XML validation, full diff review. Existing regression checklist remains
  covered by the untouched code paths + the test suites that iterate all themes.
- NOT executed: Gradle build, unit/Robolectric tests, lint, APK, device checks —
  no JDK/Android SDK in this sandbox and toolchain hosts are unreachable. Nothing
  in this stage is claimed build- or device-verified. Owner commands:
  `./build.sh test`, `./build.sh lint`, `./build.sh assembleDebug`.
- Working tree left uncommitted per the master spec's authorization rule.
