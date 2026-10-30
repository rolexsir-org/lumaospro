# Changelog

Notable changes to LumaOS Pro. Entries use the app's `versionName`; the Play
`versionCode` is derived as `major * 100 + minor * 10 + patch` (1.0.0 → 100).

Nothing in this file is a claim that a build was produced, tested or published.
See [BUILD_REPORT.md](BUILD_REPORT.md) for the verification boundary and
[RELEASE-1.0.0.md](RELEASE-1.0.0.md) for the release checklist.

## 1.0.0 — unreleased

### Notification shade and Quick Controls

- **The launcher has its own pull-down shade** (`LumaShade`): real notifications
  above, a Quick Controls panel below, drawn at the same glass depth the widget
  picker uses and built on the same overlay pattern. It opens only through a
  gesture the user binds — **Settings → Gestures → *Open controls panel*** — and
  closes on Back, on a scrim tap or on an upward drag from the scrim. No default
  binding changed and no chrome was added to the resting home screen.
- **Cards show only what apps posted**: icon, app name, title, body, time, group
  count and the app's own actions. A group summary is never listed next to the
  children it summarises, content an app marked secret stays hidden, and there is
  no sample or placeholder card in the source.
- **Inline reply is the app's reply.** A reply field appears only when the
  posting app exposed a `RemoteInput`; sending re-uses that action's own
  `PendingIntent`, so the message is delivered by the app. Dismiss, dismiss-all
  and open all go through Android's listener APIs, and a refused action is
  reported as refused.
- **One grant, two features.** The shade reuses the existing
  `MusicNotificationListener` and its optional notification-access grant — the
  listener now forwards connect/disconnect/destroy and posted/removed events to
  `NotificationHub`, which is callback-driven, extracts off the main thread,
  keeps at most 60 entries in memory, and never persists, logs or transmits
  notification text. Access states (granted, not granted, revoked, disconnected)
  each render their own explanation and a route to Android's own screen.
- **Quick controls report real device state and never fake a write.** Wi-Fi,
  mobile data, Bluetooth, airplane mode, hotspot and cast are read where Android
  allows a read and routed to Android's own panels or settings pages wherever a
  third-party write is reserved to the system. Brightness and rotation lock need
  the `WRITE_SETTINGS` app-op, Focus needs Notification Policy Access, and both
  are requested through the real system screens with an explanation. Torch is
  offered only when the device has a flash unit no camera app is holding; battery
  comes from Android's sticky broadcast; media comes from the active
  `MediaSession` and shows an empty state when nothing is playing.
- **Focus and the context-profile automation no longer fight over one setting**:
  `DndCoordination` ends the automation's ownership when Focus is switched off by
  hand and records what to restore when it is switched on from a clean state.
- **No new permission.** The manifest still declares four; four `<queries>`
  intents were added so camera and cast destinations resolve on Android 11+.
- See [docs/SHADE_CONTROLS_REPORT.md](docs/SHADE_CONTROLS_REPORT.md) for the
  capability table and the device-test matrix.

### Widget geometry and saved-layout repair

- **Widgets are no longer clipped and no longer push the grid around.** One home
  row occupies `cellHeight + 2 × gutter`, and three places disagreed with that:
  a widget item was sized `rows × cellHeight` (24dp short at four rows), the size
  reported to the provider was ~16dp taller than the frame it was drawn in and
  had no width at all, and the drop-zone guide advanced by `cellHeight` per row
  (32dp of drift by row five). `widgets/WidgetSlotGeometry.kt` is now the single
  definition of that arithmetic and all three read it — including the box a
  provider's RemoteViews actually get, which is the item minus four paddings
  because `item_widget.xml` pads the root and the frame.
- **Resizing commits what is drawn**: the gesture preview steps by the real grid
  pitch, the live row count rounds a partial row up, and the committed height is
  the same `itemHeightPx` the adapter binds.
- **A saved layout that holds the same item twice is repaired on load**
  (`HomeLayoutOps.dedupe`): dock before pages, first occurrence wins, a folder
  keeps every member and claims them so a loose duplicate of a member is the cell
  that goes, widgets and smart folders dedupe by id, nothing is reordered, and a
  clean layout comes back as the same instance. The repair is written back with a
  compare-and-set so a concurrent write is never clobbered. `HomeLayoutCodec`
  is unchanged, so saved layouts stay byte-compatible in both directions.
