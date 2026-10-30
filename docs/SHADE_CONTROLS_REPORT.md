# Notification shade and Quick Controls — engineering report

Scope: the launcher's own pull-down shade (notifications + Quick Controls), the
widget slot geometry it sits above, the repair of saved layouts that hold an item
twice, and the Studio preview artwork decode. No launcher rebuild, no new
dependency, no new Android permission, no schema or preference change, and no
change to how the home screen behaves outside the shade.

---

## 1. How it is reached

`LumaShade` is an overlay in `home_root`, built the same way as the widget picker
and the living-icon sheet: same glass tokens, same `LumaDepthStack` elevation
binding (`depthShade = 3f`), same `onDepthProgress` hook into `applyDepth()`,
same `shutdown()` removal.

It is reachable **only** through a user-bound gesture — `GestureAction
.OpenQuickControls`, labelled "Open controls panel" in Settings → Gestures. Two
decisions follow from that, both deliberate:

| Decision | Why |
| --- | --- |
| No default binding is changed | Swipe-down is already `OpenSearch` and swipe-up is already `OpenDrawer`; silently stealing either would break a habit the shipped settings promise. Binding is one tap in Settings. |
| No new chrome on the home screen | The previous branch removed the edit banner and the permanent toolbar. Adding a status-bar handle or a pull tab would reintroduce exactly that reserved chrome. |

Accessibility: the panel is a real view tree with content descriptions, 48dp
minimum targets, and it is focusable by TalkBack once open. A TalkBack user who
cannot perform a swipe can bind **Long press on empty space** to it, which is an
existing, reachable gesture slot. Back closes it; a scrim tap closes it; an
upward drag from the scrim closes it. Nothing invisible blocks touch: while the
shade is closed it is not in the hierarchy at all, and while it is open only its
own scrim receives outside taps.

Gesture arbitration lives in the existing `dispatchTouchEvent` claim logic. The
shade pull requires `abs(dy) > touchSlop * 1.5f && abs(dy) > abs(dx) * 1.3f`
downwards from the top region, and the claim is followed by a synthetic
`ACTION_CANCEL` to the grid, so the drawer swipe, the search pull, the icon
long-press lift and the widget resize drag keep their existing thresholds. The
close drag is accepted only from the scrim (`ownsDismissDrag`), never from a card
or a slider.

## 2. Notifications — what is rendered and what is not

One listener component serves both features: `MusicNotificationListener` (in
`widgets/music/MusicWidgetProvider.kt`) already held the optional
notification-access grant for the music widget. It now also forwards
`onListenerConnected/Disconnected/Destroyed` and
`onNotificationPosted/Removed` to `NotificationHub`. No second listener, no
second grant, no polling: the hub is purely callback-driven and re-syncs with
`activeNotifications` on connect.

| Rule | Implementation |
| --- | --- |
| Only real notifications | Cards come from `StatusBarNotification` objects Android posted. There is no sample, seed or placeholder card anywhere in the source. |
| Never twice | `NotificationPolicy.visibleCards` suppresses a group summary while any of its children are live, and reports the summary's own count only when it is the last notification standing. |
| Reply only when the app offered one | `replyAction()` requires a non-blank `RemoteInput` key; the semantic-reply extra breaks ties. Sending re-uses that action's own `PendingIntent` via `Notification.Action.getRemoteInputs()`, so the message is delivered by the posting app. A failure is surfaced, never swallowed. |
| Dismiss and open are Android's | `cancelNotification(key)` / `cancelAll` through the listener; opening fires the notification's content intent and closes the shade. |
| Secret content stays secret | `contentIsSecret` hides title and body on the card; the app name and time remain, as in the system shade. |
| Bounded | 60 raw entries and 24 cards in memory, newest first, ongoing pinned to the top. |
| Access states are truthful | `NotificationAccess.state()` distinguishes GRANTED / NOT_GRANTED / REVOKED / DISCONNECTED and the panel shows the matching explanation plus a button that opens Android's own notification-access screen. Cards are hidden unless the state is GRANTED. |

Threading: extraction runs on a daemon worker (`luma-notifications`), state is
published to the main thread, and the model carries no `Notification`,
`PendingIntent` or `Bitmap` reference — only content and Android's key. Nothing
is persisted, logged or transmitted; `LumaLog` is used for lifecycle events only,
never for notification text.

