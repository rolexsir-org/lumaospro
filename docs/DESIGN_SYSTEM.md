# LumaOS Pro — Design System

**Release:** 1.0.0 (`versionCode` 100).

> **Verification status (read first).** This document describes the design system as
> implemented in this checkout. There is no Android SDK, Gradle distribution,
> emulator or physical device in the current environment, so the Android app has
> **not** been compiled with Gradle, linked, linted, or rendered on a phone. A
> standalone Kotlin 2.2.21 compiler on a JRE was bootstrapped locally to compile
> the pure token/theme layer and run 242 JVM-only unit tests with a JUnit shim;
> that is not the Gradle test task and does not cover Robolectric or UI rendering.
> Static resource, XML, delimiter and contract checks are listed in
> `QA_CHECKLIST.md`. Nothing here should be presented as device-verified until
> the checklist's manual rows are run.

---

## Luma Glass v4.0 (current visual system)

The v3.2 token bundle below remains the source of truth for colours, contrast,
type, spacing and motion. **Luma Glass** is the shared material layer above it:
one role-based recipe is consumed by both the Android Views launcher and the
Compose settings/Studio/dashboard screens.

- `GlassMaterial.kt` defines semantic roles (`PRIMARY`, `SECONDARY`, `SEARCH`,
  `DOCK`, `FOLDER`, `MENU`, `SHEET`, `WIDGET`, `SETTINGS_CONTROL`, `OVERLAY`),
  each with its own tint/opacity, blur budget, radius, elevation, edge highlight,
  internal reflection and pressed/focused/selected colours. No screen owns a
  private surface hex value.
- `GlassViews` turns those recipes into one cached, stateful `GlassDrawable` for
  Views; `GlassComponents` turns the same recipes into Compose brushes, borders,
  shapes and state colours. `LumaOSTheme` maps MaterialTheme surface roles onto the same glass palette.
- `GlassCapabilities` maps the existing `EffectsDecision` to a readable opaque
  fallback when translucency, blur or depth are unavailable. There is no stacked
  full-screen blur and no rainbow/chrome/bloom treatment.
- The wallpaper contributes through the existing `AmbientBackdropView` and a
  single polarity-aware `GlassBackdrop.scrim`; `FLAG_SHOW_WALLPAPER`, bundled
  LUMA artwork and `Backdrop` preferences retain their existing behaviour.
- Home now has one centred clock/date, one aligned app grid, one primary search
  field and one floating glass dock. The decorative chronometer dial, header
  action cluster and context chip are hidden compatibility views; their IDs and
  actions remain bound (context selection on clock long-press, settings on date
  or search long-press).
- Legacy `ThemeId` values and storage keys are preserved. Studio's new accent
  presets (`LumaGlassIdentity.ACCENTS`) change semantic colour within one
  material system rather than creating another theme engine.

The pure `GlassMaterialTest` checks role completeness, deliberate geometry,
light/dark contrast, capability fallbacks, scrim polarity, accent presets and
motion-token aliasing. Android/Robolectric rendering still requires a device or
CI build.

## 0. Design System v3.2 (current)

The authored bundle from the project owner's Drive — specification, per-theme token
JSON and the studio build — is checked in at [`design/v3.2/`](design/v3.2/) and is
now the source the app draws from. Provenance, the zip's SHA-256, the naming
mapping for the "Luma Display / Text / Mono" faces and two measured token
discrepancies are documented in [`design/v3.2/README.md`](design/v3.2/README.md).

### 0.1 The three identities

| | **Signature** | **Atmosphere** | **Monolith** |
| --- | --- | --- | --- |
| Personality | Refined, timeless, expensive | Immersive, cinematic, adaptive | Architectural, bold, exact |
| Background | `#06070B → #0F1219` midnight ramp | `#04131A → #0A2430`, wallpaper-led | `#0B0B0A → #171715` stone neutrals |
| Accent | `#7C86FF` indigo-violet, `#A78BFA` | `#46E0B0` aurora mint, `#7FB6FF` | `#FF5A2B` vermilion |
| Radii (xl/lg/md/sm/xs) | 34 / 26 / 16 / 10 / 6 | 38 / 30 / 20 / 12 / 8 | 8 / 4 / 2 / 2 / 2 |
| Icon tile | 58 dp at 24 dp, squircle | 58 dp at 26 dp, circular | 58 dp at 10 dp, rounded-square |
| Material | blur 26 dp · sat 135 % · translucent | blur 34 dp · sat 165 % · glass | **no blur** · sat 100 % · solid |
| Clock | 64 sp, light, −0.035 em, caps date | 58 sp, light, −0.040 em, sentence date | 84 sp, semibold, −0.060 em, mono date |
| App label | 10.5 sp | 11 sp | 9 sp |
| Voice | `precision` (Inter) | `ambient` (Inter) | `monograph` (Grotesk + mono) |
| Motion | `subtle` | `cinematic` (one overshoot) | `exact` (no stagger, no overshoot) |
| Backdrop | own atmosphere (`THEME`) | the user's wallpaper (`WALLPAPER`) | own atmosphere (`THEME`) |

