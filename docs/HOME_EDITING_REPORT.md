# Home editing redesign — engineering report

Scope: the home screen's editing experience only. No launcher rebuild, no new
dependency, no schema or persistence change, and no change to how the home screen
looks or behaves outside a session.

---

## 1. Root cause

Editing was modelled as a **full-screen chrome layout** rather than a state of the
desktop. `activity_home.xml` contained two permanent rows — a header card
(`edit_top_bar`: "Edit home" title, `edit_hint` paragraph, Done pill) and a
five-button toolbar (`edit_toolbar`: Widgets, Layout, Studio, Wallpaper,
Settings) — and `HomeActivity.applyChromeInsets()` reserved their measured
heights by padding the desktop, the grid, the dock and the drop-zone guide.

Three consequences followed, and all three were the reported symptoms:

1. **The workspace was never visually dominant.** Two opaque cards and several
   instructional lines surrounded a grid that shrank to make room for them, at
   rest and while editing.
2. **The layout jumped on entry and exit.** Chrome heights were measured after
   the first layout pass, so the first frame of a session moved the grid, the
   dock and the clock.
3. **Two gesture handlers competed for the same long-press.** `ItemTouchHelper`
   had `isLongPressDragEnabled()` returning `true` *and* the cells had their own
   `setOnLongClickListener` that opened an item menu, so a long press could fire
   both paths — lift and menu — in the same gesture.

The redesign removes the model, not just its visibility: the chrome rows are
deleted from the layout, the tokens, the drawables, the strings and the inset
authority, and editing is expressed as one contextual capsule plus affordances
that exist only while an item is in the air.

## 2. What the user gets instead

| Moment | Behaviour |
| --- | --- |
| Long-press empty workspace | Editing starts: one capsule cross-fades into the search row (140 ms), the wallpaper dims, nothing else moves. |
| Long-press an app or widget | The icon lifts immediately and follows the finger. No menu, no sheet, no second handler. Editing begins in the same gesture. |
| Release without moving | The item settles back and its contextual menu opens. |
| Drag | Dashed drop zones appear on free slots; the remove target appears under the status bar and swells when armed. |
| Tap an app while editing | Its contextual menu. |
| Tap outside / Back / Home / Done | The session ends and the committed order is kept. |

The capsule holds the same five features plus Done, icon-only, with
`contentDescription` labels: Widgets → `showWidgetPicker()`, Layout →
`showLayoutMenu()`, Studio → `openStudio()`, Wallpaper → `openWallpaperPicker()`,
Settings → `openSettings()`. Nothing was dropped; page add/remove moved from the
dot row (where the buttons grew the row mid-session) into the Layout menu.

## 3. Files changed

Removed chrome:

- `app/src/main/res/layout/activity_home.xml` — `edit_top_bar` and
  `edit_toolbar` deleted; `edit_action_bar` (~one search row) and a floating
  `edit_remove_target` remain.
- `app/src/main/res/drawable/` — `bg_edit_card.xml`, `bg_edit_pill.xml`,
  `bg_edit_toolbar.xml` deleted; `bg_edit_action_bar.xml` added (the pre-theme
  fallback; `GlassViews` replaces it at runtime).
- `app/src/main/res/values/strings_v2.xml`, `dimens.xml` — instructional strings
  and every `luma_edit_*` token that belonged to the cards removed; the new set
  added.

Logic:

- `launcher/home/EditModeController.kt` — rewritten around a contextual session
  (`enter/exit/exitImmediately/toggle`, `beginItemDrag/beginDockDrag`,
  `isLongPressDragEnabled = false` + `startDrag()`), two `ItemTouchHelper`s (grid
  and dock), hold-without-move → menu, armed remove/merge regions, theme-driven
  capsule and guide styling.
- `launcher/home/EditModeDesign.kt` — the contextual token set (capsule 48 dp /
  24 dp radius / 2 dp inset / 48 dp targets / 22 dp glyphs, 52 dp remove target,
  20 dp zones at 12 % outline and 2 % fill, 24 dp margin, 55 % scrim).
- `launcher/home/PlacementGuideView.kt` — drop zones take the theme's
  `SETTINGS_CONTROL` content colour instead of hard-coded white.
- `launcher/home/HomeGridAdapter.kt` — an `editMode` change while an item is
  lifted is deferred to `endDrag()`, so the row under the finger is never
  re-bound mid-gesture.
- `launcher/home/DesktopLayout.kt` — the side-by-side right column is anchored on
  `home_action_slot` (the pill and the capsule are two states of one row).
- `launcher/HomeActivity.kt` — banner/toolbar wiring deleted; `applyChromeInsets`
  no longer depends on editing; long-click → drag; tap-to-dismiss, `isOverItem`,
  page-dot rule, Layout-menu page management, `onStop` → `exitImmediately()`.
- `launcher/LauncherViewModel.kt` — `moveDockItem()` so a dock reorder inside a
  session persists through `HomeLayoutOps.moveInDock`.

Tests and docs: `EditModeDesignTest` rewritten for the new tokens;
`HomeActivityRuntimeTest` gained three assertions (no legacy ids, contextual
surfaces absent at rest, no reserved space across a session);
`docs/DESIGN_SYSTEM.md` §7.1/§10, `docs/QA_CHECKLIST.md` (new D6b) and
`CHANGELOG.md` updated.

## 4. Verification performed here, and its limits

Executed in this sandbox:

- All Kotlin sources under `app/src/main` and the two changed test files were
  parsed with tree-sitter's Kotlin grammar: no new syntax errors. `HomeActivity`
  reports the same 14 pre-existing grammar artefacts (assignment expressions
  inside `apply {}` blocks) as the committed file — three fewer than before
  because the removed typeface lines are gone.
- `activity_home.xml`, `dimens.xml` and `strings_v2.xml` parse.
- Delimiter/string/comment balance was checked on every edited Kotlin file.
- A repository-wide grep confirms no reference to `edit_top_bar`, `edit_toolbar`,
  `edit_title`, `edit_hint`, `headerHeightPx`, `toolbarHeightPx`,
  `onEditChromeChanged` or `ToolbarAction` survives.

**Not executed:** `./build.sh assembleDebug`, `./gradlew test`, `./gradlew lint`,
any APK install and any on-device run. This sandbox has no JDK, no Gradle, no
Android SDK and no network access to the hosts that serve them, so compile
errors, Robolectric results and lint output are *not* claimed. Run:

```
./build.sh assembleDebug
./gradlew test
./gradlew lint
```

and walk `docs/QA_CHECKLIST.md` §D6b on a device before treating this as
release-ready.

## 5. Known follow-ups for a build-capable environment

1. Compile and fix whatever the Kotlin compiler disagrees with — the risk sits in
   the new `EditModeController` surface (callback signatures, `GlassRole` lookup,
   `LumaSpring`/`SpringAnimator` usage).
2. Run `EditModeDesignTest` and `HomeActivityRuntimeTest`; the three new
   Robolectric assertions are the first automated guard that the legacy chrome
   cannot come back.
3. On device: check the first and last frame of a session for any grid re-measure
   (the design intention is that there is none), the cross-fade of
   pill ↔ capsule, and drag over the remove target at the top of the screen.
