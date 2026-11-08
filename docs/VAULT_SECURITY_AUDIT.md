# LumaOS Vault — pre-release security audit & hardening report

**Date:** 2026-11-07 · **Scope:** the Vault package already in this repository
(`com.lumaos.pro.launcher.vault`, 29 source files) and the five integration points it
touches.

**Verdict up front: not release-ready.** The cryptography, storage layout and backup
format hold up under review and under the automated tests in this repository (105
executed here, 0 failing), and seven findings were fixed in this pass — but the Gradle
build, lint, R8 and every on-device behaviour are **unverified** in this environment,
and no hardware testing has been performed. §9 lists exactly what must happen before
release.

> **Update, 2026-11-07 (remediation pass).** A follow-up pass addressed seven specific
> findings at the maintainer's request — `guard.json` hardened and documented, clipboard
> sensitivity marking, further zeroization, cryptographic-deletion verification, a full
> PBKDF2/PIN audit, a written threat model, and the regression run. It found and fixed one
> **real defect** that this document's V-15 had stated too favourably: the crash-recovery
> backup of the metadata index still held the wrapped data key of deleted items. See
> `docs/VAULT_REMEDIATION_2026-11.md` for what changed, the actual test and benchmark
> results, and the remaining limitations. The verdict below is unchanged.

---

## 1. Method, and what "verified" means here

| Activity | Performed | Evidence |
|---|---|---|
| Read the full Vault implementation and its integration points | yes | 29 Vault sources, `LumaCore`, `SettingsScreen`, manifest, backup/data-extraction rules |
| Static type-check against a real `android-36/android.jar` | yes | Kotlin 2.2.21 compiler, 29 sources, 160 classes, no errors |
| Execute the automated tests | yes | 105 tests, 0 failures (§8) |
| Review Android platform surface (manifest, providers, intents, clipboard, backup rules) | yes | §7, plus two automated manifest tests |
| `./build.sh assembleDebug`, `test`, `lint` | **no** | no Gradle, no Android SDK, no Google Maven in this sandbox |
| Any device or emulator run | **no** | no device, no emulator |

Where this report says *verified*, it means "checked by reading the code and by a test
that was actually executed here". Anything that depends on Android framework behaviour
at runtime is marked *unverified* and appears in the hardware checklist.

---

## 2. Architecture assessment (and why it was not replaced)

The existing design is sound and was retained:

* **Key hierarchy:** per-vault 256-bit VMK → HKDF-derived metadata key and per-object
  wrapper keys → per-object random 256-bit DEK → AES-256-GCM or chunked streaming AEAD.
  This is envelope encryption, done with platform primitives (JCA) and no custom
  cryptography.
* **Why not Tink/SQLCipher/Argon2:** adding a dependency here cannot be resolved,
  reviewed or verified in this environment, and the vault's storage is deliberately a
  single encrypted JSON index rather than a database — a schema would leak exactly the
  metadata the product hides. The KDF and construction identifiers are stored per
  wrapper (`pin-pbkdf2-sha256-aesgcm-v1`, …), so migrating to Argon2id or Tink later
  needs no re-encryption of existing content. This is a documented trade-off, not a
  silent substitution: PBKDF2 at 210k–800k iterations is weaker per guess than Argon2id
  at equal latency.
* **Why the index is a file, not a database:** atomic rename gives all-or-nothing
  commits, `.bak` gives rollback, and nothing outside the ciphertext describes the
  content.

Net effect of this pass on the architecture: **none**. Every fix below is local to the
boundary where hostile input arrives.

---

## 3. Findings

Severity follows the usual CRITICAL / HIGH / MEDIUM / LOW / INFORMATIONAL scale. "Fixed
& tested" means a test in this repository fails if the fix regresses.

### HIGH