### 0.2 Tokens

| Group | Values | Kotlin |
| --- | --- | --- |
| Spacing | 2 · 4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 56 · 72; gutter 20 (compact 16, large 22), row 12, section 24, dock gap 40 | `LumaDesignTokens.SPACING_DP` / `spacing(step)`; `LumaSpacing` converts the same numbers to Compose `Dp` |
| Radius | per theme (above) + dock 34 / 36 / 4, tile 24 / 26 / 10 | `TokenShape`, `ResolvedTheme.radius(RadiusRole…)` |
| Material | blur 26 / 34 / 0, saturation 135 / 165 / 100 % | `TokenMaterial`, `ResolvedTheme.blurDp / blurFactor / saturation` |
| Type | one ladder: clock 66, screen title 21, section 14, widget head 12.5, primary 13, label 11, supporting 10.5, numeric 10, button 13.5, kicker 9.5 | `LumaDesignTokens.TYPE_SCALE`, `ResolvedTheme.typeScale` |
| Motion | 120 / 200 / 320 / 480 ms, press 90, long-press 500; standard `(.22,.61,.36,1)`, emphasis `(.16,.84,.28,1)`, exit `(.4,0,1,1)`; drag spring 480/34 | `LumaDesignTokens.Motion` |
| Colour | `bg-0/1/2`, `surface-1/2/3` + `surface-solid`, `stroke-1/2`, `text-1/2/3`, `accent`, `accent-2`, `accent-soft`, `ok/warn/danger` | `TokenColors` per theme |

The JSON in `design/v3.2/tokens/` is the source of truth and is enforced:
`DesignTokensParityTest` fails with the token name if the Kotlin constants and the
bundle disagree. It reads the checked-in JSON, not a copy.

Two boundaries are deliberate. `ThemeTokens` carries a theme's clock *size* and app
label size, but not clock weight or tracking: those belong to the type voice
(`precision` / `ambient` / `monograph`), which already owned them, and duplicating
them would let the two disagree. And the authored `ok` / `warn` roles are passed
through to `SemanticColors` as seeds, so a v3.2 theme's status colours are the
designed ones rather than hue-derived — while the retained palettes keep the derived
status ramp.

### 0.3 Authored colour vs derived colour

The engine has two colour paths and picks per request:

* **Authored** (`ThemeEngine.authoredColors`) — used for a v3.2 theme when the user
  has not replaced its accent or background. Every role comes from the bundle
  (including `ok` / `warn`, carried as `ThemeColors.successSeed` / `warningSeed`):
  translucent roles (`surface-1 = rgba(255,255,255,.045)`) are composited over the
  authored `bg-1`, `outline` is the authored hairline, and `accent-soft` becomes the
  selection fill. Nothing is regenerated from a seed, so the shipped palette is the
  designed palette.
* **Derived** (`ThemeEngine.deriveColors`) — used for the retained pre-3.2 palettes,
  for Chroma (wallpaper-seeded), and whenever the user sets a custom accent or
  background. Unchanged from the previous pass.

Both paths run the same guarantee tail (`assemble`), so the §2.3 contrast contract
holds either way — including for a hand-edited token file.

### 0.4 Compatibility, defaults and the light-mode decision

* **Nothing is removed and no key is rewritten.** The pre-3.2 palettes (Aurora, Zen,
  Void, Obsidian, Graphite, Chroma) keep their IDs, storage keys, seeds, geometry
  and voices. They are shown under "Retained palettes" in Studio, below the three
  v3.2 identities.
* **Defaults.** A fresh install follows the system: **Signature** in dark mode and
  **Zen** in light mode. The three v3.2 identities are authored dark, and the
  specification defines a paper *surface* variant for widgets and sheets (§3.1) —
  not a light theme. Putting a midnight launcher on a light-mode phone would be the
  wrong default, so the retained paper palette stays the light partner until that
  variant is implemented. This is a deliberate limitation, not an oversight.
* **Blur is material, not preference.** Monolith's token is 0 dp, so
  `HomeActivity.applyDepth()` draws no blur for it even when the device and the
  effects policy would allow one.
* **Custom colours still win.** An accent or background override falls back to the
  derived path by design, because only the derived path can reach the guarantees
  for an arbitrary colour.

### 0.5 Verification of this pass

Mechanically checked here, with no JDK or Android SDK (see `QA_CHECKLIST.md` §A):

* token parity between the bundle JSON and `LumaDesignTokens.kt` — scripted, exact
  on colour, radii, tile, blur, saturation, type ladder, spacing and motion;
* the measured contrast of every authored pair, over the composited surfaces the
  roles actually sit on;
* balanced source in all 19 changed Kotlin files, every `R.string` reference
  resolving, and each of the nine `ThemeId`s having exactly one preset.

