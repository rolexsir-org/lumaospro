# LumaOS Vault — threat model

**Scope:** the Vault feature in `com.lumaos.pro.launcher.vault` (this repository).
**Companion documents:** `docs/VAULT.md` (design and behaviour), `docs/VAULT_SECURITY_AUDIT.md`
(findings and evidence), `docs/VAULT_REMEDIATION_2026-11.md` (this pass: what changed, why,
and what was measured).

This document exists to say what Vault protects, what it does **not** protect, and where the
line between the two sits. Every claim below is either backed by code in this repository and a
test that runs in it, or it is marked as unverified. Nothing here is aspirational.

---

## 1. What Vault is defending

A single user's private files, notes, photos, recordings and documents, kept inside the
LumaOS launcher on their own phone, against an attacker who has the phone but not the PIN.

### Assets

| Asset | Where it lives | Protection |
|---|---|---|
| File contents, note bodies, media | `noBackupFilesDir/vault/objects/<id>.obj` | AES-256-GCM per-object DEK, chunked streaming AEAD for large objects |
| File names, kinds, folder names, item metadata, counts | `meta.bin` (+ `meta.bin.bak`) | AES-256-GCM under an HKDF-derived metadata key; names never appear on disk |
| Vault master key (VMK) | RAM only, except three wrapped copies | AES-256-GCM wrappers: PBKDF2(PIN), HKDF(recovery key), Android Keystore (biometric) |
| PIN | never stored | PBKDF2-HMAC-SHA-256, per-wrapper salt and iteration count |
| Recovery key | shown once, user writes it down | 256-bit CSPRNG secret; only its wrapper is stored |
| Thumbnails / decoded pixels | RAM only (`LruCache`) | never written to disk; dropped on lock, on deletion, on eviction |

### Adversary 1 — someone holding the powered-off (or locked) phone

This is the case Vault is built for. After `lock()`, the VMK and the metadata key are
zeroized in the heap, and everything on disk is ciphertext: payloads, the index, the key
wrappers, the guard file. Removing the flash chip and reading it yields no plaintext. Offline
guessing of the PIN is bounded by PBKDF2 cost × PIN entropy (§4).

### Adversary 2 — another app on the same device

Vault's data lives in the app's private, no-backup directory and is not exported through any
provider or activity (asserted by `VaultPlatformIntegrationTests`). Screenshots are blocked
with `FLAG_SECURE`. Content leaves the app only through an explicit user export to a
system-chosen location, an explicit user copy to the clipboard (marked sensitive, §5), or an
explicit user-chosen backup file. The manifest carries no `INTERNET` permission, so no Vault
code path can transmit anything.

### Adversary 3 — an attacker with the unlocked phone in hand

Vault reduces exposure (auto-lock timer, lock on leave, concealed preview, `FLAG_SECURE`, no
recents thumbnail) but does not claim to defeat this. See §3.

---

## 2. Explicit limits — what Vault does not protect against

These are not caveats at the end of the document; they are part of the design contract.

