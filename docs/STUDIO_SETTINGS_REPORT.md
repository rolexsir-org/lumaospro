# Studio & Settings upgrade — report (addendum, 2026-11-04)

Audit: `docs/STUDIO_SETTINGS_AUDIT.md`.

## The central fix: preview and applied are now actually separate
Studio already had a draft + live preview, but nearly every control called
`applyStudio()` immediately, so previewing overwrote the applied configuration
and Apply/Revert were dead controls. Now **every Studio control edits the draft
only**; `SettingsViewModel.applyStudio(draft)` (the existing atomic
`prefs.applyStudioSettings`) is the single persistence path. Revert discards to
the applied configuration. Theme tiles, layout/dock segmented controls, effects,
motion, backdrop, icon shape/style/pack, living-icons switch, accent swatches,
typography, retained palettes and per-profile themes all stage as previews.

## What was added
- **Undo/redo**: pure-Kotlin `StudioHistory<T>` (edit/undo/redo/reset) driving
  Undo/Redo buttons; a fresh history starts after Apply lands so Undo cannot
  resurrect an applied config as "unsaved". Covered by `StudioHistoryTest`
  (round-trip, redo-branch clearing, no-op edits, preview-never-mutates-applied).
- **Configuration management**: Save-as-preset / Restore-preset for the draft,
  held in `SettingsViewModel` for the session (duplicating = saving a copy of
  the current draft). No new stored keys/schema: surviving a restart still goes
  through Apply → existing prefs; portable import/export remains the existing
  SAF backup on the Settings tab (UI copy states this honestly).
- **Live configuration overview**: the summary card now reads Layout · Dock ·
  Material · Icons & Motion · **Backdrop · Icon pack** from the same
  draft/resolved theme (no duplicated storage), is headed
  "Applied configuration"/"Preview — not applied" with the dirty state, and its
  values wrap to two lines instead of silently truncating. Flagship tile names
  also wrap to two lines.
- **Top navigation**: tab labels are single-line (`maxLines=1, softWrap=false`,
  ellipsis) so "Diagnostics" can no longer wrap and grow the header; selected
  tab is semibold with proper `selected` semantics; the glass top bar gains a
  subtitle naming the current destination. All three destinations, tab state
  persistence and insets behaviour unchanged.
- **Accessibility**: segmented controls now expose RadioButton role + selected
  state + label (they were click-only surfaces), matching theme tiles/swatches.

## Preserved
All existing settings, saved preferences, theme definitions (incl. Luma
Glass/Mono), instant-apply behaviour of the separate Settings tab, navigation
destinations, preview icon rendering, backup import/export, persistence
mechanisms and data schemas. No Gradle/manifest changes.

## Verification — actually executed
- tree-sitter Kotlin parse: **0 errors** in all modified Kotlin files;
  `strings_v2.xml` parses as XML; full diff reviewed control-by-control to
  confirm no `applyStudio` call remains in any Studio control (only the Apply
  button), and that StudioHistory semantics match the new unit tests.

## Not executed / limitations (honest)
- No Gradle build, unit-test run or device test was possible in this sandbox
  (no JDK/Android SDK; toolchain hosts unreachable, as in previous passes).
  Run `./build.sh test lint assembleDebug` on a toolchain machine.
- The Studio preset is session-only by design (no schema change); use Apply
  or backup export for permanence — the UI says so.
- Requires device checks: tab layout at 1.3×+ font scale on a narrow phone,
  undo/redo discoverability, and preview-vs-applied behaviour after process
  restart (applied config must be exactly what Apply last wrote).