Not run: Kotlin compilation, resource linking, lint, unit tests (including
`DesignTokensParityTest` and `DesignSystemV32Test`), and any on-device rendering.
Do not present the new themes as compiled or device-verified.

---

### 0.6 History: what the token layer replaced

The pre-1.0 builds (0.1 / 0.2) kept the product's promise — a fast launcher for
low-resource phones — and gave it a deliberate visual identity. They drew with
Material defaults plus a thin palette, which produced three problems:

1. **One look for three products.** Aurora, Zen and Void differed only in accent
   hue, so switching themes felt like a colour swap, not a change of character.
2. **Drift.** Radii, paddings and text sizes were re-typed at each call site, so
   the same control could be 12 dp round in the drawer and 18 dp round on the
   home screen.
3. **Contrast by luck.** Text colours were chosen per widget; some pairs only
   passed AA on dark surfaces and failed on light ones.

1.0.0 keeps that rule and ships the three-identity system above. The sections
that follow (§1–§14) are the reference for the roles; they were written for the
retained palettes before v3.2 and remain accurate for them.

---

## 1. Principles

| Principle | What it means in code |
| --- | --- |
| Quiet luxury, not decoration | No gradients on text, no heavy shadows, no outlines that only decorate. Every stroke exists to separate two materials. |
| One hierarchy, three voices | The type scale is identical everywhere. Themes change family, weight, tracking and motion — never the ladder. |
| Material honesty | Signature is midnight glass, Atmosphere is lit water, Monolith is cut stone; the retained palettes keep their own materials (Aurora glass, Zen paper, Void a machined instrument). Each theme's radii, borders, elevation and blur follow its material — Monolith's zero blur is a token, not an exception. |
| Legibility is a guarantee | Text roles are contrast-checked by the theme engine, not chosen by eye. |
| Motion is feedback, not theatre | Every animation is interruptible, honours the system animation scale, and vanishes under reduced motion. |
| Identity is preserved | App icons are never recoloured. Bundled glyphs are used only for the interface; app artwork is passed through untouched. |

---

## 2. Colour

### 2.1 Themes

v3.2 identities first, then the retained palettes.

| Theme | Storage key | Character | Material | Type voice | Motion |
| --- | --- | --- | --- | --- | --- |
| **Signature** | `signature` | Midnight ramp, indigo-violet | Translucent, blur 26, sat 135 % | Inter, refined | Subtle |
| **Atmosphere** | `atmosphere` | Wallpaper-led, aurora mint | Glass, blur 34, sat 165 % | Inter, airy | Cinematic |
| **Monolith** | `monolith` | Stone neutrals, vermilion rules | Solid, **no blur** | Space Grotesk + mono | Exact |
| Aurora (retained) | `aurora` | Midnight ribbon light | Translucent glass, 1 px hairline, 6 dp elevation ceiling | Inter, quiet luxury | Subtle |
| Zen (retained) | `porcelain` | Quiet paper and mist — the light-mode partner | Solid paper, almost no elevation, soft radii | Inter, modern serenity | Quiet |
| Void (retained) | `vector` | Indigo depth, cyan signal | Layered panels, sharper corners, accent halo | Space Grotesk + Inter | Expressive |
| Obsidian (retained) | `obsidian` | True black for OLED | Flat black, no glow | Inter | Subtle |
| Graphite (retained) | `graphite` | Machined grey, amber instrument | Matte panels, 3 dp | Inter | Subtle |
| Chroma (retained) | `chroma` | Built from the wallpaper | Same as current backdrop | Inter | Subtle |

Every legacy storage key — including 0.1's `aurora`, `porcelain`, `vector` — and the
old light/dark switch are preserved. No saved preference or exported backup is
rewritten or discarded on upgrade; a stored `aurora` still resolves to Aurora.

### 2.2 Semantic roles

`ResolvedTheme.colors` (`ui/theme/ThemeModel.kt`) exposes the base roles:
`background`, `surface0..3`, `onSurface`, `onSurfaceMuted`, `onSurfaceFaint`,
`outline`, `outlineStrong`, `accent`, `onAccent`, `accentContainer`,
`onAccentContainer`, `secondary`, `scrim`, `homeForeground`, `homeMuted`,
`homeShadow`, `ripple`, `danger`.

`ResolvedTheme.semantics` (`ui/theme/SemanticColors.kt`) adds the roles Material 3
has no slot for:

| Group | Roles | Used by |
| --- | --- | --- |
| Status | `success`, `warning`, `danger`, `info` + containers and on-colours | Dashboard reading provenance, toasts, validation |
| Interaction | `pressed`, `selectedSurface/Content`, `unselectedSurface/Content`, `disabledSurface/Content`, `focusRing` | Menus, chips, settings rows |
| Overlays | `dialogSurface/Border/Scrim`, `overlay` | Menus, folders, widget picker, Studio dialog |
| Widgets | `widgetSurface`, `widgetBorder`, `widgetForeground`, `widgetMuted` | Hosted widget frames |
| Fields | `searchSurface/Border/Hint/Icon` | Home search field, drawer search |
| Dock | `dockSurface`, `dockBorder`, `chipSurface/Border` | Dock plate, context chip |