| Not protected | Why |
|---|---|
| **A rooted or otherwise compromised operating system** | Root can read app-private storage, ptrace the process, dump RAM while the vault is unlocked, or hook the crypto provider. Encryption at rest cannot help against an OS that is allowed to read keys in use. |
| **Keyloggers, screen recorders, accessibility abuse, malicious input methods** | If something else can see the PIN as it is typed, the PIN is disclosed. `FLAG_SECURE` blocks screenshots on compliant systems, not a hardware or kernel-level recorder. |
| **Privileged malware (system-signed, MDM with device-owner power)** | Same reason as root, plus the ability to read backups and Keystore metadata. |
| **Physical access to the device while the vault is unlocked** | An open vault is an open vault. The auto-lock window is the only mitigation, and it is a timer, not a proof. |
| **Offline PIN brute force beyond the PIN's entropy** | A 6-digit PIN is 10⁶ candidates. PBKDF2 makes each guess expensive, it does not make the space larger. §4 quantifies this honestly. |
| **The clipboard, once the user copies from a note** | On Android 13+ the clip is marked sensitive, so the system does not show a preview or add it to history; a clipboard manager may still read it, and on Android 12 and older the flag does not exist. |
| **Exported and backed-up copies** | The moment content is exported to a file or a backup container, it is the user's responsibility. A backup is encrypted with the recovery key, but a file the user saved in the clear stays in the clear. |
| **Guaranteed memory erasure** | `zeroize()` overwrites arrays; the JVM and ART may have copied them (by the JIT, the GC, or a crypto provider), and the OS may have paged them. Vault zeroizes every buffer it owns, on every path, but "wiped" means "overwritten here", not "gone from the hardware". |
| **Guaranteed physical erasure on flash** | Android devices use wear-levelled flash with a translation layer. Overwriting a file does not reliably overwrite the blocks that held its old contents. Cryptographic deletion (§3) is what actually makes deleted content unreadable — not the overwrite. |
| **Tamper-proof local throttling** | `guard.json` is now authenticated with a non-exportable Keystore HMAC, so it cannot be edited or deleted to reset the delay with filesystem access alone. It is still **not** rollback-proof: replaying an older valid record verifies, and code running as this app (root, compromised OS) can ask the Keystore for the tag. §3.1 states exactly how far the protection goes. |
| **Supply-chain / build integrity** | Reproducible builds, signing-key custody and Play Integrity are deployment concerns; see `docs/MANAGED_DEVICE_DEPLOYMENT.md`. |

---

## 3. The three places where the honest answer is "it is weaker than it looks"

### 3.1 `guard.json` — throttling that can be edited, but not silently reset

`guard.json` holds a failure count and a lockout deadline. It is read **before** the vault is
unlocked, so it cannot be protected with the master key or with anything derived from the PIN:
the file must be interpretable while no secret exists yet.

What it *is* protected with is a key that does not live in the vault directory at all. An
HMAC-SHA-256 key is generated in the Android Keystore (`lumaos.vault.throttle`) with
`PURPOSE_SIGN | PURPOSE_VERIFY`; its bytes are never returned to the app, so the tag on the
record can only be produced by code running as this app on this device. Every record is signed
over a length-prefixed encoding of `vaultId | failures | lockedUntil | lastFailure`.

The consequences, stated as precisely as the mechanism allows:

* **editing the file does not work.** A changed count, deadline or timestamp fails verification
  and is read as tampering: the counter is seeded above the free attempts, so editing it can
  only make the attacker wait longer;
* **deleting the file does not work either.** The Keystore entry outlives every file in the
  vault directory, so it is the witness that distinguishes "never used" from "record removed".
  An absent record on a vault that has been used is reported as a deletion and — importantly —
  **stores the maximum penalty immediately**, so the attempt that follows is refused. Deleting
  before every guess therefore grants *zero* attempts, not one free guess per deletion. The
  penalty is written on detection, not renewed on every read, so rendering the lock screen in a
  loop cannot push the deadline forward;
* **copying a record between devices or vaults does not work.** The tag is bound to this
  device's Keystore key and to the vault id;
* **a new vault is the only clean slate.** `resetForNewVault()` is called from vault creation
  and nowhere else, and a test asserts there is exactly one such call site;
* **absurd values are clamped**: `failures ∈ [0, 1000]`, `lockedUntil ≤ now + 15 min`,
  `lastFailure ≤ now`. A forged record cannot impose a permanent lockout, and a record with a
  negative count or timestamp is treated as damaged rather than parsed as "zero failures";
* **a device without a usable Keystore HMAC degrades instead of failing.** The guard then runs
  in the old, unauthenticated mode rather than reading its own records as tampered and locking
  the owner out for a platform limitation. The state is visible in the UI copy and here.

**What this does not do.** The Keystore offers no rollback protection for *file contents*: an
attacker who kept a previously valid `guard.json` can restore it byte-for-byte and the HMAC will
verify — counter monotonicity would need a hardware counter Android does not expose to apps.
And an attacker who can execute code as this app, or who is root, can ask the Keystore for the
tag directly. So the honest claim is: **the retry state cannot be reset through the filesystem,
but it is not tamper-proof against a compromised OS.**

