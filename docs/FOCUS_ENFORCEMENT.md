# Focus Protection enforcement

How LumaOS Pro intercepts selected launches and attempts optional foreground
interventions during Work/Study sessions, what each layer can and cannot do,
and which permissions are involved. Written for reviewers, QA, and Play Store
policy review.

## The three levels (never conflated)

| Level | Name | Mechanism | Blocks |
|---|---|---|---|
| A | Launcher interception | LumaOS Pro launch surfaces check `LauncherViewModel.isRestricted` / `isPackageRestricted` before starting a restricted app, while Focus Protection is enabled in Work or Study | Home icons, drawer rows, search results, folders, smart folders, drawer shortcuts, long-press menu shortcuts, gesture and voice launches |
| B | Best-effort Accessibility-assisted foreground enforcement (this document) | Optional `AccessibilityService` may observe supported foreground package changes and attempt Home plus the native restriction screen; Android can stop delivery or refuse the screen | Some notification taps, Recents switches, and app handoffs may trigger an intervention; this is not guaranteed coverage of every launch path |
| C | Device-owner / managed-device enforcement | `DevicePolicyManager` lock-task / app suspension | Not implemented: requires device-owner provisioning, which a normal Play Store launcher cannot assume |

Level A is available only while Focus Protection is enabled in an active Work
or Study context; it does not depend on Accessibility. Level B is optional,
off by default, and requires both the user-enabled Accessibility grant and a
connected service. Even then it is best-effort: Android may omit foreground
events or refuse the restriction activity. Level C is documented only so the
gap is explicit: LumaOS Pro does not claim system-wide blocking.

## Level B design

- **One decision engine.** `FocusEnforcement.shouldBlock` is the only
  decider; the service and the restriction activity both call it with a
  snapshot taken fresh from DataStore for each delivered event/presentation.
  No verdict is cached across events. This avoids stale decisions when the
  service is running; it does not guarantee that Android delivers an event or
  re-connects the service after process death, reboot, or OEM intervention.
- **Event-driven, no polling.** The service subscribes to
  `typeWindowStateChanged` only and sleeps otherwise. Idle battery cost is
  effectively zero; there are no wakeups, timers, or usage-stats polls.
- **Minimal capabilities.** The service config
  (`res/xml/focus_accessibility_config.xml`) sets
  `canRetrieveWindowContent="false"`. The only field ever read is
  `event.packageName`. Node trees are never traversed, so screen content,
  text, passwords, and input are unobservable even in principle. Package names,
  screen content, and user activity are not logged, persisted, or transmitted;
  a generic diagnostic line may record that an intervention was attempted.
- **Intervention attempt.** For a restricted foreground package, request Home
  with `GLOBAL_ACTION_HOME`, then attempt `FocusRestrictionActivity` after
  350 ms (`singleTask`, excluded from Recents). This sequence is not a
  guarantee: if another app owns the Home role, Home may open that launcher,
  and Android may refuse to start the restriction screen. A failed start is
  logged and not retried in a loop.
- **Loop guard.** `FocusInterventionGate` allows one intervention per package
  per 2 s cooldown; a different restricted package still intervenes
  immediately, and returning to the same app later re-blocks it. The activity
  additionally re-evaluates on every start and every resume, finishing
  itself when there is nothing left to enforce.
- **Safety allowlist.** `FocusSafety` unconditionally exempts LumaOS Pro
  itself, System UI, Settings, the dialer/phone/telecom stack, the package
  installer (uninstalling a restricted app must keep working), and the
  permission controller. Personal and Sleep never enforce, and an unexpired
  emergency unlock for the active profile lifts both layers.

## Emergency unlock

Both restriction surfaces offer "Unlock for N min" (N configurable in
Settings → Contexts → Work/Study, 1–60, default 5). The unlock records the
granting profile plus a strict wall-clock deadline; `FocusEnforcement`
honors it only while that profile is still active, so it can neither leak
across contexts nor outlive its deadline. After unlocking, the blocked app
the user originally asked for is opened. The live unlock is transient
device-local state (never backed up); only the configured length is backed up.

## Permissions and Play policy

- No new manifest permission is needed for the binding itself: the service is
  protected by `android.permission.BIND_ACCESSIBILITY_SERVICE` and can only
  be enabled by the user in system settings.
