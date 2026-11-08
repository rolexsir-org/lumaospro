# Three critical Vault fixes — November 2026

Scope: three defects reported against the Vault after the Hide Apps unification. None of
them is a redesign. Each is a defect in shipped behaviour, fixed at its cause, with the
existing encryption, storage formats and state management left alone.

| # | Issue | Root cause | Status |
|---|---|---|---|
| 1 | Hidden apps visible after reboot | The hidden set existed only in the encrypted index, which is unreadable without the master key | Fixed |
| 2 | 400 ms hand-off heuristic | The conceal decision inferred "the user left" from a screen count plus a timer | Fixed |
| 3 | `guard.json` deletable | The retry record had no integrity check and no witness that it had ever existed | Fixed |

Verification is in §6. Anything that could not be executed here is marked **NOT RUN**
rather than assumed.

---

## 1. Hidden apps were visible after a reboot

### 1.1 Root cause

`VaultManager.hiddenComponentKeys()` reads `VaultIndex.settings.hiddenApps`, which lives in
`meta.bin` — encrypted under a key derived from the vault master key. After a reboot, and
any time the vault is locked, **there is no master key**, so the function correctly returns
an empty set. The drawer then filters against the empty set and renders every hidden app.

This was not an intermittent bug. The drawer was correct only while the vault happened to be
unlocked. Everything else — boot, lock, timeout, screen-off, and the entire window between
upgrading and the first unlock — showed the full app list.

### 1.2 Fix

A **device-bound concealment index** (`conceal.bin`) that the launcher can read before
authentication.

* `VaultConcealment` (core, pure Kotlin) — format, validation and the reconcile rules. The
  payload is versioned JSON: `format`, `vaultId`, and rows of `pkg`/`cls`/`user`/`label`.
  Rows are sorted and de-duplicated, so an unchanged selection serialises identically.
* `VaultConcealmentStore` (platform) — seals the payload with AES-256-GCM under a
  non-exportable Android Keystore key (`lumaos.vault.conceal`). Layout is
  `[version][12-byte IV][ciphertext + tag]`; the vault id is bound in as associated data, so
  a mirror from another vault or another install fails authentication instead of hiding the
  wrong apps.
* `VaultManager` — `concealedComponentKeys()` and `concealmentState()` read it;
  `publishHiddenApps()` rewrites it on every hide, restore and legacy import;
  `completeUnlock()` rebuilds it from the authoritative index on every unlock.
* `LauncherViewModel` — the drawer's hidden set now comes from the index when unlocked and
  from the mirror otherwise, seeded synchronously so no frame can render a hidden app.

The encrypted index remains the single source of truth. The mirror is derived, never
authoritative, and is repaired from the index on every unlock — including for a vault
upgraded from a build that had no mirror at all.

### 1.3 Failure modes, each with a defined answer

| Situation | Behaviour |
|---|---|
| No mirror, vault has never hidden anything | Empty set; a clean first run, not an error |
| Mirror unreadable, backup readable | Previous generation is used; logged |
| Both generations unreadable | Reported as **degraded**, never as "nothing is hidden"; rebuilt at the next unlock |
| Write interrupted | Atomic temp-file + `fsync` + rename; the reader sees the old file or the new one |
| Keystore key gone (restore to a new device) | Degraded, then rebuilt on the next unlock |
| Vault locked / rebooted | Mirror is read normally — this is the case it exists for |
| Vault destroyed and re-created | Mirror is cleared on creation |

### 1.4 What this does not claim

The mirror is not a plaintext copy: it cannot be read from an ADB pull, a bug report, another
app's view of the directory, or a copy of the disk image. It is **not hidden from the device
itself**. The key deliberately has no `setUserAuthenticationRequired` and, equally
deliberately, **no `setUnlockedDeviceRequired`** — requiring the first unlock after boot would
reintroduce the original bug, because the drawer is drawn before that unlock. Anyone who can
execute code as LumaOS Pro can therefore ask the Keystore to open it. That is stated in
`docs/VAULT.md` §7.1 rather than implied otherwise.

---

## 2. The 400 ms hand-off heuristic

### 2.1 Root cause

