# LumaOS Pro — bug hunt & QA report (2026-11-04)

Method: full-source static tracing (null-safety, threading, lifecycle/receivers,
resource cross-check of 641 `R.*` references, exhaustive-`when` review, diff
review of all uncommitted changes), plus tree-sitter parse of every Kotlin file.
**No Gradle build, unit test, logcat or device run was possible** — this sandbox
has no JDK/Android SDK and the toolchain hosts are unreachable. Nothing below
that requires execution is claimed as verified.

## Confirmed bugs found & fixed
1. **P2 — Main-thread blocking disk read at cold start.**
   `LumaThemeManager._palette` was initialised with `runBlocking { prefs…first() }`
   during construction (Application.onCreate path): a blocking DataStore read on
   the main thread — cold-start jank, StrictMode violation, ANR risk on slow
   storage. Fixed: palette starts at `LumaWallpaperPalette.DEFAULT`; the cached
   value loads asynchronously in `start()` and never overwrites a freshly
   extracted palette. (`LumaThemeManager.kt`)
2. **P2 — Crash on truncated cached wallpaper palette.**
   `LumaWallpaperPalette.decode` guarded `parts.size < 11` but then read
   `parts[11]`; `encode()` writes 12 fields, so an 11-field (truncated/corrupt)
   cache string threw `IndexOutOfBoundsException` in a function whose contract
   is "return null on bad input". Callers happened to wrap it in `runCatching`,
   which masked the defect rather than fixing it. Fixed the guard to `< 12`
   and added a regression test (`paletteDecodeRejectsTruncatedCacheInsteadOfCrashing`)
   covering truncated, garbage, null and round-trip inputs.

## Suspected / minor (not fixed — would need execution to confirm)
- **P3** `IconCache.trim()` ends with `cache.resize(cache.maxSize())`, a no-op;
  eviction itself works via `trimToSize`. Harmless, left untouched.
- **P3** `IconLoader.renderSync` is dead code (no callers). Left in place;
  removal is a cleanup, not a bug fix.
- Six files fail the *tree-sitter* Kotlin grammar parse (e.g. `HomeGridAdapter`)
  but are untouched, pre-existing code — this is a parser limitation, **not**
  evidence of compile errors; only a real `kotlinc` run can settle it.

## Areas audited with no confirmed defect
- Backup/restore: whitelist + type coercion before every cast; malformed files
  are skipped/rejected, never partially written (`BackupCodec`).
- Receivers: dynamic receivers are either Activity-scoped with matching
  unregister in `onDestroy`, or app-singleton scoped; export flags are correct
  for API 33+. PendingIntents, cursor `use {}`, widget-provider null handling,
  icon decode threading (single background executor, spec invalidation) all
  traced and consistent.
- My own previous stages re-audited: Studio staged-draft/undo/redo flow,
  Glass/Mono exhaustive `when` sites, Diagnostics card, preview icon-size —
  no defects found beyond those already fixed in-stage.
- Resource cross-check: all 641 referenced strings/drawables/layouts/styles
  resolve (one `android.R` false positive).

## Still unverified (requires toolchain + device)
Everything in the master regression checklist: build (`assembleDebug/Release`),
existing unit/Robolectric suites, cold/warm start, drawer scroll performance,
lifecycle (process kill, reboot, package install/uninstall, widget provider
removal), font-scaling and rotation rendering, logcat. **LumaOS Pro is not
declared production-ready by this report.** Owner commands:
`./build.sh test`, `./build.sh lint`, `./build.sh assembleDebug`, then the
device matrix in `docs/QA_CHECKLIST.md`.

## Files modified this phase
`ui/theme/LumaThemeManager.kt` (bugs 1–2), `LumaTokensAndThemeManagerTest.kt`
(regression test), this report. Working tree uncommitted per authorization rule.
