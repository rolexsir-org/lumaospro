# LumaOS Pro — Design System QA Checklist

This checklist separates **what has been verified in this workspace** from **what
must be verified on a device or in a real build**. Nothing in the "device" section
has been executed here.

Environment of record: no JDK, Gradle, Android SDK, emulator or physical device was
available. The project was therefore **not compiled, not linted, and not rendered**.
Do not treat any screenshot-like description in the design document as evidence of
on-device appearance.

The rows below cover two passes: the pre-3.2 design pass and the **Design System
v3.2 adoption** (rows A12–A13 updated, A17–A22 added). The v3.2 rows are marked.

---

## A. Static checks performed in this workspace

| # | Check | Method | Result |
| --- | --- | --- | --- |
| A1 | `activity_home.xml` is well-formed | Python XML parser | PASS |
| A2 | `styles.xml`, `strings_v2.xml` are well-formed | Python XML parser | PASS |
| A3 | Every `@drawable/@color/@string/@style/@font/@mipmap/@dimen` reference in `res/layout*`, `res/menu*`, `res/values*` resolves to a declared resource | Scripted cross-check of resource tables | PASS (0 unresolved) |
| A4 | Every `R.drawable/R.string/R.color/R.style/R.font/R.dimen/R.layout/R.id` reference in Kotlin resolves | Scripted cross-check of resource tables | PASS (only `android.R.string.cancel` matched, which is a framework resource) |
| A5 | Every `import com.lumaos.pro.launcher.*` resolves to a declared top-level symbol in the importing project | Scripted package/declaration index over 121 Kotlin files | PASS (0 unresolved) |
| A6 | Brace/paren/bracket balance in the 22 most-edited Kotlin files | String/comment-stripping counter | PASS |
| A7 | Every `R.id` used by `HomeActivityRuntimeTest` exists in `activity_home.xml` | ID set comparison | PASS |
| A8 | No references remain to deleted glyphs (`ic_search`, `ic_settings`, `ic_voice`, `ic_close`) | Repo-wide grep | PASS |
| A9 | 45 interface glyphs exist and parse as vector XML | Generated from a shared path table + XML parse | PASS |
| A10 | Bundled fonts and licences present | Directory check | 7 TTF, 2 OFL texts |
| A11 | Wallpaper art present in both orientations | Directory + size check | 3 portrait, 3 landscape WebP |
| A12 | Typography storage keys unchanged, v3.2 voices added | Enum inspection | `precision`, `serene`, `grotesk`, `serif`, `mono` + new `ambient`, `monograph` |
| A13 | Theme storage keys unchanged, v3.2 themes added | Enum inspection | `aurora`, `porcelain`, `vector`, `obsidian`, `graphite`, `chroma` + new `signature`, `atmosphere`, `monolith` |
| A14 | `WallpaperArt` API matches its call sites (`storageKey`, `focalX`, `focalY`, `forTheme`) | Cross-read | PASS |
| A15 | Motion profile bounds and invariants | `MotionProfile.init` requires | Encoded, not executed |
| A16 | `IconCatalog` names resolve for every glyph file | Name/resource cross-check | PASS |
| A17 | **v3.2** token parity: Kotlin constants vs `docs/design/v3.2/tokens/*.json` (18 colour roles × 3 themes, radii, tile, blur, saturation, type scale, spacing, motion) | Scripted comparison using the same rounding and parsing as `DesignTokensParityTest` | PASS — exact match, 0 differences |
| A18 | **v3.2** authored contrast measured against the engine's contract (text-1 ≥ 7:1 on surfaces 0/1, text-2 ≥ 4.5:1 on 1/2, text-3 ≥ 3:1, accent ≥ 3:1, danger ≥ 4.5:1, max ink on accent ≥ 4.5:1) | Scripted WCAG maths on composited roles, all three themes | PASS for every enforced floor. Two authored roles sit below the *specification's own* targets and are shipped as authored: Monolith text-2 = 6.8:1 (spec 7:1) and text-3 = 4.1 / 4.4 / 3.4:1 by theme (spec 4.6:1) — see `docs/design/v3.2/README.md` |
| A19 | **v3.2** every `ThemeId` has exactly one preset and `ThemePresets.ALL` covers the enum | Scripted set comparison | PASS (9 identities, 3 v3.2 + 6 retained) |
| A20 | **v3.2** bracket balance in the 15 Kotlin files touched by this pass | String/comment-stripping counter | PASS (aggregate counts match) |
| A21 | **v3.2** new string resources declared and referenced (`type_ambient`, `type_monograph`) | Resource cross-check | PASS (only `android.R.string.cancel` is a framework resource) |
| A22 | **v3.2** no unbundled typeface is referenced by the token layer | Manual review | PASS — `FontFamilyId` still covers exactly Inter, Space Grotesk, system sans/serif/mono |
| A23 | **v3.2** the Compose spacing object draws every value from the token scale (no off-scale 6/10/14 dp) | `LumaSpacing` definitions reviewed against `SPACING_DP` | PASS — `grid/xxs 4`, `compactGap/elementGap/sm 8`, `md 12`, `lg/contentPadding 16`, `screenHorizontal/Vertical 20`, `sectionGap/xl/screenEnd 24`, `xxl 32`, `dockGap 40`, `xxxl 56`, `display 72` |
| A24 | **v3.2** authored `ok`/`warn` reach the semantic layer instead of being re-derived | `ThemeColors.successSeed` / `warningSeed` → `SemanticPalette.of`, retained palettes null | PASS (by inspection; unit test `DesignSystemV32Test` asserts the seeds) |
| A25 | **v3.2** clock size is a per-theme token, not a constant | `ResolvedTheme.clockSp` → `HomeActivity.applyPreset` | PASS (by inspection; 64 / 58 / 84 sp) |

