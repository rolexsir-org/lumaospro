# LumaOS Pro — Design System

> **Verification status (read first).** This document describes the design system as
> implemented in this checkout. The implementation was written in an environment
> with **no JDK, Gradle, Android SDK or device access**, so it has **not** been
> compiled, linted, or rendered on a phone. What *has* been checked mechanically is
> listed in `QA_CHECKLIST.md`: XML well-formedness, resource-reference resolution,
> Kotlin import resolution against declared packages, contrast mathematics in the
> theme engine, and typography/geometry invariants. Nothing here should be
> presented as device-verified until the checklist's manual rows are run.

---

## 0. What this system replaces

LumaOS Pro 0.2 keeps the product's promise — a fast launcher for low-resource
phones — and gives it a deliberate visual identity. The old build drew with
Material defaults plus a thin palette. That produced three problems:

1. **One look for three products.** Aurora, Zen and Void differed only in accent
   hue, so switching themes felt like a colour swap, not a change of character.
2. **Drift.** Radii, paddings and text sizes were re-typed at each call site, so
   the same control could be 12 dp round in the drawer and 18 dp round on the
   home screen.
3. **Contrast by luck.** Text colours were chosen per widget; some pairs only
   passed AA on dark surfaces and failed on light ones.

The design system fixes all three with one rule: **components ask for a role,
never for a number.**

---

## 1. Principles

| Principle | What it means in code |
| --- | --- |
| Quiet luxury, not decoration | No gradients on text, no heavy shadows, no outlines that only decorate. Every stroke exists to separate two materials. |
| One hierarchy, three voices | The type scale is identical everywhere. Themes change family, weight, tracking and motion — never the ladder. |
| Material honesty | Aurora is midnight glass, Zen is paper, Void is a machined instrument. Each theme's radii, borders, elevation and blur follow that material. |
| Legibility is a guarantee | Text roles are contrast-checked by the theme engine, not chosen by eye. |
| Motion is feedback, not theatre | Every animation is interruptible, honours the system animation scale, and vanishes under reduced motion. |
| Identity is preserved | App icons are never recoloured. Bundled glyphs are used only for the interface; app artwork is passed through untouched. |

---

## 2. Colour

### 2.1 Themes

| Theme | Storage key | Character | Material | Type voice | Motion |
| --- | --- | --- | --- | --- | --- |
| **Aurora** | `aurora` | Midnight ribbon light | Translucent glass, 1 px hairline, 6 dp elevation ceiling | Inter, quiet luxury | Subtle |
| **Zen** | `porcelain` | Quiet paper and mist | Solid paper, almost no elevation, soft radii | Inter, modern serenity | Quiet |
| **Void** | `vector` | Indigo depth, cyan signal | Layered panels, sharper corners, accent halo | Space Grotesk + Inter | Expressive |
| Obsidian | `obsidian` | True black for OLED | Flat black, no glow | Inter | Subtle |
| Graphite | `graphite` | Machined grey, amber instrument | Matte panels, 3 dp | Inter | Subtle |
| Chroma | `chroma` | Built from the wallpaper | Same as current backdrop | Inter | Subtle |

Legacy 0.1 storage keys (`aurora`, `porcelain`, `vector`) and the old light/dark
switch are preserved. No saved preference is rewritten or discarded.

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
| **Precision** | `precision` | Inter Light, −0.035 | Inter Semibold, −0.02 | Inter Regular | Inter Medium | Inter Medium, caps, 0.14 |
| **Serene** | `serene` | Inter Light, −0.015 | Inter Medium, −0.008 | Inter Regular | Inter Medium | Inter Medium, mixed case |
| **Grotesk** | `grotesk` | Space Grotesk Regular | Space Grotesk Semibold | Inter Regular | Space Grotesk Medium | Space Grotesk Medium, caps |
| Serif (fallback) | `serif` | Platform serif | Platform serif | Inter | Platform serif | Platform serif |
| Mono (fallback) | `mono` | Platform mono | Platform mono | Inter | Inter | Platform mono |

The three signature voices are what the Studio offers first. `serif` and `mono`
remain loadable for old backups and explicit user choices; their storage keys are
unchanged.

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

`ShapeTokens` (`ui/theme/Components.kt`) defines component roles; every value is
multiplied by the theme's `cornerScale` (Void 0.82, Aurora/Zen 1.0/1.08).

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
  Zen 0.12, Void 0.26, Graphite 0.14, Chroma 0.16, Obsidian 0.16.
* **Elevation ceilings:** Zen/Obsidian 0 dp, Graphite 3 dp, Chroma 4 dp, Aurora
  6 dp, Void 10 dp. Shadows exist only where a surface genuinely floats.
* **Translucency** is `SurfaceStyle`-driven and is disabled entirely when the
  effects policy says so, then `surfaceAlpha()` returns 1.

---

## 7. Home composition

Layer order in `res/layout/activity_home.xml` (bottom → top):

1. `wallpaper_art` — bundled artwork, or gone.
2. `backdrop` — `AmbientBackdropView`: atmosphere plan, legibility scrims, glow.
3. `desktop` — header, grid, search, dock.
4. Edit chrome — top bar and toolbar.
5. Drawer container — scrim + sheet.

Desktop, top to bottom:

* **Header** (20 dp start inset, 12 dp top inset)
  * 64 dp chronometer dial (`home_dial`) when the preset uses `DIAL`
  * clock at the theme's `clockSizeSp` (DIAL 66 sp, HERO ×1.42, COMPACT ×0.70),
    Inter Light/Space Grotesk, tracking from the voice
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

`MotionIdentity.kt` defines three profiles. All share one spring vocabulary
(`MotionTokens`), one rule set, and the system animator-duration scale.

| Property | Aurora (subtle) | Zen (quiet) | Void (expressive) |
| --- | --- | --- | --- |
| `pressMs` | 90 | 110 | 70 |
| `themeChangeMs` | 420 | 480 | 320 |
| `pressScale` | 0.985 | 0.99 | 0.96 |
| `enterScaleFrom` | 0.98 | 0.99 | 0.92 |
| `popupScaleFrom` | 0.96 | 0.98 | 0.94 |
| Overshoot | Never | Never | Once, on open |

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
| `ui/DesignTokens.kt` | Spacing, corner base radii, sizes, elevation, `LumaMotion` |
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