Every one of these is *derived* from the theme's own base palette (OKLCH maths in
`ColorMath.kt`), so a custom accent produces a coherent status palette without a
second table of colours.

### 2.3 Contrast contract

Enforced by `ThemeEngine.deriveColors()` and asserted by `ThemeEngineTest`:

| Pair | Target |
| --- | --- |
| `onSurface` on `surface0`/`surface1` | ≥ 7:1 (AAA body) |
| `onSurfaceMuted` on `surface1`/`surface2` | ≥ 4.5:1 (AA) |
| `onSurfaceFaint` on `surface1` | ≥ 3:1 |
| `accent` on `surface1` | ≥ 3:1 (non-text UI) |
| `onAccent` on `accent` | ≥ 4.5:1 |
| `onAccentContainer` on `accentContainer` | ≥ 4.5:1 |
| `onBackground` on `background` | ≥ 7:1 where achievable, never < 4.5:1 |
| status colours on `surface1` | ≥ 4.5:1 |
| disabled content on disabled surface | ≥ 3:1 |

On mid-tone custom colours where 7:1 is mathematically impossible (e.g. `#FF4581`),
the engine uses the strongest available colour (`ColorMath.maxContrast`) instead of
emitting unreadable text.

### 2.4 The rules

* **No colour literals in UI code.** Components read `theme.colors`,
  `theme.semantics`, or Material theme roles derived from them. `ColorMath.WHITE`
  and `ColorMath.BLACK` are only used as blend anchors (e.g. deriving a rim
  highlight), never as a final UI colour.
* **Home text is a different role.** Text over artwork uses `homeForeground` /
  `homeMuted` / `homeShadow`, which the engine chooses from the artwork's declared
  lightness (the Zen wallpaper is light and therefore uses dark text with a light
  shadow).
* **Colour is never the only signal.** Status badges carry a label and a glyph.

---

## 3. Typography

### 3.1 Families

| Family | Files | Licence | Used for |
| --- | --- | --- | --- |
| **Inter** | `inter_light`, `inter_regular`, `inter_medium`, `inter_semibold` | SIL OFL 1.1 | Aurora and Zen display, all body copy in every theme |
| **Space Grotesk** | `space_grotesk_regular`, `space_grotesk_medium`, `space_grotesk_semibold` | SIL OFL 1.1 | Void display and headings |

Both licences ship in `app/src/main/assets/licenses/` and are readable from
**Settings → About**. No second typeface family is mixed in, and no glyph is
synthesised from a system font when an asset exists.

### 3.2 The scale

`LumaTypeScale` (`ui/theme/TypeSystem.kt`) is the single ladder. Sizes are sp and
therefore scale with the system font-size setting.

| Role | Size | Line height |
| --- | --- | --- |
| Display large / medium / small | 72 / 64 / 52 sp | 1.02 |
| Headline large / medium / small | 30 / 26 / 23 sp | 1.18 |
| Title large / medium / small | 21 / 18 / 15.5 sp | 1.24 |
| Body large / medium / small | 16 / 14 / 12.5 sp | 1.5 (1.42 compact) |
| Label large / medium / small | 13.5 / 12 / 11 sp | 1.3 |
| App label | 12 sp | 1.12 |

The same numbers feed Material's `Typography` (`typographyFor`) and the View
system (`LumaTypeScale`), so Settings (Compose) and Home (Views) cannot drift.

### 3.3 Voices

`TypeStyle` maps a theme to a complete voice — family + weight + tracking per role:

| Voice | Storage key | Clock | Titles | Body | Labels | Readouts |
| --- | --- | --- | --- | --- | --- | --- |
| **Precision** (Signature) | `precision` | Inter Light 64 sp, −0.035 | Inter Semibold, −0.02 | Inter Regular | Inter Medium | Inter Medium, caps, 0.14 |
| **Ambient** (Atmosphere) | `ambient` | Inter Light 58 sp, −0.040 | Inter Semibold, −0.015 | Inter Regular | Inter Medium | Inter Medium, sentence case |
| **Monograph** (Monolith) | `monograph` | Space Grotesk Semibold 84 sp, −0.060 | Space Grotesk Semibold, −0.015 | Inter Regular | **Mono Medium**, 0.10 | **Mono** Medium, caps, 0.12 |
| **Serene** (Zen, retained) | `serene` | Inter Light, −0.015 | Inter Medium, −0.008 | Inter Regular | Inter Medium | Inter Medium, mixed case |
| **Grotesk** | `grotesk` | Space Grotesk Regular | Space Grotesk Semibold | Inter Regular | Space Grotesk Medium | Space Grotesk Medium, caps |
| Serif (fallback) | `serif` | Platform serif | Platform serif | Inter | Platform serif | Platform serif |
| Mono (fallback) | `mono` | Platform mono | Platform mono | Inter | Inter | Platform mono |