Deciding "did the user leave?" from an attached-screen count is inherently racy, because
`startActivity()` is asynchronous: the outgoing screen's `onStop` can be observed before the
incoming screen's `onStart`. The previous fix treated a drop to zero as *provisional* for
400 ms and cancelled it if another Vault screen attached in the meantime.

That worked, but it was a guess: the window had to be longer than the slowest device's
transition and shorter than an acceptable shoulder-surfing gap, and the settle callback ran on
the `Handler` uptime clock while the window was measured against `System.nanoTime` — two
clocks that had to stay in agreement. A timer is a knob, not a proof.

### 2.2 Fix

`VaultScreenPresence` no longer contains a clock, a window, or a pending callback. It tracks
each Vault activity's stage:

```
CREATED -> STARTED -> STOPPED -> (destroyed)
```

and a stop is a **real departure** only when, after it, no other Vault activity is `CREATED`
(a successor is materialising) and none is `STARTED` (a screen is still visible). This is
deterministic because Android always creates a successor before its predecessor is stopped —
a platform ordering guarantee, not a timing assumption.

`VaultLifecycleGuard` now conceals **synchronously** inside `onActivityStopped`, passing
`activity.isChangingConfigurations` so a rotation is never mistaken for a departure.

### 2.3 The cases that decided the design

| Transition | Result | Why |
|---|---|---|
| Lock screen → home after a correct PIN | No conceal | Home is already `CREATED` when the lock screen stops |
| Home → gallery → back to home | No conceal | Gallery stops while home is `STARTED` |
| **Deep screen → press Home** | Conceal | The gallery stops; the parent behind it is alive but `STOPPED`, so nothing is visible |
| Last screen finished | Conceal | Nothing is alive |
| Rotation / configuration change | No conceal | `isChangingConfigurations` |
| Screen off | Lock immediately | Separate path, unchanged, no grace period |
| Idle timeout | Lock | Separate path, unchanged |

That third row is the case a screen *count* cannot express, and it is why the class holds a
map instead of an integer. With lock-on-leave, pressing Home from a deep screen previously
left the vault open because the parent activity was still alive.

### 2.4 Preserved without change

Lock-on-leave, the idle timeout, immediate screen-off locking, `FLAG_SECURE`, the
lock-screen redirect on resume, and the ordering in `VaultLockActivity.openHome()` (start the
next screen, then finish) — that ordering is still correct, just no longer load-bearing.

### 2.5 Enumeration required before the change

Every `startActivity`/`finish` site in all twelve `vault/ui` files was inspected first. All
Vault screens extend `VaultScreenActivity` and are registered with the process-wide
`VaultLifecycleGuard`, so the new per-activity tracking covers every transition without
per-screen changes. The only navigation patterns present are: base-class
`openLockScreen()`/`returnToLauncher()`, `open()` in the home screen, viewer/gallery pushes,
`startActivityForResult` for SAF import/export, and `finish()` on close/back — all covered.

---

## 3. `guard.json` could be reset by deleting it

### 3.1 Root cause

The retry record must be readable *before* the vault is unlocked, so it cannot be protected
with the master key or anything derived from the PIN. It was plain JSON with no integrity
check, and a missing file was indistinguishable from a first run. Editing it or deleting it
between attempts reset the progressive delay. This was documented as a known limitation
because "a file readable before unlock cannot be authenticated against that same attacker" —
which is true of the *vault's* keys, but not of a key that lives outside the vault directory.

### 3.2 Fix

* `VaultGuardAuthenticator` (core, interface) — `isInitialised`, `ensureInitialised`, `sign`,
  `verify`.
* `VaultGuardSigner` (platform) — HMAC-SHA-256 under a Keystore key
  (`lumaos.vault.throttle`, `PURPOSE_SIGN | PURPOSE_VERIFY`). Refuses a key that exports its
  own bytes, since a software-backed key would make the tag forgeable.
* `VaultUnlockGuard` (core) — every record carries a `tag` over a length-prefixed
  `vaultId | failures | lockedUntil | lastFailure`, with a domain prefix. Decoding has four
  outcomes:

