# Changelog

## Unreleased — three critical Vault fixes

Follow-up to the Hide Apps / PIN hand-off work below. Each of these is a defect in
shipped behaviour, not a new feature.

### Hidden apps no longer reappear after a reboot

- **Cause.** The hidden set lived only in the Vault's encrypted index, which cannot
  be read without the master key. Every time the Vault was locked — and therefore
  after every reboot, and during the whole post-upgrade window before the first
  unlock — `hiddenComponentKeys()` returned an empty set and the drawer rendered
  every hidden app.
- **Fix.** A second, device-bound copy (`conceal.bin`) is sealed with AES-256-GCM
  under a non-exportable Android Keystore key and read by the launcher *before*
  authentication. The encrypted index stays authoritative; the mirror is rewritten
  on every hide and restore and rebuilt from the index on every unlock, so an
  upgraded Vault repairs itself on its first unlock.
- **Failure handling.** A missing mirror is a clean first run only if the Vault has
  never hidden anything; a damaged or unreadable one falls back to the previous
  generation and is then reported as degraded — never silently treated as
  "nothing is hidden" — and is rebuilt at the next unlock. Writes are atomic, so an
  interrupted update leaves the old or the new selection, never a half file.
- **What it is not.** The mirror is not a plaintext copy: it cannot be read from an
  ADB pull, a bug report or a disk image. It is not hidden *from the device* — the
  key needs no user authentication (it must work before the first unlock), so code
  running as the app can open it. See `docs/VAULT.md` §7.1.

### The 400 ms hand-off window is gone

- **Cause.** Deciding "did the user leave?" from an attached-screen count is racy,
  because `startActivity()` is asynchronous. The previous fix treated a drop to zero
  as provisional for 400 ms and cancelled it if another screen attached. That worked,
  but it was a timing guess with no platform guarantee behind it: the window had to
  be longer than the slowest device and shorter than a shoulder-surfer's patience,
  and the settle callback ran on a clock that had to agree with the `Handler`.
- **Fix.** `VaultScreenPresence` no longer uses a clock. It tracks each Vault
  activity's lifecycle stage and decides a departure from the state of the *other*
  activities. Android always creates a successor before its predecessor stops, so a
  hand-off is recognised as a hand-off rather than inferred from elapsed time.
  Configuration changes are explicitly exempt.
- **Preserved.** Lock-on-leave, the idle timeout and immediate screen-off locking all
  behave exactly as before; a genuine departure — including pressing Home from a
  deep screen while the parent is still alive — conceals synchronously, in the same
  callback that observes it.

### `guard.json` can no longer be reset by deleting it

- **Cause.** The retry counter had to be readable before unlock, so it could not be
  protected with the master key. It was plain JSON: editing or deleting it reset the
  progressive delay, which was documented as an accepted limitation.
- **Fix.** Every record is now tagged with an HMAC-SHA-256 key generated in the
  Android Keystore and never exported. Editing the record fails verification and is
  treated as tampering. Deleting it is detected too — the Keystore entry outlives
  the file — and the maximum penalty is stored immediately, so a delete-then-guess
  loop grants **no** attempts rather than one free guess per deletion.
- **Preserved.** The delays (3 free attempts, then 10 s → 30 s → 60 s → 5 min →
  15 min cap), PIN verification and recovery are unchanged. A new Vault is the only
  clean slate (`resetForNewVault()` is called from creation and nowhere else), and a
  device whose Keystore cannot provide an HMAC key degrades to the previous mode
  instead of locking its owner out.
- **Not claimed.** Replaying an older valid record still verifies, and root or a
  compromised OS can ask the Keystore for the tag. Rollback protection for file
  contents is not available to apps on Android; this is stated in
  `docs/VAULT_THREAT_MODEL.md` §3.1.

## Unreleased — Hide Apps moves into the Vault; PIN hand-off fixes

Two related changes: the launcher-level **Hide Apps** feature is now part of the
Vault, and the three defects behind "correct PIN bounces you back to the launcher"
are fixed.