## 3. Quick Controls — capability table

Every tile is rendered from a reading. `TileTapOutcome` has four variants —
`Toggled`, `OpenedSystemRoute`, `NeedsGrant`, `Unavailable` — and the panel never
animates a switch to a state the device did not report.

| Control | Read | Write | When Android reserves the write |
| --- | --- | --- | --- |
| Wi-Fi | `WifiManager.isWifiEnabled` | direct on API ≤ 28 | `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` (API 29+), else Wi-Fi settings |
| Mobile data | `ConnectivityManager` active-network transport | never (radio is system-owned) | connectivity panel, else wireless settings; state shown is "live transport", not a claimed toggle |
| Bluetooth | `BluetoothManager.adapter.isEnabled` (`null` when API 33+ refuses without `BLUETOOTH_CONNECT`) | never | Bluetooth settings; a refused read renders as system-managed, not as "off" |
| Airplane mode | `Settings.Global.AIRPLANE_MODE_ON` | never | airplane-mode settings |
| Hotspot | not readable by a third-party app | never | connectivity panel; the tile reports no state rather than a guessed one |
| Torch | `CameraManager` flash-unit probe + `TorchCallback` | `setTorchMode` | tile absent when there is no flash unit, no camera, or the camera is in use (`TorchPolicy`) |
| Rotation lock | `Settings.System.ACCELEROMETER_ROTATION` | `WRITE_SETTINGS` app-op | grant screen with an explanation; tile shows `NEEDS_GRANT` until granted |
| Brightness | `Settings.System.SCREEN_BRIGHTNESS` (+ automatic flag) | `WRITE_SETTINGS` app-op | same; level floor is 1 so the screen is never blanked by a slider at zero |
| Volume | `AudioManager` STREAM_MUSIC | `setStreamVolume` (no permission) | zero is allowed — mute is a real intent |
| Focus / DND | `NotificationManager.currentInterruptionFilter` | Notification Policy Access | policy-access screen; the tile observes `ACTION_INTERRUPTION_FILTER_CHANGED` so its state is the device's |
| Cast | resolvable destination probe | never | `ACTION_CAST_SETTINGS`, with display settings as fallback; tile only offered when a destination exists |
| Camera | resolvable-intent probe | never | `INTENT_ACTION_STILL_CAMERA`, falling back to `ACTION_IMAGE_CAPTURE`; tile only offered when one resolves |
| Battery | sticky `ACTION_BATTERY_CHANGED`, then `BATTERY_PROPERTY_CAPACITY` | n/a | a null reading renders as unknown, never as a number |
| Media | `MediaController` from `getActiveSessions(listenerComponent())` | advertised transport actions only | no active session renders the empty state; no track is invented |

**Focus and the existing context-profile automation.** Both write one system
setting, so `DndCoordination.afterUserChange()` decides the handover: switching
Focus off by hand ends the automation's ownership (`managed = false`, nothing to
restore); switching it on from a clean state records the previous filter so
`ContextAutomation` can give it back exactly as it would have. Without policy
access nothing is claimed and nothing is written. The controller reads
`currentInterruptionFilter()` *before* the write, so "previous" is the real
previous value and not the value the panel last rendered.

