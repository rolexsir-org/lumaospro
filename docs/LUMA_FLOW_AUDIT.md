# Luma Flow drawer architecture (historical)

Superseded by [OBSIDIAN](OBSIDIAN_AUDIT.md); retained as the audit of the earlier branch changes.

The drawer is a view-based sheet in `activity_home.xml`. `DrawerController`
coordinates its reversible spring/drag, keyboard, filtering controls, list
updates and accessibility index. It shares the launcher's motion and glass
policies; reduced motion avoids the additional sheet scale.

`AppRepository` discovers launchable activities via the platform. The
`LauncherViewModel` combines that snapshot with the active profile, Vault and
profile-hidden keys, search, persisted sort and pins, local launch history,
shortcut availability and preferences. `DrawerModel` builds the rows off the
main thread; `DrawerAdapter` renders them in the existing list or grid layout.
The Activity handles row taps and long presses, including Focus restrictions,
shortcut launch, app menus and pin/unpin. No new package query, launch path,
permission or persistence store is introduced by Flow.

Recently opened is derived from the existing on-device launch records, ordered
by last launch, bounded by the existing suggestion count, and shown only when
learning from launches is enabled. It excludes hidden, uninstalled and pinned
apps. Suggestions remain independently ranked and the complete app collection
remains available below the curated sections; therefore an app can also appear
in more than one section. Search and category views do not include the curated
sections. The A–Z positions are calculated after all section rows are built.

The new visual language is scoped to the drawer: a blue editorial signature,
larger title and search, quieter section markers and compact icon shelves for curated apps in list mode,
plus a small scale component to the existing sheet transition. Theme-derived surfaces,
contrast, icon settings, list/grid preference and system font scaling remain in
use. App launch, pin and filter behaviour are deliberately shared with the
existing implementation.
