# Hide Apps → Vault, and the Vault PIN hand-off (2026-11-07)

**Scope:** two changes to the code already in this repository — moving the launcher-level
Hide Apps feature into the Vault, and fixing the "correct PIN bounces you back to the
launcher" crash. No rebuild, no new dependency, no schema change, no loss of Vault data.

**Evidence standard:** a statement is *verified* only if this repository's own test suite
exercises it and passed when run for this report. Everything that needs a phone, Gradle,
lint, R8 or an APK is marked **not run** in §5. Nothing here is called complete because it
compiles.

**Headline:** the PIN defect had **three** independent causes, not one (§2). Hide Apps is
now a Vault section with an additive, idempotent migration of the old selection (§3). This
round adds 55 tests to the 126 that existed: **180 passing, 0 failing**, plus a 34-source
type-check against a real `android-36/android.jar`. Gradle, lint, R8, the debug APK and
every on-device check **could not be run** in this offline sandbox (§5) — none of them are
reported as passing.

---

## 1. What was read before anything was changed

| Area | Files |
|---|---|
| Vault core | `vault/core/*` (crypto, keyring, metadata store, object store, session, unlock guard, models, backup, screen presence) |
| Vault platform | `vault/platform/VaultManager.kt`, `VaultLifecycleGuard.kt`, `VaultDeviceKeyGuard.kt` |
| Vault UI | all 15 files under `vault/ui` |
| Launcher path | `launcher/HomeActivity.kt`, `launcher/LauncherViewModel.kt`, `launcher/model/*` |
| Preferences | `core/prefs/LumaPrefs.kt` (hidden apps, drawer, backup snapshot) |
| Settings | `settings/SettingsScreen.kt`, `SettingsViewModel.kt`, `SettingsActivity.kt` |
| Backup | `backup/BackupCodec.kt` (which preference keys a backup carries) |
| Config | `AndroidManifest.xml`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` |

The existing implementation was **extended**, not replaced: `VaultManager`, `VaultIndex`,
`VaultSession`, `VaultLifecycleGuard`, the key architecture (PBKDF2 + AES-GCM wrapped VMK),
the recovery key, backups and every existing store are unchanged in format.

---

## 2. The PIN defect: three separate causes

Tracing PIN submission → verification → session → navigation found three defects, each of
which alone could look like "the app crashed / threw me out". All three are fixed.

### D-01 — Correct PIN re-locked the Vault (the actual "bounces back" bug)

`VaultScreenActivity.onVaultLocked` used to call `finish()` and `startActivity()` in that
order, and `VaultLockActivity.openHome` likewise. `finish()` is immediate; `startActivity()`
is not. For one frame the process had **zero attached Vault screens**, and
`VaultLifecycleGuard.onActivityStopped` read that as "the user left the app". With
lock-on-leave enabled it called `lock()`, so the vault was re-locked the instant the PIN had
been accepted and the user was sent back to the lock screen. Nothing was wrong with the PIN
verification at all.

*Fix:* `vault/core/VaultScreenPresence.kt` — a pure policy object that treats a stop as
*provisional* for 400 ms and cancels it when the next Vault screen starts in that window.
The guard posts `settleConceal` and `reset()`s only on a genuine departure. The ordering is
also corrected (start the next screen, then finish). Tests:
`VaultPresenceTests` (9) — including `unlockSurvivesTheHandoffWithLockOnLeaveEnabled`,
`lockOnLeaveStillLocksOnAGenuineDeparture`, `screenOffLocksImmediatelyAndIndependentlyOfPresence`.

*Known residual:* a 400 ms window is a heuristic, not a platform guarantee. It is bounded
and only affects concealment (screen-off and the idle timeout are independent of it).

### D-02 — `finishAffinity()` finished the launcher's task

`VaultHomeActivity.lockAndReturn()` and `VaultSecurityActivity` called `finishAffinity()`.
The Vault shares the launcher's task affinity, so this finished the **launcher's** task too
and dropped the user to the system home screen — "the app closed itself". `finishAffinity()`
is no longer **called** anywhere under `vault/`; the only remaining mentions are the three
comments that explain why it must not be (verified with
`grep -rn "finishAffinity()" .../vault | grep -v '\*'`, which returns nothing).

*Fix:* `vault/ui/HomeLaunch.kt` resolves the platform HOME intent (`ACTION_MAIN` +
`CATEGORY_HOME` + `CLEAR_TOP|SINGLE_TOP`) with a `resolveActivity` guard. It lives in the
Vault package on purpose: the Vault must not import `launcher.*` (the dependency runs the
other way — the launcher reads hidden apps from the Vault).

### D-03 — Wrong recovery key crashed

`VaultLockActivity.showMessage()` used `messageView`, a `lateinit` property that only
`buildPinPad()` creates. The recovery-key screen does not call `buildPinPad()`, so a wrong
recovery key threw `UninitializedPropertyAccessException` — a genuine crash on an error
path, and exactly the "clear error, no crash" case the brief asked for.

*Fix:* `::messageView.isInitialized` guard with a fallback message view added to the body,
plus a `recoveryCheckInFlight` flag so a double tap cannot queue two checks.

Also corrected while in this path: `openHome()` now reports a failed `startActivity`
instead of finishing silently, and consumes its destination extra so a later lock/unlock
does not navigate away again.

---

## 3. Hide Apps moved into the Vault

### 3.1 Storage

`VaultSettings.hiddenApps: List<HiddenApp>` inside `meta.bin`, encrypted under the metadata
key derived from the master key. Each entry carries `pkg`, `cls`, `user`, `label`, `at` —
and nothing else. `hiddenComponentKeys()` returns `serial:pkg/cls` strings and is **empty
while the vault is locked**, which is why a locked device shows every app.

### 3.2 Launcher

`LauncherViewModel` maps `VaultManager.hiddenAppsRevision` to
`HiddenAppKeys.toComponentKeys(...)`. The private `toComponentKeys()` extension that read
the old preference is deleted. Hidden apps are excluded from the drawer, the category
chips, suggestions, folders **and search**; the `hiddenInSearch` opt-in is gone, so search
can never reveal a hidden app.

### 3.3 Migration (additive, idempotent, never destructive)

`HiddenAppMigration.fromLegacy` converts the raw `hidden_apps_<profile>` strings;
`VaultManager.importLegacyHiddenApps` adds only what is missing; a
`launch.migrated` marker outside the encrypted index records that it ran. The legacy
preference is **never deleted**, and an unread preference (`null`) is *not* treated as
"nothing hidden" — the migration simply does not mark itself done.

Tests: `HiddenAppMigrationTests` (9), `VaultLegacyHiddenAppsMigrationTests` (9),
`VaultHiddenAppsTests` (13), `HiddenAppKeysTests` (5), `VaultDestinationTests` (4),
`VaultHiddenAppsScreenTests` (5, static: manifest non-exported, no uninstall/disable/clear
APIs, no logging, honest copy).

### 3.4 Entry points removed / redirected

| Was | Now |
|---|---|
| Settings → "Manage hidden apps" dialog | one row: "Hidden Apps moved into the Vault" → Vault |
| Settings → "Find hidden apps in search" | removed (inert; it could only defeat hiding) |
| Settings → "Protect hidden apps with screen lock" | removed; the Vault's own PIN is the lock |
| Drawer long-press → "Hide in <profile>" (wrote prefs) | "Hide apps in the Vault" → Vault (already unlocked ⇒ straight to the section; locked ⇒ PIN, then the section) |
| `SettingsViewModel.setAppHidden`, `LumaPrefs.setAppHidden` | deleted — nothing writes the old key any more |
| `SettingsViewModel.apps/appsLoading/appsFailed` + its app scan | deleted — the list existed only for that dialog |

`VaultLockActivity.EXTRA_DESTINATION` carries the post-unlock destination; unknown values
fall back to the Vault home (`VaultDestination`, tested).

### 3.5 What the UI says, and does not

The screen states, verbatim, that hidden means **hidden from the LumaOS Pro launcher**;
that the app is not uninstalled, disabled or cleared, keeps running, keeps its Android
data, and that any other launcher or system settings can still open it; and that this is
**not** the Android work-profile isolation feature. A test fails the build if that copy,
the non-exported manifest entry, or the "do not uninstall/disable/suspend/force-stop/clear"
property is lost.

---

## 4. Verification actually executed

All commands were run from the repository root in this sandbox, against the real sources.

| # | Command | Result |
|---|---|---|
| 1 | `bash /home/user/harness/check.sh` (Kotlin 2.2.21 against real `android-36/android.jar`) | **34 Vault sources, 0 errors** |
| 2 | `bash /home/user/harness/test.sh` (JVM) | **180 passing, 0 failing** |
| 3 | `bash /home/user/harness/test.sh VaultPresenceTests` | 9 passing, 0 failing |
| 4 | `bash /home/user/harness/test.sh VaultHiddenAppsTests` | 13 passing, 0 failing |
| 5 | `bash /home/user/harness/test.sh VaultLegacyHiddenAppsMigrationTests` | 9 passing, 0 failing |
| 6 | `bash /home/user/harness/test.sh HiddenAppMigrationTests` | 9 passing, 0 failing |
| 7 | `bash /home/user/harness/test.sh VaultDestinationTests` | 4 passing, 0 failing |
| 8 | `bash /home/user/harness/test.sh VaultHiddenAppsScreenTests` | 5 passing, 0 failing |
| 9 | `bash /home/user/harness/test.sh HiddenAppKeysTests` | 5 passing, 0 failing |
| 10 | `grep -rn "finishAffinity()" app/src/main/java/com/lumaos/pro/launcher/vault \| grep -v '\*'` | no matches (3 comment-only mentions remain, by design) |
| 11 | `grep -rn "hidden_manage\|pref_hidden_search\|pref_hidden_lock\|setAppHidden" app/src` | no matches |

The 126 tests that existed before this change still pass unchanged; the totals above are
the whole suite for the packages the sandbox can build.

## 5. Not run — blocked, and reported as blocked

| Required check | Why it could not run |
|---|---|
| `./build.sh assembleDebug` | `services.gradle.org`, `dl.google.com`, `repo1.maven.org` are unreachable; Gradle cannot resolve a single dependency |
| `./build.sh test` (Gradle unit/Android tests) | same; the AndroidX and Robolectric suites cannot be compiled offline |
| `./build.sh lint` | no Android SDK manager or lint jar available |
| R8 / minified release build | needs Gradle + AGP |
| Install and unlock on a real device | no device, no emulator image |
| Motion/UX check of the new screen on hardware | no device |
| Robolectric tests of `HomeActivity`/`SettingsScreen` | AndroidX unreachable |

Consequently, the following edits were **reviewed by reading, not compiled**: everything
under `launcher/`, `settings/` and the Compose UI (AndroidX imports). They are listed here
so no reader mistakes "the Vault package compiles" for "the app was built".

## 6. Residual risks / honesty notes

1. **Hiding is launcher-level.** Not isolation, not encryption of the app's own data, not
   protection against another launcher, ADB or Android settings. Stated in the UI and in
   `docs/VAULT.md` §7.1.
2. **A locked Vault shows every app in the drawer.** The hidden set is only readable while
   unlocked. This is deliberate (the alternative is a plaintext copy), and it means a
   device rebooted into a locked Vault displays the apps until the first unlock — including
   the post-upgrade case, where the legacy list is imported on that first unlock.
3. **The 400 ms hand-off window** in `VaultScreenPresence` is a heuristic; screen-off and
   idle timeout lock independently of it.
4. **`launch.migrated` is not encrypted** — it names a migration, never an app or a key.
5. **Deleting `guard.json` still resets the retry delay** (unchanged; it never bypasses PIN
   verification).
6. Existing Vault residuals from the previous report are unchanged: no guaranteed
   flash-level erasure, no full-memory-wipe claim, `meta.bin.bak` may retain a wrapped key
   of an item deleted before the remediation commit, and the clipboard sensitivity flag is
   a hint that is ignored below API 33.

## 7. Files touched

New: `vault/core/VaultScreenPresence.kt`, `vault/core/VaultDestination.kt`,
`vault/ui/HomeLaunch.kt`, `vault/ui/VaultHiddenAppsActivity.kt`,
`launcher/model/HiddenAppKeys.kt`, `launcher/model/HiddenAppMigration.kt`, and six test
files. Modified: `VaultManager`, `VaultPaths`, `VaultLifecycleGuard`, `VaultLockActivity`,
`VaultScreenActivity`, `VaultHomeActivity`, `VaultSecurityActivity`, `VaultIcons`,
`LauncherViewModel`, `HomeActivity`, `LumaCore`, `LumaPrefs`, `SettingsScreen`,
`SettingsViewModel`, `SettingsActivity`, `BackupCodec` (comment only), `AndroidManifest.xml`,
`strings_v2.xml`, `CHANGELOG.md`, `docs/VAULT.md`, `docs/VAULT_SECURITY_AUDIT.md`.