`TypeStyle.SIGNATURE` — the voices the three v3.2 themes speak — is
`precision`, `ambient`, `monograph`. `serene`, `grotesk`, `serif` and `mono` remain
loadable for old backups and explicit user choices (`TypeStyle.RETAINED`), and
every storage key is unchanged.

### 3.4 Runtime application

* **View system:** `Typefaces` (`launcher/home/Typefaces.kt`) caches `Typeface`s
  from `LumaFonts` and exposes `clock/title/body/label/readout`. Installed once in
  `LumaApp`; a bare test harness falls back to a platform face of the same
  temperament.
* **Compose:** `typographyFor(style)` builds a full `Typography`, exposed through
  `LumaOSTheme`.
* **Text scale:** user text scale multiplies `LocalDensity.fontScale` (clamped
  0.75–1.6) rather than each font size, so every sp unit — including inside
  Material components — scales exactly once.
* **Caps readouts** use `isAllCaps` + tracking rather than upper-casing strings in
  code where possible; the Studio preview uppercases for measurement only.

---

## 4. Iconography

### 4.1 Interface glyphs

45 original glyphs in `res/drawable/ic_rx_*.xml`, defined in `ui/IconCatalog.kt`
as `LumaIcon`.

| Property | Value |
| --- | --- |
| Viewport | 24 × 24 dp |
| Stroke | 1.75 dp |
| Caps / joins | Round |
| Optical inset | 3 dp |
| Colour | White path, tinted at runtime |
| Filled variants | `check_filled`, `pin_filled`, `folder_filled`, plus the solid half of `contrast` |

Filled glyphs are reserved for **selected** states: a ticked menu item, a pinned
row, an open folder. Nothing is filled decoratively.

`LumaIcons.drawable(context, icon, selected, tint)` resolves the glyph and falls
back to `APPS` if a resource is missing from a stripped build — an empty image is
never drawn.

### 4.2 Application icons

App artwork is third-party identity and is **never recoloured**. `IconRenderer`
applies the theme's shape and optional depth to the icon's own pixels; `IconLoader`
passes the result through unchanged. When an icon cannot be resolved at all,
`MonogramDrawable` draws the app's initial in the active icon shape with semantic
plate/text colours — a legible fallback that still distinguishes the app.

### 4.3 What is no longer used

`ic_search`, `ic_settings`, `ic_voice`, `ic_close` and the rest of the legacy set
were deleted. Layouts and Kotlin remap to `ic_rx_*`; no second icon pack is mixed
into the interface.

---

## 5. Wallpaper art direction

Three signature artworks ship with the app, packaged as WebP in `drawable-nodpi`
(portrait) and `drawable-land-nodpi` (landscape) so neither orientation crops the
other's composition.

| Art | Theme | Palette | Focal point | Scrim (top / bottom) | Text |
| --- | --- | --- | --- | --- | --- |
| **Aurora · Nightfall** | Aurora | Obsidian, navy, silver ribbons | 0.50 / 0.46 | 0.26 / 0.48 | Light |
| **Zen · Morning Mist** | Zen | Porcelain mist, blue-grey, sage | 0.50 / 0.58 | 0.20 / 0.30 | Dark |
| **Void · Flux** | Void | Indigo-black, violet, cyan lattice | 0.50 / 0.44 | 0.24 / 0.50 | Light |

Source renders live in `design-src/wallpapers/` (not packaged). Each keeps a quiet
central band so the grid stays readable, and each declares its own lightness in
`WallpaperArt.supportsDarkText` — the engine uses that property, it does not guess
from pixel averages at runtime.

**Rendering** (`launcher/home/WallpaperArtView.kt`):

1. centre-crop from the declared focal point — never stretched, never letterboxed;
2. decode off the main thread, downsampled to screen width, in a single-thread
   executor with a 6 MB LRU cache;
3. cross-fade between artworks on a theme change, on the incoming theme's
   `themeChangeMs` clock;
4. paint the theme background colour until the bitmap is ready, so the first frame
   is never a black rectangle;
5. if an asset cannot be decoded, report it: the home screen hides the layer and
   restores the theme's atmospheric backdrop rather than showing an empty panel.

**Scrims are not painted by the artwork view.** `AmbientBackdropView` draws them
from the same `AtmospherePlan` used by the Studio preview, so contrast is managed
in one place and a system wallpaper gets balanced defaults.

The Studio preview draws the decoded artwork with the same focal-point crop, and
clearly labels a system-wallpaper preview as approximate (Android exposes
wallpaper *colours*, not the image).

---

## 6. Shapes and surfaces

Two shape systems coexist, intentionally:

