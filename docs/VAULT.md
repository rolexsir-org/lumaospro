# LumaOS Vault — implementation report

> **Pre-release security review (2026-11-07):** see
> [`VAULT_SECURITY_AUDIT.md`](VAULT_SECURITY_AUDIT.md) for the vulnerability report, the
> seven fixes made in that pass, the executed test results (105 passing) and the hardware
> checklist that is still outstanding.

**Status: source-complete for the security foundation, capture, files, notes, gallery,
recorder, scanner, backup/restore and the Security Centre. Unit-tested on the JVM
(87 tests, all passing). Not device-verified: this sandbox has no Android SDK, no
Gradle, no emulator and no access to Google Maven, so the Gradle build, lint, and any
on-device behaviour have not been run. Read the "Verification" section before
treating any of this as release-ready.**

---

## 1. What was audited first (Milestone 1)

The repository is a single-module Android launcher (`com.lumaos.pro.launcher`,
minSdk 26, targetSdk 36, AGP 8.13.2, Kotlin 2.2.21) with:

| Area | Findings relevant to Vault |
|---|---|
| UI | Home and drawer are platform **Views**; settings, Wallpaper Studio and onboarding are **Compose**. Glass surfaces live in `ui/glass`, tokens in `ui/theme/LumaTokens.kt`, fonts are bundled Inter/Space Grotesk files. |
| State | One hand-written holder, `core/LumaCore.kt`, created by `core/LumaApp.kt`; no DI framework. |
| Persistence | DataStore Preferences only (`core/prefs/LumaPrefs.kt`); SQLite is used solely by the diagnostics store. No Room, no SQLCipher. |
| Hide Apps | Formerly per-profile hidden apps in `LumaPrefs` (drawer/search filtering). **Now part of Vault**: the selection lives in the Vault's encrypted index and is only readable or editable after unlocking. Still app-visibility filtering, **not** private content storage or OS isolation. |
| Backup | Launcher settings JSON backup (`backup/BackupCodec.kt`) plus Android Auto Backup rules that already include only `sharedpref` and `datastore/`. |
| Monetisation | Theme premium state only; no billing library, nothing for Vault to disturb. |
| Tests | 74 JVM/Robolectric test classes under `app/src/test`. |
| Build | `build.sh` provisions JDK/Gradle/SDK from the public internet; release signing is driven by a gitignored `keystore.properties`. |

Integration risks identified and handled:

1. **Name collisions in resources** — Vault adds only `vault_*` / `settings_group_private`
   strings.
2. **Auto Backup** — Vault stores everything under `noBackupFilesDir`, and a unit test
   now fails the build if a backup rule ever names the vault directory.
3. **Session/process lifetime** — the launcher process is long-lived by design, so Vault
   keys must never be reachable from the home screen; the guard hooks only Vault screens.
4. **`LumaCore` growth** — Vault is one lazily-initialised field plus one lifecycle
   guard; nothing is constructed on the ordinary launcher start path beyond that.
5. **Hidden apps** — moved *into* Vault. The list is now `VaultSettings.hiddenApps` in the
   encrypted index; the old per-profile preference is imported once, additively, on the
   first unlock (see §3 "Hidden Apps") and is never deleted. Hiding remains a launcher
   filter: nothing is uninstalled, disabled or cleared.

---

## 2. Architecture

New package: `com.lumaos.pro.launcher.vault`.