| Record | Reading |
|---|---|
| Valid tag | Obeyed, after clamping |
| Parsed but wrong tag | **Tampered** → seeded above the free attempts |
| Not parseable | **Damaged** → seeded above the free attempts (labelled damage, not tampering: a crash can truncate a file) |
| Absent, signing key exists | **Deleted** → maximum penalty **written immediately** |
| Absent, no signing key | First run → clean |

### 3.3 The deletion case in detail

This is the part that had to be right. Reporting a deletion as "suspicious" but granting a
free attempt would have made the fix cosmetic: an attacker could delete the file before every
guess and never accumulate a failure. So `state()` persists the maximum penalty the moment it
detects an authenticated vault with a missing record. The attempt that follows is refused,
and deleting again simply re-anchors the same fifteen minutes while still granting zero
attempts.

The penalty is written **once per deletion**, not on every read, so rendering the lock screen
in a loop cannot push the deadline forward and trap the owner. A successful unlock rewrites a
clean, signed record. `resetForNewVault()` is the only unconditional reset, is called from
vault creation and nowhere else, and a test asserts there is exactly one such call site.

### 3.4 Surfaced in the UI

A penalty the owner is never told about is indistinguishable from a bug. The Vault's Security
Centre now has an **Unlock attempts** section that reports which of the four states applies —
signed and valid, edited, removed, or damaged — in plain language, and explains that a
successful unlock rewrites a correct record. It is read-only.

### 3.5 Preserved without change

The delay schedule (3 free attempts, then 10 s → 30 s → 60 s → 5 min → 15 min cap), the
clamping of absurd or negative values, PIN verification, biometric unlock, recovery-key
unlock, and the guarantee that the guard holds no key material.

### 3.6 Keystore rollback-protection limitation — stated plainly

The Keystore does **not** provide rollback protection for file contents. An attacker who kept
a previously valid `guard.json` can restore it byte-for-byte and the HMAC verifies. Counter
monotonicity would need a hardware counter that Android does not expose to apps.

Likewise, an attacker who can execute code as this app — root, or a compromised OS — can ask
the Keystore for the tag, because the app must be able to compute it. And an attacker who
simply copies the encrypted vault off the device is not affected by this file at all; their
real limit is PBKDF2's cost and the PIN's entropy.

So the accurate claim is: **the retry state can no longer be reset through the filesystem,
but it is not tamper-proof against a compromised operating system.** This is recorded in
`docs/VAULT_THREAT_MODEL.md` §3.1 and `docs/VAULT.md` §7.

---

## 4. Changed files

**New — production**

| File | Purpose |
|---|---|
| `vault/core/VaultConcealment.kt` | Concealment format, validation, reconcile rules |
| `vault/core/VaultGuardAuthenticator.kt` | Signing contract for the retry record |
| `vault/platform/VaultConcealmentStore.kt` | Keystore-sealed mirror, atomic, two generations |
| `vault/platform/VaultGuardSigner.kt` | Keystore HMAC implementation |

**Modified — production**

| File | Change |
|---|---|
| `vault/core/VaultScreenPresence.kt` | Replaced the window with per-activity stage tracking |
| `vault/core/VaultUnlockGuard.kt` | Authenticated records, deletion detection, single reset path |
| `vault/platform/VaultLifecycleGuard.kt` | Synchronous conceal; created/destroyed tracking |
| `vault/platform/VaultManager.kt` | Mirror wiring, guard signer, `resetForNewVault` on creation |
| `launcher/LauncherViewModel.kt` | Drawer reads mirror before unlock |
| `vault/ui/VaultLockActivity.kt` | Comment corrected (no behaviour change) |
| `vault/ui/VaultSecurityActivity.kt` | New "Unlock attempts" section reports the record's integrity |

**New — tests**

| File | Tests |
|---|---|
| `vault/VaultConcealmentTests.kt` | 15 |
| `vault/VaultCriticalFixesTests.kt` | 13 |
| `vault/FakeGuardAuthenticator.kt` | Shared real-HMAC test double |

**Modified — tests**