What none of this changes is that the delay is not what protects the data. `guard.json` holds no
key material; `VaultManager.unlockWithPin` still has to unwrap the master key through PBKDF2 +
AES-GCM. A test asserts the guard exposes no key-producing method. Against a copying attacker the
throttle is worth nothing — which is exactly why the real offline defence is the KDF cost and the
PIN's entropy, not this file. What the throttle *is* worth is stopping a casual, in-person
attacker, and it survives process death, so force-quitting does not reset it either.

### 3.2 "Deletion" means the key is gone, not that the blocks are gone

Deleting an item (`VaultManager.delete`) does, in this order:

1. commit a new encrypted index **without** the item, fsynced before anything is destroyed;
2. overwrite the previous generation (`meta.bin.bak`) with the new index, and shred any
   leftover temp file — because that generation still contained the object's **wrapped data key**;
3. evict the decoded pixels from the in-memory thumbnail cache;
4. overwrite and unlink the payload file.

Step 2 is the part that matters. Without it, the old index — kept deliberately for crash
recovery — would still hold the wrapped DEK; anyone who later recovered both the stale index
and the old ciphertext blocks could decrypt the "deleted" file. With it, no key the vault still
holds opens that object: not the master key, not a restore of the pre-deletion index, not the
biometric or recovery wrapper (all three protect the same VMK, so they are not independent
escape hatches).

Interrupted deletions are safe in one direction only, and that direction is the safe one: the
index is committed first, so a crash leaves an **unreferenced ciphertext file**, which the next
unlock shreds (`sweepOrphans`). It can never leave an index entry pointing at a half-destroyed
payload. `VaultDeletionTests` exercises every one of these claims.

The limit: step 4 is best effort. A flash translation layer may keep the old blocks. What makes
the content unrecoverable is step 2, not step 4. Vault says this in the UI's own words
("This cannot be undone") and does not claim physical erasure.

### 3.3 Zeroization is best effort, and that is the whole claim

Vault zeroizes: the PBKDF2 output, the wrapped-key KEKs, the metadata key on lock, the master
key on lock and on every failed unlock path, every unwrapped DEK after use, the reader's chunk
key and cached plaintext, the streaming encrypt/decrypt buffers, PIN character buffers in the UI,
and PIN arrays created by the `String` convenience overloads. It does **not** claim any of that
erases the data: an immutable `String` cannot be wiped, a `Cipher` object cannot be wiped, and
the JIT is free to have copied anything. The goal is to shrink the window, and it is measured in
the audit, not asserted.

---

## 4. PIN and KDF — the real offline defence

| Property | Implementation | Verified by |
|---|---|---|
| KDF | PBKDF2-HMAC-SHA-256 (platform JCA, `PBKDF2WithHmacSHA256`) | `pbkdf2MatchesPublishedVector` |
| Salt | 32 random bytes per wrapper, from `SecureRandom` | `everyPinWrapperGetsAFreshSalt` |
| Iterations | calibrated on the device at creation (120k–800k, 350 ms target), **stored per wrapper**, floor 100k, ceiling 4M | `pbkdf2CalibrationStaysInDocumentedRange`, `aDowngradedIterationCountIsRefusedBeforeDeriving` |
| PIN length | 6–12 digits; 8 recommended; repeated/sequential/few-distinct patterns refused | `pinPolicyAcceptsStrongPins`, `pinPolicyRejectsWeakPins` |
| Verification | AES-GCM tag over the wrapped VMK; a wrong PIN and a corrupt wrapper are the same failure | `wrongPinIsRejected`, `wrapperIsBoundToItsVault` |
| PIN at rest | never stored; only salt, iteration count and ciphertext | `pinWrapperRecordsItsIterationCountAndScheme` |
| PIN in memory | `CharArray` end to end in the UI and manager; `String` overloads copy and wipe | `pinPolicyCharArrayValidationDoesNotMutateTheBuffer` |
| Derived-key caching | none; only the calibrated iteration *count* is cached | code review of `unlockWithPin` |
| Constant-time comparison | used for backup digests and gate comparisons; not used for PIN verification (the GCM tag comparison is inside the platform provider) | `constantTimeEqualsBehavesLikeEquality` |