### Hide Apps is a Vault section

- Hidden apps are selected, stored and restored inside the Vault, in the
  `Hidden Apps` section of the Vault home. The list lives in the Vault's encrypted
  index (`VaultSettings.hiddenApps`), not in a plaintext DataStore preference.
- The standalone entry points are gone: the Settings "Manage hidden apps" dialog,
  the "Find hidden apps in search" and "Protect hidden apps with screen lock"
  switches, and the drawer long-press action that wrote the preference directly.
  Settings now has one row that opens the section, and the drawer's long-press
  entry routes into the Vault instead of changing a locked vault's state.
- **Migration is additive and never discards the old selection.** On the first
  unlock after upgrading, the per-profile `hidden_apps_<profile>` preference is
  converted and imported. The import only adds, is idempotent, is recorded by a
  marker file outside the encrypted index, and the old preference is left in place
  so an interrupted import can retry and a downgrade still finds it.
- Hidden apps stay out of the drawer **and** out of launcher search; the old
  "show hidden apps in search" opt-in is removed rather than left inert.
- **What did not change, by design:** hiding is launcher-level metadata. No app is
  uninstalled, disabled, suspended, force-stopped or cleared, and the screen says
  so, including that any other launcher can still open a hidden app and that this
  is not Android work-profile isolation.

### Vault PIN hand-off

- **Correct PIN re-locked the Vault.** `startActivity()` is asynchronous but
  `finish()` is immediate, so the count of attached Vault screens dropped through
  zero during the lock→home transition; the lifecycle guard read that frame as
  "the user left" and, with lock-on-leave on, locked the vault the moment the PIN
  was accepted. `VaultScreenPresence` now treats a short depart-while-starting
  window as provisional and cancels it when the next screen attaches.
- **`finishAffinity()` finished the launcher's task.** The Vault shares the
  launcher's affinity, so "lock and return" closed the whole task and dropped the
  user to the system home — "the app closed itself". Vault now starts the
  launcher's own HOME intent instead.
- **Wrong recovery key crashed.** `messageView` was only created by `buildPinPad()`,
  so `showMessage()` threw `UninitializedPropertyAccessException` on the recovery
  screen. It is now guarded and falls back to a message view in the body.
- **The hand-off window was on the wall clock.** The policy timestamped departures
  with `currentTimeMillis()` while the settle runs on `Handler` uptime, so a clock
  correction could collapse the window and re-lock the vault mid-transition. It now
  uses a monotonic source.
- **The lock screen closed itself when the vault locked.** Screen-off, the idle
  timeout or a failed biometric finished the only attached Vault screen, dropping the
  user out of the app instead of back to PIN entry. The lock screen now resets itself
  and never finishes on a lock.
- **`lateinit` crashes in the camera screen.** `showCameraProblem`, `savePhoto`,
  `savePdf` and `resetFooter` touched views from background callbacks before those
  views existed. Guarded; a failure no longer takes the process down.
- The lock screen is started before the old screen finishes, and unlocks can carry
  a destination (`EXTRA_DESTINATION`) so the drawer's entry lands on Hidden Apps
  rather than the Vault home.

**Verification:** see `docs/HIDDEN_APPS_AND_PIN_2026-11.md`. 187 JVM tests pass and
34 Vault sources compile; Gradle, lint, R8, the debug APK and on-device checks
could not run in the offline sandbox and are reported as blocked, not as passing.

## Unreleased — LumaOS Vault targeted remediation

The seven findings from the maintainer's remediation brief, plus one defect found while
verifying them. Details, evidence and limits: `docs/VAULT_REMEDIATION_2026-11.md`;
the written threat model is `docs/VAULT_THREAT_MODEL.md`.

- **Deleted items are now cryptographically deleted.** The crash-recovery copy of the
  metadata index (`meta.bin.bak`) still held the wrapped data key of a deleted item, so
  recovered ciphertext plus that stale index could be decrypted. Deletion now rewrites the
  backup generation with the post-deletion index before destroying the payload, and evicts
  thumbnails first. Interrupted deletions leave a swept orphan, never a dangling entry.