| File | Change |
|---|---|
| `vault/VaultPresenceTests.kt` | Rewritten for the deterministic contract (15 tests) |
| `vault/VaultSessionTests.kt` | +9 throttle-integrity tests (19 → 28) |
| `vault/VaultPinHandoffRegressionTests.kt` | Timer guard replaces the clock guard (5 tests) |

**Docs:** `CHANGELOG.md`, `docs/VAULT.md` (§4 layout, §7 throttling, §7.1 hidden apps),
`docs/VAULT_THREAT_MODEL.md` (§3.1 rewritten), `docs/HIDDEN_APPS_AND_PIN_2026-11.md`
(residual list now marks the three as fixed).

No database schema, storage format or state-management change was required. `meta.bin`,
`keyring.json`, `objects/*.obj` and the backup format are untouched.

---

## 5. Compatibility and migration

* **Existing vaults are not migrated, rewritten or re-created.** The offline vault format is
  unchanged.
* An upgraded vault has no mirror. Its drawer therefore behaves exactly as before until the
  first unlock, which rebuilds the mirror from the index — no user action, no data loss.
* `guard.json` written by an older build has no tag. On a vault whose signing key does not yet
  exist, that record is read in the documented unprotected mode. Once the key exists, an
  untagged record reads as tampered, which is intended: after the upgrade, a record without a
  tag is not one this build wrote.
* Downgrading loses neither the hidden selection (still in `meta.bin`) nor the vault.

---

## 6. Verification

### 6.1 Executed here

| Command | Result |
|---|---|
| `bash /home/user/harness/check.sh` | **38 vault sources compiled** against android-36 |
| `bash /home/user/harness/test.sh` | **228 passing, 0 failing** |

Full per-suite run (`PASS` count per class):

| Suite | Tests | | Suite | Tests |
|---|---|---|---|---|
| `VaultSessionTests` | 28 | | `VaultStreamAeadTests` | 11 |
| `VaultKeyringTests` | 22 | | `VaultLegacyHiddenAppsMigrationTests` | 9 |
| `VaultHardeningTests` | 17 | | `VaultBackupTests` | 9 |
| `VaultStorageTests` | 16 | | `HiddenAppMigrationTests` | 9 |
| `VaultPresenceTests` | 15 | | `VaultDeletionTests` | 8 |
| `VaultCryptoTests` | 15 | | `VaultPlatformIntegrationTests` | 5 |
| `VaultConcealmentTests` | 15 | | `VaultPinHandoffRegressionTests` | 5 |
| `VaultHiddenAppsTests` | 13 | | `VaultHiddenAppsScreenTests` | 5 |
| `VaultCriticalFixesTests` | 13 | | `HiddenAppKeysTests` | 5 |
| `VaultDestinationTests` | 4 | | `VaultClipboardHygieneTests` | 4 |
| **Total** | | | **228** | |

Baseline before this work was **187 passing / 34 sources**. Delta: **+41 tests, +4 sources**.

* `VaultConcealmentTests` — 15 new
* `VaultCriticalFixesTests` — 13 new
* `VaultSessionTests` — 19 → 28 (+9: eight new throttle-integrity tests, and the
  obsolete `deletingGuardStateCannotProduceAKeyOrBypassThePin` split into two)
* `VaultPresenceTests` — 11 → 15 (+4, suite rewritten for the deterministic contract)
* `VaultPinHandoffRegressionTests` — 5 → 5, one guard replaced rather than added
* New production sources: `VaultConcealment`, `VaultGuardAuthenticator`,
  `VaultConcealmentStore`, `VaultGuardSigner`

`VaultPinHandoffRegressionTests` kept its count because the D-04 monotonic-clock guard became
obsolete when the clock itself was removed; it was replaced by a stronger "no clock, no timer"
assertion rather than kept alongside it.

### 6.2 Proof that the regressions are load-bearing

Each guard was validated by re-introducing the defect and confirming the test fails:

