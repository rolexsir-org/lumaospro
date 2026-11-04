# LumaOS Pro — security & privacy audit (Pillar 6, 2026-11-04)

Scope: full source + manifest + build config static assessment against OWASP
MASVS themes. **Not performed** (no tooling in this sandbox): MobSF, lint,
Gradle dependency/vulnerability reports, APK inspection, traffic capture,
device intent-fuzzing. No scanner result is claimed anywhere below.
No secret values appear in this report.

## Threat model (attack surface → mitigation in place)
| Surface | Threat | Existing mitigation |
|---|---|---|
| Exported HomeActivity | intent spoofing, extra injection | reads no action-triggering extras; only MAIN/HOME/LAUNCHER filters |
| Other apps' data | inventory disclosure | LauncherApps API, **no** `QUERY_ALL_PACKAGES`; scoped `<queries>` |
| Malformed backup import | DataStore poisoning, crash | whitelist + per-key type coercion, version gate, 4 MB cap, widget/layout sanitising (`BackupCodec`) |
| Malicious wallpaper file | decoder exploit, decompression bomb, path traversal | 12 MB / 100 MP / 20k-edge limits, bounds-then-probe decode, app-generated UUID names, `photo:` IDs regex-validated |
| Untrusted icon packs | bad XML, masquerading icons | XmlPullParser (no DTD/XXE surface), install-state re-verified before use |
| Widget providers | unavailable/misbehaving providers | null-safe provider lookup, widget ids never exported in backups |
| Notification content | sensitive text exposure | in-memory only; models deliberately carry no PendingIntent/text to disk or logs; hidden-app gate uses system Keyguard, no custom PIN |
| Network | exfiltration, MITM | **no `INTERNET` permission, no HTTP stack, no analytics/ads/crash SDKs** — the only URL in code is an `https://example.com` intent used to *resolve* a browser |
| Signing/secrets | key theft | keystore + `keystore.properties` git-ignored; build fails loudly on incomplete release signing; no keys/secrets in tracked files (verified by scan) |

## Permissions inventory (4 — all justified, least-privilege upheld)
- `REQUEST_DELETE_PACKAGES` — system-confirmed uninstall intent from long-press menus; the app never uninstalls anything itself.
- `RECEIVE_BOOT_COMPLETED` — rebuilds the next inexact context-profile alarm only.
- `POST_NOTIFICATIONS` — runtime-requested **only** when the user enables context alerts; revoked state is re-synced on resume.
- `SET_WALLPAPER` — user-initiated wallpaper application.
- Deliberately absent: `INTERNET`, `QUERY_ALL_PACKAGES`, location, contacts, storage, accessibility, device-admin, overlay, usage-stats. Notification *listener* access is a separate, user-granted system setting for the optional music/notifications features, protected by `BIND_NOTIFICATION_LISTENER_SERVICE`.

## Exported components
HomeActivity (exported, required for HOME role), ContextBootReceiver (exported,
protected system broadcasts only — BOOT_COMPLETED/TIME_SET/TIMEZONE_CHANGED
cannot be sent by third-party apps), two AppWidget providers (exported by
platform requirement), MusicNotificationListener (exported + BIND permission,
platform-correct). Everything else — Settings, Studio host, Wallpaper,
Onboarding, Music access/control, schedule receiver — is `exported=false`.
All internally created PendingIntents use `FLAG_IMMUTABLE`.

## Data & storage
All data is local: DataStore prefs (themes, layouts, hidden/pinned lists,
schedules, gestures), launch-history + diagnostics SQLite, imported wallpaper
files in `filesDir`. Launch history exists for frecency/suggestions, is
disclosed in the privacy draft, never leaves the device by the app's own code.
Android cloud backup is limited to sharedprefs + datastore; the diagnostics DB
moves only on direct device-to-device transfer (explicit rules files).
No accounts, no identifiers, no contacts/location. Ordinary preferences are
intentionally **not** encrypted (no sensitive payload warrants Keystore risk);
hidden-apps use the system credential prompt instead of stored secrets.

## Logging
`LumaLog` is debug-gated (v/d/i stripped in release; w/e survive). Audit of log
call sites: messages carry package names and exception classes only — no
notification text, queries, tokens, paths of user files, or credentials.

## Dependencies & release config
Pinned catalog (AGP 8.13.2, Kotlin 2.2.21, Compose, DataStore, RecyclerView,
coroutines, lifecycle) from `google()`/`mavenCentral()` only — no third-party
repositories, no analytics surface. Release: R8 + resource shrinking, no
`debuggable` override, no cleartext-traffic configuration (and no network to
apply it to), `allowBackup=true` with explicit include-only rules.

## Findings
- **CRITICAL/HIGH: none found.** No code fix was required by this audit.
- **LOW (accepted, documented):** release `w/e` logs include package names;
  exposure is minimal (logcat is not world-readable on modern Android) and the
  diagnostic value is real. No change made.
- **Process reminder (owner):** `PRIVACY_POLICY_DRAFT.md` still needs the
  public URL assigned and Play Data-safety answers filed to match this audit
  (no data shared; data collected on-device = app interactions for frecency,
  diagnostics — both excludable per Play's launcher guidance after review).

## Unverified — required before any "security-ready" claim
Gradle `--dependency` report + vulnerability scan, Android lint, a MobSF (or
equivalent) pass over a built APK, exported-component intent testing on
device, and restore-tampering tests with hand-edited backups. Until those run,
this is a source-level assessment, not a certification.
