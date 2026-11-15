# First-run onboarding and the "Getting started" tour

LumaOS Pro has a seven-step first-run walkthrough and the same flow is
replayable from **Settings → Getting started**. It is built on the onboarding
activity that already existed; no launcher surface, database schema, preference
key or Focus Protection rule was rebuilt.

The one durable fact is the existing `onboarding_done` preference
(`LumaPrefs.onboardingDone`). There is no second "has seen onboarding" flag, no
"onboarding version" in storage, and no reset path: a replay simply walks the
same screens again over the user's current settings.

## Files

| File | Role |
| --- | --- |
| `onboarding/OnboardingFlow.kt` | The seven steps, forward/back navigation and the skip rule. Pure Kotlin, unit tested. |
| `onboarding/OnboardingScreens.kt` | The step composables and the shared shell (progress, back, one scroll column). |
| `onboarding/OnboardingActivity.kt` | State owner: step, Home role, accessibility consent, persistence, system-settings hand-offs. |
| `core/util/OnboardingEligibility.kt` | Launch gate: first installation only, never for an updated install. Pure Kotlin, unit tested. |
| `launcher/HomeActivity.kt` | `maybeLaunchOnboarding()` calls the gate; the existing `onboardingLaunchInFlight` guard and result contract are unchanged. |
| `settings/SettingsScreen.kt`, `settings/SettingsActivity.kt` | "Getting started → Replay the tour" entry point. |
| `context/FocusAccessDisclosureScreen.kt` | The disclosure body is now a reusable `FocusAccessDisclosureContent`; the standalone screen and onboarding step 5 render the *same* text and actions. |
| `settings/GestureLabels.kt` | Gesture names shared by Settings and step 2, so the tour can print live bindings. |

## The seven steps

| # | Step | What the user sees | Optional |
| --- | --- | --- | --- |
| 1 | `WELCOME` | App icon, "LumaOS Pro", the one-line promise, three real capability points and the tour controls. | No (leaving it completes onboarding) |
| 2 | `GESTURES` | The **current** gesture bindings, read from `LumaPrefs.gesture(...)` and rendered with the same labels as Settings → Gestures. | Yes |
| 3 | `PERSONALIZE` | Real Living Icons from this install's renderer, the three `ExperienceProfile` cards (each writes through `applyExperienceProfile`), and a button that opens Theme Studio. | Yes |
| 4 | `FOCUS_PROTECTION` | What launcher blocking does today and what the optional stronger layer adds. Nothing is toggled or requested on this screen. | Yes |
| 5 | `ACCESSIBILITY` | The prominent disclosure, with explicit **Agree** and **Decline**, plus manage/withdraw when a decision exists. | Yes |
| 6 | `HOME_ROLE` | The Home-role explanation and the real `RoleHelper` request; status is re-read from Android. | No |
| 7 | `COMPLETE` | Setup summary and "Enter LumaOS Pro". | No |

Steps 2–5 can be skipped, and the welcome screen offers the same escape. Skipping
never skips the Home-role question: "skip the rest of the tour" always lands on
step 6. Back from step 1 leaves onboarding and records completion, which is the
pre-existing behaviour.

## Persistence and launch rules

- **First installation only.** `HomeActivity.maybeLaunchOnboarding()` starts the
  walkthrough only when all of these hold: not already the default Home app,
  `onboardingDone == false`, and this package has not been updated since it was
  installed (`PackageInfo.firstInstallTime` vs `lastUpdateTime`, with a 60 s
  slack for installer skew).
- **Upgrades are not first runs.** An existing installation that receives an
  update is never walked through setup; the flag is set so a later Home-role
  change cannot bring the tour back. The tour stays available on demand from
  Settings.
- **Never repeated on normal launches.** Reaching step 7, pressing back out of
  step 1, or requesting the Home role writes `onboarding_done = true`. The
  launcher only re-checks the flag; it never launches onboarding again once it
  is true.
- **No reset on replay.** Replay uses the same `Set as default`/profile calls as
  Settings. Nothing is cleared; a replay starts from whatever the user has now.
- `onboarding_done` is part of the existing DataStore backup/restore set, so a
  restored install keeps its completion state.

