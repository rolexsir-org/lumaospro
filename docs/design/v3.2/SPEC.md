# LumaOS — Design System Specification
**Version 3.2 · Launcher build target: Android 13+ (API 33), Compose**
Companion files: `index.html` + `app.js` + `style.css` (interactive studio), `tokens/*.json` (implementation tokens), `design-system.html` (single-file build).

---

## 1. What LumaOS is

A premium Android home-screen replacement whose value is *composition*: a home screen that reads as designed, an app drawer that stays fast at 300 apps, and a Theme Studio that lets people change the interface without breaking it.

**Three themes, one grammar.** Signature, Atmosphere and Monolith differ in composition, typography, icon treatment, surface material, widget anatomy and motion feel. They share the icon grid, glyph set, spacing scale, state model, motion family and accessibility contract, so a user who switches theme does not have to relearn the launcher.

### Design principles (decision rules, not slogans)
| Principle | Operational rule |
|---|---|
| Precision | 4px baseline grid. Icon tiles centre on the optical centre of glyph + label pair, not the bounding box. Row gaps are a token, never ad-hoc. |
| Depth | Three layers only: wall → surface → control. Blur is a material with a theme value; it is **0** in Monolith. |
| Clarity | One primary action per surface. Supporting text never competes with a label. Empty space is kept, not filled. |
| Personality | A theme earns difference through composition + type + icon treatment, never through added ornament. |
| Restraint | ≤ 2 type weights per screen. One accent per theme. No gradient behind text. Motion ≤ 480ms. |

---

## 2. Themes

| | **Signature** | **Atmosphere** | **Monolith** |
|---|---|---|---|
| Personality | Refined, timeless, expensive | Immersive, cinematic, adaptive | Architectural, bold, exact |
| Composition | Centred clock, single full-width agenda, floating dock | Left-aligned clock, search pill, weather card, elevated dock | Left-aligned oversized clock, ruled info block, edge-anchored bar |
| Background | `#06070B` → `#0F1219` midnight ramp | Wallpaper-driven, wall-sampled palette | Stone-neutral `#0B0B0A` → `#171715` |
| Accent | `#7C86FF` indigo-violet | `#46E0B0` aurora mint (+ `#7FB6FF`) | `#FF5A2B` vermilion |
| Radii | 34 / 26 / 16 / 10 / 6 | 38 / 30 / 20 / 12 / 8 | 8 / 4 / 2 / 2 / 2 |
| Clock | 64dp / w250 / −0.035em, centred, uppercase date | 58dp / w200 / −0.04em, sentence date | 84dp / w600 / −0.06em, mono date |
| Icons | Outline glyph, accent-tinted, near-transparent plate | Filled glyph on chroma, specular top light | Engraved glyph on opaque plate, one accent tile |
| Material | blur 26 · sat 135% · low ambient | blur 34 · sat 165% · adaptive scrim | no blur · 1px rules · tonal plates |
| Labels | 10.5 / 480 sentence case | 11 / 480 sentence case | 9 / 500 mono, uppercase +0.1em |
| Why it exists | For users who want the interface to disappear | For users who treat the wall as the product | For users who want a design object with structure |

---

## 3. Tokens

### 3.1 Colour (per theme, full ramps in `tokens/*.json`)
Roles: `bg-0/1/2` (wall, screen, elevated background) · `surface-1/2/3` (rows, controls, active) · `surface-solid` (opaque fallback below API 31) · `stroke-1/2` (hairline, emphasis) · `text-1/2/3` · `accent`, `accent-2`, `accent-soft` · `ok`, `warn`, `danger`.

Validated pairs (against composited wallpaper):
`text-1 ≥ 15:1` · `text-2 ≥ 7:1` · `text-3 ≥ 4.6:1` · focus ring `accent` at 3:1 minimum against adjacent surface.

State colour rules: selected = `accent-soft` fill + accent stroke at 30–42% + `text-1`; disabled = 38% opacity on the whole control, no accent; warning/error = 11–14% fill with full-strength glyph, never used as body-text colour.

Light variant: widgets and sheets may invert to a paper surface (`rgba(255,255,255,.92)`, ink `#0B0F16`, secondary `#475569`) for bright wallpapers. Inversion is a theme-level decision, not per-widget improvisation.

### 3.2 Typography
Luma Sans (Display cut + Text cut), Luma Mono for numerals, labels (Monolith) and technical values.

| Role | Family | Size / Weight / Tracking |
|---|---|---|
| Clock display | Display | 66 (64/58/84 by theme) / 250 / −0.035em |
| Screen title | Display | 21 / 600 / −0.015em |
| Section heading | Text | 14 / 560 / 0 |
| Widget heading | Text | 12.5 / 540 / 0 |
| Primary info | Text | 13 / 450 / 0 |
| App label | Text | 11 / 480 / +0.005em |
| Supporting | Text | 10.5 / 400 / 0 |
| Numeric | Mono | 10 / 500 / +0.12em |
| Button | Text | 13.5 / 600 / −0.005em |
| Kicker | Text | 9.5 / 650 / +0.14em uppercase |