* **Authored radii (v3.2).** A component asks for a role —
  `theme.radius(RadiusRole.EXTRA_LARGE)` — and Signature answers 34 dp, Atmosphere
  38 dp, Monolith 8 dp. The full ladders are 34/26/16/10/6, 38/30/20/12/8 and
  8/4/2/2/2, with dock 34/36/4 and icon tile 24/26/10 at 58 dp.
* **Scaled base radii (retained palettes).** `ShapeTokens`
  (`ui/theme/Components.kt`) multiplies a base radius by the theme's `cornerScale`
  (Void 0.82, Aurora 1.0, Zen 1.08, and so on). v3.2 themes also drive this path
  from their authored medium radius (`cornerScale` = `r-md` / 16: 1.0, 1.25,
  0.125), so a legacy call site lands on the same geometry family.

| Role | Base radius | Used by |
| --- | --- | --- |
| `controlDp` / `chipDp` | 12 | Icon buttons, chips, menu rows |
| `cellDp` | 16 | App cells, drawer rows, empty state |
| `searchDp` | 18 | Home search field, drawer search |
| `menuDp` | 16 | Context menus, edit toolbar |
| `dialogDp` | 24 | Dialogs |
| `dockDp` | 24 | Dock plate |
| `folderDp` | 24 | Folder sheet |
| `widgetDp` | 18 | Hosted widget frame |
| `sheetDp` | 28 | Drawer sheet top corners |

Rules:

* **No fully-round pills for text fields or chips.** A field is a rounded
  rectangle; the dock's FLOAT style is the only deliberate capsule.
* **One radius per control.** A parent's radius is ≥ its children's; nothing is
  square inside a round container.
* **Borders are hairlines, not decoration.** `borderAlpha` per theme: Aurora 0.18,
  Zen 0.12, Void 0.26, Graphite 0.14, Chroma 0.16, Obsidian 0.16. v3.2 themes read
  it from the authored `stroke-2` token (Signature 0.17, Atmosphere 0.26,
  Monolith 0.24).
* **Elevation ceilings:** Zen/Obsidian 0 dp, Graphite 3 dp, Chroma 4 dp, Aurora
  6 dp, Void 10 dp; v3.2 derives the ceiling from the authored blur
  (Signature 6 dp, Atmosphere 8 dp, Monolith 0 dp). Shadows exist only where a
  surface genuinely floats.
* **Blur is a material token, not a global switch.** 26 dp (Signature), 34 dp
  (Atmosphere), 0 dp (Monolith, which therefore never blurs even on capable
  hardware).
* **Translucency** is `SurfaceStyle`-driven and is disabled entirely when the
  effects policy says so, then `surfaceAlpha()` returns 1.

---

## 7. Home composition

Layer order in `res/layout/activity_home.xml` (bottom → top):

1. `wallpaper_art` — bundled artwork, or gone.
2. `backdrop` — `AmbientBackdropView`: atmosphere plan, legibility scrims, glow.
3. `edit_scrim` — edit mode only: 55% black veil over the wallpaper (alpha 0→1).
4. `glass_scrim` — the theme's polarity-aware legibility veil.
5. `desktop` — header, grid, search, dock.
6. Edit chrome — header card + bottom toolbar (see §7.1).
7. Drawer container — scrim + sheet.

Desktop, top to bottom:

* **Header** (20 dp start inset, 12 dp top inset)
  * 64 dp chronometer dial (`home_dial`) when the preset uses `DIAL`
  * clock at `ResolvedTheme.clockSp` — the theme's authored size (Signature 64,
    Atmosphere 58, Monolith 84 sp; a retained palette answers with its voice's
    `clockSizeSp`) — scaled per preset (DIAL × 1.0, HERO × 1.42, COMPACT × 0.70),
    in the voice's clock family and tracking
  * date as an overline: caps, tracked, `homeMuted` over artwork
  * context chip — the active profile and time-of-day readout, 48 dp tall,
    chevron included, one radius shared with the design system
  * search and settings: 48 dp circular targets, one shared scrim and one tint
* **Grid** — 4 columns default, 8 dp side padding, cells with 48 dp icons and a
  two-line label with shadow; widget rows span the full width.
* **Search field** — 52 dp tall, 20 dp side insets, 18 dp radius, leading glyph,
  hint, voice and (HERO only) settings actions.
* **Dock** — one plate, three styles: `BEZEL` (full-width, rim highlight),
  `FLOAT` (inset capsule), `MINIMAL` (no plate, shadowed labels). No style changes
  the icon treatment.
* **Drawer** — sheet with 28 dp top corners, title + subtitle, search field with
  clear, category chips, alphabetical index, section headers exposed as headings,
  and rows that carry a chevron only when they navigate.

Every element in this composition is driven by the tokens above; the layout file
contains no padding or radius that is not mirrored in a token.

### 7.1 Edit Home (edit mode)

Edit mode is a *framed* composition, not an overlay: the desktop reserves exactly
the height of the chrome, so nothing can sit on top of anything else. Every value
is authored in `launcher/home/EditModeDesign.kt` and mirrored 1:1 in
`res/values/dimens.xml` as `luma_edit_*`.