- Before routing there, Settings shows an in-app disclosure
  (`focus_access_disclosure`); the system toggle screen additionally shows
  the service `android:description`. Both state that the connected service
  reads only foreground package names from window-state events, that it acts on
  the configured restriction list only during enabled Work/Study sessions, and
  that it does not read screen content.
- Revocation and a grant-enabled-but-disconnected service are supported
  degraded states. Settings re-checks the grant on resume and shows the service
  connection separately; layer B is unavailable while either prerequisite is
  missing, and layer A continues. There is no re-prompting, silent re-enabling,
  or functionality held hostage behind the grant.
- Accessibility-policy alignment: the API supports the user's configured
  focus restrictions with the minimum event subscription and no content
  retrieval. Foreground package names are processed on-device for the decision;
  package names are not persisted or transmitted. The feature is optional,
  disclosed before the system redirect, and revocable at any time.

## Why not UsageStats / DevicePolicy

- `UsageStatsManager` foreground detection needs the `PACKAGE_USAGE_STATS`
  grant (also a settings redirect) *plus* polling on a timer to be timely —
  weaker detection at a permanent battery cost. Accessibility events are push
  based and immediate, so usage stats were deliberately not requested.
- `DevicePolicyManager` suspension/lock-task is the only true system-wide
  block, but it requires device-owner or profile-owner provisioning via ADB,
  QR, or NFC — unacceptable for ordinary users and out of scope for a
  consumer launcher. Not implemented, never claimed.

## Support matrix (layer B is best-effort, even when enabled)

| Entry point / condition | Verdict | Notes |
|---|---|---|
| LumaOS Pro icons / drawer / search / folders / shortcuts / gestures / voice | Layer A intercepts when Focus Protection is enabled in Work or Study | Pre-launch gate; Accessibility grant is not needed |
| Notification taps, Recents switches, external-app handoffs | Best-effort attempt (layer B) | A supported foreground event may trigger Home plus the restriction screen; event delivery and activity start are not guaranteed |
| Split-screen / freeform | Partial | The service may observe the focused window; an already-visible restricted app in an unfocused pane is not pushed away |
| LumaOS Pro is not the default Home app | Limited | Home may open another launcher; Android may refuse the restriction screen. A bounce or screen presentation is not guaranteed |
| Accessibility grant enabled but service not connected, stopped, or suspended | Unavailable (layer B) | Settings reports the grant and service connection separately; layer A is unaffected |
| Accessibility grant revoked mid-session | Unavailable (layer B, by design) | Foreground events stop; layer A continues when Focus Protection is enabled |
| Other launchers, lock-screen shortcuts, widgets | Not covered | Outside both layers' supported entry points |
| In-app content (Reels, Shorts, feeds, individual screens) | Not covered | Package-level enforcement cannot see inside apps |
| Device-owner suspension, app hiding from Settings | Not implemented | Requires managed-device provisioning |

## Manual QA checklist

With Focus Protection enabled in Work or Study and a restricted app configured:

1. Tap the app on Home, in the drawer, in search, in a folder, via both
   shortcut surfaces, via gesture and via voice → layer A should intercept;
   Back/dismiss should not launch it.
2. If Accessibility is enabled and Settings reports the service connected,
   try a notification tap, a Recents switch, and an app handoff. Record what
   happens on that Android/OEM build; misses or refused screens are within the
   documented best-effort boundary. Confirm layer A still works.
3. On a restriction screen: unlock → the requested app opens; after the unlock
   deadline, a later supported interception should block it again.
4. Switch to Personal or Sleep mid-session → Focus is inactive; switch back to
   Work/Study → launcher interception resumes without a new permission prompt.
5. Revoke Accessibility mid-session → layer B becomes unavailable while layer
   A remains. On returning to Settings, check that the grant is shown as off.
6. Rapid Work → Study → Personal switches → no stale restriction decisions or
   unlock leaking across profiles.
7. After reboot, check grant and service-connection status separately. Do not
   assume layer B is available until the service reports connected; layer A is
   independent of Accessibility.
8. The service is event-driven and has no polling loop; idle behavior can be
   checked without promising a particular battery result.