Not performed: Kotlin compilation, resource linking, lint, unit tests, Robolectric
tests, APK/AAB assembly, signing, installation.

---

## B. Automated tests that exist but were **not executed** here

Run with `./build.sh test` (or `./gradlew test`) on a machine with the toolchain.

| Test | Covers | Expected |
| --- | --- | --- |
| `ui/theme/ThemeEngineTest` | Contrast guarantees for every preset × backdrop × light/dark, all curated swatches, 300 random accents/backgrounds; storage-key compatibility; AMOLED behaviour; override preservation | All pass |
| `ui/theme/ColorMathTest` | OKLCH conversion, contrast maths, blending | All pass |
| `ui/theme/AtmosphereTest` | Layer plan per backdrop/atmosphere | All pass |
| `robolectric/HomeActivityRuntimeTest` | Inflates the real `activity_home.xml`, binds adapters, exercises drawer/recreation/back | All pass |
| `robolectric/PreferencesAndSamplersRuntimeTest` | Preference round-trips, including typography enum | All pass |
| `ui/theme/DesignTokensParityTest` **(v3.2)** | Kotlin tokens ↔ `docs/design/v3.2/tokens/*.json`, token by token, including the bundle version | All pass |
| `ui/theme/DesignSystemV32Test` **(v3.2)** | Authored vs derived colour paths, radius roles (34/26/16/10/6, 38/30/20/12/8, 8/4/2/2/2), blur material (26/34/0), saturation, label sizes, motion characters, backdrop fallbacks, fresh-install defaults | All pass |

If `ThemeEngineTest` fails on contrast after these changes, the failure is a real
regression in the palette — do not relax the assertion.

---

## C. Build and packaging (required, not yet done)

- [ ] `./build.sh test` — green.
- [ ] `./build.sh lint` — no new errors; review any warnings touching `NewApi`.
- [ ] `./build.sh assembleDebug` — APK produced.
- [ ] `./build.sh assembleRelease` — R8 completes; check that `res/font` and
      `res/drawable-*nodpi` survive shrinking (they are referenced from code and
      XML, but verify the release mapping).
