# Wallpaper Studio / Luma Glass Music UI repair

## Status

Source repair implemented. **Not runtime-verified or release-certified.** Android build, JVM tests, lint, device interactions, and application screenshots remain blocked by the toolchain/device limitations below. Do not treat this report as evidence that the final on-device acceptance criterion has passed.

## Diagnosis and navigation trace

### Wallpaper

- Home's `openWallpaperPicker()` in `launcher/HomeActivity.kt` starts `WallpaperActivity` explicitly. Settings passes the same explicit destination through `onOpenWallpaper` in `settings/SettingsActivity.kt`. No incorrect destination was found.
- `WallpaperActivity` already composed the gallery, category/collection filters, preview, target selection, import, favorites, and dynamic-theme controls.
- The header's `LumaGlassTopBar` uses a Row with unweighted navigation followed by a weighted title Column. Its Back navigation is `LumaGlassChoice`, whose content uses `fillMaxWidth()`. Navigation could consume all available Row width before the title was measured. The title/subtitle were then laid out at effectively zero width without line limits. This can expand the header to the viewport height, vertically centering Back in the oversized surface and leaving no useful viewport for the gallery.
- This is a sizing defect, not evidence of missing artwork, a placeholder destination, or a failed image loader. Runtime reproduction could not be performed in this sandbox.

### Music

- `settings/SettingsScreen.kt` opens `MusicAccessActivity`. Empty widget content also opens it through `MusicBridge.accessIntent()`.
- This Activity is intentionally the optional notification-access/setup destination for a real Android AppWidget, not a standalone player. It used the same top bar with a fill-width Back choice, causing the same width-starvation/oversized-header defect.
- Actual playback is implemented in `widgets/music/MusicBridge.kt` and `MusicWidgetProvider.kt` using `widget_luma_music.xml`. Those integrations were not replaced.

### Shared fix and scope

`LumaGlassTopBar` now bounds navigation to 96dp before measuring the weighted title and caps title/subtitle to two lines with ellipsis. Glass drawing, themes, general choice sizing, and other screen layouts are unchanged. The direct consumers are Wallpaper, Music Access, Settings, and `LumaGlassScaffold`. Settings uses no navigation slot; Studio and Diagnostics are tabs in that Settings shell. Runtime regression of these consumers is still required.

## Implemented behavior

### Wallpaper

- Responsive 2–5-column gallery replaces the horizontal strip, using the existing screen's vertical scroll. No unbounded nested lazy grid was introduced. The small existing catalog uses keyed rows and the existing bounded thumbnail cache.
- Real thumbnails retain crop/focal alignment, active badges, selection, and favorites.
- Thumbnail tap selects that exact entry and opens a full-screen preview dialog with Back and Apply.
- Apply opens the existing target-selection flow: Android Home, Android Home + Lock, Android Lock, or Luma Home only.
- Gallery initialization failure offers retry; loading, empty filters, thumbnail decode failure, and preview decode failure are explicit states rather than silent colored placeholders.
- Existing inline launcher/theme preview and theme controls remain available below the gallery.

### Assets and loading

There are **30 distinct curated WebP files** under `app/src/main/res/drawable-nodpi/luma_wall_01_…webp` through `luma_wall_30_…webp`, across Liquid Dreams, Aurora, Midnight, Minimal, Himalayan Essence, and Dark Luxe. Their actual dimensions range from 702×1520 to 851×1844. Legacy Aurora/Zen/Void resources remain mapped for old IDs; the current gallery catalog has 30 entries, not 33.

`WallpaperCatalog.bundled()` supplies stable entries; `WallpaperArtResources.resForEntry()` maps them to packaged drawable resources. `WallpaperImageStore.entries()` appends private imported photos. `preview()` decodes off-main-thread with sampling and its existing LRU cache. No assets, fake entries, remote image source, or new storage scheme were added.

### Application, persistence, and themes (source inspection, not device results)

The existing `WallpaperImageStore.apply()` checks platform support/policy, decodes the selected image, and uses `WallpaperManager.setBitmap()` with the requested system/lock flags. A nonpositive result is an error. Luma-only application validates the image without changing Android's wallpaper.

The existing Activity handler only records the home selection after successful application. `LumaPrefs.recordAppliedWallpaper()` atomically stores its stable ID, system wallpaper ID when relevant, and backdrop using existing DataStore keys. The chosen entry falls back to the persisted active ID when reopening. Lock-only application intentionally does not replace the launcher/home selection. Favorites, theme settings, and cached seeds retain their existing schema. Dynamic wallpaper colors still use the existing theme repository and seeds; no competing engine was added. This does not promise Android system color changes.

### Music setup and integration (source inspection, not device results)

