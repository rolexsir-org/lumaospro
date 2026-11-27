# LumaOS Pro 1.5.1 widget system — implementation audit

The checksummed debug APKs recorded here were built before the version bump; their embedded version is 1.5.0. Rebuild from current source to verify a 1.5.1 APK.

## Architecture inspected

LumaOS already had a platform `AppWidgetHost`, provider discovery through `AppWidgetManager`, binding consent, provider configuration, a widget picker, and a row-based planner and editor. `HomeActivity`, `HomeGridAdapter`, `EditModeController`, and the persisted `HomeLayout` own placement, resize, drag/reorder, removal with undo, orphan cleanup, and restore-ID reconciliation. This implementation uses the same host and storage for bundled and third-party widgets; it does not add a parallel layout engine. Luma Home widgets occupy the full feed width and resize vertically. External hosts can offer width resizing as well.

The previous music and battery widgets were already native. Their media-session controls and battery source remain in use. The six additions are Android `AppWidgetProvider` receivers with real `RemoteViews` layouts, not web previews.

## Widget inventory

| Widget | Actual source | Lifecycle and failure behavior |
| --- | --- | --- |
| Digital clock | Android `TextClock` and system time/date settings | Host updates the time and date without polling; honors the system 12/24-hour preference. |
| Analog clock | Android `AnalogClock`, vector dial and hands, and `TextClock` for the date | Host updates the hands automatically; the clock scales with available height. |
| Calendar | Android Calendar Provider (`CalendarContract.Instances`) | Shows a visible, non-canceled event within seven days; explicit read-only permission, clear no-events and unavailable states. |
| Weather | Open-Meteo current temperature and weather code | User-entered coordinates, no device-location request. `JobScheduler` runs HTTPS with a network constraint and retry backoff. Shows dated cached readings or a no-data state; data more than 24 hours old is not shown as current. |
| Music | Existing Android `MediaSessionManager` bridge | Optional Notification Access. Existing previous/play-pause/next controls only act on a supported, active Android media session. No demo metadata. |
| Battery | Existing Android sticky battery reading | Real battery level and charge status; launcher refresh on device battery changes and native periodic refresh on other hosts. |
| Internal storage | `StatFs` on Android's internal data volume | Shows actual bytes available and total in device units. No broad media/storage permission. |
| Screen time | Android `UsageStatsManager` | Optional Usage Access. API 28+ counts interactive-screen intervals clipped to local midnight; API 26–27 uses the platform's reported app foreground duration and labels it differently. |

## Presentation and persistence

- All eight providers use the shared theme-aware glass plate renderer, with a per-instance corner radius or the theme default. The new layouts share spacing and typography, derive text colors and accents from the resolved theme, and adapt to the host's reported widget size. The plate is translucent; it does not promise real wallpaper blur inside a third-party host.
- Luma Home retains its existing spring item transitions and reduced-motion behavior for placement and movement. The clocks tick in the Android host. Content updates use Android `RemoteViews` replacement; third-party hosts own their animation policy.
- Widget settings are stored per platform widget ID. The provider remaps settings on restore and removes them when the widget ID is deleted. The Home layout continues to store its ID, page, and row span through the existing layout codec. Weather readings are excluded from Android backup.
- Refreshes use host update/resize broadcasts, theme changes, and return to Luma Home. Periodic native updates are inexact and can be delayed by Android. The weather network request does not extend a widget broadcast. Requests include only the manually chosen coordinates and the forecast fields; Calendar, Usage Access, notifications, and Vault do not feed the network code.
- The default public Open-Meteo endpoint is suitable for non-commercial testing. Before commercial distribution, review Open-Meteo's terms and obtain the appropriate endpoint/license or replace the transport. Weather failure/denial must remain truthful regardless of endpoint.

## Build and APK verification

Standard production path: `./build.sh assembleDebug` or the repository's documented release build. To verify an actual artifact rather than source XML:

```sh
python3 toolchain/checks/verify_widget_apk.py path/to/app.apk --aapt2 /path/to/aapt2
```

The checker reads the packaged manifest, links each provider's metadata to its compiled native layout, and checks the DEX class definitions for the eight providers, configuration activity, weather job, and widget host. It does not assert the providers rendered successfully on a phone.

### Results in this workspace

- Standard Gradle setup was blocked by the sandbox's JDK download restriction. An alternate local Android 36 / Kotlin / D8 build compiled the current source and produced `artifacts/app-debug.apk`. This uses cached dependencies and is a debug-signed test artifact, not a Gradle release build or a replacement for an installed release APK.
- `ApkVerifier` passed APK signature schemes v2 and v3. The packaged-widget checker passed for all eight providers and for the configuration activity, weather job, and host class.
- A JVM smoke check passed for coordinate validation and screen-time accounting across midnight. Robolectric tests for native layout inflation, persisted configuration and forecast parsing were added but not run in this workspace.
- SHA-256: `08c354d993bb9fc454a7dc72ada84d6cec8c131f032609738b45f03bcf90e9ef`

## Physical-device sign-off (pending)

No physical Android device is attached to this workspace. Install a debug build on a test device, choose Luma Home, and run the following checks. Repeat on an external launcher for provider discovery and configuration; include API 26–27 and a recent Android version for the different usage modes.

- [ ] Add every widget from the actual picker. Reposition it between pages, resize to its declared minimum and larger, persist after process death/reboot, remove/undo, and remove permanently. Mix with a third-party configurable widget and verify its system bind/configure flow still works.
- [ ] Check both clocks across a minute boundary, in 12/24-hour mode and after a system time-zone change. Check the analog dial at the smallest supported height.
- [ ] Calendar: deny and grant permission; create and remove a timed event and an all-day event; revoke access and verify the widget does not retrieve event data on its next refresh.
- [ ] Weather: configure known coordinates, verify the real API values and attribution; test offline, cache freshness, location change, and network recovery. Confirm removing the last weather widget cancels its job.
- [ ] Music: deny and grant Notification Access; verify track information and supported controls across player changes, no active player, and permission revocation.
- [ ] Battery: plug/unplug a real charger; compare percent/charging state to Android. Storage: compare internal free/total and verify a file allocation changes the subsequent reading.
- [ ] Screen time: grant and revoke Usage Access, compare screen-on and screen-off intervals, and check midnight rollover; verify the API 26–27 label.
- [ ] Switch light/dark theme and accent with widgets placed; set different per-instance corners, check large font scale, and verify the launcher remains usable with system animations disabled.

## Theme Lab visual pass

The supplied Theme Lab HTML serves as a visual reference, not as a widget implementation or data source. Native cards borrow the diagonal glass plate, fading specular edge, compact tracked labels, and lighter display type. The digital clock uses a hero readout; the analog clock uses a four-tick dial and outlined hands. Calendar has a live date tile and the next real event, Weather uses a code-driven condition glyph, Music has outlined transport controls and abstract fallback artwork only when a session provides no cover, and Battery draws a segmented charge arc. Internal Storage uses a partition-ticked bar for the actual fraction used. Screen Time can show up to seven days of observed screen-active durations on API 28+, leaving unavailable days empty.

The lab's example cities, world times, agenda entries, hourly forecast curve, RAM partitions, charge speed, charge ETA, and top apps were not copied as fixed values. These are native adaptations of the reference rather than an exact copy of every large demo card.

The restyled debug APK is `artifacts/theme-lab/app-debug.apk` (SHA-256 `4a3cdc47ae5d226050acfc7f7f493b944c9aff14f6ebfa61bee38d09c0d8f713`). Signature and packaged-provider checks passed again, as did the screen-time boundary smoke check. Physical-device visual and behavior checks remain pending.
