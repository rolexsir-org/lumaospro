# Premium UI/UX & Theme System Upgrade (2026-11-07)

## Audit findings (before)
- The token pipeline was already in place (`LumaDesignTokens` → `ThemeEngine` → `GlassMaterial`/`Components` → Views + Compose). Screens contained almost no hard-coded colours.
- Gaps:
  - Obsidian and Aurora were only retained palettes, derived from seed colours.
  - There was no authored light theme, and the authored path assumed a dark theme (white pressed veil, black scrim).
  - Picking a custom accent discarded the authored palette.
  - The UI had no light/dark/auto appearance control.
  - Material `labelSmall`/`labelMedium` used the home readout voice (0.14em tracking) everywhere.
  - Studio preview icons were drawn under the search pill.
  - The Studio Apply button used white text on the accent.
  - Studio segmented controls had an almost invisible selected state.
  - Studio section headers were jargon in monospace.

## Themes
| | Obsidian (`obsidian`) | Ivory (`ivory`, new) | Aurora (`aurora`) |
|---|---|---|---|
| Role | Premium OLED dark | Elegant light | Expressive gradient |
| Field / panels | #000 / #000, white 5–12 % glass | #F6F3EC paper / #FBF9F5, white cards, ink 4.5–8.5 % controls | #070A1C indigo, lilac-tinted glass |
| Text 1/2/3 | #F5F3EF / #AAA7A1 / #79766F | #1B1A17 / #5D5950 / #8A857A | #F3F1FF / #B3B0D6 / #7F7CA8 |
| Accent / accent 2 | Champagne #D9BC8C / platinum #B9C3D3 | Sapphire #2E5AAC / bronze #8E6E42 | Orchid #B48CFF / teal #4FE3C1 |
| Radii xl/lg/md/sm/xs, dock, tile | 30/22/16/10/6, 30, 22 | 30/22/16/10/6, 30, 22 | 36/28/20/12/8, 34, 24 |
| Elevation card/float, shadow | 2/8 dp, 0.34 | 1/6 dp, 0.10 | 3/10 dp, 0.32 |
| Material | 20 dp blur, translucent | 22 dp blur, translucent | 32 dp blur, glass |
| Voice / motion | Precision / subtle | Precision / subtle | Ambient / cinematic |
| Backdrop | Pure black (no glow) | Daylight lift and bronze whisper | Violet sky, teal and orchid ribbons, rose seam |

All three use squircle natural icons, with their own plate and tint treatments for the Plate and Tinted styles. Ivory is now the light half of the default system-following pair. A light theme the user has already stored, such as Zen, is never rewritten.

## Theme experience
- The authored path is polarity-aware: light themes get an ink pressed veil and an ink scrim.
- A custom accent keeps the authored surfaces and re-seeds only the accent family. Contrast guarantees still apply.
- **Studio:**
  - Large live miniatures of the premium trio, then a row of the other identities. Miniatures are painted from `AtmospherePlan` and the glass palette, so they match the launcher.
  - New Appearance card: Always, Auto or Scheduled, plus light-theme, dark-theme and dark-hours choosers. The preview follows the theme you last touched.
  - Curated accent row for each theme, plus a Match wallpaper option.
  - Sentence-case section headers with inline Reset.
  - Clearer segmented control and correct `onPrimary` text on Apply.
- **Settings:** the theme hub shows the trio as miniatures, other identities as chips, and a primary Open Theme Studio button.
- Compose shapes now come from each authored theme's radius roles.
- **Persistence:** the legacy `theme_mode` key maps any light preset to LIGHT.

## Launcher surfaces (second pass)
- **Home grid:** row pitch is capped at 1.4× the content height (`homeCellMaximumPx`). With few rows on a tall screen, icons now keep a steady rhythm from the top instead of spreading about 185dp apart. Spare height becomes calm space above the search field.
- **One chip style everywhere:** `GlassViews.chip` is a quiet pill at rest and accent-filled when selected, 34dp tall inside a 48dp touch target. The drawer filters, widget picker retry button, shade "Clear all" and notification actions all use it.
- **App drawer:**
  - Filter chips use the shared chip style, and the selected chip uses the title weight.
  - The scrim uses the theme's own tone, so Ivory gets a soft warm-ink veil instead of grey.
- **Widget picker:**
  - Grabber handle, and a search field with an icon.
  - One grouped card per app, with inset hairline dividers.
  - App headers in the title voice instead of tracked readout caps.
  - Larger 112×76 preview plates at the theme's medium radius, and the size as a pill.
  - An accent pill for Retry, and an illustrated empty state.
- **Shade and quick toggles:**
  - Panel and tiles use the theme's extra-large and large radius roles instead of the legacy scaling.
  - Each tile glyph sits in a circular badge: a soft onAccent disc when on, a whisper of the content colour when off.
  - The notifications header uses the title voice.
  - "Clear all" and the notification actions are pills.

## Verification
- `assembleDebug`, `lintDebug`: pass (0 errors).
- `testDebugUnitTest`: 584 tests, 0 failures.
  - Fixed a race in `PreferencesCorruptionRecoveryTest`: the test opened a second DataStore before the first had closed, and now waits with `cancelAndJoin`.
  - New suites: `PremiumThemesTest` (13 tests) and `PremiumThemePersistenceTest` (5 tests).
  - Older tests whose contracts changed on purpose were updated, each with a comment:
    - Zen → Ivory as the light default.
    - Obsidian and Aurora are now authored themes.
    - A custom accent stays on the authored path.
- **Device inspection:** the baseline UI was inspected on an emulator. The upgraded build has not been checked visually on a device yet.

## Not done
- The upgraded UI has not been checked visually on a device; install it and check.
- Living Icons artwork is deep-blue glass, which may look heavy on Ivory. Turn it off in Studio if so.
- The Settings "Open Theme Studio" label is still a hard-coded English string, as before.