**Manifest delta:** four `<queries>` intents (camera still-image, image capture,
cast, media-session hosts) so the destination probes are not defeated by Android
11+ package-visibility filtering. Permission count is unchanged at four
(`REQUEST_DELETE_PACKAGES`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`,
`SET_WALLPAPER`). No `TileService` was added: one would put a LumaOS Pro tile in
Android's own Quick Settings, which is a different surface than the one asked
for, and it needs no capability the panel lacks.

## 4. Widget slot geometry

Root cause of the clipping and the drift: one home row occupies
`cellHeight + 2 × gutter` because the item decoration adds a gutter on both
sides, and three places disagreed with that.

1. `HomeGridAdapter.bindWidget` sized a widget `rows × cellHeight` — short by
   `2 × gutter × (rows − 1)`, i.e. 24dp for a four-row widget — so the widget was
   clipped and everything below it drifted up into the next row.
2. `HomeActivity.reportWidgetSize` told the provider `cellHeight × rows` in dp,
   about 16dp taller than the frame the provider was actually drawn in, and never
   reported a width at all. Providers that laid out for the reported height had
   their RemoteViews content cut off.
3. `PlacementGuideView.onDraw` advanced drop zones by `cellHeight` per row, so
   zones drifted 8dp per row — 32dp by row five — against the real grid.

`widgets/WidgetSlotGeometry.kt` is now the single definition of that arithmetic
(`GUTTER_DP`, `pitchPx`, `itemHeightPx`, `frameHeightPx`, `contentBoxPx`,
`reportedWidthDp`/`reportedHeightDp`, `rowsForHeightPx`, `isReportable`), and all
three call sites read it. `item_widget.xml` pads twice with the same dimension —
the item root and the glass frame — so the box a provider's RemoteViews get is
`item − 4 × padding`; that is what `reportWidgetSize` now reports, on both axes,
from the real measured item width rather than a constant.

| Value | Formula | 4 rows, 200px cell, 8px gutter, 16px padding |
| --- | --- | --- |
| Item height (RecyclerView child) | `rows × cell + 2 × gutter × (rows − 1)` | 848px |
| Item + decoration | `rows × pitch` | 864px = 4 × 216 ✓ |
| Frame | `item − 2 × padding` | 816px |
| Reported to provider | `frame − 2 × padding`, in dp | 784px → 392dp at density 2 |

The drop-zone guide now advances by the pitch: `EditModeController.Host` gained
`rowPitchPx()` (defaulting to `cellHeight()`, so no other implementer changes),
and `updateGuideMetrics()` feeds it to `guide.cellHeight`. The guide's own
`gutterPx` still comes from `EditModeDesign.gutterPx(density)` — the 12dp design
token is untouched, which keeps zones ~2dp inside each cell as designed and keeps
`EditModeDesignTest`'s pinned relationship true.

Resizing: `WidgetResizeGesture` treats its `rowHeight` argument as pixels per row,
so the adapter now passes the pitch (preview may overshoot by up to one gutter,
which is invisible under the finger) and commits with `itemHeightPx`, so the
committed height and the drawn height are the same number. Live row count during
the drag comes from `rowsForHeightPx`, which rounds a partial row up because the
grid cannot share a row with an icon.

## 5. Saved layouts that hold the same item twice

The mutation paths already refuse duplicates (`addToHome`, `addToDock`,
`moveHomeToDock`), but saved layouts outlive the code that wrote them: an older
build, a cloud restore or a hand-edited backup can contain two cells for one app,
two cells bound to one widget id, or an app both loose and inside a folder. On
screen that is the "duplicated, overlapping icons" a restored layout produces.

`HomeLayoutOps.dedupe(layout)` repairs that in two conservative passes: folders
that survive (first of each id, in reading order) claim their members first — an
app inside a folder is placed, so a loose copy of it is the duplicate, and
dropping the loose cell loses nothing while dropping a folder member would lose a
folder the user named — then one cell per identity survives, dock before pages,
first occurrence winning. Nothing is reordered, no page is added or removed, and
a layout without duplicates is returned as the **same instance**.

It runs where storage becomes UI state (`LauncherViewModel`'s layout flow), and a
repair is written back with `setHomeLayoutRawIfUnchanged`, so a concurrent write
is never clobbered. The write re-enters the flow once and stops, because a
repaired layout de-duplicates to itself.

`HomeLayoutCodec.decode` was deliberately **not** changed. `canSafelyRewrite`
proves the forgiving parser skipped no record (`encode(decode(raw)) == raw`), and
`mayReferenceWidget` depends on that proof before releasing a platform widget id.
De-duplicating inside the decoder would make a duplicated-but-parseable layout
look unparseable: `seedDefaultDock` would skip and warn on every launch and
`pruneUninstalled` would skip that profile forever, so storage would never heal.
The decoder stays byte-faithful; `HomeLayoutDedupeTest` pins both halves.

## 6. Studio preview artwork

`StudioScreen`'s theme backdrop decoded a full-screen wallpaper on the
composition's main-thread context with a blind `inSampleSize = 2` and no bounds
pass, inside `produceState` — so it allocated a near-full-size bitmap, dropped
frames while scrolling themes, and re-decoded the same art on every
recomposition. It now resolves the legacy `WallpaperCatalog` entry for that theme
art and calls `WallpaperImageStore.preview(entry, 640)`, which is the cached
`Dispatchers.IO` decode the wallpaper picker already uses. `openRawResource` was
correct and is unchanged: it accepts any file-based resource, which is how
`drawable-nodpi` art has always been read here.

## 7. Files

| File | Role |
| --- | --- |
| `notifications/NotificationModels.kt` | content-only models + `NotificationPolicy` (pure, JVM-tested) |
| `notifications/NotificationExtractor.kt` | `StatusBarNotification` → model, off the main thread |
| `notifications/NotificationHub.kt` | callback-driven store, reply/dismiss/open through real intents |
| `notifications/NotificationAccess.kt` | grant state detection + the system screen that changes it |
| `notifications/NotificationCards.kt` | glass card views, inline reply field, action row |
| `controls/QuickControlsModel.kt` | policies: tile set, columns, slider mapping, `DndCoordination`, `TorchPolicy` |
| `controls/DeviceControls.kt` | every real read, write and system route |
| `controls/MediaSessionSource.kt` | active-session observation through the existing grant |
| `controls/QuickControlsView.kt`, `GlassSlider.kt` | panel and slider rendering |
| `controls/QuickControlsController.kt` | snapshot lifecycle, tap outcomes, DND handover |
| `launcher/home/LumaShade.kt` | the overlay: open/close, depth, insets, theme, host contract |
| `launcher/HomeActivity.kt` | gesture wiring, `LumaShade.Host`, widget geometry helpers |
| `launcher/model/GestureAction.kt` | `OpenQuickControls` + the "not offered, on purpose" note |
| `launcher/model/HomeLayout.kt`, `LauncherViewModel.kt` | duplicate repair and its write-back |
| `widgets/WidgetSlotGeometry.kt`, `home/HomeGridAdapter.kt`, `home/EditModeController.kt`, `home/PlacementGuideView.kt` | one geometry, three call sites |
| `core/apps/AppRepository.kt` | `labelForPackage` / `iconForPackage` for card app identity |
| `widgets/music/MusicWidgetProvider.kt` | forwards listener callbacks to the hub |
| `settings/SettingsScreen.kt`, `res/values/strings_v2.xml` | gesture label |
| `res/values/strings_shade.xml`, `res/drawable/ic_rx_*` | 82 strings/plurals, 15 glyphs — all cross-checked, no duplicates |
| `app/src/test/.../WidgetSlotGeometryTest.kt`, `NotificationPolicyTest.kt`, `QuickControlsPolicyTest.kt`, `HomeLayoutDedupeTest.kt` | pure-JVM coverage of the decisions above |

## 8. Verification boundary — what actually ran here

**Executed in this workspace:**

- tree-sitter Kotlin parse over all 141 main sources and all 64 test sources: the
  only reported errors are in four files whose constructs the grammar cannot
  express (`IntelligenceEngine.kt`, `ChronoDialView.kt`, `HomeGridAdapter.kt`,
  `WallpaperImageStore.kt`), all pre-existing and all on lines this branch does
  not touch (`git diff` confirms zero hits).
- Resource cross-check: 82 new strings/plurals and 15 new drawables resolve, with
  no missing or duplicated name; `AndroidManifest.xml` parses and still declares
  exactly four permissions.
- Independent re-derivation in Python of every numeric expectation in
  `WidgetSlotGeometryTest` and `QuickControlsPolicyTest` (columns, slider
  mapping, battery, geometry chain, row recovery): all pass. This validates the
  arithmetic, **not** the Kotlin.

**Not executed:** `./build.sh assembleDebug`, `./build.sh test`, `./build.sh
lint`, `./build.sh assembleRelease`, any `./gradlew` task, and any APK install.
This sandbox has no JDK, no Gradle, no Android SDK, no `adb`, and no network
route to the hosts that serve them (`api.adoptium.net`, `services.gradle.org`,
`dl.google.com`, `repo1.maven.org` all unreachable). No compile error, test
result, lint result or runtime behaviour is claimed anywhere in this report. Run:

```
./build.sh assembleDebug
./build.sh test
./build.sh lint
./build.sh assembleRelease
```

## 9. Device test matrix — every row is UNVERIFIED

No physical device or emulator is available here, so each of these is outstanding
work, not a passed check.

| # | Case | What must hold |
| --- | --- | --- |
| D1 | Bind Swipe down → Open controls panel, swipe down | Shade opens over the home screen; swipe-up drawer, search pull, long-press lift and widget resize still work |
| D2 | Back / scrim tap / upward drag from scrim | Shade closes each way; no touch is swallowed afterwards |
| D3 | Receive a notification with a `RemoteInput` reply | Reply field appears; sending it lands in the originating app |
| D4 | Receive a notification with no reply action | No reply field; only the app's own actions render, capped at three |
| D5 | Grouped notifications | Children listed once, summary suppressed; dismiss the last child and the summary appears with its count |
| D6 | Dismiss from the card, dismiss all | Both remove the notifications system-wide |
| D7 | Tap a card | The app's content intent opens; the shade closes |
| D8 | Revoke notification access while the shade is open | The panel switches to the revoked state; no crash, no stale cards |
| D9 | An app marked content secret | Title and body hidden; app name and time shown |
| D10 | Rotation / process recreation with the shade open | No leaked activity reference; state is rebuilt or the shade is closed cleanly |
| D11 | Wi-Fi, airplane, rotation lock, brightness, volume, torch | State matches Android's; a write either happens or opens the real system page — never both claimed and refused |
| D12 | Mobile data, Bluetooth, hotspot, cast | Tile opens the system route; Bluetooth on API 33+ without `BLUETOOTH_CONNECT` shows system-managed, not "off" |
| D13 | Focus with and without Notification Policy Access | Grant screen appears when needed; with a context profile active, a manual Focus change hands ownership over correctly (`DndCoordination`) |
| D14 | Media playing / nothing playing | Real metadata and advertised actions; empty state otherwise |
| D15 | Rapid repeated taps on every tile | No corrupted state, no double animation, no stuck switch |
| D16 | Place a 1-, 2-, 3- and 4-row widget; resize each | No clipping, no overlap with the row below, drop zones land on the cells, size survives recreation |
| D17 | Restore a layout containing a duplicate (backup or hand-edited DataStore) | One cell per item after launch; folders keep every member; the repaired layout is written back once |
| D18 | Studio → scroll themes | No dropped frames, no OOM, correct backdrop per theme in light and dark |
| D19 | TalkBack through the whole panel | Every control announced, 48dp targets reachable, long-press binding opens the shade |
| D20 | Contrast over a busy wallpaper, light and dark | Card and tile text legible at the authored glass opacities |

## 10. Known limitations and follow-ups

1. **Compile risk sits in the new surface**, which has never been through a
   Kotlin compiler: `LumaShade`'s host contract, `DeviceControls`' API-level
   branches, `NotificationExtractor`'s use of `Notification.MessagingStyle`
   extras, and the Compose-free view code in `NotificationCards`.
2. **Hotspot state is unknowable** to a third-party app; the tile is a route, not
   a switch. Same for mobile data, where only the live transport is readable.
3. **Android widget host ids are not remapped after system backup/restore**
   (pre-existing, documented in `README.md`), so a restored widget placement may
   still need re-adding; the duplicate repair fixes duplicated *cells*, not
   unbindable ids.
4. **No `TileService`**: LumaOS Pro does not add a tile to Android's own Quick
   Settings. The system surface is reachable through the routes above.
5. **The intermittent Studio crash is still unconfirmed.** The artwork decode
   fixed here was a real main-thread defect and a plausible cause of Studio
   stutter, but no stack trace was available in this environment; per the project
   rule, no cause is claimed. `StudioScreen` contains no `!!`, `.first()`,
   index-zero access, `require` or `check`, and its artwork path is wrapped in
   `runCatching`, so a crash there would have to come from elsewhere.
6. Walk `docs/QA_CHECKLIST.md` after a real build; the shade rows above belong in
   it once D1–D20 have been executed on hardware.