Rules: the clock is the only element above 32dp. Never more than two weights per screen. Labels scale to 1.3× before truncation; the grid drops a column before the label is clipped. Monolith swaps app labels to mono uppercase — that is a theme characteristic, not a user setting.

### 3.3 Spacing
`2 · 4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 56 · 72` (sp-1…sp-11). Screen gutter 20 (compact 16, large 22). Row gap 12. Section gap 24. Dock-to-grid gap 40.

### 3.4 Radius
`r-xl 34/38/8` (sheets, dock, large cards) · `r-lg 26/30/4` (widgets) · `r-md 16/20/2` (rows, tiles, toasts) · `r-sm 10/12/2` (chips) · `r-xs 6/8/2` (badges) · icon tile `24/26/10` at 58dp. Radii never mix within a theme.

### 3.5 Elevation & material
| Token | Spec | Use |
|---|---|---|
| e-0 | flat | wall, grid |
| e-1 | `surface-1` + 1px `stroke-1` | rows, tiles |
| e-2 | blur 26 + inset top light | widgets, dock |
| e-3 | blur 34–40 + ambient 28px | shelf, sheets, toast |
| scrim | 42% (adaptive 28–62%) | text over wallpaper |
| hairline | 1px at 8.5–13% | dividers only |

Compose mapping: tonal elevation + a single ambient shadow; `Modifier.blur` on API 31+, pre-blurred wallpaper bitmap below. Never more than 3 blurred layers on screen.

### 3.6 Motion
Durations `120 / 200 / 320 / 480` ms; press 90 in / 180 out; long-press 500. Easings: standard `cubic-bezier(.22,.61,.36,1)`, emphasis `cubic-bezier(.16,.84,.28,1)`, exit `cubic-bezier(.4,0,1,1)`, drag `spring(480, 34)` (reordering only).

| Interaction | Behaviour | Reduced motion |
|---|---|---|
| Open drawer | 280ms emphasis; home scales to .96, dims 18%; drawer rises 96px, blur 0→22px; 24ms stagger | fade 120ms |
| Close drawer | 220ms standard; compresses toward dock | fade 90ms |
| Quick Shelf | 320ms emphasis; scrim to 48%, sheet settles 1.02→1 | fade 140ms |
| Switch space | 260ms standard; clock + dock travel, wall parallax 12%, cross-fade at 60% travel | cross-fade 140ms |
| Open folder | 240ms emphasis; tile radius morph, contents .9→1, wall blur 14px | fade 120ms |
| Home editing | 500ms hold; wall recedes, labels dim, ±1.1° jiggle, edit bar rises 24px | no jiggle, 2px accent frame |
| Move widget | lift to e-3, 1.03, 12% translucent; neighbours spring | no lift, drop outline |
| Apply theme | 420ms; cross-fade in dependency order wall → surface → icons → type | 160ms |
| Open settings | 300ms emphasis; push 26px + fade, 16ms row stagger | fade 120ms |

Reduced motion is a user-visible setting (Settings → Advanced) *and* respects `ANIMATOR_DURATION_SCALE=0`. Every transition above has a reduced alternative — none is an afterthought.

---

## 4. Iconography

- 24×24 grid, 1.6 stroke, round caps/joins, 2px optical clearance, geometric construction.
- Filled variants (2.1 stroke) reserved for **active/selected** only.
- 36 application glyphs, 40 UI glyphs (system, navigation, settings, quick action) — one library, no mixing.
- Glyph geometry is **identical across themes**; only the plate changes (outline tint / chroma fill / engraving). This is what keeps icons recognisable when themes switch.
- Tile: 58dp, radius 24 / 26 / 10. Dock icons 54dp (50dp in Monolith). Minimum target 44dp, always ≥ 8dp apart.
- App icon balance: every third-party icon is placed inside the same 58dp plate with a 26dp live area, so an irregular original shape cannot disturb the grid rhythm.

---

## 5. Screen inventory (26)

**Home** — `home`, `edit`, `folder`
**Drawer** — `drawer`, `drawerSearch`, `drawerEmpty`
**Shelf** — `shelfPeek` (collapsed), `shelf`, `shelfEdit`
**Spaces** — `spaces`, `space-work`, `space-night`, `space-travel`, `space-create`
**Studio** — `studio` (7 sections), `studioPreview`, `applyState`, `appliedState`, `themeError`
**Settings** — `settings`, `settingsAppearance`, `settingsGestures`, `settingsAdvanced`, `backup`
**Premium** — `premium`
**System** — `glance` (notification/quick-action concepts)