**V-01 · Backup contents were streamed straight into the destination provider.**
An interrupted write (process death, storage full, provider error) left a truncated file
in the user's folder that still looked like a backup, and re-using a destination URI
truncated the previous good backup before the new one existed.
*Fix:* the container is now completed and integrity-verified **on local storage first**
(`VaultBackup.writeToFile` → fsync → `verifyFile`), and only then copied to the
destination, which is read back and verified again. A provider failure now leaves the
previous backup untouched.
*Tests:* `writeToFileStagesAVerifiedContainer`, `verifyFileRejectsTruncationAndTampering`.

**V-02 · Restore allocated attacker-controlled sizes and accepted arbitrary object ids.**
`restore()` read a 32-bit id length and passed it to `ByteArray(n)`, so a hand-edited
container could request a ~2 GB allocation (out-of-memory kill) before any authentication
happened. Ids were only checked as "all letters or digits", which admits non-ASCII
look-alikes and made the filename under `objects/` depend on hostile input.
*Fix:* id length capped at 64 bytes; ids must match `^[0-9a-f]{32}$`; per-record caps
(index 64 MiB, object 64 GiB, 200 000 objects); `VaultPaths.objectFile` now enforces the
same canonical form as defence in depth.
*Tests:* `restoreRejectsAnOversizedIdLengthBeforeAllocating` (asserts rejection in <5 s),
`restoreRejectsObjectIdsThatAreNotCanonical`, `restoreRejectsAnOversizedIndexRecord`,
`objectFileRefusesATraversingIdentifier`.

**V-03 · Restore did not check that a container is complete.**
A container could be internally consistent — valid digest, valid header — while omitting
payloads that the encrypted index still references. Restoring it produced a vault full of
items whose contents are gone.
*Fix:* after the index is decrypted, every referenced object must exist in the staged
directory, or the restore aborts before anything is swapped in.
*Test:* `restoreRejectsAContainerThatOmitsAnObject`.

### MEDIUM

**V-04 · A failure after the swap left a half-established vault.**
If the recovery unlock succeeded but the new PIN could not be written (or the follow-up
commit failed), the restored directory stayed in place — openable only with the recovery
key — while the user's original vault sat in a `.previous` directory they could not
reach.
*Fix:* every post-swap step now runs through a rollback that restores the previous vault.
*Test:* the rollback path is exercised indirectly by the restore failure tests; the
manager itself is Android-bound and is covered by the hardware checklist.

**V-05 · The key ring accepted unbounded KDF iterations.**
`iterations` is read from plain JSON before any secret is known, and only a floor was
enforced. A key ring edited to claim ~2·10⁹ iterations would freeze the lock screen for
hours — a denial of service against the legitimate owner, triggered without any secret.
*Fix:* ceiling of 4 000 000 iterations, enforced on both read and write.
*Tests:* `keyRingRejectsAnAbsurdIterationCountInsteadOfHanging`,
`keyRingRejectsADowngradedIterationCount`, `keyRingJsonRoundTripPreservesTheCeilingCheck`.

**V-06 · The recovery key could be copied to the system clipboard.**
The recovery key was rendered in a selectable `TextView` on both the setup screen and the
Security Centre, so a long-press copied the master recovery secret into the shared
clipboard, where other apps can read it and where it outlives the activity.
*Fix:* both views are non-selectable and non-long-clickable. Copying is intentionally not
offered anywhere in the UI.
*Verification:* code review; clipboard behaviour is a device test (checklist item 10).

**V-07 · Valid recovery keys were rejected during transcription.**
The two-character checksum was compared as *characters*, so a correctly transcribed key
whose checksum contained `1` or `0` failed when the user wrote `l` or `O` — about 12 % of
keys, and precisely when someone is recovering from a forgotten PIN. (Found by an
existing test that had been passing by luck; it failed 605 times in 5 000 generated keys
during this audit.)
*Fix:* the checksum is decoded through the same Crockford alias map and compared by value.
*Tests:* `everyRecoveryCodeSurvivesHumanAliasTranscription` (400 keys per run),
`recoveryCodeStillRejectsAMistypedCharacter`, `recoveryCodeRejectsCharactersOutsideCrockford`.

### LOW