```
vault/
  core/                    pure Kotlin/JVM — no android.* imports, unit-testable
    VaultCrypto.kt         CSPRNG, AES-256-GCM, HKDF-SHA-256, PBKDF2, SHA-256, zeroize
    VaultStreamAead.kt     chunked authenticated encryption for large objects
    VaultRecoveryCode.kt   256-bit recovery key  <->  checksummed, human-typable text
    VaultKeyring.kt        VMK, PIN/recovery wrappers, key file format, PIN policy
    VaultPaths.kt          storage layout + atomic file helpers
    VaultObjectStore.kt    encrypted object payloads + random-access decrypting reader
    VaultMetadataStore.kt  single encrypted, atomically committed metadata index
    VaultModels.kt         items, folders, settings, events (all inside the ciphertext)
    VaultSession.kt        lock/unlock state machine, idle timer, listeners
    VaultUnlockGuard.kt    persisted progressive retry throttling
    VaultBackup.kt         encrypted backup container writer/reader
  platform/                android.* only
    VaultDeviceKeyGuard.kt Android Keystore key for the biometric wrapper
    VaultBiometricGate.kt  platform BiometricPrompt with CryptoObject
    VaultManager.kt        the single façade: create/unlock/lock/items/backup/restore
    VaultLifecycleGuard.kt screen-off, background concealing, idle ticker
    VaultMedia.kt          in-memory thumbnails, transient PDF files, capture scratch
  ui/                      platform Views, no Compose
    VaultDesign.kt / VaultIcons.kt / VaultActions.kt
    VaultScreenActivity.kt VaultLockActivity.kt VaultHomeActivity.kt
    VaultGalleryActivity.kt VaultFilesActivity.kt VaultNotesActivity.kt
    VaultRecorderActivity.kt VaultCaptureActivity.kt (camera + scanner)
    VaultViewerActivity.kt VaultSecurityActivity.kt
```

Integration points (the only changes outside the new package):

| File | Change |
|---|---|
| `core/LumaCore.kt` | one `val vault: VaultManager`, one `VaultLifecycleGuard`, started in `start()`, locked in `shutdown()` |
| `settings/SettingsScreen.kt` | one "Private → LumaOS Vault" navigation row |
| `AndroidManifest.xml` | ten unexported, recents-excluded Vault activities; `CAMERA` and `RECORD_AUDIO` permissions; two optional hardware features |
| `res/values/strings.xml` | four strings |
| `res/xml/backup_rules.xml`, `data_extraction_rules.xml` | comments only (semantics already excluded Vault by location) |

**No dependency was added and no Gradle, DataStore, theme or backup code path was
replaced.** The original Vault work touched no existing behaviour; the follow-up change
that moved Hide Apps into the Vault did edit the launcher's hidden-app path, and that is
described in §3. Vault has zero new third-party dependencies: everything is `android.jar`, `javax.crypto` and
`java.security`. That is a deliberate cost/benefit call (see §8).

---

## 3. Cryptography actually implemented

All primitives come from the platform provider; Vault implements composition and
framing only.

### 3.1 Key hierarchy

```
VMK  = 32 random bytes from SecureRandom          (per vault, never derived from anything)
 ├── PIN wrapper       AES-256-GCM under PBKDF2-HMAC-SHA-256(PIN, salt32, N)
 ├── recovery wrapper  AES-256-GCM under HKDF-SHA-256(recovery secret, salt32, info)
 ├── biometric wrapper AES-256-GCM under a device-bound Android Keystore key
 ├── metadata key      HKDF(VMK, info="lumaos/vault/metadata/v1")
 └── object wrapper    HKDF(VMK, info="lumaos/vault/object/v1|<objectId>")
```

* Every object additionally gets its own **random 256-bit DEK**; the DEK is wrapped by
  the object wrapper key, and its IV+ciphertext live in the encrypted index. The VMK
  never encrypts bulk data.
* All wrappers are AES-256-GCM with a 96-bit random IV and a 128-bit tag, with
  associated data `"<scheme>|<vaultId>"`, so a wrapper cannot be transplanted to
  another vault or another purpose.
* HKDF is RFC 5869 (`HmacSHA256`, extract + expand), verified against the RFC's own
  test vectors (cases 1 and 3).
* PBKDF2-HMAC-SHA-256 is verified against the published vector for
  `("password","salt",1,32)`.
* Iteration counts are **calibrated per device** at vault creation
  (`calibratePbkdf2Iterations`, ~350 ms target, clamped to 120k–800k, default 210k) and
  stored in the key ring with a 100k floor on read.

### 3.2 Streaming AEAD for objects (format v1)

