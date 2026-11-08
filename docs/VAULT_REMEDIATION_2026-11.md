# LumaOS Vault — targeted security remediation (2026-11-07)

**Scope:** the seven findings in the maintainer's remediation brief, against the Vault package
already in this repository. No rebuild, no architecture change, no schema change, no loss of
functionality or UI.

**Evidence standard used throughout:** a claim is *verified* only if this repository's own test
suite exercises it and passed when run for this report. Everything that needs a phone, Gradle,
lint or R8 is marked *not run* and appears in §7. Nothing in this document is marked complete on
the strength of reading code alone.

**Headline:** six of the seven items were confirmed as described and fixed/hardened. Item 4 was
**worse than reported** — a real defect was found (§1, D-01) and fixed. Test suite went from
105 to **126 passing, 0 failing**. `./build.sh assembleDebug|test|lint` could **not** be run in
this environment (§7).

---

## 1. Actual vulnerabilities confirmed

| ID | Severity | Confirmed? | Finding |
|---|---|---|---|
| **D-01** | **Medium** | **Yes — new, found in this pass** | **A deleted item's wrapped data key survived in the crash-recovery index.** `VaultMetadataStore.commit()` copies the outgoing `meta.bin` to `meta.bin.bak` on every commit (that is what makes an interrupted commit recoverable). `VaultManager.delete()` committed the index-without-the-item and then destroyed the payload — leaving `meta.bin.bak` holding the previous index, including the item's `wrappedDek` and `dekIv`. That generation is exactly what `read()` falls back to when the primary index fails authentication. Anyone who later recovered the deleted ciphertext blocks *and* that stale index could re-derive the object's data key and decrypt "deleted" content. The remaining copy is also reachable through a legitimate path — a crash between the two commits — so this is not purely a forensic scenario. The previous audit (V-15) stated deletion was already cryptographic; that statement was **incomplete** and is corrected in `docs/VAULT_SECURITY_AUDIT.md`. |
| **D-02** | Low | Yes | **Throttle state was parsed optimistically.** A `guard.json` that existed but was malformed (`{}`, `[]`, a truncated write, `{"failures":"lots"}`) was decoded as *zero failures* — so corrupting the file was equivalent to deleting it, and a partially-written file silently reset the counter. A forged record was also trusted up to `Int.MAX_VALUE` failures and an arbitrary far-future deadline, i.e. a permanent lockout of the owner. |
| **D-03** | Low | Yes | **Note copy produced an ordinary clip.** Copying from a note put the body on the shared clipboard without `EXTRA_IS_SENSITIVE`, so Android 13+ showed it in the copy preview / clipboard history and the platform's clipboard editor rendered it in clear. |
| **D-04** | Low | Yes | **Plaintext chunk buffers were not wiped.** `VaultStreamAead.encrypt/decrypt` left their 64 KiB chunk buffers (and each decrypted chunk) reachable on the heap after return; `VaultObjectReader.chunk()` replaced its cached decrypted chunk without wiping the one it replaced. PIN handling in `VaultSecurityActivity.promptChangePin` built two immutable `String`s of the PIN that could never be wiped. |
| **D-05** | Informational | Yes | **Deletion left an orphan payload unshredded.** `VaultObjectStore.sweepOrphans` used `File.delete()` (unlink) while `clearStaging` shreds; an interrupted delete therefore removed the bytes logically but not the blocks, with no overwrite pass at all. |
| **D-06** | Informational | Confirmed as *not* a vulnerability | Resetting/deleting `guard.json` does **not** bypass encryption or PIN verification: the guard holds no key material and `unlockWithPin` still has to unwrap the VMK through PBKDF2 + AES-GCM. Verified by `deletingGuardStateCannotProduceAKeyOrBypassThePin` and by review of the unlock path. The throttle bypass itself is real and documented, not fixed (it cannot be). |
| **D-07** | Informational | Confirmed as *not* a defect | No derivation-parameter change was made, so **no migration is required**. Existing wrappers keep their own `scheme`, `salt` and `iterations` (§6). |

Classification note: no Critical or High findings were confirmed in this pass. D-01 is Medium
because exploiting it requires recovering deleted ciphertext blocks (an attacker with forensic
access to the flash) *or* winning a narrow crash window, and because its impact is "content the
user asked to delete is recoverable", not "live vault content is exposed".

