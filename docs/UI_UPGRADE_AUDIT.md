# LumaOS UI/UX upgrade — audit (2026-11-04, branch `arena/013857a3-lumaospro`)

Scope: upgrade to the two reference screenshots (deep-navy liquid-glass home/drawer;
warm peach/charcoal monochrome drawer) **without** rewriting or removing anything.

## Existing functionality (verified from source)
- Home: `HomeActivity` (3,584 lines, View system) + pager/grid adapters, dock, folders
  (`FolderOverlay`), edit mode, widget host/picker/resize, wallpaper (system / bundled
  LUMA / Wallpaper Studio), context/readout line, search pill, gestures, shade/quick controls.
- Drawer: `DrawerController` + `DrawerAdapter` + pure `DrawerModel` — real installed-app
  discovery via `AppRepository`, `Frecency.suggestions()` for the *Suggested* section
  (real launch history only, never fabricated), All/Frequent/category chips, A–Z index
  (`AlphaIndexView`, shown when >3 buckets), search over real apps.
- Icons: `IconRenderer` (adaptive re-masking, PLATE/NATURAL/TINTED, monochrome-layer
  support API 33+, rim light/shadow on the icon thread), `IconCache`/`IconLoader`,
  icon packs (`IconPackManager`), bundled Living Icons with per-app overrides.
- Themes: `ThemeEngine.resolve()` derives/authoritatively applies `ThemeSpec`s;
  contrast guarantees are unit-tested for **every** `ThemeId` × backdrop. Nine presets
  exist (Signature/Atmosphere/Monolith v3.2 + 6 retained). Persistence is by
  `ThemeId.storageKey` in DataStore (`LumaPrefs`); backups name the same keys.
- Settings/Studio (Compose): theme packages via `ThemeLauncherPackage.forTheme(id)`
  (exhaustive `when`), quick switcher iterates `ThemePresets.SIGNATURE`.
- Motion: springs (`SpringAnimator`, `MotionTokens`); blur-free themes automatically
  get SNAPPY motion and no icon depth (`IconRenderSpec.depth = depth && theme.usesBlur`).

## Architecture
Single `:app` module, Kotlin 2.2.21 / AGP 8.13.2 / compileSdk 36 / minSdk 26.
Views for launcher surfaces, Compose only for settings/dashboard. No new dependency needed.

## Inconsistencies found vs. the screenshots
1. No first-class theme matches either screenshot: the glass reference is deeper navy
   with glossy natural icons + a glass context pill; the mono reference is warm
   peach-on-charcoal with *duotone* icons and uppercase mono labels. Monolith is
   stone/vermilion with sharp geometry — not the same identity.
2. Drawer chip casing is keyed on `!theme.usesBlur` (a material property), not on the
   typographic voice — casing would silently flip if a theme's blur token changed.
3. Studio/Settings flagship rows iterate only `ThemePresets.SIGNATURE`, so any new
   first-class theme would be unselectable there (a setting that does nothing is
   worse than none).
4. Home clock alignment/context-pill treatment are hardcoded per existing theme id;
   new ids would fall through to generic branches (not broken, but not designed).

## Regression risks
- Adding `ThemeId` entries breaks exhaustive `when`s (`Components`, `ExperienceProfile`,
  `StudioScreen` canvas is `else`-guarded, `IconRenderer` uses `else`) — all must be updated.
- Tests assert `ThemeId.entries == ThemePresets.ALL.ids` and contrast for every entry:
  new presets must be in `ALL` and pass the derived/authored contrast path.
- `DesignTokensParityTest` compares `LumaDesignTokens.ALL` against the v3.2 JSON bundle:
  new token sets must **not** be added to `ALL` (they are post-v3.2 additions).
- Ordinal is never persisted (storageKey only), so appending enum entries is safe;
  no existing key, seed, or preset is modified.

## Files actually modified
`ui/theme/LumaDesignTokens.kt`, `ui/theme/ThemeModel.kt`, `ui/theme/Components.kt`,
`ui/theme/ExperienceProfile.kt`, `icons/IconRenderer.kt`,
`launcher/home/DrawerController.kt`, `launcher/HomeActivity.kt`,
`settings/SettingsScreen.kt`, `settings/studio/StudioScreen.kt`,
new test `ui/theme/LumaGlassMonoThemeTest.kt`, this audit + `docs/LUMA_GLASS_MONO_REPORT.md`.
Nothing else: no manifest, Gradle, prefs schema, database, or public API changes.