```
header (49 bytes, used as AAD for every chunk):
  "LVSA" | version=1 | chunkBytes(4, BE) | saltLen=32 | salt(32) | noncePrefix(7)
body:
  for i = 0..n:  AES-256-GCM(chunkKey, nonce_i, chunk_i)      // ciphertext || 16-byte tag
  chunkKey = HKDF(DEK, salt, "lumaos/vault/object-stream/v1")
  nonce_i  = noncePrefix(7) || i(uint32 BE) || finalFlag(1)
```

* 64 KiB chunks ⇒ bounded memory for arbitrary file sizes; the reader can seek, so
  `MediaPlayer` plays ciphertext through a `MediaDataSource` with no plaintext file.
* Reordering, truncation, extension, header tampering, chunk tampering and wrong-key use
  all fail authentication — each has a dedicated test.
* This framing follows the published Tink AES-GCM-HKDF streaming construction; the
  primitive is the platform's AES-GCM.

### 3.3 Recovery key

* 256 bits from the CSPRNG, base32 (Crockford alphabet) with a 10-bit SHA-256
  checksum, printed as 54 characters in groups of four.
* Parsing is forgiving of case, spaces, dashes and `O/I/L` confusions, and rejects a
  single mistyped character via the checksum.
* Wrapped with HKDF (no slow KDF needed: the secret is already 256 bits of entropy),
  independent of the PIN, of biometrics and of the device.
* Rotation rewraps the *same* VMK; old backups still need the previous recovery key,
  which the UI states explicitly.

### 3.4 PIN

* Policy: 6–12 digits; 8+ recommended; rejects all-same, sequences, and PINs built from
  fewer than three distinct digits.
* Wrong attempts are throttled by a persisted counter (free ×3, then 10 s, 30 s, 60 s,
  5 min, 15 min cap) that survives process death and force-stop.
* Changing the PIN rewrites one 32-byte wrapper; no object is re-encrypted.

---

## 4. Storage layout and data integrity

```
<noBackupFilesDir>/vault/          ← never included in Auto Backup or D2D transfer
  keyring.json                     wrapped VMKs, salts, iteration counts (no plaintext secret)
  guard.json                       failure counter + lockout deadline, Keystore-HMAC tagged
  conceal.bin / conceal.bin.bak    hidden-app index, sealed under a Keystore AES key
  meta.bin / meta.bin.bak          encrypted metadata index (names, kinds, settings, events)
  objects/<32-hex>.obj             streaming-AEAD payloads, opaque names
  staging/                         capture scratch, restore staging — wiped on lock and unlock
```

* No file in the vault carries a meaningful name, extension or folder structure.
* Index commits: temp file → `fsync` → rename, previous generation kept as `.bak`; the
  reader falls back to `.bak` when the primary copy fails authentication.
* Object writes: staging file → `fsync` → verified by decrypting both ends → rename →
  *then* the index entry is committed. Deletion removes the index entry first and
  shreds the payload second. A crash can leave an unreferenced encrypted blob (swept and
  shredded at next unlock) but never a reference to nothing, and never a damaged existing
  object.
* Deletion also **overwrites the `.bak` generation**. That copy would otherwise still hold
  the deleted object's wrapped data key, which is what actually makes a deletion
  unrecoverable (`docs/VAULT_THREAT_MODEL.md` §3.2; `VaultDeletionTests`).
* `FLAG_SECURE` on every Vault activity; no Vault content is ever logged (a unit test
  greps the Vault sources for logging of names/URIs).

---

## 5. Session and lifecycle

* `VaultSession` is pure Kotlin and unit-tested: `LOCKED / UNLOCKED / UNLOCKED_CONCEALED`,
  idle timeout (30 s–10 min, default 3 min), immediate conceal on background, optional
  lock-on-leave, unconditional lock on `ACTION_SCREEN_OFF`, silent lock on process death,
  and key zeroization inside `Keys.destroy()` on every lock.
* `VaultLifecycleGuard` (registered once by `LumaCore`) feeds it real events and ticks
  the timeout every 5 s while unlocked.