---

## 2. Fixes implemented

### 2.1 `guard.json` (items 1, 6)

* **No fake integrity protection was added.** The KDoc now states plainly why: the file must be
  readable before any key exists, so any authenticator would have to be readable by the same
  attacker. No HMAC, no signature, no obfuscation.
* **Missing / malformed / corrupt are now three distinct cases.** Missing = clean state (the
  documented reset); present-but-unreadable = `damaged` state with `failures = FREE_ATTEMPTS + 1`
  anchored at the file's own `lastModified()` (a one-off penalty that expires and cannot renew
  itself); a record with a negative count or timestamp is treated as damaged instead of being
  parsed as zero.
* **Every value is clamped on read:** `failures ∈ [0, 1000]`, `lockedUntil ∈ [0, now + 15 min]`,
  `lastFailure ∈ [0, now]`. A forged file can neither lock the owner out permanently nor
  resurrect a huge failure count. `recordFailure` now reads through the same clamped `state()`.
* **The delay schedule is unchanged** (3 free, then 10 s / 30 s / 60 s / 5 min / 15 min cap), so
  the user-visible behaviour is identical.
* **Documented bypass, stated where it matters:** `docs/VAULT_THREAT_MODEL.md` §3.1 and the
  class KDoc say deleting the file resets the delay, and that PBKDF2 + PIN entropy — not the
  guard — are the offline defence.

### 2.2 Clipboard (item 2)

* New `VaultClipboard` helper: builds the clip, sets `ClipDescription.extras` with
  `android.content.extra.IS_SENSITIVE` (the API-33 constant, inlined as a literal so `minSdk 26`
  compiles and older releases ignore it), and returns `false` if the clipboard is unavailable.
* `VaultNotesActivity` now uses an inner `SensitiveField : EditText` that overrides
  `onTextContextMenuItem` for **copy and cut only**, copies the current selection through the
  helper, and applies the cut afterwards. Paste, select-all, selection handles and editing are
  untouched. If the marked copy fails, the action shows a message and does **not** fall back to
  an unmarked clip.
* Nothing is logged, no clip is ever created automatically, and the recovery key remains
  non-copyable.
* Documented (threat model §5) that a clipboard manager may ignore the flag and that it does not
  exist before API 33.
* Verified statically by `VaultClipboardHygieneTests` (extra is set, no unmarked `ClipData`, no
  logging, copy still reachable).

### 2.3 Zeroization (item 3)

| Buffer | Before | Now |
|---|---|---|
| PIN entry (`VaultLockActivity`) | already `CharArray` | unchanged (already correct) |
| PIN change (`VaultSecurityActivity`) | `String` ×2 (unwipeable) | `CharArray` cores + `text.clear()` in `finally` |
| PIN wrappers (`VaultManager`) | `String` overloads | `CharArray` cores; `String` overloads copy and `fill('\u0000')` |
| PIN policy | `String` path built an array | `validate(CharArray)` core; `validate(String)` wipes its copy |
| Metadata key | destroyed on lock | also destroyed on **every failed** `completeUnlock` path |
| Master key | destroyed on lock/failure | unchanged; all failure paths audited |
| Streaming AEAD buffers | not wiped | plaintext chunk buffers and per-chunk plaintext wiped in `finally` |
| Reader cached chunk | replaced silently | previous chunk wiped before replacement; ciphertext buffer wiped |
| DEK / KEK / wrapper keys | wiped | unchanged (already correct) |
| Biometric cipher | held by activity | **no change** — a `Cipher` cannot be wiped; documented instead of pretending |

The comment on `VaultCrypto.zeroize` and the threat model §3.3 both state the limit: the JVM may
have copied the array, so this narrows the window and is not a guarantee.

### 2.4 Cryptographic deletion (item 4)

* New `VaultMetadataStore.commitAndScrubPrevious()`: commits the new index, then **overwrites
  `meta.bin.bak`** with that same new index (via the new `VaultFiles.writeReplacingNoBackup`,
  which deliberately keeps no snapshot) and shreds any leftover `.tmp`/`.bak`.
* `VaultManager.delete()` rewritten to: commit-with-scrub → evict the thumbnail → destroy the
  payload. Order guarantees an interrupted delete leaves an unreferenced ciphertext file, never
  a dangling index entry.
