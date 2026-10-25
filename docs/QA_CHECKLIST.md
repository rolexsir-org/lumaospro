# LumaOS Pro — Design System QA Checklist

This checklist separates **what has been verified in this workspace** from **what
must be verified on a device or in a real build**. Nothing in the "device" section
has been executed here.

Environment of record: no JDK, Gradle, Android SDK, emulator or physical device was
available. The project was therefore **not compiled, not linted, and not rendered**.
Do not treat any screenshot-like description in the design document as evidence of
on-device appearance.

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
| A12 | Typography storage keys unchanged | Enum inspection | `precision`, `serene`, `grotesk`, `serif`, `mono` |
| A13 | Theme storage keys unchanged | Enum inspection | `aurora`, `porcelain`, `vector` (+ specialist themes) |
| A14 | `WallpaperArt` API matches its call sites (`storageKey`, `focalX`, `focalY`, `forTheme`) | Cross-read | PASS |
| A15 | Motion profile bounds and invariants | `MotionProfile.init` requires | Encoded, not executed |
| A16 | `IconCatalog` names resolve for every glyph file | Name/resource cross-check | PASS |

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

Repeat for **Aurora**, **Zen**, **Void**, and for at least one specialist theme.

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