* Every Vault activity finishes and re-opens the lock screen when the session locks, and
  refuses to show content when resumed while locked.
* Locking performs **no disk writes** (it runs on the main thread from a broadcast
  receiver); the lock event is recorded with the next unlock.

---

## 6. Backup and restore

* A backup is one file: `"LVBK" | version | flags | salt | header JSON | body | SHA-256`.
  The header carries the vault id, counts, and the **recovery-wrapped** VMK. The body is
  the already-encrypted metadata index plus every encrypted payload, byte for byte.
* Therefore a backup contains only ciphertext, and the recovery key — not the PIN, not a
  Keystore key, not any server — is what reopens it.
* Writing rejects an incomplete vault (missing payloads) instead of reporting success,
  then reads the destination back and verifies the trailer digest. Only a verified
  backup moves the status to "Protected".
* Restore stages into a sibling directory, verifies the container digest and proves the
  recovered VMK against the index *before* swapping anything into place, keeps the old
  vault until the new PIN is written, then requires the recovery key and a fresh PIN.
  The restored vault has no PIN or biometric wrapper until they are created locally.
* Backup status is derived, never asserted: `Protected`, `Pending`, `Failed`,
  `Destination lost` (persisted SAF permission revoked), `Not configured`.

**Not implemented:** automatic scheduled backups. The destination permission, reminder
and status plumbing is in place, and the Security Centre offers one-tap backup, but no
`WorkManager`/`JobScheduler` job runs unattended. This is an honest gap, listed in §10.

---

## 7. Privacy verification performed here

Executed as unit tests against the real code paths (all passing):

* raw vault files are scanned for a stored plaintext marker and for the original file
  name — both absent, and object names match `[0-9a-f]{32}.obj`;
* the metadata index does not contain item names, MIME types or kinds in the clear;
* a single flipped byte in the index, in an object, in a backup body or in a backup
  trailer is rejected;
* an interrupted store leaves neither an object nor staging debris;
* an interrupted index commit recovers from `.bak`;
* two stores of identical bytes produce different ciphertext and different DEKs;
* a wrapped DEK refuses to open a different object;
* the backup container contains neither plaintext nor names, and restores on a
  "different device" (fresh directory, no Keystore) using only the recovery key;
* the Android backup rules, extraction rules and manifest are parsed and asserted: no
  rule names the vault directory, no Vault activity is exported.

### 7.1 Hidden Apps inside the Vault

Hiding an app used to be a plain launcher preference: `hidden_apps_<profile>` in
DataStore, written by a Settings dialog and a drawer long-press action, guarded
(optionally) by a `KeyguardManager` prompt. It moved into the Vault for one reason —
*which* apps someone hides is private, and a plaintext preference is readable by any
backup, bug-report or file manager.

What is stored, and where:

* one entry per hidden launcher component in `VaultSettings.hiddenApps`
  (`pkg`, `cls`, `user`, `label`, `at`), inside `meta.bin`, encrypted under the
  metadata key derived from the master key. `at` is a timestamp, `label` is the
  display label the launcher already shows;
* nothing about the application itself — no data path, no state, no content.

What the launcher can see depends on whether the Vault is open, and both answers come from
the Vault:

* **unlocked** — `VaultManager.hiddenComponentKeys()`, the authoritative set from the
  encrypted index;
* **locked, or before the first unlock after a reboot** — `concealedComponentKeys()`, which
  reads `conceal.bin`: the same selection, re-encoded as `pkg`/`cls`/`user`/`label`, sealed
  under an AES-256-GCM key held in the Android Keystore that the app cannot export.

The mirror exists because the index cannot be read without the master key, and without a
pre-unlock source every hidden app reappeared in the drawer after a reboot. It is written on
every hide and every restore, and rebuilt from the index on every unlock, so it cannot drift.

The distinction that matters: `conceal.bin` is **not** a plaintext copy — it cannot be read
from an ADB pull, a bug report, another app's view of the directory, or a copy of the disk —
but it is **not hidden from the device itself**. The key requires no user authentication (it
must be usable before the first unlock, or the reboot fix would do nothing), so any code
running as LumaOS Pro on an unlocked boot can ask the Keystore to open it. It protects the
answer from *outside the app*, not from a compromised OS. Android's own encrypted backup and
`noBackupFilesDir` restrictions apply on top.