### 5.1 Home
Status bar integrated (not owned); clock, date, weather where the theme calls for it; optional search entry (Atmosphere only — the other two themes reserve that role for the swipe gesture); app grid 4×5 (Monolith 4×4); page dots; theme-native dock; widget slot. Negative space is intentional: Signature keeps a full empty band above the agenda, Monolith keeps a large void between the info block and grid.

**Dock**: Signature = floating translucent capsule (blur 26, 44% fill) sitting *inside* the composition. Atmosphere = elevated adaptive-tint capsule whose tint is sampled from the wall. Monolith = edge-anchored bar with a top hairline, zero radius, no blur — structurally part of the screen, not a container on top of it.

**Edit mode**: dashed widget frame + resize handle, remove badges, ±1.1° jiggle, edit bar (Style / Widgets / Spaces / Layout / Done), dock clears the bar. Labels dim to `text-3`.

### 5.2 App drawer
Search field 44–46dp (pill in Signature, 2dp rect in Monolith), persistent near the top; Recents row (max 5) with Manage; alphabetical sections with letter + hairline, 12.4dp alpha rail on the right (hidden below 720dp height); 4-column grid (5 on large); keyboard state keeps search + results above the IME and pins the keyboard to the bottom edge; empty state names the query and offers Clear / Search web; result rows surface a suggested *action*, not just the app.

### 5.3 Quick Shelf
Three states: **collapsed peek** (118dp: four pins + All), **expanded** (Pinned, People, Quick actions, Now), **editing** (jiggle, swap, add slot, density slider, section toggles). Reachable by two-finger swipe up; scrim to 48%; max height 88%. It reads as the same surface family as the home screen (same radius, same blur, same kicker style).

### 5.4 Focus Spaces
Four example spaces with distinct *layouts*, not just tints:
- **Personal** — balanced; 2-up widgets, 4×5 grid, page dots.
- **Work** — structured; agenda-first, mono time labels, work-only shortcuts, tighter row rhythm.
- **Night** — subdued; 6 apps, one wind-down card, no weather, dimmed wall, clock de-emphasised.
- **Travel** — information-dense; flight card with SXR→DEL times, translate/wallet/maps priority, grid wall.
Switcher: 2-up cards with live mini-previews, active state = accent ring + check. Create screen: *start from* (duplicate / empty / preset), name, accent, label style, with an explicit note that spaces never touch each other's layout.

### 5.5 Theme Studio
Seven sections, one continuous surface (no deep tree): Themes, Walls, Colors, Icons, Type, Layout, Widgets. Persistent footer with **Preview** and **Apply**. Preview screen shows the theme applied to a live home composite, a surface switcher (Home / Drawer / Widgets), and a "What changes" list with token counts. Apply is a 420ms progress with per-stage status; success raises a toast with 10s Undo; failure is non-destructive (previous state intact, retry offered).

### 5.6 Settings
Groups: Appearance, Home screen, App drawer, Interaction, Spaces & data. Rows: 26dp icon, label, one-line description, trailing value/switch/chevron, 44dp min height. Appearance → surfaces & depth; Interaction → gestures with a hero explainer, assignment list and inline **conflict** banner; Advanced → reduced motion, blur quality, layout snapshots, theme bundle import/export, diagnostics; Backup → local-first with state strip (idle/loading/success/failed).

### 5.7 Premium (LumaOS Plus)
Free tier keeps every widget, 3 themes, 2 spaces, no ads, no telemetry. Plus adds curated designer themes, deep Studio token control, unlimited spaces/snapshots, widget stacks, advanced gestures, optional encrypted cloud backup. Presentation: understated card, real prices (₹1,499/yr, ₹199/mo), feature list with outcomes rather than adjectives, explicit statement that Plus never locks a home screen the user already built. No gold gradients, no artificial scarcity, no feature implied that is not specified.

### 5.8 Glance (notification concepts)
Launcher-owned surface over notifications Android has already posted: grouped media control, per-app cards, quiet section, channel filter chips. Quick-action tiles are explicitly labelled as launcher-owned controls that mirror platform toggles — the design never implies LumaOS restyles system Quick Settings or other apps' notification layouts.

---

## 6. Widgets

Eight widgets, eight anatomies, shared tokens. Anatomy order is fixed: **what is it** (kicker) → **what matters now** (primary figure/title) → **what can I do** (≤ 2 controls).

| Widget | Anatomy | Sizes |
|---|---|---|
| Clock | Local place + time + next event, thin display type | 2×2 / 4×2 |
| Weather | Icon + temperature + range + 7-bar trend | 2×2 / 4×2 |
| Agenda | Time rail + accent bar + event meta | 4×2 / 4×3 |
| Music | Art, title/artist, progress, transport | 4×2 |
| Device | Battery %, 12-segment meter, time remaining | 2×1 |
| Quick actions | 2×2 control grid with active state | 4×2 |
| Tasks | Checkbox rows with strike-through | 2×2 / 4×2 |
| Search | Field with voice affordance | 4×1 |