- [ ] Install debug APK on a device; launch via HOME role and via the app icon.
- [ ] `aapt2 dump resources` (or `apkanalyzer`) confirms 7 fonts, 6 WebP artworks,
      45 `ic_rx_*` drawables are packaged.

---

## D. Visual verification per theme (device required)

Repeat for **Signature**, **Atmosphere** and **Monolith**, then for **Aurora**,
**Zen**, **Void** and at least one specialist theme.

### D0. Design System v3.2 specifics (device required)
- [ ] Signature renders the authored midnight ramp with translucency; no banding on
      the `#06070B → #0F1219` transition.
- [ ] Atmosphere draws the user's wallpaper, tints to it, and keeps text legible on
      both a light and a dark wall.
- [ ] Monolith draws **no blur** anywhere — drawer, folders and edit mode included —
      even on an Android 12+ device with blur available.
- [ ] Monolith's mono labels and 84 sp clock do not clip at 1.3× text scale.
- [ ] Radius differences are visible between themes (34 vs 38 vs 8 dp on a sheet).
- [ ] Switching theme cross-fades colours over the theme's own duration (420 ms
      Signature/Atmosphere, 280 ms Monolith) and never mixes type mid-fade.
- [ ] A custom accent or background visibly switches the palette to the derived
      path and still meets the contrast contract on screen.
- [ ] Old installs keep their `aurora` / `porcelain` / `vector` selection after
      upgrade, and a restored pre-3.2 backup round-trips unchanged.

### D1. Colour and surfaces
- [ ] Text is legible over both the bundled artwork and the system wallpaper.
- [ ] No hard-coded colour is visible on any screen (spot-check Settings, Studio,
      drawer, menus, widget frames, edit mode).
- [ ] Surfaces, borders and ripples change with the theme; nothing stays "Aurora
      blue" after switching to Zen.
- [ ] Status, disabled and selected states are distinguishable with colour vision
      simulation on.
- [ ] Light theme (Zen) has no white-on-porcelain text at first frame.

### D2. Typography
- [ ] Clock, date, context chip, search hint, app labels, drawer title and section
      headers all use the theme's voice.
- [ ] Aurora/Zen render Inter; Void headings render Space Grotesk; body copy in
      Void is still Inter.
- [ ] Signature and Atmosphere render Inter; Monolith headings and clock render
      Space Grotesk while Monolith body copy stays Inter and its labels/readouts are
      monospaced.
- [ ] No synthetic bold or substituted glyphs; `f`/`l` ligatures look correct.
- [ ] Font size "Largest" in system settings: nothing clips, overlaps or becomes
      untappable.
- [ ] App label scale and tracking match between home grid, dock and drawer.

### D3. Iconography
- [ ] All interface glyphs share stroke weight and optical size (compare search,
      settings, close, sort, voice).
- [ ] Filled variants appear only for selected states.
- [ ] App icons retain their original colours under every icon style.
- [ ] An app with a missing icon shows a legible monogram, not an empty square.
- [ ] No legacy icon style (filled Material) appears anywhere.

### D4. Wallpaper
- [ ] Portrait and landscape show different crops of the same artwork, neither
      stretched nor letterboxed.
- [ ] Zen displays dark text; Aurora and Void display light text.
- [ ] Scrim keeps the grid and dock legible without visibly "banding".
- [ ] Switching theme cross-fades artwork; no black flash, no double image stuck.
- [ ] Rotating the device re-decodes at the new width and does not leak memory.
- [ ] Selecting the system wallpaper or the theme atmosphere hides the bundled art
      correctly.
- [ ] Simulate a missing asset (rename a WebP in a scratch build): home falls back
      to the atmospheric backdrop, no empty panel.

