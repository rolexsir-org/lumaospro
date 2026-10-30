# LumaOS Pro — Design System QA Checklist

This checklist separates **what has been verified in this workspace** from **what
must be verified on a device or in a real build**. Nothing in the "device" section
has been executed here.

Environment of record: no full JDK, Gradle, Android SDK, emulator or physical
device was available. The Android application was therefore **not compiled with
Gradle, not linted, not linked and not rendered**. A standalone Kotlin compiler
and JRE were bootstrapped locally for pure-JVM tests only. Do not treat any
screenshot-like description in the design document as evidence of on-device
appearance.

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
| A20 | **v3.2** bracket balance in the 19 Kotlin files touched by this pass | String/comment-stripping counter (backticked test names handled) | PASS |
| A21 | **v3.2** new string resources declared and referenced (`type_ambient`, `type_monograph`) | Resource cross-check | PASS (only `android.R.string.cancel` is a framework resource) |
| A22 | **v3.2** no unbundled typeface is referenced by the token layer | Manual review | PASS — `FontFamilyId` still covers exactly Inter, Space Grotesk, system sans/serif/mono |
| A23 | **v3.2** the Compose spacing object draws every value from the token scale (no off-scale 6/10/14 dp) | `LumaSpacing` definitions reviewed against `SPACING_DP` | PASS — `grid/xxs 4`, `compactGap/elementGap/sm 8`, `md 12`, `lg/contentPadding 16`, `screenHorizontal/Vertical 20`, `sectionGap/xl/screenEnd 24`, `xxl 32`, `dockGap 40`, `xxxl 56`, `display 72` |
| A24 | **v3.2** authored `ok`/`warn` reach the semantic layer instead of being re-derived | `ThemeColors.successSeed` / `warningSeed` → `SemanticPalette.of`, retained palettes null | PASS (by inspection; unit test `DesignSystemV32Test` asserts the seeds) |
| A25 | **v3.2** clock size is a per-theme token, not a constant | `ResolvedTheme.clockSp` → `HomeActivity.applyPreset` | PASS (by inspection; 64 / 58 / 84 sp) |
| A26 | Version identity is `1.0.0` / `versionCode` 100 in the build file and every document | Cross-check of `app/build.gradle.kts`, `README.md`, `RELEASE-1.0.0.md`, `CHANGELOG.md`, `BUILD_REPORT.md` | PASS |

Additional Luma Glass static checks (current branch): 72 XML files parsed; 130
Kotlin sources checked for balanced comments/strings/delimiters; all XML and
Kotlin resource references resolved; `activity_home.xml` runtime-test IDs and
types preserved; no unused imports in modified Kotlin files; pure Kotlin token
layer compiled with standalone `kotlinc` 2.2.21; 242 JVM-only unit tests passed
with a JUnit shim (`GlassMaterialTest` included).

Not performed: Gradle Kotlin compilation, resource linking, lint, Android unit
tests, Robolectric tests, APK/AAB assembly, signing, installation, screenshots.

---

## B. Automated tests

The pure JVM rows below were executed locally with a standalone Kotlin compiler
and JUnit shim. The Android/Robolectric rows remain unexecuted here.

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
| `ui/theme/GlassMaterialTest` **(Luma Glass)** | Role completeness, distinct geometry/opacity/blur, light/dark contrast, opaque capability fallbacks, scrim polarity, unified accent presets, motion-token aliasing | All pass |
| `widgets/WidgetSlotGeometryTest` **(shade)** | Grid pitch, multi-row item height, frame and content box, reported dp, row recovery, degenerate input | All pass |
| `notifications/NotificationPolicyTest` **(shade)** | Reply only from a real `RemoteInput`, semantic-reply preference, inline-action cap, title/body precedence, secret content, group-summary suppression, ordering, card bound, age buckets | All pass |
| `controls/QuickControlsPolicyTest` **(shade)** | Tile columns per width, tile set vs real capability, system-route decisions, brightness floor, volume zero, clamping, battery from level/scale, DND handover, torch verdict | All pass |
| `launcher/model/HomeLayoutDedupeTest` **(shade)** | Duplicate repair (dock wins, first wins, folder keeps members, widget/smart ids), page and order preservation, stability, codec round-trip, decoder stays byte-faithful | All pass |

The four **(shade)** rows were added after the standalone run above and have
**never been executed**: this workspace has no JDK, Gradle or Android SDK, so
their expectations were re-derived independently instead
([docs/SHADE_CONTROLS_REPORT.md](docs/SHADE_CONTROLS_REPORT.md) §8). Treat them
as unrun until `./build.sh test` is green.

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
      60 `ic_rx_*` drawables are packaged (45 plus the 15 the shade added), and
      that `strings_shade.xml`'s 82 entries survive shrinking.

---

## D. Visual verification per theme (device required)

Repeat for **Signature**, **Atmosphere** and **Monolith**, then for **Aurora**,
**Zen**, **Void** and at least one specialist theme.

### D0. Design System v3.2 specifics (device required)
- [ ] Signature renders the authored midnight ramp with translucency; no banding on
      the `#06070B → #0F1219` transition.
- [ ] Atmosphere draws the user's wallpaper, tints to it, and keeps text legible on
      both a light and a dark wall.
- [ ] Monolith draws **no blur** anywhere — drawer, folders and the editing
      backdrop included — even on an Android 12+ device with blur available.
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
      drawer, menus, widget frames, the editing capsule and its drop zones).
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

### D6b. Home editing (device required — the core of this release)

Resting state, nothing to touch:
- [ ] The home screen shows **no** banner, title, hint card, tutorial strip or
      permanent editing toolbar, and none of that space is reserved: the clock,
      grid, search row and dock sit exactly where they do with editing off.