Migration, in order of what could go wrong:

* **nothing is deleted.** The legacy preference is read once, converted, imported, and
  left in place;
* **the import only adds.** `importLegacyHiddenApps` skips entries already present,
  de-duplicates by component key and never rewrites or removes an existing entry, so
  running it twice is a no-op;
* **it runs once, but only when it can prove it ran.** A success is recorded in
  `launch.migrated` (outside the encrypted index; a migration name, never a secret). A
  failed import leaves no marker and is retried at the next unlock. If the legacy list
  has not been read yet (DataStore is asynchronous), the migration deliberately does
  *not* mark itself done rather than mistaking "unknown" for "empty";
* **a corrupt entry is dropped, not guessed at.** `HiddenAppKeys.parse` returns null and
  the loader skips it — a malformed key must never turn into "hide a different app".

What "hidden" does not mean, and the UI says so verbatim: the app is not uninstalled,
disabled, suspended, force-stopped or cleared; it keeps running with its data intact;
any other launcher or Android settings can still open it; and this is not the Android
work-profile isolation feature. A static test fails the build if that copy, the
non-exported manifest declaration, or the "no uninstall/disable/clear" property is lost.

---

## 8. Deliberate deviations, and why

| Requested | Used instead | Reason |
|---|---|---|
| Argon2id | PBKDF2-HMAC-SHA-256, calibrated, versioned in the key ring | No vetted Argon2 implementation is reachable without adding a dependency (all are on Maven Central / require NDK). Shipping an unreviewed pure-Java Argon2 as "vetted" would be worse. The KDF id and parameters are stored per wrapper, so switching to Argon2 later needs no data migration. |
| Tink streaming AEAD | AES-GCM with Tink's published streaming framing | Adding Tink is a dependency Vault cannot resolve or verify in this environment. The framing is byte-compatible in design, and `VaultStreamAead` is the single place to swap. |
| SQLCipher / Room | one encrypted JSON index, atomically committed | Avoids a native database in a launcher; and a database schema would leak the very metadata Vault hides. |
| AndroidX BiometricPrompt | platform `BiometricPrompt` (API 28+) | Same system dialog, no new dependency; API 26–27 report biometrics as unavailable and use the PIN. |
| CameraX | `Camera2` + `PdfDocument` | CameraX is a five-artifact dependency; `Camera2` is in the platform and keeps the capture path auditable. |
| WorkManager | nothing (manual backup) | Documented gap rather than an unverified background job. |
| Private video recording | not implemented | Recording video needs a MediaRecorder + Camera2 session combination that could not be exercised here; shipping it untested in the one feature that writes large files was judged worse than documenting the gap. Imported video *playback* works. |

---

## 9. Tests

`app/src/test/java/com/lumaos/pro/launcher/vault/`

| File | Covers |
|---|---|
| `VaultCryptoTests` | HKDF RFC vectors, PBKDF2 vector, GCM round-trip/tamper/AAD/wrong key, IV uniqueness over 500 seals, key independence over 500 keys, zeroize, constant-time compare, base64/hex |
| `VaultStreamAeadTests` | lengths 0…2·chunk+7, no plaintext leakage, wrong key, byte flips at four positions, header tamper, truncation, extension, reordering, non-vault input, random-access reader (including cross-chunk reads) and its corruption detection |
| `VaultKeyringTests` | PIN wrap/unwrap, wrong PIN, vault binding via AAD, PIN change preserves the VMK, recovery wrap/unwrap, rotation invalidates the old key, per-object derivation, key-ring JSON round-trip, PIN policy truth table, recovery-code format/checksum/aliases/uniqueness |
| `VaultStorageTests` | object round-trip, on-disk plaintext absence, independent keys/ciphertexts, DEK transplant rejection, corrupt payload rejection, interrupted store, metadata encryption and tamper, `.bak` recovery, vault-id binding, orphan sweep, deletion shredding, staging clearing, empty and 5 MB objects |
| `VaultSessionTests` | lock/unlock, key zeroization, screen-off, idle expiry, touch reset, conceal/reveal, lock-on-leave, concealed expiry, silent process death, re-unlock, remaining-time maths, and the retry-throttle table, persistence and resets |
| `VaultBackupTests` | ciphertext-only container, restore on a fresh device (VMK recovery → index → payload equality), wrong recovery key, flipped byte, truncation, trailer tamper, missing payload aborts, header inspection, empty vault |
| `VaultPlatformIntegrationTests` | backup rules / extraction rules contain no vault path, every Vault activity is unexported, no Vault source logs names or URIs |
| `VaultDesignParityTest` | Vault's mirrored design tokens still equal `LumaTokens` (Gradle-only: needs Compose) |