## Accessibility consent in step 5

Step 5 is not a second disclosure. It renders `FocusAccessDisclosureContent`,
the same composable the standalone `FocusAccessDisclosureActivity` uses, so the
wording and the Agree/Decline actions cannot drift between the two surfaces.

- No accessibility screen is opened from onboarding without an explicit Agree.
- Agree persists a `FocusAccessConsentRecord` first, then opens Android's
  accessibility settings only if the grant is not already present.
- Decline is persisted and the step advances to the Home-role question. Layer 1
  (launcher interception) is unaffected.
- "Skip for now" advances without any consent and without touching the system
  permission.
- On return to the activity, the grant is re-read from `Settings.Secure`, so a
  revocation shows as "awaiting permission", never as active.

The complete data flow, and the facts the disclosure is derived from, are in
[`ACCESSIBILITY_DISCLOSURE.md`](ACCESSIBILITY_DISCLOSURE.md).

## Design, responsiveness and accessibility

- One vertical scroll column per screen; no nested scrolling. Every step is
  usable at 200 % font scale and in short landscape windows.
- Content is capped at 520 dp and centred, so tablets and foldables do not get
  full-width line lengths; the background and safe-drawing insets come from the
  same `theme.colors` and `WindowInsets.safeDrawing` as the rest of the app.
- The progress indicator exposes a single content description ("Step 3 of 7").
- Profile choices expose `Role.RadioButton` and the selected state; each gesture
  row merges its label and binding into one announcement.
- Buttons are the app's standard glass controls with a 52 dp minimum height, so
  the touch target is not reduced by the layout.
- No timers, no auto-advance and no auto-focus: screen readers are never moved
  by the tour itself.

## Test matrix

| Requested scenario | Covered by |
| --- | --- |
| Fresh install | `OnboardingFlowTest` (starts at Welcome), `OnboardingEligibilityTest` (offered when not done, not default, not an upgrade), existing `HomeActivityRuntimeTest` onboarding launch test |
| Onboarding completion | `OnboardingFlowTest` (7 steps, terminal final step), `OnboardingCompletionPrefsTest` (flag persists across a new DataStore instance) |
| Onboarding skip | `OnboardingFlowTest` (every tutorial step skips to the Home role; non-tutorial steps are no-ops) |
| Relaunch after completion | `OnboardingCompletionPrefsTest`, `OnboardingEligibilityTest` (never offered again) |
| Upgrade from an existing install | `OnboardingEligibilityTest` (updated install is not offered; completion is recorded implicitly) |
| Accessibility consent and refusal | `FocusAccessConsentTest`, `FocusAccessDisclosureScreenTest`, `OnboardingScreenTest` (Agree and Decline reachable, Continue absent before an answer) |
| Accessibility permission revocation | `FocusAccessConsentTest` (`uiState` becomes awaiting after revoke), `FocusAccessDisclosureScreenTest` |
| Getting started replay | `OnboardingFlowTest` (replay mode), `OnboardingReplayIntentTest` (intent carries only the mode), `OnboardingScreenTest` (replay note) |
| Default launcher setup | `OnboardingScreenTest` (pending vs. default Home-role states), `OnboardingEligibilityTest` |
| Existing launcher regression suite | Unchanged test sources; no existing test needed a rename or a behaviour change |

Executed in this workspace: `OnboardingFlowTest` (13), `OnboardingEligibilityTest`
(8) — 21/21 pass with the standalone Kotlin compiler. The DataStore, Robolectric
and Compose tests are written to the repository's conventions but require Gradle
and the Android toolchain, which are unavailable here; see
[`QA_CHECKLIST.md`](QA_CHECKLIST.md) for the exact verification status.

## Verification limits

No Gradle, Android SDK, emulator or device was available in this workspace, so:

- the app was not assembled, linted or instrumented here;
- Compose behaviour is verified by source-level tests, not by running them;
- the images in `docs/screenshots/onboarding/` are static design renderings
  generated from the real strings and layout values, **not** device captures;
- a TalkBack pass on a device is still required before any store submission,
  and no Google Play compliance claim is made.
