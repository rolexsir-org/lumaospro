# Focus Protection enforcement

How LumaOS Pro keeps restricted apps closed during Work/Study sessions, what
each layer can and cannot do, and which permissions are involved. Written for
reviewers, QA, and Play Store policy review.

## The three levels (never conflated)

| Level | Name | Mechanism | Blocks |
|---|---|---|---|
| A | Launcher interception | Every launch path inside LumaOS Pro checks `LauncherViewModel.isRestricted` / `isPackageRestricted` before starting anything | Home icons, drawer rows, search results, folders, smart folders, drawer shortcuts, long-press menu shortcuts, gesture and voice launches |
| B | Accessibility-assisted foreground enforcement (this document) | Optional `AccessibilityService` observes the foreground package and sends restricted apps back to a native restriction screen | Notification taps, Recents switches, and external-app intents that bring a restricted package to the foreground — while the grant holds |
| C | Device-owner / managed-device enforcement | `DevicePolicyManager` lock-task / app suspension | Not implemented: requires device-owner provisioning, which a normal Play Store launcher cannot assume |

Level A always runs. Level B is off by default and needs a deliberate user
grant. Level C is documented here only so the gap is explicit: LumaOS Pro
does not claim it.

## Level B design

- **One decision engine.** `FocusEnforcement.shouldBlock` is the only
  decider; the service and the restriction activity both call it with a
  snapshot taken fresh from DataStore at event time. No cached verdicts, so
  process death, reboot, revocation, and rapid context switching cannot
  strand stale decisions.
- **Event-driven, no polling.** The service subscribes to
  `typeWindowStateChanged` only and sleeps otherwise. Idle battery cost is
  effectively zero; there are no wakeups, timers, or usage-stats polls.
- **Minimal capabilities.** The service config
  (`res/xml/focus_accessibility_config.xml`) sets
  `canRetrieveWindowContent="false"`. The only field ever read is
  `event.packageName`. Node trees are never traversed, so screen content,
  text, passwords, and input are unobservable even in principle. Nothing is
  logged (one line records *that* an intervention happened, never what was
  on screen), persisted, or transmitted.
- **Intervention order.** On a restricted foreground package: press Home via
  `GLOBAL_ACTION_HOME`, then after 350 ms present `FocusRestrictionActivity`
  (`singleTask`, excluded from Recents). Pressing Home first is what keeps
  the presentation inside Android's background-activity-start rules when
  LumaOS Pro is the default launcher. If the presentation is refused, the
  user is still safely at Home; the failure is logged once, never retried in
  a loop.
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
  the service `android:description`. Both state exactly what is observed
  (foreground package name, during focus sessions, for the user's own
  restriction list) and what is not collected (everything else).
- Revocation is a supported steady state, re-checked every time Settings
  resumes: layer B silently stops receiving events and layer A carries on.
  There is no re-prompting, no silent re-enabling, and no functionality held
  hostage behind the grant.
- Accessibility-policy alignment: the API is used for its declared purpose
  (helping the user stay in their focus session by acting on their behalf
  when a self-restricted app opens), with the minimum event subscription, no
  content retrieval, no data collection, and prominent user control. No
  personal or sensitive data is accessed, collected, used, or shared by this
  feature, so there is nothing new to declare under the User Data policy.

## Why not UsageStats / DevicePolicy

- `UsageStatsManager` foreground detection needs the `PACKAGE_USAGE_STATS`
  grant (also a settings redirect) *plus* polling on a timer to be timely —
  weaker detection at a permanent battery cost. Accessibility events are push
  based and immediate, so usage stats were deliberately not requested.
- `DevicePolicyManager` suspension/lock-task is the only true system-wide
  block, but it requires device-owner or profile-owner provisioning via ADB,
  QR, or NFC — unacceptable for ordinary users and out of scope for a
  consumer launcher. Not implemented, never claimed.

## Support matrix (level B, grant held)

| Entry point | Verdict | Notes |
|---|---|---|
| Launcher icons / drawer / search / folders / shortcuts / gestures / voice | Supported (layer A, no grant needed) | Interception before launch; unlock honors the tap |
| Notification tap opening a restricted app | Supported | Window change observed → Home + restriction screen |
| Recents switch to a restricted app | Supported | Same foreground observation |
| External app's intent (share target, link handler) | Supported | Same foreground observation |
| Split-screen / freeform second surface | Partial | The focused window's package is observed; an already-visible restricted app in the unfocused pane is not pushed away |
| Default launcher is not LumaOS Pro | Partial | Home lands elsewhere and the restriction presentation may be refused by background-start rules; the user is still bounced out of the app |
| Service killed / suspended by OEM task manager | Degraded | Events stop until Android rebinds; layer A unaffected. No rebind API exists for accessibility services (unlike notification listeners) |
| Grant revoked mid-session | Degraded (by design) | Layer B stops instantly; layer A carries on |
| In-app content (Reels, Shorts, feeds, individual screens) | Impossible at this level | Package-level enforcement cannot see inside apps; no claim is made |
| Other launchers, lock-screen shortcuts, widgets | Impossible | Outside both layers' reach; documented, not promised |
| Device-owner suspension, app hiding from Settings | Impossible / not implemented | Requires managed-device provisioning |

## Manual QA checklist

With Work + Focus Protection on, a restricted app configured, and layer B granted:

1. Tap the app on home, in the drawer, in search, in a folder, via both
   shortcut surfaces, via gesture and via voice → restriction screen every
   time; Back/dismiss never launches.
2. Open it from a notification, from Recents, and from another app's share
   sheet → bounced to Home + restriction screen.
3. On the restriction screen: unlock → app opens; wait past the deadline →
   next entry blocks again.
4. Switch to Personal mid-session → no enforcement anywhere; switch back →
   enforcement resumes without re-prompting.
5. Revoke the grant mid-session → layer B stops, layer A still blocks
   launcher taps; Settings status row reads Off on return.
6. Rapid Work → Study → Personal switches → no stacked restriction screens,
   no unlock leaking across profiles.
7. Reboot with the grant held → Android rebinds the service; enforcement
   resumes with persisted settings.
8. Battery: no measurable drain attributable to the service at idle (no
   polling exists to drain with).