### D5. Shapes and surfaces
- [ ] Search field, drawer search, menus, folders, dock and widgets use their
      token radii; no control is inconsistent between themes.
- [ ] Void reads sharper; Zen reads softer; the difference is visible but calm.
- [ ] No fully-round pill used for a text field or chip.
- [ ] Borders are hairlines; no decorative outlines.
- [ ] Shadows appear only on floating surfaces (menus, FLOAT dock, dialogs).

### D6. Home composition
- [ ] Header, grid, search and dock align to the same 20 dp / 16 dp insets.
- [ ] Dial, clock, date and context chip align on one optical axis in DIAL mode.
- [ ] HERO centres the clock and hides duplicate actions.
- [ ] COMPACT keeps the grid usable on a small screen.
- [ ] Empty state is centred, readable and actionable.
- [ ] Drawer sheet corners, top padding and scrim look correct under the status bar
      and above the navigation bar (gesture and 3-button).

### D7. Widgets
- [ ] Every hosted widget sits inside the themed frame with matching radius.
- [ ] Widget content is not recoloured.
- [ ] An unavailable widget shows the themed message, not a crash or blank frame.
- [ ] Resize handle works in edit mode and snaps to rows.

### D8. Motion
- [ ] Press feedback is subtle and immediate; it never delays launch.
- [ ] Reduced motion (system "Remove animations") disables press scaling, ambient
      animation and the return settle.
- [ ] Animator duration scale 0.5× and 2× do not break or desynchronise springs.
- [ ] Theme switch cross-fades colours, then switches type/icons once.
- [ ] Spring animations are interruptible: tapping during a drawer settle does not
      strand the sheet.
- [ ] Void's overshoot is a single, controlled overshoot; Aurora/Zen never bounce.

### D9. Theme switching and persistence
- [ ] Switch Aurora → Zen → Void → restart: the choice persists.
- [ ] Custom accent and background survive a preset switch and a restart.
- [ ] Per-profile theme (e.g. Work → Aurora) still overrides the global choice.
- [ ] Scheduled light/dark still respects the dark window.
- [ ] Chroma still derives from wallpaper colours and explains itself when it
      cannot.
- [ ] An upgrade from 0.1 keeps the saved theme, typography and gesture keys.
- [ ] Studio preview matches the home screen after Apply (same artwork, same
      colours, same type).

### D10. Accessibility
- [ ] TalkBack: each cell announces one label; folders announce member count.
- [ ] TalkBack: drawer section headers are announced as headings.
- [ ] TalkBack: every icon-only button has a description.
- [ ] Every control is at least 48 dp, including the context chip and edit "Done".
- [ ] All text meets AA in both dark and light themes (use an on-device contrast
      checker on the rendered screenshot).
- [ ] Large font + display size maximum: no clipped labels, no overlapping chrome.
- [ ] RTL language: insets and chevrons mirror; the clock stays centred.
- [ ] Colour-blind simulation: status badges remain distinguishable by label + icon.

### D11. Performance
- [ ] Cold start to first frame is not slower than the previous build on a
      low-end device (target: no regression > 40 ms).
- [ ] Scrolling the drawer and home grid stays at target frame rate; no jank from
      per-frame drawable creation during theme animation.
- [ ] Wallpaper decode does not appear in StrictMode main-thread violations.
- [ ] Memory: switching themes repeatedly does not grow the bitmap cache beyond
      its 6 MB budget.
- [ ] Battery: ambient backdrop idles when motion is off; no wakelocks.

---

## E. Acceptance gate

A release candidate is acceptable only when:

1. Section A and B are green on a clean checkout with the project toolchain.
2. Sections C and D have been run on at least one physical device (ideally one
   low-end device and one modern device) and every box checked or explicitly
   waived with a reason.
3. Any waived item is documented in `BUILD_REPORT.md` with the device model,
   Android version and observed behaviour.
4. No claim of "verified" appears in README or store listing for an item that is
   still unchecked above.