- Screen title is Luma Glass Music; a setup card describes adding the actual home widget and shows the number of installed widget instances.
- The existing accurate access check, notification disclosure, settings action with failure feedback, and `onResume()` refresh remain intact. The installed-widget count also refreshes on resume.
- `MusicBridge.hasAccess()` queries Android's listener grant for the actual listener component. `refresh()` obtains active sessions only with access and installed widgets, registers callbacks, and prefers playing then paused sessions.
- Existing RemoteViews rendering distinguishes missing permission, unavailable access, and no active session. Metadata/artwork and transport controls come from real sessions; commands re-check session selection and advertised support. No fake metadata, no-op preview controls, extra permissions, or new player were introduced.
- This repair preserves the intended setup purpose. It does **not** add an in-Activity playback player or claim to have obtained a live session here.

## Checks executed

| Check | Result |
|---|---|
| Inspect destinations, glass layout, assets, preview/apply/preferences/theme integration, music bridge/manifest/tests | Completed source review |
| Host Pillow decode of all 30 curated WebPs | PASS: all fully decoded, nonzero dimensions |
| SHA-256 uniqueness of 30 curated files | PASS: 30 unique files |
| Parse updated strings XML | PASS |
| `git diff --check` | PASS |
| `./build.sh assembleDebug` | BLOCKED before Gradle: missing toolchain; provisioning curl to api.adoptium.net failed, exit 35, SSL_ERROR_SYSCALL |
| `./build.sh testDebugUnitTest` | BLOCKED by same provisioning failure; no JUnit tests executed |
| `./build.sh lintDebug` | BLOCKED by same provisioning failure; lint did not execute |
| Emulator/device, adb/logcat, Layout Inspector | UNAVAILABLE: no installed Android SDK/adb/emulator/Java or connected device; no /dev/kvm |
| Running-application screenshots and comparison | NOT CAPTURED; no running Android application, and no screenshot attachments were present in this conversation |

The sandbox permits outbound access only to a small host allowlist; the repository's toolchain provisioner requires Adoptium, Gradle, and Google Android hosts outside it. Build scripts were not altered to hide the failure. Pillow was installed only into an external cache for the host asset check, not into the project.

## Regression tests added (not executed)

- `GlassTopBarLayoutTest`: real Compose/Robolectric measurement for Wallpaper at 360dp, Music at 320dp/2× font scale, and Settings without navigation. Asserts visible destination content, meaningful title width, compact header height, and functioning Back callback.
- `WallpaperImageStoreRuntimeTest`: every current gallery entry must decode a thumbnail and pass the existing Luma-only apply validation.
- Compose UI test libraries use the project's existing 1.7.8 Compose version. No existing dependency was upgraded.
- Existing catalog, bitmap/import, music logic, and theme matrix tests remain unchanged apart from the additional bitmap test.

These cover the shared layout and assets, **not** end-to-end navigation, permission settings, system wallpaper application, or persistence across process death.

## Exact changed files

1. `app/src/main/java/com/lumaos/pro/launcher/ui/glass/GlassComponents.kt`
2. `app/src/main/java/com/lumaos/pro/launcher/wallpaper/WallpaperActivity.kt`
3. `app/src/main/java/com/lumaos/pro/launcher/widgets/music/MusicAccessActivity.kt`
4. `app/src/main/res/values/strings_wallpaper_music.xml`
5. `app/src/test/java/com/lumaos/pro/launcher/ui/glass/GlassTopBarLayoutTest.kt`
6. `app/src/test/java/com/lumaos/pro/launcher/robolectric/WallpaperImageStoreRuntimeTest.kt`
7. `app/build.gradle.kts`
8. `gradle/libs.versions.toml`
9. `docs/CRITICAL_UI_REPAIR_REPORT.md`

## Required follow-up on an Android-capable runner/device

1. Run `./build.sh assembleDebug testDebugUnitTest lintDebug`; resolve any compiler/test/lint findings before release.
2. Open Choose Wallpaper from Settings and Home; confirm thumbnails, vertical scrolling, preview identity/Back, and each apply target. Return Home/reopen Studio and verify active selection and preferences; also restart the process. Test unreadable photo and empty filters.
3. Open Music setup; verify denied/granted/revoked access, opening Android settings and return refresh, installed widget count, and Back. With an installed widget test no sessions, unavailable listener, real paused/playing sessions, metadata/artwork updates, and only supported transport actions.
4. Check 320/360/large-width devices, landscape, increased density/font scale, status/navigation insets, Settings/Studio/Diagnostics tabs, existing home widgets, and theme persistence.
5. Capture actual application screenshots of gallery, full-screen preview, music setup, and live/empty widget states. Compare against original device captures when supplied.

The code changes address the identified shared defect, but final runtime acceptance remains outstanding until these checks are executed.
