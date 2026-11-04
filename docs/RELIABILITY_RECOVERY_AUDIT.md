# LumaOS Pro — launcher reliability & disaster-recovery audit (2026-11-04)

Method: source-level tracing of startup, layout decode/recovery, DataStore
reads/writes, widget host, icon pipeline, wallpaper store, backup/restore and
lifecycle cleanup. **No scenario below was executed on a device or emulator** —
this sandbox has no JDK/Android SDK/ADB. "Traced" means the code path was read
and reasoned about; it is not a test result.

## Confirmed bug found & fixed (P1)
**Corrupted preferences file left the launcher permanently read-only.**
Every `LumaPrefs` read catches `IOException` and falls back to defaults, so a
corrupted `lumaos_settings` file did not crash startup. But the
`preferencesDataStore` delegate had **no corruption handler**, so every
subsequent `DataStore.edit` kept throwing `CorruptionException`: theme/layout/
Studio changes could never persist again, and unguarded write call sites risked
crashing — effectively a settings crash-loop after a single interrupted write
or storage fault (precisely the low-storage/power-loss scenario).
Fix (`LumaPrefs.kt`): the production delegate now uses
`ReplaceFileCorruptionHandler` (`lumaosPrefsCorruptionHandler`), which is
invoked **only** when the file is actually unparseable — never on a healthy
file — logs the event (no user data), and resets to empty preferences, the
platform's documented recovery. Readable user data is never discarded by this
path; an unparseable proto file has no readable data to preserve, and the
alternative was a launcher that could never save again.
Regression tests (`PreferencesCorruptionRecoveryTest`): garbage-bytes store
recovers to defaults and accepts writes; a healthy store is never reset.
*(Implemented; not yet executed — no toolchain here.)*

## Recovery mechanisms already present (traced, not device-verified)
- Layout: forgiving codec returns EMPTY on unknown/partial data and
  `canSafelyRewrite` blocks overwriting layouts it cannot reproduce;
  dedupe-healing and first-run seeding only write when the stored value is
  unchanged (compare-and-set), so user edits win races. Duplicates are repaired
  in place, never wiped.
- Widgets: reconcile strips ids the host no longer reports, remaps ids after
  system widget restore, provider lookups are `runCatching`-guarded.
- Icons/wallpaper: missing icons are negative-cached (no decode loop) and
  omitted, never faked; missing wallpaper falls back to the theme backdrop;
  wallpaper imports are size/pixel-limited and decode-verified.
- Databases: launch-history v1 no-op upgrade; diagnostics DB recreates on
  upgrade by design (disposable measurements, documented in code).
- Backup: validate-before-apply, single atomic DataStore edit on restore,
  device-local photo wallpaper preserved, failed export/import reports and
  leaves current settings intact.
- Lifecycle: HomeActivity unregisters receivers, shuts down widget picker,
  shade and icon loader in `onDestroy`; package callbacks re-register on
  foregrounding; boot receiver rebuilds only the next inexact alarm.

## Recovery matrix (status)
| Scenario | Status |
|---|---|
| App crash / process death | Traced: prefs+layout are disk-backed, seed-if-empty only; NOT device-tested |
| Device reboot | Traced: boot receiver + disk state; NOT tested |
| Force-stop & relaunch | Traced; NOT tested |
| Missing/uninstalled app | Traced: launch failures caught, icons negative-cached; NOT tested |
| Wallpaper missing/corrupt | Traced with fallbacks; NOT tested |
| Widget provider unavailable | Traced: reconcile + guards; NOT tested |
| Corrupted configuration | **Fixed at code level + regression tests written (not yet run)** |
| Low storage | Partially traced (corruption fix covers interrupted writes; DataStore writes are atomic rename); NOT tested |
| App update preserving config | Traced: storage keys stable, legacy theme migration in-code; NOT tested |
| Backup restore | Traced: validate-then-atomic-apply; NOT executed |
| Default launcher changed | Traced: role re-resolved on every Settings resume; NOT tested |
| Permission revoked | Traced: notification/DnD state re-synced on resume, guards on use; NOT tested |

## Unresolved / risks
- **P2 (unverified):** no in-code uncaught-exception/crash-loop counter at
  startup; if a device-only crash exists in HomeActivity init it would loop —
  undetectable from static analysis. Requires logcat on device.
- **P3:** `DiagnosticsStore.onUpgrade` drops measurements (intentional,
  documented); long-term memory/perf (drawer open/close, theme switching,
  large app lists) unmeasured — no profiler data exists, none is claimed.
- All previous-phase caveats stand: `./build.sh test` (which runs the new
  recovery tests), `lint`, `assembleDebug`, and the device matrix are
  owner-run. **Not production-ready until executed.**

Files modified this phase: `core/prefs/LumaPrefs.kt`,
`core/prefs/PreferencesCorruptionRecoveryTest.kt` (new), this report.
No schema, manifest, signing or identity changes; nothing committed.