| Section | Geometry | Type |
| --- | --- | --- |
| Screen margin | 16 dp for the header, grid, drop zones, dock and toolbar | — |
| Vertical rhythm | 12 dp between sections; 16 dp clear below the header card | — |
| Header card | 24 dp radius, 18 dp padding, 1 px white-8% border, 12 dp shadow | title 20 sp SemiBold, subtitle 13 sp at 65% (max 2 lines) |
| Done | 76×40 dp pill, dark glass, 1 px hairline | 14 sp Medium |
| Clock/date | own block, never under the header | date 14 sp at 80% |
| Drop zones | 20 dp radius, 1 dp dashed white-15% outline, 3% fill, 12 dp gutters | — |
| Hint pill | fully rounded, 14/20 dp padding, 16 dp shadow | 14 sp at 85% |
| Dock | 28 dp radius, 14 dp padding, one row, equal spacing | icons at the user's size |
| Toolbar | 24 dp radius, 10 dp padding, 16 dp above the gesture bar, five equal buttons 64 dp tall, 16 dp radius | icon 22 dp, label 12 sp Medium, single line, auto-shrink to 9 sp |

Rules this section enforces:

* the desktop's insets are computed once, in `HomeActivity.applyChromeInsets()`,
  from the measured chrome — no hard-coded card height anywhere;
* the wallpaper is the only thing dimmed while editing (55% black + a slight
  API 31+ defocus), so a bright or patterned wallpaper lands near 16% effective
  opacity while text and icons stay crisp;
* the desktop does not scale in edit mode (`LumaDepthStack`), because the layout
  already reserves the space a scale would otherwise steal from the dock;
* drop zones are derived from the real grid — content width ÷ span count, the
  adapter's row height, the grid's scroll offset and the live child bounds — so
  they cannot drift out of alignment or outline an occupied slot;
* selected/pressed toolbar buttons use one 8% white fill; the accent colour
  appears only as the drag target in the grid.

---

## 8. Widgets

Third-party widget content is never recoloured, but it is *framed* so it belongs to
the composition:

* one frame drawable (`widgetSurface` + `widgetBorder` + `widgetDp`) behind every
  host, re-applied on theme change;
* provider content clipped to the frame radius;
* rows snap to the grid's row height, resize handle only in edit mode;
* an unavailable widget shows `widget_missing` in `bodySmall` with the theme's
  muted colour, not a raw stack trace or a blank hole.

---

## 9. Motion

`MotionIdentity.kt` defines five profiles. All share one spring vocabulary
(`MotionTokens`), one rule set, and the system animator-duration scale. Durations
come from the v3.2 family — 120 / 200 / 320 / 480 ms, press 90, long-press 500 —
with the authored cubic-beziers for standard, emphasis and exit.

| Property | Signature (subtle) | Atmosphere (cinematic) | Monolith (exact) | Zen (quiet) | Void (expressive) |
| --- | --- | --- | --- | --- | --- |
| `pressMs` | 90 | 90 | 70 | 110 | 70 |
| `openMs` / `closeMs` | 320 / 240 | 320 / 220 | 200 / 150 | 380 / 280 | 260 / 190 |
| `themeChangeMs` | 420 | 420 | **280** | 480 | 320 |
| `staggerMs` | 18 | 24 | **0** | 24 | 14 |
| `pressScale` | 0.985 | 0.97 | 0.97 | 0.99 | 0.96 |
| `enterScaleFrom` | 0.98 | 0.96 | 0.97 | 0.99 | 0.92 |
| Overshoot | Never | Once, on open | Never | Never | Once, on open |

Invariants, asserted by `MotionProfile.init`:

* `pressMs` ∈ 40–200, `themeChangeMs` ∈ 120–800;
* `pressScale` ≥ 0.94 (smaller reads as a glitch, not a press);
* enter/popup scale ≥ 0.85;
* exactly four easing control points.

Reduced motion: `MotionPolicy.reduced` cancels running animations, sets progress to
its end state, disables press scaling, and stops ambient backdrop animation.
`Settings.Global.ANIMATOR_DURATION_SCALE = 0` is honoured the same way.

Theme switching animates **colour roles only** on a spring, and switches type and
icons once at the end — type morphing mid-fade would look like a font glitch.

---

## 10. Micro-interactions