Sandbox execution: **87 passing, 0 failing** on the tree that contains every Vault
source file. After that run, the only edits were (a) three small Vault Kotlin fixes
(the lock screen no longer touches its action button before it exists, locking no
longer writes to disk on the main thread, the thumbnail cache no longer recycles a
bitmap an ImageView may still be drawing), (b) manifest, backup-rule and string
additions, (c) documentation. The Vault sources were re-typed-checked after (a), and
the four assertions in `VaultPlatformIntegrationTests` were re-evaluated against the
edited XML files by an equivalent script — both still pass. The harness that hosted
the JVM run lives outside the repository and is not available in this session, so a
fresh 87/87 run on the exact final tree is a Gradle-time task.

---

## 10. Known limitations and residual risks

1. **No device verification.** Camera2 session handling, MediaRecorder, PdfRenderer,
   BiometricPrompt/Keystore behaviour and the Compose settings row have never executed.
   Expect first-run fixes on real hardware.
2. **Argon2id not used** (§8). PBKDF2 at ~210k–800k iterations is weaker per guess than
   Argon2id at comparable latency.
3. **No automatic backup**; "Protected" can only be reached by an explicit backup.
4. **Video recording is not implemented**; audio, photos and scans are.
5. **PDF viewing uses one transient plaintext file** in the vault's staging directory,
   shredded on exit and on lock. This is the only plaintext file Vault ever writes.
6. **Shredding is best-effort.** Flash translation layers may keep old blocks; the app
   states this rather than promising erasure.
7. **Throttling is local, and is now authenticated.** `guard.json` must be readable before
   unlock, so it cannot be protected with the master key; it is tagged instead with an
   HMAC key that lives in the Android Keystore and is never exported. Editing the record
   fails verification and is treated as tampering; deleting it is detected because the
   Keystore entry outlives the file, and it immediately stores the maximum penalty, so a
   delete-then-guess loop grants no attempts at all. This is **not** rollback-proof — an
   older valid record replays — and it does not defend against root or a compromised OS,
   which can ask the Keystore for the tag. Against someone who simply copies the encrypted
   files off the device, the real limit remains PBKDF2 and the PIN's entropy.
8. **Not defended against:** root, a compromised OS, a keylogger, an unlocked bootloader,
   or someone using the phone while the vault is already open. Nothing here claims
   otherwise.
9. **Compile verification is a stand-in.** The Vault sources were type-checked against a
   real `android-36/android.jar` with the Kotlin 2.2.21 compiler, but the Gradle/AGP
   build, R8, resource linking and the existing 74 test classes have not been run.

---

## 11. How to build and test

```bash
./build.sh assembleDebug         # existing entry point, unchanged
./build.sh test                  # existing JVM + Robolectric suite, now including Vault
./build.sh lint
```

Vault adds no dependency, so no dependency resolution changes are needed.

---

## 12. How this sandbox was verified

No Android SDK, Gradle, emulator, or Maven access exists here. To avoid shipping
unverified code, the following was done instead:

* **Provisioned a JDK** from PyPI (`jdk4py`, JDK 21 runtime) and the **Kotlin 2.2.21
  compiler** from npm (`kotlin-compiler`), both on allowed hosts.