* `VaultManager.updateNote()` uses the same path, so an edited note's previous object key is
  scrubbed too.
* `sweepOrphans` now **shreds** instead of unlinking.
* Idempotent: deleting twice, or re-running the scrub, is safe (tested).
* Documented in threat model §3.2: the overwrite is best effort on flash; the removal of the
  wrapped key from every index generation is what makes it unrecoverable.

### 2.5 PBKDF2 / offline PIN resistance (item 5) — audit result

| Requirement | Result |
|---|---|
| PBKDF2-HMAC-SHA-256 | Yes — `PBKDF2WithHmacSHA256` (platform JCA), RFC 8018 usage, `PBEKeySpec` cleared |
| CSPRNG salt | `SecureRandom`, 32 bytes, fresh per wrapper |
| Salt uniqueness | Verified: same PIN + same VMK → different salt and different wrapper |
| Iteration count | Device-calibrated at creation (350 ms target, 120k–800k), default floor 210k, hard floor 100k enforced on read *and* write, ceiling 4M |
| PIN entropy / length | 6–12 digits, 8 recommended; repeated-digit, sequential (incl. 123123/112233) and <3-distinct-digit PINs refused |
| KDF / verification | AES-256-GCM tag over the wrapped VMK with AAD binding scheme + vault id; no separate verifier to attack |
| Constant-time comparison | Used for backup digests/gates; not applicable to the PIN (the tag check is inside the provider, and a wrong PIN is indistinguishable from a corrupt wrapper) |
| Plaintext PIN persistence | None — only salt, iterations, ciphertext |
| Derived-key caching | None — only the calibrated iteration *count* is cached |
| Silent parameter change | **None made.** Wrappers carry their own scheme/salt/iterations |

**No parameter change was needed or made** (also see §6).

---

## 3. Files modified

**Production code**