- **Studio's theme backdrop decodes on the IO thread through the wallpaper
  store's own cache** instead of decoding a full-screen image on the composition
  thread with a blind `inSampleSize`, which was dropping frames while scrolling
  themes.
- Tests: `WidgetSlotGeometryTest` (geometry chain, row recovery, reported size),
  `NotificationPolicyTest` (replies, grouping, precedence, age buckets),
  `QuickControlsPolicyTest` (tile set, columns, slider mapping, battery, DND
  handover, torch verdict) and `HomeLayoutDedupeTest` (repair + codec
  round-trip) — all plain JVM, no device and no Robolectric.
- Verified in this environment: tree-sitter parse of every main and test source
  (no new syntax errors), resource and manifest cross-check (82 new
  strings/plurals, 15 glyphs, permission count unchanged), and an independent
  re-derivation of the new arithmetic. **Not compiled, not tested, not linted and
  not run on a device** — this sandbox has no JDK, Gradle, Android SDK, `adb` or
  network route to them. See
  [docs/SHADE_CONTROLS_REPORT.md](docs/SHADE_CONTROLS_REPORT.md) §8–§10.

### Home editing, gesture-first

- **Removed the legacy editing chrome entirely.** The "Edit home" banner
  (title, hint line, container, the height it reserved) and the persistent
  five-button toolbar are gone from the layout, the activity, the resources and
  the inset calculation. Nothing replaced them: no banner, no floating
  instruction card, no tutorial, and no space reserved in the resting home
  screen.
