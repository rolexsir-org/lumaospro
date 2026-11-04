# Luma Glass & Luma Mono — upgrade report (2026-11-04)

Audit: `docs/UI_UPGRADE_AUDIT.md` (existing functionality, architecture, risks, file list).

## Implemented
- **Two first-class themes** through the existing `ThemeEngine`/`ThemeSpec` architecture,
  appended as new storage keys (`luma_glass`, `luma_mono`); no existing key, seed,
  preset, prefs schema, manifest, Gradle or public API changed, so saved themes,
  backups, layouts and hidden-app state are untouched by switching.
  - *Luma Glass*: deep-navy authored tokens (bg `#050D1F`, sky accent), GLASS material
    (28 dp blur), squircle **natural** icons (existing renderer adds rim light/shadow —
    the glossy look), wallpaper backdrop, glass context pill + left-aligned 72 sp
    light clock (both as in the reference), floating dock via its theme package.
  - *Luma Mono*: charcoal-brown authored tokens (`#161110`, peach `#F0BE9A`), zero blur,
    SOLID material, **TINTED** icon style — the existing monochrome-layer/tone-filter
    path renders every icon in the single peach ink on dark plates (icon packs and
    original artwork are never hardcoded or replaced), Monograph voice (uppercase
    mono labels), ruled context line, bezel dock via its theme package. Living Icons
    are off in the Mono package (as in Monolith's) so brand icons stay duotone.
- **Selectable everywhere a flagship is**: Studio flagship tiles + Settings quick
  switcher gained a second row for the two themes, both applying the real
  `ThemeLauncherPackage` (preset, dock, backdrop, effects, icon shape/style) through
  the existing persistence path — no dead settings. Studio tiles draw accurate
  Glass/Mono miniatures; `HomePreview` aligns their clocks to the leading edge.
- **Drawer consistency fix**: chip casing now follows the typographic label voice
  (mono-faced labels ⇒ caps) instead of the blur material, so Glass keeps title case
  and Mono keeps caps even if a blur token changes. Suggested/Frequent/All, search,
  A–Z index and app discovery are unchanged and remain driven by real launch history.
- New token sets are deliberately **excluded from `LumaDesignTokens.ALL`**, which
  remains the exact v3.2 JSON-parity set.
- New tests: `LumaGlassMonoThemeTest` (storage-key round-trip, material/icon/voice
  identity, AAA contrast, package integrity, settings preservation on switching).

## Preserved (unchanged code paths)
App discovery/launch, drawer model & suggested apps, home grid/dock/folders, widgets
& picker, wallpaper, icon packs, gestures, shortcuts, notification features, settings
persistence, all nine pre-existing themes byte-for-byte.

## Verification — actually executed
- tree-sitter Kotlin parse of all 209 main+test sources: **no parse errors in any
  modified file** (6 pre-existing parser-grammar quirks in untouched files).
- Independent Python re-derivation of WCAG contrast for the authored tokens:
  Glass body text 15.42:1 / accent 7.73:1; Mono 13.31:1 / 9.52:1 on surface-1.
- Diff review of every exhaustive `when (ThemeId)` site; existing tests already
  iterate **all** `ThemeId.entries` for contrast and storage-key completeness and
  will cover the new themes on a real toolchain.

## Not executed (no toolchain in this sandbox)
Gradle build, unit/Robolectric tests, lint and any on-device check could not run:
the sandbox has no JDK/Android SDK and the JDK/Gradle download hosts are
unreachable (GitHub release-asset CDN also blocked). **Do not treat this as
build-verified.** On a machine with the toolchain run:
`./build.sh test`, `./build.sh lint`, `./build.sh assembleDebug`.

## Requires manual device testing
Glossy icon depth over busy wallpapers, Mono duotone legibility for icons without
a monochrome layer, clock/context-pill layout at large font scales and in
landscape, theme-switch persistence across process restart.