- [ ] The only thing that appears while editing is one capsule inside the search
      row (Widgets, Layout, Studio, Wallpaper, Settings, Done) — icon-only, no
      labels, no paragraph, one row tall.

Starting and ending:
- [ ] Long-press empty workspace opens editing; the capsule cross-fades into the
      search row over ~140 ms and the wallpaper is the only thing that dims.
- [ ] Long-press an app icon lifts it **immediately** and it follows the finger.
      No menu, no sheet, no second gesture handler firing at the same time.
- [ ] Releasing a lifted icon without moving opens that item's contextual menu;
      dragging past the slop never opens the menu.
- [ ] Tapping an icon while editing opens its contextual menu.
- [ ] Tapping outside the capsule and the grid ends the session.
- [ ] Back and Home end the session; the app does not exit and no work is lost.
- [ ] Rotating or recreating the activity mid-session restores the layout and the
      committed order.

While editing:
- [ ] Drop zones appear only while an icon is in the air and land exactly on the
      grid cells (no offset, no zone over an occupied slot).
- [ ] The remove target appears only during a drag, sits under the status bar,
      swells when armed, and nothing is removed unless the release is on it.
- [ ] Page dots keep the same height and visibility as at rest; add/remove page
      is in the Layout menu and never re-flows the workspace.
- [ ] All five features still work: Widgets picker, Layout menu (presets, smart
      folders, pages), Studio, Wallpaper picker, Settings.
- [ ] Nothing on the desktop shifts, scales or re-measures at any point of the
      session (watch the first and last frame).

### D7. Widgets
- [ ] Every hosted widget sits inside the themed frame with matching radius.
- [ ] Widget content is not recoloured.
- [ ] An unavailable widget shows the themed message, not a crash or blank frame.
- [ ] Resize handle works while an item is lifted and snaps to rows.
- [ ] A 1-, 2-, 3- and 4-row widget is drawn whole: no clipped content, no gap at
      the bottom of the frame, and the row below starts where the grid says it
      does (item + decoration equals exactly that many grid pitches).
- [ ] Provider content is laid out for the box it really gets — check a provider
      that draws edge-to-edge (music, battery) at every row count, in both
      orientations, and after a rotation.
- [ ] Drop zones under a widget drag land on the cells, with no cumulative drift
      by the fourth or fifth row.
- [ ] A resize commits the row count that is drawn, and that row count survives
      process recreation.

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
- [ ] Every control is at least 48 dp, including the context chip and each of the
      six editing actions.
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

### D12. Notification shade and Quick Controls (device required)

Full detail, including what each control may and may not do, is in
[docs/SHADE_CONTROLS_REPORT.md](docs/SHADE_CONTROLS_REPORT.md) §9.

Opening and closing:
- [ ] **Settings → Gestures → Swipe down → Open controls panel**, then swipe down
      from the top: the shade opens over the home screen.
- [ ] Swipe-up drawer, search pull, long-press icon lift, widget resize drag and
      page swipes all still work with the binding set.
- [ ] Back, scrim tap and an upward drag from the scrim each close it; no touch is
      swallowed afterwards and the home screen is immediately interactive.
- [ ] Opening and closing rapidly ten times leaves no stuck animation, no leaked
      view and no double-open.
- [ ] Rotation and process recreation while open: no crash, no stale activity
      reference; the shade is either rebuilt or closed cleanly.

Notifications:
- [ ] A notification with a `RemoteInput` reply shows a reply field; sending it
      lands in the originating app (verify inside that app, not by the animation).
- [ ] A notification without one shows no reply field, and only the app's own
      actions, capped at three.
- [ ] Grouped notifications: children listed once, summary suppressed; dismiss the
      last child and the summary appears with its own count.
- [ ] Dismiss from a card and dismiss-all both remove the notifications in Android
      too.
- [ ] Tapping a card opens the app's content intent and closes the shade.
- [ ] An app that marked content secret shows its name and time, not its text.
- [ ] Revoke notification access while the shade is open: the panel switches to
      the revoked state with the route to Android's screen; no crash, no stale
      cards, and no repeated prompt on the next open.
- [ ] Long text, many notifications (24+) and a notification from an uninstalled
      app all render without clipping or a blank card.

Quick controls:
- [ ] Wi-Fi, airplane mode, rotation lock, brightness, volume, torch: the state
      shown matches Android's, and a tap either changes it or opens the real
      system page — never both claimed and refused.
- [ ] Mobile data, Bluetooth, hotspot, cast: the tile opens the system route;
      Bluetooth on API 33+ without `BLUETOOTH_CONNECT` reads as system-managed,
      not as "off".
- [ ] Brightness without `WRITE_SETTINGS` shows the grant explanation and the
      route; after granting, the slider moves the real screen brightness and never
      reaches a level that blanks it.
- [ ] Focus with and without Notification Policy Access; with a context profile
      active, switch Focus off by hand and confirm the automation stops restoring
      a filter it no longer owns.
- [ ] Media playing shows real metadata and only advertised actions; nothing
      playing shows the empty state and no invented track.
- [ ] Battery matches Android's reading; a device that reports none shows unknown.
- [ ] Torch on a device with no flash, and while a camera app holds the camera:
      the tile is absent or explains itself — never a switch that cannot move.
- [ ] Rapid repeated taps on every tile leave no corrupted or stuck state.
- [ ] Card and tile text is legible over a busy wallpaper in light and dark, and
      every control is announced and reachable by TalkBack with 48dp targets.

Studio:
- [ ] Scrolling Studio's theme list is smooth, the correct backdrop shows per
      theme in light and dark, and no main-thread decode appears in StrictMode.

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