**V-08 · No free-space guard on restore.** A container could fill the partition mid-restore.
*Fix:* each object checks `usableSpace` minus a 16 MiB margin first.

**V-09 · Staging was unlinked, not shredded.** Staging is the only place Vault ever writes
plaintext (PDF staging, recorder scratch). *Fix:* `clearStaging()` shreds files (best
effort — see §10).

**V-10 · Container verification had no size ceiling.** A hostile provider could stream
forever. *Fix:* `VaultBackup.verify` is bounded at 1 TiB and rejects beyond it.

### INFORMATIONAL (verified acceptable — no change)

* **V-11 Throttle state is unauthenticated.** `guard.json` holds a counter and timestamps.
  Deleting it resets the delay; editing it can shorten it. It is not defended because it
  cannot be: the file must be readable before the vault is open, and any key that could
  authenticate it would have to be available to the same attacker. The real defences are
  PBKDF2 cost and PIN entropy; the throttle only slows a person tapping at a lock screen.
  *Updated in the 2026-11 remediation pass:* a malformed record is now read as *damaged*
  (seeded failure count anchored to the file's mtime) instead of a clean slate, and all
  values are clamped so a forged record cannot impose more than the 15-minute cap or a
  negative count. Deleting the file still resets the delay, as documented. See
  `docs/VAULT_THREAT_MODEL.md` §3.1.
* **V-12 No per-object nonce registry.** 96-bit random IVs per key are used. For the
  metadata index (one key, many commits) the collision bound is ~2⁴⁸ commits; per-object
  keys are single-use, so their nonce space is never shared. Covered by
  `VaultCryptoTests` (500 seals, all distinct) and by design.
* **V-13 Notes can be copied by the user.** A note's text can be selected and copied. That
  is the user's own content and an intentional feature. *Fixed in the 2026-11 pass:* copy and
  cut are intercepted and the clip is created with `ClipDescription.EXTRA_IS_SENSITIVE`, so
  Android 13+ suppresses the preview, clipboard history and editor rendering. Copying is still
  available and never silently disabled; the flag is a hint that a clipboard manager may
  ignore, and it does not exist before API 33. See `docs/VAULT_THREAT_MODEL.md` §5.
* **V-14 No Google Drive SDK is present.** Backups go through the Storage Access Framework,
  which is provider-agnostic: the user may pick Google Drive, a USB drive or a local
  folder, and the app never talks to a network itself. Any statement that Vault ships a
  "Drive integration" would be false.
* **V-15 Object deletion is cryptographic.** Deleting an item drops its wrapped DEK with
  the index entry (and then the payload). The key is unrecoverable, which is stronger than
  overwriting, but the filesystem may retain old blocks — see §10. *Corrected and hardened in
  the 2026-11 pass:* the original claim was incomplete. `commit()` keeps the previous index as
  `meta.bin.bak`, so the DEK of a just-deleted item survived in that generation until the next
  commit. Deletion now uses `VaultMetadataStore.commitAndScrubPrevious`, which overwrites the
  backup generation with the post-deletion index and shreds leftover temp files, in addition to
  evicting thumbnails before destroying the payload. Verified by `VaultDeletionTests`.

---

## 4. Authentication audit

| Requirement | Implementation | State |
|---|---|---|
| No plaintext PIN stored | Only a PBKDF2-derived AES-GCM wrapper of the VMK | verified (code) |
| PIN never logged | logging rule enforced by test `vaultSourcesDoNotLogFileNamesOrContent` | verified (test) |
| PIN handled as chars | `CharArray` in the manager and zeroized in `finally`; the lock screen's digit buffer is cleared after submit | verified (code) |
| Brute-force throttling | 3 free attempts, then 10 s → 30 s → 60 s → 5 min → 15 min cap, persisted in `guard.json`, re-read after process death and reboot | verified (tests + code) |
| No permanent lockout | cap is 15 minutes; recovery key is always reachable | verified (code) |
| Biometric unlock is cryptographic | platform `BiometricPrompt` with a `CryptoObject` from a Keystore key (`setUserAuthenticationRequired(true)`, `setInvalidatedByBiometricEnrollment(true)`) | unverified at runtime |
| Biometric cannot bypass the KEK | the Keystore key only unwraps the biometric copy of the VMK | verified (code) |
| Auth state expires | idle timeout 30 s–10 min (default 3 min), immediate conceal on background, lock on screen-off | verified (core tests); wiring unverified |
| Process termination invalidates the session | keys exist only in memory; nothing is persisted; `PROCESS_DEATH` lock reason is silent | verified (code) |
| Recovery cannot bypass a secret | the recovery key is a 256-bit factor, not a shortcut; it unwraps the same VMK, and a recovered vault starts with no PIN and no biometric wrapper, forcing a fresh local PIN | verified (code) |
| Wrong recovery key leaves the vault locked | `unlockWithRecovery` returns `WrongRecoveryKey`; no state change | verified (tests) |

Residual (documented, not fixable in-app): an attacker with the vault directory and the
ciphertext can attack the PIN offline, limited only by PBKDF2 and PIN entropy.

---

## 5. Encryption assessment

| Property | Status | Evidence |
|---|---|---|
| CSPRNG only (`SecureRandom`) | yes | `VaultCrypto.randomBytes` |
| 256-bit VMK per vault | yes | `VaultKeyring.newMasterKey`, per-vault random |
| Unique DEK per file | yes | `VaultObjectStore.store`, tested by `duplicateContentGetsIndependentKeysAndCiphertext` |
| AEAD for content | AES-256-GCM ≤ 64 KiB; chunked streaming AEAD above it | `VaultStreamAeadTests` (11 cases incl. reorder/truncate/tamper) |
| DEKs protected by a KEK | HKDF(VMK, `object/v1|<id>`) wraps each DEK; wrapped DEK stored in the index, never beside the payload | `VaultStorageTests` |
| Keys stored separately from content | yes (`keyring.json`, index, `objects/`) | storage layout |
| Nonce reuse prevented | random 96-bit IVs; per-object keys are single-use | `VaultCryptoTests` |
| Metadata and content authenticated | GCM AAD binds vault id, purpose and object id | `VaultStorageTests.wrongMasterKeyCannotOpenMetadata`, tamper tests |
| Rotation | PIN rotation and recovery-key rotation rewrap the VMK without touching content; DEK/VMK rotation is not offered | `VaultKeyringTests` |
| No partial plaintext on failure | streams authenticate before the caller consumes them; staging files are shredded on failure | `VaultStreamAeadTests`, `VaultStorageTests` |

**Design note.** Content is decrypted in 64 KiB authenticated chunks and fed straight to
the consumer (bitmap decoder, `MediaPlayer` via `MediaDataSource`, SAF output stream). A
tampered chunk fails its tag before any of its bytes are used, so an attacker cannot
induce "half a photo" or a partial export.

---

## 6. Media isolation audit

| Workflow | Path | Plaintext at rest | State |
|---|---|---|---|
| Camera | Camera2 → JPEG in memory → `addStream` → ciphertext | none | unverified at runtime |
| Scanner | `PdfDocument` composed in memory → `addStream` | none | unverified at runtime |
| Recorder | MediaRecorder → staging `.m4a` → encrypt → shred | staging only, cleared on lock/unlock | unverified at runtime |
| Gallery / image viewer | decrypt to `Bitmap` in RAM (LRU, dropped on lock) | none | unverified at runtime |
| Video / audio | seekable `MediaDataSource` over the decrypting reader | none | unverified at runtime |
| PDF viewer | decrypt to a staging file handed to `PdfRenderer` | staging, shredded on close/lock | unverified at runtime |
| Import | SAF descriptor → encrypted directly, streamed | none | unverified at runtime |
| Export | explicit confirmation → decrypt into the user's chosen URI | destination is the user's choice (stated in the dialog) | unverified at runtime |

Verified by inspection: no `MediaStore` write anywhere in the package; no `FileProvider`;
no `ACTION_VIEW`/`ACTION_SEND` intent is ever built from Vault content (the only
`startActivity` calls target Vault activities); every Vault activity is unexported and
excluded from recents; `FLAG_SECURE` is set by the single base activity before content is
attached. Two automated tests enforce the manifest side of this.

Abandoned work: staging is cleared on unlock and on lock, and a store that fails midway
shreds its staging file — covered by `stagingIsClearedAndLeftEmpty` and the existing
interrupted-store test.

---

## 7. Android platform security

| Item | Finding |
|---|---|
| Exported components | none added; 9 activities, all `exported="false"`, none with intent filters (tested) |
| Providers | the app declares no `ContentProvider` and no `FileProvider` (tested) |
| Services / receivers | Vault adds none; its only receiver is registered at runtime by the launcher's idle guard, not exported |
| Backup | content lives in `noBackupFilesDir`; `backup_rules.xml` and `data_extraction_rules.xml` never name it (tested) |
| Network | the app requests no `INTERNET` permission (tested); Vault has no network code |
| Screenshots / recents | `FLAG_SECURE` on the shared base activity, `excludeFromRecents` per activity |
| Clipboard | only the recovery key was exposed and is now not selectable; notes remain copyable by design (V-13) |
| Logging | `LumaLog` is DEBUG-gated; Vault logs state only, never names, URIs or content (tested) |
| Temp files | app-private `staging/`, created with default app-private permissions, shredded on lock/unlock |

---

## 8. Tests executed in this audit

Environment: JDK 21 (runtime), Kotlin 2.2.21 compiler, real `android-36/android.jar`,
JUnit-4-compatible runner. Command: the harness that compiles all 29 Vault sources and
the JVM test sources, then runs every `*Tests` class.

**Result: 105 passing, 0 failing** (87 pre-existing + 18 added in this pass).

Added here (`VaultHardeningTests` unless noted):

| Test | Proves |
|---|---|
| `keyRingRejectsAnAbsurdIterationCountInsteadOfHanging` | V-05 |
| `keyRingRejectsADowngradedIterationCount` | V-05 (floor still enforced) |
| `keyRingJsonRoundTripPreservesTheCeilingCheck` | V-05 through the on-disk path |
| `everyRecoveryCodeSurvivesHumanAliasTranscription` | V-07 (400 keys) |
| `recoveryCodeStillRejectsAMistypedCharacter` | checksum still catches typos |
| `recoveryCodeRejectsCharactersOutsideCrockford` | `U` is invalid by design |
| `writeToFileStagesAVerifiedContainer` | V-01 staging + verification |
| `verifyFileRejectsTruncationAndTampering` | V-01, V-10 |
| `restoreRejectsObjectIdsThatAreNotCanonical` | V-02 (no file outside `objects/`) |
| `restoreRejectsAnOversizedIdLengthBeforeAllocating` | V-02 (no OOM; <5 s) |
| `restoreRejectsAnOversizedIndexRecord` | V-02 |
| `restoreRejectsAContainerThatOmitsAnObject` | V-03 |
| `aFailedRestoreLeavesTheExistingVaultUntouched` | V-04 (live vault byte-identical after a failed restore) |
| `objectFileRefusesATraversingIdentifier` | V-02 defence in depth |
| `stagingIsClearedAndLeftEmpty` | V-09 |
| `duplicateContentGetsIndependentKeysAndCiphertext` | per-object keys |
| `concurrentStoresRemainReadable` | 4 threads storing simultaneously |
| `vaultBringsNoExportedComponentsAndNoProviderOrNetworkSurface` (platform suite) | §7 |

Flakiness: the suite was executed three times; no test changed result. The recovery-key
test that motivated V-07 is now deterministic (it failed ~12 % of runs before the fix).

---

## 9. Hardware testing checklist (none of this has been run)

Record device, Android version, procedure, expected, actual and pass/fail for each.

| # | Test | Expected |
|---|---|---|
| 1 | First run: create vault, write down key, unlock, relaunch | Recovery key shown once; vault opens; lock screen on relaunch |
| 2 | Wrong PIN ×6 | Free ×3, then 10 s, 30 s, 60 s countdowns; no data loss |
| 3 | Kill the app mid-lockout, reopen | Delay continues (persistence) |
| 4 | Reboot, reopen | Vault starts locked; PIN works |
| 5 | Camera capture, then check every gallery/file manager app | Vault photo appears in none of them; no DCIM entry |
| 6 | Scanner: 3 pages → PDF | Multi-page PDF opens in the Vault viewer only |
| 7 | Recorder: 60 s, lock screen mid-recording | Recording stops; staging cleared; no file in any public folder |
| 8 | Video playback of an imported clip | Plays, seeks, no plaintext file appears in `staging/` |
| 9 | Biometric enrol → unlock → add a new fingerprint → unlock again | Falls back to PIN and asks to re-enrol; never opens silently |
| 10 | Recovery key screen: long-press, try to copy | Nothing is selectable; nothing reaches the clipboard |
| 11 | Backup to Google Drive; then restore on a factory-reset device | Restore asks for the recovery key, then a new PIN, and all items decrypt |
| 12 | Backup then kill the app mid-upload | Provider holds either the old backup or a file that is not presented as verified |
| 13 | Screen-off while viewing a video | Vault locks immediately; returning shows the lock screen |
| 14 | Background: open Recents with the vault open | No thumbnail of Vault content |
| 15 | Settings → Private → LumaOS Vault | Lock screen opens; launcher unaffected |
| 16 | Delete an item, then check storage and orphan sweep | Payload gone after the next unlock; no orphan left |
| 17 | Fill the device, then restore a backup | Restore refuses with a clear message; existing vault intact |
| 18 | Interrupt encryption (kill during capture) | No partial item; staging empty after next unlock |

---

## 10. Performance, data integrity and honest limits

**Data integrity (verified here):** atomic index commits with `.bak` fallback, object
writes that are verified before the index is committed, completeness-checked backups,
rollback-safe restores, and orphan sweeping at unlock. `aFailedRestoreLeavesTheExistingVaultUntouched`
compares the live vault's file digests before and after a failed restore.

**Performance (not measured here):** 64 KiB chunk streaming bounds memory for large
files; `256 MB`-class device behaviour, decode memory during gallery scrolling, recorder
battery cost and playback CPU are all device measurements. No optimisation was traded for
security in this pass.

**Limits the product must keep stating:**

* No protection against a rooted or otherwise compromised OS, a keylogger, malware with
  sufficient privilege, or physical access to a device that is already unlocked.
* Zeroization is best effort: Kotlin/ART may copy buffers, and **flash wear levelling can
  retain old blocks after a file is overwritten or deleted**. "Crypto shredding" here
  means the key is destroyed, not that the platter is clean.
* Throttling is a local speed bump (V-11); offline attack cost is PBKDF2 × PIN entropy.
* A backup protects against device loss only if it lives somewhere else, and its secrecy
  depends on the recovery key — which Vault never transmits, stores in readable form, or
  can regenerate.
* Keys are visible to anyone who can instrument the app process while it is unlocked.

---

## 11. Production-readiness assessment

**Not ready for release.** Blocking items, in order:

1. `./build.sh assembleDebug`, then `test`, then `lint` — none have run. The Vault sources
   are type-checked against a real `android.jar`, but AGP, resource linking, R8 and the
   pre-existing Robolectric suite are unverified.
2. Run the 18-item hardware checklist in §9; fixes are expected in Camera2, MediaRecorder,
   `PdfRenderer` and the biometric fallback.
3. Decide the release stance on the documented deviations (PBKDF2 instead of Argon2id,
   no automated backup, no video recording).
4. Bump `versionCode`/`versionName` for this release.

Ready and verified now: the cryptography, the storage layout, backup/restore integrity,
the recovery-key encoding, throttling persistence, the pre-authentication input
validation added in this pass, and the manifest/backup-rule hygiene — all backed by tests
that were executed.