| Interaction | Feedback | Source |
| --- | --- | --- |
| Tap an app cell | Ripple in `ripple` + scale to `pressScale` over `pressMs` | `AppCells.attachPressFeedback` |
| Long-press a cell | Haptic + edit mode spring; the drag starts from the pressed scale | `EditModeController` |
| Open the drawer | Sheet follows the finger; spring settle; desktop recedes and blurs | `DrawerController` |
| Drawer swipe up | Tracked drag with a velocity-aware release | `HomeActivity.dispatchTouchEvent` |
| Theme switch | Colour cross-fade + artwork cross-fade on the same clock | `HomeActivity.applyTheme` |
| Return from an app | Desktop settles forward (0.4 s spring) | `playReturnSettle` |
| Menu open | Fade + scale from `popupScaleFrom` | `LumaMenu` |
| Folder open | Scale/fade reveal, scrim behind | `FolderOverlay` |
| Widget resize | Row-height snapping with the height following the finger | `HomeGridAdapter` |
| Toggle a setting | Material 3 switch, themed by `LumaOSTheme` | Settings screens |
| Reading provenance | Badge with colour **and** glyph **and** label | `DashboardScreen` |

---

## 11. Theme switching

There is one implementation and one source of truth:

```
ThemeSettings (DataStore)  +  ThemeEnvironment (system dark, time, profile, wallpaper)
        │
        ▼
ThemeEngine.resolve() ──► ResolvedTheme ──► ThemeRepository.theme (StateFlow)
                                │
              ┌─────────────────┴──────────────────┐
       HomeActivity (Views)                 LumaOSTheme (Compose)
   colours · type · icons · wallpaper     Settings · Studio · onboarding
```

* The launcher is not duplicated: Settings, Studio, onboarding and the home screen
  all observe the same `ResolvedTheme`.
* Custom accent/background overrides, per-profile themes, light/dark scheduling and
  the Chroma wallpaper-derived theme all pass through the same resolver.
* Switching themes touches storage only through the stable keys listed above, so
  upgrading never resets a choice.

---

## 12. Accessibility

* **Touch targets:** 48 dp minimum for every control (context chip and edit "Done"
  were raised to 48 dp in this pass).
* **Contrast:** guaranteed by the engine and unit-tested; wallpapers that are light
  produce dark home text.
* **Text scaling:** the system font scale is multiplied, never replaced; the app's
  own text scale is clamped to 0.75–1.6.
* **Screen readers:** cells expose one announcement (“App name” or “Folder, N
  apps”); decorative glyphs are `importantForAccessibility="no"`; drawer sections
  are marked as headings via `ViewCompat.setAccessibilityHeading` (works below
  API 28, unlike the XML attribute).
* **Colour independence:** statuses have labels and glyphs; selection is shown by
  a filled icon and a container, not hue alone.
* **Reduced motion:** respected globally, including ambient animation.

---

## 13. Token map

| File | Contains |
| --- | --- |
| `ui/theme/LumaDesignTokens.kt` | **Design System v3.2**: authored colour/shape/material/type tokens per theme, spacing scale, motion tokens (parity-tested against `docs/design/v3.2/tokens`) |
| `ui/DesignTokens.kt` | Corner base radii, sizes, elevation, `LumaMotion`; `LumaSpacing` converts the v3.2 scale to Compose `Dp` |
| `ui/theme/TypeSystem.kt` | Families, weights, faces, `TypeStyle`, `LumaTypeScale`, tracking |
| `ui/theme/SemanticColors.kt` | Status, interaction, overlay, widget, field roles |
| `ui/theme/Components.kt` | `ShapeTokens`, `SurfaceTokens`, `SizeTokens`, per-theme material |
| `ui/theme/MotionIdentity.kt` | Motion characters and profiles |
| `ui/theme/WallpaperArt.kt` | Art catalogue, focal points, scrims, lightness |
| `ui/theme/WallpaperResources.kt` | Art → packaged drawable |
| `ui/IconCatalog.kt` | 45 glyphs, roles, filled variants, fallback |
| `icons/MonogramDrawable.kt` | Initial-letter fallback for unresolved icons |
| `launcher/home/Typefaces.kt` | View-system typeface cache |
| `settings/Theme.kt` | `LumaOSTheme`, Material scheme/type/shape mapping, Compose locals |

## 14. Known limitations

* The bundled artwork is AI-authored source renders, art-directed for this system;
  it has been visually reviewed in this workspace but not on a calibrated display.
* The Studio's system-wallpaper preview remains an approximation by design.
* Blur (`RenderEffect`) is API 31+ only and is skipped below it.
* Manual visual, orientation and TalkBack rows in `QA_CHECKLIST.md` still require a
  real device.
* **v3.2 screens that do not exist yet are not claimed.** The bundle specifies 26
  screens, eight widget anatomies, Quick Shelf states, Focus Spaces, a 7-section
  Studio, a Plus plan and Glance surfaces. This adoption ships the token system,
  the three identities, their typography, geometry, material and motion. Screens
  beyond that are design intent, not implementation.
* **No paper/light variant of the v3.2 identities.** The specification's light
  surface variant (§3.1: `rgba(255,255,255,.92)` sheets, ink `#0B0F16`) is not
  implemented; light mode uses the retained Zen palette (see §0.4).
* **No bundled Luma Display/Text/Mono faces.** Inter and the platform mono face
  stand in (see `docs/design/v3.2/README.md`).