**Offline attack cost.** One guess costs one PBKDF2 evaluation: ~62 ms at 210k iterations on the
x86-64 sandbox used for this audit (§5 of the remediation report) and, on a current mid-range
phone, typically 150–400 ms at the calibrated count. A 6-digit PIN is 10⁶ guesses, so a full
sweep at 200 ms/guess is ~55 hours of one core (~2 days), or a few hours on a rig with many
cores. An 8-digit PIN is 10⁸ guesses: 100× that. **The iteration count raises the price of each
guess; only PIN length raises the size of the search space.** Vault recommends 8 digits in the
UI for exactly this reason and does not describe 6 digits as strong.

This is also why the `guard.json` bypass (§3.1) is not a break of offline resistance: an attacker
who can read the vault's filesystem can also skip the guard entirely and attack the wrapper
directly, where only PBKDF2 stands in the way.

**Changing parameters later.** A wrapper stores its own scheme id, salt and iteration count, so
raising the default cannot lock out an existing vault, and there is no need to re-derive anything
in place: a PIN change re-wraps the same VMK under the current parameters (`anOldWrapperStaysReadableAfterTheDefaultsChange`).
No parameter change was made in this pass, so no migration is required.

---

## 5. Android platform surface

| Surface | Behaviour | Limit |
|---|---|---|
| Clipboard | User-initiated copy of a note body is preserved; the clip is created with `ClipDescription.EXTRA_IS_SENSITIVE` so Android 13+ hides the preview, keeps it out of clipboard history and masks it in the editor | A clipboard manager, IME or accessibility service may ignore the flag; on Android 12 and older nothing reads it. Vault never logs or persists clipboard content, and never copies anything automatically. |
| Screenshots / recents | `FLAG_SECURE` on Vault screens; concealed preview on backgrounding | A compromised OS or an external camera is out of scope. |
| Backup / device transfer | Vault directory is under `noBackupFilesDir`; no backup rule names it (asserted by tests) | A user-initiated encrypted backup is the supported transfer path. |
| Autofill / keyboard learning | PIN and recovery inputs set `IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS`, `isSaveEnabled = false`, no-suggestions input types | A malicious IME still sees keystrokes. |
| Biometric unlock | `BiometricPrompt` with a `CryptoObject`; the Keystore refuses to decrypt until auth succeeds, so a spoofed callback yields no key. The callback also rejects a substituted cipher object | Device-bound; a new enrolment invalidates the wrapper by design. |
| Keystore | `AndroidKeyStore`, per-vault alias, deleted on "turn off" and when invalidated | Root can use a live Keystore key while the device is unlocked. |

---

## 6. Threat → outcome summary

| Scenario | Outcome |
|---|---|
| Phone lost, powered off, strong PIN | Content unreadable; offline guessing bounded by PBKDF2 × PIN entropy |
| Phone lost, powered off, 6-digit PIN, determined attacker with the flash chip | **Breakable** by exhaustive search in days (est. §4). 8+ digits recommended |
| Phone lost, unlocked, app in background, auto-lock expired | Locked; keys zeroized |
| Phone lost, unlocked, Vault on screen | **Compromise.** Out of scope by definition |
| Attacker deletes `guard.json` | Throttle reset (documented); PIN still required, PBKDF2 still applies |
| Attacker forges `guard.json` to lock the owner out | Bounded to a 15-minute delay; the owner can still unlock |
| Attacker copies the vault directory, restores it elsewhere | Useless without the PIN, recovery key or the device's Keystore key |
| Attacker restores today's backup over a vault, then reads a file the user deleted last week | The deleted object's key is absent from the backup as well, because backups carry the index; the stale ciphertext alone cannot be decrypted |
| Item deleted, phone later imaged at block level | Payload blocks may survive; the wrapped key does not, so the content stays closed |
| Malicious app on the device | Cannot read private storage or exported components; may attempt clipboard/IME/screenshot abuse — see §5 |
| Rooted device | **Compromise.** Out of scope |

---

## 7. What would change this document

Any change to the key hierarchy, the on-disk layout, the guard semantics, the clipboard
behaviour, or the deletion order must update this file and the tests named here. The tests are
the enforcement: `VaultSessionTests`, `VaultDeletionTests`, `VaultKeyringTests`,
`VaultClipboardHygieneTests`, `VaultPlatformIntegrationTests`, `VaultStorageTests`.
