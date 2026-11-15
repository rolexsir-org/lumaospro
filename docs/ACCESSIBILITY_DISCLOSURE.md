# Accessibility use, disclosure and consent

This document is the reviewable record of what LumaOS Pro's optional
`AccessibilityService` actually observes, what it does with it, and how the
prominent in-app disclosure and consent flow work. It is written for Play
policy review, internal audit, and anyone verifying the claims in the app's UI.

**Verification status:** the implementation and the automated tests listed at
the end were written and (where the sandbox allowed) executed against this
source tree. Android lint, the Robolectric suite, and on-device behaviour were
**not** executed in the environment that produced this change. Google Play
compliance is therefore **not claimed** until the checklist in
[§8](#8-what-still-has-to-be-verified-before-any-compliance-claim) has been run
on a real build and device.

---

## 1. Why LumaOS Pro has an accessibility service at all

Focus Protection has two enforcement layers. Only the second one is relevant
here.

| Layer | Mechanism | Needs accessibility? |
|---|---|---|
| 1 | Launcher interception: every launch path inside LumaOS Pro (home, drawer, search, folders, shortcuts, gestures, voice) checks the restriction list before starting an app | **No** |
| 2 | Foreground enforcement: an accessibility service observes which package enters the foreground during a Work/Study session and sends a restricted app back | **Yes** |

Android exposes no other API that reports "this app just came to the
foreground" to an ordinary Play-distributed app. `UsageStatsManager` would
need the `PACKAGE_USAGE_STATS` settings grant *and* polling; LumaOS Pro
deliberately does not request it. Device-owner suspension would need managed
provisioning. So layer 2 exists as an *optional* accessibility service, and
layer 1 works with no grant at all.

## 2. Component inventory (exact)

| Component | Role |
|---|---|
| `context/FocusAccessibilityService.kt` | The `AccessibilityService` implementation; reads one field, decides, intervenes |
| `res/xml/focus_accessibility_config.xml` | Service configuration: event types and capabilities |
| `AndroidManifest.xml` → `<service .context.FocusAccessibilityService>` | Declares the service, protected by `BIND_ACCESSIBILITY_SERVICE` |
| `context/FocusAccess.kt` | Reads Android's grant state (`Settings.Secure`) and resolves the settings screen |
| `context/FocusAccessConsent.kt` | Pure consent model: states, disclosure version, UI status derivation, enforcement gate |
| `context/FocusAccessDisclosureActivity.kt` | The prominent disclosure, AGREE/DECLINE, revisit/revoke surface |
| `context/FocusAccessDisclosureScreen.kt` | The disclosure UI (stateless composable) |
| `core/prefs/LumaPrefs.kt` | Persists the consent record (`focus_access_consent_*` keys) |
| `core/prefs/PrefsFocusAccessConsentStore.kt` | Adapter from the consent model to DataStore |
| `context/FocusEnforcement.kt` | The single blocking decision (pure Kotlin) |
| `focus/FocusRestrictionActivity.kt` | The native restriction screen layer 2 presents; also requires consent |

## 3. What is observed, exactly

**Event subscription** (`focus_accessibility_config.xml`):

- `android:accessibilityEventTypes="typeWindowStateChanged"` — one event type.
- `android:canRetrieveWindowContent="false"` — the service cannot read the
  view hierarchy (no node traversal is possible, so app text, fields, images
  and input are unobservable even in principle).
- `android:canPerformGestures="false"` — no gesture injection.
- `android:canRequestFilterKeyEvents="false"` — no key events.
- `android:accessibilityFlags="default"`, `android:notificationTimeout="0"`.

**Fields read** (`FocusAccessibilityService.onAccessibilityEvent`):

```kotlin
if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
val foreground = event.packageName?.toString() ?: return
```

That is the entire read surface: **the package name of the window that gained
focus**. Other fields that may be present on the event object (class name,
content description, text list, item count) are never accessed. Node trees are
never queried, so `getSource()`, `getChild()`, `getText()` etc. are never
called.

**Purpose of that read:** compare the foreground package with the user's own
restriction list, for the active Work or Study session only. If the package is
restricted and no emergency unlock is active, the service presses Home
(`GLOBAL_ACTION_HOME`) and, after 350 ms, presents `FocusRestrictionActivity`.
When it intervenes, it also resolves that package's display label via
`PackageManager` so the restriction screen can name the app; the label is passed
to that screen and is not stored. If no focus session is running, or the package
is not restricted, the name is discarded and nothing else is read.

**Where the decision is made:** `FocusEnforcement.shouldBlock` — pure Kotlin,
unit tested, the only decider for both layers. The service takes a fresh
snapshot from DataStore per event and gates repeated interventions with
`FocusInterventionGate` (2 s per package).

## 4. Data lifecycle

| Question | Answer |
|---|---|
| Is accessibility data accessed? | Yes: the foreground **package name** only, from window-state-change events while the service is enabled; it is acted on only during a Work/Study focus session with restrictions. |
| Is it processed? | In memory only: compared with the configured restriction list, and — only when intervening — used to look up that app's display label for the restriction screen. |
| Is it stored? | **No.** The observed package name is never written to DataStore, files, logs, analytics, or a database. The user's restriction list is stored because it is a setting the user created, not because of this service. |
| Is it logged? | **No.** The one log line on an intervention (`LumaLog.i("Focus enforcement intervened on a foreground restricted app")`) contains no package name and no screen information. |
| Is it transmitted? | **No.** The app declares no `INTERNET` permission at all, so no network request is possible from this process. |
| Is it shared with third parties? | **No.** No SDKs, no advertising, no analytics, no server, no account. |
| Does it affect other apps' data? | **No.** No input injection, no reading of other apps' content, no force-stop, no uninstall, no settings changes. The only action is "press Home and show a LumaOS Pro screen". |
| What happens without a focus session? | The service still receives window-state events (it is bound while enabled) and reads the foreground package name, then discards it: no decision, no lookup, no screen, no storage. Only the safety allowlist and the active-context check run. |

## 5. What is *not* claimed

- Not a general-purpose monitoring or data-collection tool; the service has no
  storage or transport of observed data by construction, and the code does not
  contain any.
- Not content-level blocking. It cannot see or block Reels, Shorts, feeds, or
  individual screens inside an app.
- Not unbypassable. The user can always disable the service, withdraw consent,
  use another launcher, or use split-screen and other paths listed in
  [FOCUS_ENFORCEMENT.md](FOCUS_ENFORCEMENT.md).
- Not a system-wide app blocker: device-owner suspension is not implemented.

## 6. Disclosure and consent flow

### 6.1 Where the user meets it

- Settings → Advanced → Contexts → Work or Study → **Stronger blocking**.
- Settings → Advanced → Privacy → **Accessibility access** (always reachable).

Both open `FocusAccessDisclosureActivity`. The row never jumps straight to
Android's accessibility screen.

### 6.2 What the screen says

The disclosure is a full-screen surface with explicit sections, not a line in a
privacy policy and not a checkbox buried in settings. It states:

1. **Why this access is needed** — the only API that reports the foreground app.
2. **How it helps Focus Protection** — layer 1 always on; layer 2 optional;
   decline leaves layer 1 intact.
3. **What is accessed** — package name only; window content, gestures and key
   events disabled; nothing read outside a focus session.
4. **Collected, stored or shared** — nothing collected, logged, stored or
   shared; no account/analytics/ad SDK/server/internet permission.
5. **Your control** — turn it off in Android settings or withdraw consent;
   either stops layer 2 immediately; every other feature keeps working.

Strings live in `res/values/strings_v2.xml` (`focus_access_*`) so the wording is
reviewable without reading Kotlin. The system toggle also shows
`focus_service_description`, which repeats the essentials.

### 6.3 Explicit actions and ordering

- **AGREE** (`Agree and open Android settings`): the consent record is written
  to DataStore **first**; only after that write completes does the app resolve
  and open `Settings.ACTION_ACCESSIBILITY_SETTINGS`. If the intent cannot be
  resolved, the user gets an explanation and stays where they are.
- **DECLINE** (`Decline`): the refusal is persisted, a short message explains
  that launcher blocking is unchanged, and the screen closes. **No system
  screen is opened.**
- Back / Close: leaves without recording an answer; the disclosure is still
  unanswered on the next visit.

### 6.4 Persisted state (separate from Android permission)

`LumaPrefs` keys (DataStore, additive only; no schema or table changes):

| Key | Meaning |
|---|---|
| `focus_access_consent_state` | `NOT_ASKED` \| `GRANTED` \| `DECLINED` |
| `focus_access_consent_version` | The disclosure version the answer refers to (`FocusAccessConsent.CURRENT_DISCLOSURE_VERSION`, currently 1) |
| `focus_access_consent_at` | When the answer was given (diagnostic only; never expires consent) |

Android's grant is read from `Settings.Secure` by `FocusAccess.isGranted` and is
never written by the app. The two facts are combined only in
`FocusAccessConsent.uiState` (status surfaces) and
`mayEnforce`/`grantsAccess` (enforcement):

| Consent record | Android grant | UI state | Enforces? |
|---|---|---|---|
| NOT_ASKED | off | NEEDS_DISCLOSURE | No |
| NOT_ASKED | **on** (enabled directly in system settings) | NEEDS_DISCLOSURE | **No** |
| GRANTED (current version) | on | ACTIVE | Yes |
| GRANTED (current version) | off / service disabled / revoked | AWAITING_PERMISSION | No |
| DECLINED | off | DECLINED | No |
| DECLINED | on | DECLINED | **No** |
| GRANTED (older version) | on | NEEDS_DISCLOSURE | No |

Consequences, all covered by tests:

- Enabling the service in Android settings without the in-app disclosure does
  **not** make LumaOS Pro enforce. The disclosure must still be answered.
- Declining does not block anything else: Focus Protection's launcher
  interception continues, and no other feature is conditioned on this grant.
- Revoking in Android settings (or the service being disabled) turns
  enforcement off immediately and does **not** erase consent, so re-enabling
  needs no second disclosure.
- Withdrawing consent in-app stops enforcement immediately **even if the
  system grant is still on**; the screen then tells the user how to turn the
  service off in Android settings.
- Bumping `CURRENT_DISCLOSURE_VERSION` (only when the meaning of the agreement
  changes) invalidates older answers and re-asks once.
- Upgrading from a build that predates this consent record: if the service was
  already enabled in system settings, it stays bound but inactive until the
  user accepts the disclosure once. The Settings row shows this state; layer 1
  is unaffected.
- Consent keys are deliberately excluded from LumaOS Pro's own backup export
  (`BackupCodec` whitelist). Android's system backup copies the settings
  DataStore as a whole, so a restored GRANTED record can exist on a new device;
  it is honoured there only after the user enables the service in system
  settings, and a restored DECLINED record keeps stronger blocking off.

### 6.5 Revisiting and revoking

The disclosure screen doubles as the control surface:

- When consent is recorded it shows the current status and two actions:
  **Open Android accessibility settings** (the only place the grant can be
  switched off; the app cannot do that for the user) and **Withdraw consent**.
- When consent was declined it shows the same disclosure text plus Agree and
  Close — review remains possible, but nothing nags.
- Whenever Android holds a grant (including a grant enabled directly in system
  settings with no consent, or one left on after withdrawal) the screen also
  shows **Open Android accessibility settings**, so an unwanted switch-off can
  always be reached without first agreeing.
- The Privacy row is always visible, so the screen is reachable even outside
  Work/Study editing.

### 6.6 Service-side enforcement of consent

`FocusAccessibilityService.evaluate` returns immediately unless the stored
record is a current `GRANTED`. `FocusRestrictionActivity` (layer 2's screen)
applies the same check on start and on resume, so a withdrawal retires a
restriction screen that is already showing.

## 7. Play policy mapping (as implemented, to be verified)

| Policy expectation | Implementation |
|---|---|
| Accessibility used for a disclosed, user-facing purpose | Foreground enforcement of the user's own focus restrictions; stated in the disclosure, the service description, and the docs |
| Prominent disclosure shown **in the app** before the permission request | `FocusAccessDisclosureActivity`, full-screen, opened from two explicit entry points |
| Disclosure explains what is accessed and why | Sections "Why this access is needed", "How it helps Focus Protection", "What is accessed" |
| Disclosure explains collection, use and sharing | Section "Collected, stored or shared": nothing collected, stored or shared; no network permission |
| Explicit affirmative consent; no pre-selected or coerced choice | Two equally reachable buttons, AGREE and DECLINE; nothing auto-advances |
| Permission requested only after explicit consent | Settings screen is opened only from the AGREE path, after the consent write; verified by unit test and code ordering |
| Consent recorded and respected; decline does not break the app | Consent persisted with a version; DECLINED keeps layer 2 off and layer 1 intact |
| Access is revocable and the path is explained | Manage-in-settings action + withdraw consent + system description; service stops on either |
| Minimum necessary scope | One event type, one field, no content retrieval, no gestures, no key filtering, no storage, no transmission |
| No misrepresentation of capabilities | Limitations documented in §5 and `FOCUS_ENFORCEMENT.md`; strings avoid claiming in-app blocking |

## 8. What still has to be verified before any compliance claim

Run these on a real build and device; the sandbox that produced this change had
no Android SDK, so lint/Robolectric/device steps below are **unverified**:

1. `./build.sh test` — full JVM + Robolectric suite (see §9 for the new tests).
2. `./build.sh lint` — no new lint errors; check `UnusedResources` does not
   flag the new strings.
3. Fresh install, first run: Settings → Work → Stronger blocking shows the
   disclosure, not a system screen. Decline → no system screen, launcher
   blocking still works. Agree → DataStore write then system screen.
4. Relaunch after Agree: row reads "Consent given — enable access in Android
   settings"; after enabling in system settings it reads "On".
5. Enable the service directly in Android settings **without** the in-app
   disclosure: row reads "Off — only launches from LumaOS Pro are blocked",
   and no intervention happens.
6. Revoke the grant in Android settings: row reads "Consent given — enable
   access in Android settings" on return; no re-prompt; layer 1 blocks.
7. Withdraw consent while the grant is on: no intervention occurs; the toast
   tells the user to switch the service off.
8. Check `adb logcat` during enforcement: no package names or screen content in
   LumaOS logs (one intervention line per event, package-free).
9. Play Console: no new data-safety declaration is expected from this feature;
   if the Accessibility declaration form is required, answer it from §3–§4.

## 9. Automated tests added or updated

| Test | Covers |
|---|---|
| `context/FocusAccessConsentTest` (JVM) | First-time disclosure, acceptance, refusal, relaunch, revocation, service disabled, enabling without consent, withdrawal, disclosure version bump, tolerant parsing, layer 1 independence |
| `core/prefs/FocusAccessConsentPrefsTest` (JVM/DataStore) | Persistence across relaunch, refusal not overwritten by other writes, withdrawal, stale version round-trip, backup exclusion, separation from Focus settings |
| `robolectric/FocusAccessConsentRuntimeTest` | Consent + real `Settings.Secure` grant: grant-without-consent, consent-without-grant, revocation, framework disabled, layer 1 with declined consent |
| `robolectric/FocusAccessDisclosureScreenTest` (Compose) | Prominent sections rendered, AGREE/DECLINE reachable on first run, declined state, manage/withdraw state, status derivation |
| `context/FocusAccessTest` | Existing settings-list parsing (unchanged) |
| `robolectric/FocusAccessRuntimeTest` | Existing grant detection (unchanged) |

Execution status in the environment that produced this change: the pure-JVM
`FocusAccessConsentTest` was compiled and run (13/13 passed) with a standalone
`kotlinc` 2.2.21 and a minimal JUnit shim, because Maven Central is unreachable
here. The harness substitutes only `ContextEngine.focusActive` — copied exactly
from source, and Android-dependent — so `FocusEnforcement` itself is the real
implementation. The DataStore, Robolectric and Compose tests require the Android
Gradle toolchain, which could not be provisioned here; they are part of the
change and must be run before release.

## 10. Files changed by this work

See the change summary in `CHANGELOG.md` (Unreleased). The manifest change is
one unexported activity declaration; no permission was added.