| Injected defect | Tests that failed |
|---|---|
| Drawer reads only the encrypted index (the reboot bug) | `theDrawerFallsBackToTheDeviceBoundMirrorBeforeUnlock` |
| Conceal posted through `Handler.postDelayed(400)` | `concealIsDecidedSynchronouslyNotScheduled` |
| Guard constructed without the signer | `theThrottleRecordIsAuthenticatedByTheKeystore` |
| Signature not verified | `anEditedRecordIsDetectedAsTampering`, `aRecordFromAnotherVaultIsRejected` |
| Missing record read as a clean slate | `deletingTheRecordDoesNotResetTheDelay` (+2) |

All were restored and the suite returned to 0 failing.

### 6.3 NOT RUN — and why

The sandbox has no Android device, emulator, or access to the Gradle/Maven repositories
(`services.gradle.org`, `dl.google.com`, `repo1.maven.org` are unreachable). The following
were **not** executed and are not claimed:

* Gradle build, lint, R8/minification, and the **release APK build** — **NOT RUN**
* Any instrumented or Robolectric test — **NOT RUN**
* Installation and use on a physical device — **NOT RUN**
* **Reboot test** (hide apps → reboot → verify the drawer before unlocking) — **NOT RUN**
* **PIN unlock, lock-on-leave, idle timeout and screen-off on device** — **NOT RUN**
* **Hide and restore round-trip on device** — **NOT RUN**
* **`guard.json` tamper/delete test on device** (edit, delete, restore an older copy) — **NOT RUN**
* **Keystore behaviour across reboot for `conceal.bin`** — **NOT RUN**

Compilation of `VaultManager`, the UI and the launcher sources is done by the harness for all
38 vault sources; `SettingsScreen`/`HomeActivity` and any Compose code cannot be compiled here,
and the `LauncherViewModel` change is verified by inspection plus the source-level guard in
`VaultCriticalFixesTests`, not by the compiler.

### 6.4 Device checklist for the reviewer

Prerequisite for every item: build and install the release APK.

1. Hide two apps. Force-stop the app. Open the drawer → both still hidden.
2. **Reboot.** Before entering the PIN, open the drawer → both still hidden.
3. Unlock with the PIN → both still hidden; the Vault's Hidden Apps list shows both.
4. Restore one → it reappears in the drawer immediately.
5. Enable lock-on-leave. Unlock, press Home, return → the Vault is locked (unchanged).
6. Enable lock-on-leave. Unlock with the correct PIN → the home screen stays (no bounce).
7. From the gallery, press Home → the Vault is locked.
8. Rotate on the lock screen and on the home screen → no lock, no bounce.
9. Screen off and on → locked immediately.
10. Enter a wrong PIN four times → 10 s delay. `adb shell run-as <pkg> rm files/../no_backup/vault/guard.json`
    → the next attempt is refused with a wait, not accepted.
11. Edit `guard.json` (change `failures`) → the next attempt is refused. Unlock successfully,
    then open **Security Centre → Unlock attempts**: the record reads *Signed and valid*
    after the rewrite. To see the tampered state itself, edit the file and observe the
    refusal and the wait; the Security screen is only reachable once the vault is open, by
    which point a successful unlock has rewritten a correct record.
12. Restore an older copy of `guard.json` → **it is accepted** (documented rollback limit).

---

## 7. Remaining limitations

1. **Rollback of `guard.json` is not prevented.** Replaying an older valid record verifies.
   Android exposes no hardware counter for this. Documented, not hidden.
2. **Root or a compromised OS defeats both new protections.** `conceal.bin` can be opened and
   the throttle tag computed by anything running as the app.
3. **`conceal.bin` is readable before the first unlock by design.** It cannot be given
   `setUnlockedDeviceRequired` without reintroducing the reboot bug.
4. **Hiding remains launcher-level.** No app is uninstalled, disabled or cleared; another
   launcher can still open a hidden app. Unchanged, and stated in the UI.
5. **No physical-device verification of any of the above** (§6.3).
6. Pre-existing residuals are unchanged: no guaranteed flash-level erasure, no full-memory-wipe
   claim, `meta.bin.bak` may retain a wrapped key for an item deleted before the earlier
   remediation, and the clipboard sensitivity flag is a hint ignored below API 33.

---

## 8. Commit

Implementation and documentation are committed together on
`arena/38e21eb1-lumaospro`. The SHA is recorded in the pull request.