| File | Change |
|---|---|
| `vault/core/VaultUnlockGuard.kt` | honest KDoc; `damaged` state; clamping; `MAX_DELAY_MS`, `MAX_TRACKED_FAILURES` |
| `vault/core/VaultMetadataStore.kt` | `destroy()` zeroizes the metadata key; `commitAndScrubPrevious()` + scrub helper |
| `vault/core/VaultObjectStore.kt` | deletion semantics documented; cached chunk + ciphertext wiped on replace; `sweepOrphans` shreds |
| `vault/core/VaultPaths.kt` | `VaultFiles.writeReplacingNoBackup()` |
| `vault/core/VaultStreamAead.kt` | plaintext chunk buffers wiped in encrypt and decrypt |
| `vault/core/VaultKeyring.kt` | `VaultPinPolicy.validate(CharArray)` core, `String` overload wipes |
| `vault/platform/VaultManager.kt` | `CharArray` cores for create/unlock/setPin/changePin; metadata-key wipe on all failure paths; `delete()` and `updateNote()` use commit-with-scrub; thumbnail eviction on delete |
| `vault/ui/VaultClipboard.kt` | **new** — sensitive-clip helper |
| `vault/ui/VaultNotesActivity.kt` | `SensitiveField` intercepting copy/cut |
| `vault/ui/VaultSecurityActivity.kt` | PIN change works on `CharArray`, fields cleared |
| `vault/ui/VaultLockActivity.kt` | PIN buffers as `CharArray`, wiped in `finally` (from this pass's earlier stage) |

**Tests** — `VaultSessionTests` (4 guard tests, replacing 1 that asserted the old behaviour),
`VaultKeyringTests` (+6), new `VaultDeletionTests` (9), new `VaultClipboardHygieneTests` (4 =
22 new or rewritten).

**Docs** — `docs/VAULT_THREAT_MODEL.md` (**new**), `docs/VAULT_REMEDIATION_2026-11.md` (this
file), `docs/VAULT_SECURITY_AUDIT.md` (V-11/V-13/V-15 corrected, header note), `CHANGELOG.md`.

No UI, navigation, storage layout, schema, manifest or backup-format change. The only new
runtime behaviour visible to a user is that copying from a note no longer shows the text in the
Android 13+ clipboard preview.

---

## 4. Tests executed and actual results

The Gradle tasks in item 7 could not run here (§7), so the suite was executed with an equivalent
harness: Kotlin 2.2.21 compiled against a real `android-36/android.jar`, JUnit-4 subset runner,
clocks and file systems injected exactly as the Gradle run does.

```
==> compiled 30 vault sources against android-36          (check.sh)
Ran 126 passing test(s), 0 failing test(s)                (test.sh)
```

| New/changed test | Behaviour it pins |
|---|---|
| `unreadableGuardFileStartsFromAPenaltyButNeverTrapsTheOwner` | malformed file ⇒ seeded counter, one-off delay anchored to mtime, expires |
| `malformedGuardJsonIsReadAsDamagedState` | `{}`, `[]`, `""`, wrong types, negative values ⇒ damaged |
| `forgedGuardValuesAreClamped` | huge count / far-future deadline clamped to the 15-minute cap |
| `deletingGuardStateCannotProduceAKeyOrBypassThePin` | guard exposes no key material; clearing it yields only a clean counter |
| `firstAttemptsAreFreeThenDelaysGrow` | schedule unchanged (no UX regression) |
| `noteCopyIsAlwaysMarkedSensitive` | copy/cut intercepted, marked path used, no unmarked clip |
| `clipboardHelperSetsThePlatformSensitivityExtra` | extra literal + `PersistableBundle`, no logging |
| `noVaultSourceLogsASensitiveValue` | no Vault source logs a PIN/body/key/clipboard value |
| `theNoteEditorStillAllowsPasteAndSelection` | copy was not disabled to satisfy the finding |
| `everyObjectGetsItsOwnDataKey` | per-object DEK independence |
| `deletingTheIndexEntryDestroysTheOnlyRouteToTheDataKey` | old item record cannot open the destroyed payload |
| `recoverableBackupGenerationNoLongerNamesADeletedItem` | forced fallback to `.bak` recovers a clean index (D-01 regression test) |
| `interruptedDeletionLeavesAnOrphanThatIsSwept` | crash between index and payload ⇒ swept orphan, no dangling entry |
| `editingANoteDestroysThePreviousObjectsKey` | note replacement scrubs the old key |
| `survivingObjectsStillDecryptExactly` | no data loss from the new commit path |
| `deletingTwiceIsSafe` | idempotent deletion |
| `ordinaryCommitsStillKeepTheirRollbackGeneration` | normal commits still snapshot (no reliability regression) |
| `pinWrapperRecordsItsIterationCountAndScheme` | per-wrapper params; PIN not persisted |
| `everyPinWrapperGetsAFreshSalt` | CSPRNG salt uniqueness |
| `anOldWrapperStaysReadableAfterTheDefaultsChange` | versioned KDF: old wrappers keep working |
| `aDowngradedIterationCountIsRefusedBeforeDeriving` | parameter downgrade rejected pre-derivation |
| `pinWrapperIsBoundToItsVaultId` | wrapper AAD binding |
| `pinPolicyCharArrayValidationDoesNotMutateTheBuffer` | `CharArray` validation contract |

All 104 pre-existing tests still pass, including the backup/restore, streaming-AEAD, storage and
hardening suites — the deletion and zeroization changes did not regress them.

Three independent full runs were executed during this pass; every one ended
`126 passing, 0 failing`.

---

## 5. Performance benchmarks

**Environment:** the sandbox host — Intel Xeon @ 2.60 GHz, x86-64, OpenJDK (jdk4py 21), 2 vCPU.
**This is not a phone**, so the absolute numbers are only useful as a ratio; the Android numbers
below are *not measured* and must not be presented as such.

| Operation | Median (7 samples) | Notes |
|---|---|---|
| PBKDF2-HMAC-SHA-256, 100 000 it. | 29 ms | |
| PBKDF2-HMAC-SHA-256, 210 000 it. (default) | **62 ms** | |
| PBKDF2-HMAC-SHA-256, 400 000 it. | 120 ms | |
| PBKDF2-HMAC-SHA-256, 800 000 it. (calibration ceiling) | 238 ms | |
| Full PIN unwrap (PBKDF2 210k + AES-GCM open) | **63 ms** | the number the user feels |
| `calibratePbkdf2Iterations()` (350 ms target) | 800 000 (clamped to ceiling), 12 ms after JIT warm-up | |
| 32 bytes from `SecureRandom` | < 1 ms | |

**Reading these honestly.** A cold, interpreted JVM measured 100 000 iterations at 156 ms and
210 000 at 65 ms — the second number being *lower* is JIT warm-up, not physics. Only the
warmed-up series is reported above.

**What it means for a phone.** Mid-range phones typically run PBKDF2 roughly 3–8× slower than
this host, which puts a 210k unwrap at ~200–500 ms — the calibration targets 350 ms and clamps
between 120k and 800k, so the *user-facing* cost is bounded by design. Offline attack cost per
guess is that same number, so a 6-digit PIN space (10⁶) is ~55 h on one core at 200 ms, and an
8-digit space (10⁸) is ~100× that. **The real hardware benchmark in item 5 was not performed: no
device is available in this environment.** It remains item 1 of the hardware checklist.

Overhead added by this pass: one extra encrypted index write (of the same small size) per
deletion, and one 64 KiB `Arrays.fill` per chunk on large-object I/O — invisible next to
AES-GCM itself. Nothing was added to the unlock path.

---

## 6. Migration requirements

**None.** Verified in three ways:

1. No derivation parameter changed: `DEFAULT_PBKDF2_ITERATIONS` (210 000), the 100 000 floor and
   the 4 000 000 ceiling are untouched, and the calibration targets are unchanged.
2. The on-disk formats are unchanged: `keyring.json` (format 1), `meta.bin` (`LVM1` v1),
   `objects/*.obj` (streaming AEAD v1), backups (`LVBK` v1), `guard.json`.
3. Where parameters *could* need to change in future, the wrapper already carries
   `scheme`, `salt` and `iterations` per copy, so an old vault keeps opening under its own
   parameters and a PIN change re-wraps with current ones — covered by
   `anOldWrapperStaysReadableAfterTheDefaultsChange` and
   `aDowngradedIterationCountIsRefusedBeforeDeriving`.

The `guard.json` change is forward compatible: an old file with valid values reads exactly as
before; a file that was already corrupt now reads as *damaged* instead of clean, which is a
behaviour change on a corrupt file only.

Deletion is written to be crash-safe and idempotent, so no data migration is needed for items
deleted before this fix — though their keys may remain in an existing `meta.bin.bak` until the
next deletion or commit overwrites it. §7 states this plainly.

---

## 7. Remaining limitations

1. **`./build.sh assembleDebug`, `./build.sh test`, `./build.sh lint` were not run.** All three
   fail at toolchain provisioning in this sandbox — `build.sh` needs to download the JDK, Gradle,
   the Android SDK and the Robolectric image, and outbound access is restricted to a few package
   hosts (`api.adoptium.net`, `services.gradle.org`, `dl.google.com`, `repo1.maven.org` all
   refuse the connection). Actual output:

   ```
   ==> toolchain missing, provisioning
   ==> downloading JDK 17
   curl: (35) OpenSSL SSL_connect: SSL_ERROR_SYSCALL in connection to api.adoptium.net:443
   ```

   Therefore: **assembleDebug, unit tests under Gradle, lint, R8/ProGuard and the Compose design
   parity test are unverified for this change set.** The Kotlin compile and the 126 JVM tests
   above are real and passed, but they are not a substitute.
2. **No hardware benchmark and no device test.** §5 of this document and §9 of the audit remain
   open. The biometric, clipboard-flag, `FLAG_SECURE` and Keystore behaviours in particular need
   a device (API 26, 33 and 34+).
3. **Throttle reset by deleting `guard.json` is not fixed and cannot be** — documented instead.
4. **Deletions made before this fix** may still have their wrapped key present in an existing
   `meta.bin.bak`; that generation is overwritten by the next deletion or hash-changing commit.
   There is no automatic sweep on upgrade, deliberately: rewriting the index at unlock would
   destroy the crash-recovery property this code relies on.
5. **Flash-level erasure is not claimed.** `shred()` overwrites then unlinks; wear levelling may
   retain blocks. Cryptographic deletion (no key remaining) is the real guarantee.
6. **Zeroization is best effort**, with the specific limits named in §2.3.
7. **A `Cipher` handed to a biometric prompt is dropped, not wiped** — Java offers no API to
   clear it. The reference is released when the prompt returns; the key itself never leaves the
   Keystore.
8. **The clipboard flag is a hint.** On Android ≤ 12 it does not exist, and a clipboard manager
   or IME may ignore it.
9. **The pre-existing verdict stands: not release-ready** until §7.1 and §7.2 are done.
