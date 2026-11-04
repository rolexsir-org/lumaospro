# Studio & Settings upgrade — audit (addendum, 2026-11-04)

## Existing architecture (verified from source)
- `SettingsActivity`: edge-to-edge Scaffold, `LumaGlassTopBar` + Material3
  `PrimaryTabRow` with three destinations (Settings / Studio / Diagnostics),
  `rememberSaveable` tab state, system-bar icon polarity follows the theme.
- `StudioRoot` already holds a **draft** (`StudioConfiguration`) separate from the
  persisted `studioConfiguration` flow, renders a real live preview (`HomePreview`
  fed by `loadPreviewIcons`, which renders the user's actual home/dock apps), and
  has Apply/Revert + dirty state.
- Persistence is single-sourced: `applyStudio()` → `prefs.applyStudioSettings(...)`
  (atomic DataStore edit). Backup export/import (SAF) already exists on Settings.

## Defects against the addendum
1. **Preview is not actually separate**: nearly every Studio control calls
   `viewModel.applyStudio(updated)` immediately after setting the draft, so
   previewing a theme overwrites the applied configuration and Apply/Revert are
   dead controls. (Addendum B/E/F — the central fix.)
2. Tab labels use default `Text(title)`: "Diagnostics" can wrap to two lines on
   narrow widths / large font scales, breaking the header height.
3. No undo/redo; no save/restore of a candidate configuration; reset exists only
   as whole-backup restore on the Settings tab.
4. `LauncherEffectsReadoutCard` truncates values (`maxLines = 1` + Ellipsis) and
   omits backdrop/icon-pack; flagship tile names hard-clip at one line.
5. Segmented controls lack radio semantics for accessibility.

## Plan (no schema, API or destination changes)
- Studio controls mutate the draft only; **Apply** is the single persistence
  path, **Revert** discards to the applied configuration. Theme tiles become
  preview-before-apply.
- Pure-Kotlin `StudioHistory<T>` (undo/redo stacks) + JVM unit tests.
- Session preset in `SettingsViewModel` (save/restore/duplicate of the draft);
  surviving a restart still goes through Apply → existing prefs, and portable
  export/import remains the existing backup flow. No new stored keys.
- Single-line tab labels with selected semantics/weight; expanded live
  configuration summary (layout, dock, material, icons, motion, backdrop, pack)
  read from the same draft/resolved theme — no duplicated storage.
- Files: `SettingsActivity.kt`, `studio/StudioScreen.kt`,
  `studio/StudioHistory.kt` (new), `SettingsViewModel.kt`, strings, tests, docs.