- **Long-press an icon and it lifts immediately** — drag-and-drop starts on the
  press, not a menu. `EditModeController` now owns the lift
  (`ItemTouchHelper.isLongPressDragEnabled = false`, `startDrag()` from the
  cell's long-click), so ItemTouchHelper's own long-press detection can no
  longer compete with it for the same gesture. Releasing without moving opens
  that item's contextual menu; a tap while editing opens it too.
- **Editing is one contextual capsule inside the search row** (Widgets, Layout,
  Studio, Wallpaper, Settings, Done): 48dp tall — exactly the row the search
  pill already occupies — fully rounded, icon-only, six equal 48dp targets with
  an accessible description and no visible labels. It cross-fades with the pill
  over 140ms, so the workspace never re-measures.
- **The workspace keeps its resting geometry.** `applyChromeInsets()` no longer
  depends on editing at all: desktop padding, grid padding, dock margins and the
  clock's position are identical at rest and while editing. Page dots keep their
  reset height and visibility rule; add/remove page moved into the Layout menu
  instead of two buttons that grew the dot row mid-session.
- **Affordances exist only while they are actionable**: dashed drop zones appear
  during a drag (derived from the real grid, so they land on the cells), the
  52dp remove target floats under the status bar and only during a drag, and the
  wallpaper is the only thing that dims (55% veil, 35% artwork, API 31+
  defocus).
- **All five features keep their exact behaviour**: Widgets → widget picker,
  Layout → preset/smart-folder/page menu, Studio, Wallpaper, Settings; Done and
  tap-outside end the session, Back and Home end it without leaving the app.
- Added `LauncherViewModel.moveDockItem()` so dock reorder during a session is
  persisted through `HomeLayoutOps.moveInDock` on display-space indices.
- `HomeGridAdapter` defers its edit-mode rebind while an item is lifted, so the
  row under the finger is never re-bound mid-gesture.
- `EditModeDesign` is now a small contextual token set (capsule 48dp/24dp
  radius/2dp inset/48dp targets/22dp glyphs, 52dp remove target, 20dp zones at
  12%/2%, 24dp screen margin, 55% scrim) mirrored as `luma_edit_*` in
  `dimens.xml`; `PlacementGuideView` takes its guide and fill colours from the
  theme's `SETTINGS_CONTROL` role, so no editing colour is hard-coded.
- Verified in this environment: every Kotlin source parsed with tree-sitter's
  Kotlin grammar (no new syntax errors), both XML files parsed, unit tests
  rewritten (`EditModeDesignTest`, plus three new Robolectric assertions in
  `HomeActivityRuntimeTest`). **Not compiled and not run**: this sandbox has no
  JDK, no Gradle and no network to fetch them — see `BUILD_REPORT.md`.

### Luma Glass v4.0 (unreleased)

- Added one shared, role-based Liquid Glass material layer (`GlassMaterial.kt`)
  with distinct recipes for primary surfaces, secondary panels, search fields,
  dock, folders, menus, sheets, widgets, settings controls and overlays.
- Added matching View (`GlassDrawable` / `GlassViews`) and Compose
  (`GlassComponents`) adapters, including pressed/focused/selected states and
  opaque fallbacks driven by the existing effects policy.
- Simplified the home hierarchy to one centred clock/date, one primary search
  entry, an aligned grid and one floating glass dock, while preserving the
  runtime test IDs and hidden compatibility actions.
- Unified settings, Studio, dashboard, preview, drawer, folders, menus, widget
  picker and edit chrome on the same material tokens; default appearance now
  uses the real system wallpaper with a polarity-aware legibility scrim.
- Added studio accent presets that keep all nine legacy theme IDs/storage keys
  but present them as accents within one Luma Glass identity.
- Fixed a nested block comment in `LumaDesignTokens.kt` that prevented Kotlin
  parsing, two backticked test names containing dots, `TypeStyle.entries` order,
  the type-scale hierarchy, and atmosphere tests that unintentionally exercised
  bundled artwork instead of the theme atmosphere.
- Locally bootstrapped Kotlin 2.2.21 compiled the pure token/theme layer and ran
  242 JVM-only unit tests with a lightweight JUnit shim; **Gradle, Android lint,
  Robolectric, debug APK assembly and device rendering were not executed**.

### Design System v3.2

- Adopted the LumaOS Design System v3.2 bundle (archived with provenance in
  [`docs/design/v3.2/`](docs/design/v3.2/README.md)).
- Three authored identities replace Aurora / Zen / Void as the signature set:
  - **Signature** — midnight ramp `#06070B`, indigo-violet `#7C86FF`, 34/26/16/10/6
    dp radii, blur 26 dp at 135 % saturation.
  - **Atmosphere** — deep teal `#04131A`, aurora mint `#46E0B0`, 38/30/20/12/8 dp
    radii, blur 34 dp at 165 %, wallpaper-led with a cinematic motion profile.
  - **Monolith** — stone `#0B0B0A`, vermilion `#FF5A2B`, 8/4/2/2/2 dp radii,
    **no blur**, mono labels and an 84 sp clock.
- Authored colour path: a v3.2 theme draws its designed roles verbatim
  (translucent roles composited over the authored ground) instead of being
  regenerated from a seed. `ok` / `warn` reach the semantic layer as authored.
- Authored geometry, material, label size, clock size and the 2 · 4 · 8 … 72 dp
  spacing scale are read from tokens instead of per-screen constants.
- New type voices `ambient` and `monograph`; new motion characters `cinematic`
  and `exact`.
- Fresh installs follow the system appearance: Signature in dark, Zen in light
  (the v3.2 trio is authored dark; the specification's light *surface* variant is
  not implemented).

### Compatibility

- Aurora, Zen, Void, Obsidian, Graphite and Chroma are retained unchanged:
  same storage keys (`porcelain`, `vector` included), seeds, geometry, voices and
  bundled artwork. No saved preference, layout or exported backup is rewritten.
- Theme, typography, icon-shape, surface and motion storage keys are unchanged;
  the new keys are additive.

### Verification

- Static only in this workspace (no JDK or Android SDK): token parity between the
  bundle JSON and Kotlin, a WCAG audit of every authored pair, balanced-source,
  resource-reference and preset-coverage checks.
- Added `DesignTokensParityTest` and `DesignSystemV32Test`; updated
  `ThemeEngineTest`, `DesignSystemTest` and the preferences runtime test. **None
  of these has been executed**; they must run in CI or by the owner.

### Versioning

- `versionName` `0.1.0` → `1.0.0`; `versionCode` `1` → `100`. The pre-release
  0.1.0 build was never published, so its version code is retired.

## 0.1.0 — never published

Pre-release development state: launcher hot path, settings, Studio, backup and
restore, diagnostics, widgets and onboarding, before Design System v3.2. No
artifact was published or verified.