Stacks: up to 4 widgets behind one surface, 6px peek, swipe to cycle, dot indicator. Monolith groups widgets with 1px gutters into a single composed block (no radii). Light surface variant specified for bright wallpapers. States: idle, editing (dashed frame + handle), stale (50% + refresh), loading (skeleton rows).

---

## 7. States & components

Component inventory with states is in the studio's **System → States** and **Handoff** boards. Summary:

`tile` default/pressed(.94)/editing/disabled · `dock` 4–5 slots · `pagedots` active/inactive/hidden · `w-card` idle/editing/stale/loading · `dsearch` idle/focused/typed/loading · `resultRow` default/pressed/action · `setRow` default/pressed/selected/disabled · `dswitch` on/off/disabled · `dslider` default/drag/disabled · `shelf` collapsed/expanded/editing · `toast` success/undo/error · `spaceCard` default/active/new · `plan` default/selected · `qsTile` on/off · `notif` default/quiet.

Failure & empty rules: an empty state names the cause and offers exactly one recovery action. A failed apply, backup or import never destroys the previous state. Confirmation is required only for destructive actions (delete space, replace layout) — never for reversible ones (theme apply uses undo instead).

---

## 8. Responsive behaviour

Defined by available height, not device name.

| Class | Frame | Changes |
|---|---|---|
| Compact | 340×764 | row gap 18→14, clock 52, 4×5 grid, dock padding 10, alpha rail hidden, labels 10 |
| Standard | 390×844 | baseline |
| Large | 430×920 | gutter 22, clock 72, extra row visible before dock, drawer 5th column |

Foldables/tablets: same components, gutter 24, grid max 6 columns, widgets gain a third column. Text scale to 1.3× drops a grid column before clipping. Two-pane settings on ≥ 600dp width.

---

## 9. Accessibility

- Contrast: text-1 ≥ 15:1, text-2 ≥ 7:1, text-3 ≥ 4.6:1 against the *composited* wallpaper; adaptive scrim raises the wallpaper behind text until the ratio passes.
- Targets: 44×44dp minimum, 8dp minimum separation.
- Motion: every transition has a reduced alternative; system animator scale respected; no ambient auto-motion over 3s.
- Screen readers: icons announce "Calendar, app"; widgets expose a merged description plus actions; page dots announce "page 1 of 3"; switches announce state.
- Keyboard: full traversal in drawer search and settings; Enter/Space activate; Esc dismisses sheets and folders.
- Never convey meaning by colour alone — selected states also carry a check or label.

---

## 10. Android implementation notes

- **Theming**: one `LumaTokens` data class swapped atomically; theme application cross-fades two token sets instead of re-inflating the tree.
- **Surfaces**: `Surface` + tonal elevation; `Modifier.blur` (API 31+) with pre-blurred bitmap fallback.
- **Wallpaper colour**: `WallpaperColors`/Palette for Atmosphere accent + scrim derivation; recompute on change; never sample from a scaled-down thumbnail alone.
- **Grid**: custom `LazyVerticalGrid` with fixed cell aspect and snap; widget host via `AppWidgetHost`; third-party widgets render their own RemoteViews and receive the theme's surface tint only — LumaOS does not re-skin them.
- **Performance budget**: ≤ 3 blurred layers, blur radius ≤ 40, 60fps on mid-tier hardware; auto-degrade blur below 45fps.
- **Honest scope**: LumaOS cannot restyle system Quick Settings, the notification shade chrome, or other apps' notification layouts. Glance is launcher-owned UI over posted notifications. Notification access is optional and requested only when Glance is first opened.
- **Privacy**: no telemetry; backup local by default; theme bundles never grant permissions.

---

## 11. Quality gate (run before any handoff)

Alignment: every element on the 4px grid, tiles optically centred with labels.
Spacing: only token values; no rogue 6/10/14px gaps.
Icons: one glyph family, consistent 26dp live area, consistent optical weight.
Type: ≤ 2 weights per screen, no label under 9.5dp, no truncated label without a reason.
Contrast: all text pairs measured against the composited wall, light and dark walls.
Composition: at least one deliberate empty region per home screen.
Repetition: no two widgets sharing the same skeleton; no two spaces differing only by colour.
States: every interactive component has default, pressed, selected, disabled, and (where asynchronous) loading, empty, error.
Themes: side-by-side check that Signature, Atmosphere and Monolith differ in composition *and* typography *and* icons *and* material, while remaining unmistakably LumaOS.
Adaptation: verified at 340, 390 and 430 widths and at 1.3× text scale.