- **`guard.json` is handled defensively and documented honestly.** No fake integrity
  protection; a malformed record is treated as damage (seeded count anchored to its own
  mtime) instead of a clean slate; forged values are clamped so a corrupt file cannot lock
  the owner out for more than 15 minutes. Deleting it still resets the delay — documented
  as a bypass of the delay only, which never bypasses PIN verification.
- **Note copy is marked sensitive** with `ClipDescription.EXTRA_IS_SENSITIVE` (Android 13+)
  while remaining fully available to the user; no unmarked fallback, no clipboard logging.
- **Further zeroization:** PBKDF2 plaintext chunk buffers, decrypted chunks and the reader's
  cached chunk are wiped; the metadata key is destroyed on every failed unlock; PIN change
  works on `CharArray` instead of unwipeable `String`s.
- **Orphan sweep shreds** instead of unlinking.
- 22 new or rewritten tests (126 total in this area), 0 failing. No format, schema,
  parameter or UI change, so no migration is required.

## Unreleased — LumaOS Vault security hardening

Pre-release security review of the Vault (see `docs/VAULT_SECURITY_AUDIT.md`).

- **Backups are staged and verified locally before the destination is touched**, so an
  interrupted upload can no longer leave a truncated file that looks like a backup, and a
  previous good backup is never replaced by an incomplete one.
- **Restore now treats a backup file as hostile input:** canonical object ids only, hard
  caps on every length and count, a free-space check, and a completeness check that
  refuses a container whose index references payloads it does not contain.
- **A failed restore rolls the previous vault back** instead of leaving a half-established
  one.
- **Key rings reject absurd KDF iteration counts**, closing a pre-authentication hang.
- **Valid recovery keys are no longer rejected** when a checksum character is transcribed
  with a Crockford alias (`l` for `1`, `O` for `0`) — this affected roughly one key in
  eight.
- **The recovery key can no longer be copied to the system clipboard.**
- Staging is shredded rather than unlinked; container verification is size-bounded.
- 18 new automated tests (105 total in this area), including hostile-container restore
  tests, key-ring limits, concurrent stores and manifest hygiene.

## Unreleased — LumaOS Vault

A private, fully offline encrypted space inside LumaOS Pro. No new dependency, no
second APK, and no change to existing launcher behaviour.

- **Security foundation.** Per-vault 256-bit master key, per-object random data keys,
  AES-256-GCM for small objects and a chunked streaming AEAD (Tink-style framing) for
  large ones, HKDF-SHA-256 domain separation, PBKDF2-HMAC-SHA-256 PIN stretching
  calibrated per device, a checksummed 256-bit recovery key, and a device-bound
  Keystore wrapper for biometric unlock.
- **Private capture.** Camera (Camera2), document scanner (multi-page PDF via
  `PdfDocument`), and voice recorder (MediaRecorder) all write ciphertext only — no
  DCIM, no MediaStore, no plaintext scratch outside the vault's own staging directory.
- **Private library.** Gallery with in-memory thumbnails, files with SAF import/export,
  notes, and a viewer that plays encrypted audio/video through a seekable decrypting
  source.
- **Session security.** Lock on screen-off, immediate concealing on background,
  configurable inactivity timeout, `FLAG_SECURE` everywhere, key zeroization on lock,
  progressive retry throttling.
- **Encrypted backup and restore.** Ciphertext-only container, recovery-key restore on a
  new phone, integrity verified before success is reported.
- **Security Centre.** PIN change, biometric enrolment, recovery-key rotation, auto-lock,
  backup status, restore and a content-free security log.
- **Tests.** 87 new JVM tests covering cryptography, storage privacy, session policy,
  throttling, backup/restore, backup-rule exclusions and manifest hygiene.
- Integration is limited to `LumaCore`, one Settings row, the manifest and four strings.



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