* **Fetched a real `android-36/android.jar`** from the `Sable/android-platforms` GitHub
  repository.
* **Type-checked all 33 Vault sources** against `android.jar` (the generated `R` class is
  stubbed from the project's own `res/values`), which is why every `R.string`, `R.font`
  and Android API reference in the new code is known to resolve.
* **Compiled and executed the JUnit4 test sources** with local JUnit stand-ins and a
  small reflective runner: 87 tests, all passing.

None of those harness files are committed; the scripts live outside the repository.

---

## 13. Acceptance criteria

`✅` implemented and unit-tested · `🟡` implemented, needs device testing · `❌` not implemented · `⛔` blocked by the environment

| # | Criterion | State | Evidence |
|---|---|---|---|
| 1 | One installable APK containing the Vault | ✅ | Vault is a package inside `app`; no second module, no new dependency; manifest declares ten unexported activities |
| 2 | Unique 256-bit VMK per vault | ✅ | `VaultKeyring.create` uses `VaultCrypto.randomKey()`; `VaultKeyringTests.keyringRoundTripsThroughJson` and `…vaultIdBindingRejectsAnotherVault` |
| 3 | Independent per-object keys | ✅ | `VaultObjectStore` seals a random DEK per object; `VaultStorageTests.identicalContentProducesDifferentCiphertext` and `…wrappedKeyCannotOpenAnotherObject` |
| 4 | PIN and biometric unlock both work | 🟡 | PIN path fully tested at the core level; biometric path uses the platform prompt + Keystore and compiles but has never run on hardware |
| 5 | Recovery does not depend on the original device's biometrics | ✅ | Recovery wrapping uses HKDF over a random secret only; `VaultBackupTests.restoresOnAFreshDevice` restores with a bare directory, no Keystore |
| 6 | Everything encrypted at rest | ✅ | `VaultStorageTests.objectFileContainsNoPlaintext`, `…metadataDoesNotStoreNamesInTheClear`, `…backupContainerContainsNoPlaintext` |
| 7 | Invisible to ordinary galleries and file managers | 🟡 | No MediaStore insert anywhere in Vault; storage is app-private with opaque names; requires on-device confirmation |
| 8 | Excluded from system backup and device transfer | ✅ | `VaultPlatformIntegrationTests.cloudBackupNeverIncludesTheVaultDirectory`, `…legacyBackupRulesNeverIncludeTheVaultDirectory`, plus `noBackupFilesDir` |
| 9 | Fully offline | ✅ | Vault requests no network permission; there is no networking code in the package |
| 10 | Ciphertext-only backup with verified restore | ✅ | `VaultBackupTests` (7 tests) including integrity rejection and incomplete-vault abort |
| 11 | Lifecycle locking (screen-off, background, timeout, process death) | ✅ | `VaultSessionTests` (screen-off, idle, conceal, lock-on-leave, process death) — the Android wiring itself is 🟡 |
| 12 | No launcher regressions | 🟡 | Only 5 existing files touched, all additively; the 74 pre-existing test classes were **not** executed here (no Gradle) — must be run before release |
| 13 | No false security claims | ✅ | UI states best-effort shredding, local-only throttling, and "no absolute protection"; see §10 |
| 14 | Automated tests pass | ✅ | 126/126 on the JVM harness after the 2026-11 remediation pass (`docs/VAULT_REMEDIATION_2026-11.md` §4); the Gradle run is still pending — see criterion 15 |
| 15 | Real-device testing | ⛔ | No device, emulator or SDK in this environment |
| 16 | Valid build and release configuration | 🟡 | No dependency, no Gradle, no ProGuard change was needed; the Gradle build itself has not been executed here |
| 17 | Private camera, gallery, files, notes, recorder, scanner | 🟡 | All six surfaces exist; none has run on a device |
| 18 | Security Centre with honest status | 🟡 | PIN change, biometric enrolment, recovery rotation, auto-lock, backup status, restore, plaintext-free log — same caveat |
