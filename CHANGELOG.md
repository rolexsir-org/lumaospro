# Changelog

Notable changes to LumaOS Pro. Entries use the app's `versionName`; the Play
`versionCode` is derived as `major * 100 + minor * 10 + patch` (1.0.0 → 100).

Nothing in this file is a claim that a build was produced, tested or published.
See [BUILD_REPORT.md](BUILD_REPORT.md) for the verification boundary and
[RELEASE-1.0.0.md](RELEASE-1.0.0.md) for the release checklist.

## 1.0.0 — unreleased

First public release. Configured but not yet built, tested, signed or verified on
a device from this source.

### Luma Glass v4.0 (unreleased)

- Added one shared, role-based Liquid Glass material layer (`GlassMaterial.kt`)
  with distinct recipes for primary surfaces, secondary panels, search fields,
  dock, folders, menus, sheets, widgets, settings controls and overlays.
- Added matching View (`GlassDrawable` / `GlassViews`) and Compose
  (`GlassComponents`) adapters, including pressed/focused/selected states and
  opaque fallbacks driven by the existing effects policy.
- Simplified the home hierarchy to one centred clock/date, one primary search
  entry, an aligned grid and one floating glass dock, while preserving the
  runtime test IDs and hidden compatibility actions.
- Unified settings, Studio, dashboard, preview, drawer, folders, menus, widget
  picker and edit chrome on the same material tokens; default appearance now
  uses the real system wallpaper with a polarity-aware legibility scrim.
- Added studio accent presets that keep all nine legacy theme IDs/storage keys
  but present them as accents within one Luma Glass identity.
- Fixed a nested block comment in `LumaDesignTokens.kt` that prevented Kotlin
  parsing, two backticked test names containing dots, `TypeStyle.entries` order,
  the type-scale hierarchy, and atmosphere tests that unintentionally exercised
  bundled artwork instead of the theme atmosphere.
- Locally bootstrapped Kotlin 2.2.21 compiled the pure token/theme layer and ran
  242 JVM-only unit tests with a lightweight JUnit shim; **Gradle, Android lint,
  Robolectric, debug APK assembly and device rendering were not executed**.

### Design System v3.2

- Adopted the LumaOS Design System v3.2 bundle (archived with provenance in
  [`docs/design/v3.2/`](docs/design/v3.2/README.md)).
- Three authored identities replace Aurora / Zen / Void as the signature set:
  - **Signature** — midnight ramp `#06070B`, indigo-violet `#7C86FF`, 34/26/16/10/6
    dp radii, blur 26 dp at 135 % saturation.
  - **Atmosphere** — deep teal `#04131A`, aurora mint `#46E0B0`, 38/30/20/12/8 dp
    radii, blur 34 dp at 165 %, wallpaper-led with a cinematic motion profile.
  - **Monolith** — stone `#0B0B0A`, vermilion `#FF5A2B`, 8/4/2/2/2 dp radii,
    **no blur**, mono labels and an 84 sp clock.
- Authored colour path: a v3.2 theme draws its designed roles verbatim
  (translucent roles composited over the authored ground) instead of being
  regenerated from a seed. `ok` / `warn` reach the semantic layer as authored.
- Authored geometry, material, label size, clock size and the 2 · 4 · 8 … 72 dp
  spacing scale are read from tokens instead of per-screen constants.
- New type voices `ambient` and `monograph`; new motion characters `cinematic`
  and `exact`.
- Fresh installs follow the system appearance: Signature in dark, Zen in light
  (the v3.2 trio is authored dark; the specification's light *surface* variant is
  not implemented).

### Compatibility

- Aurora, Zen, Void, Obsidian, Graphite and Chroma are retained unchanged:
  same storage keys (`porcelain`, `vector` included), seeds, geometry, voices and
  bundled artwork. No saved preference, layout or exported backup is rewritten.
- Theme, typography, icon-shape, surface and motion storage keys are unchanged;
  the new keys are additive.

### Verification

- Static only in this workspace (no JDK or Android SDK): token parity between the
  bundle JSON and Kotlin, a WCAG audit of every authored pair, balanced-source,
  resource-reference and preset-coverage checks.
- Added `DesignTokensParityTest` and `DesignSystemV32Test`; updated
  `ThemeEngineTest`, `DesignSystemTest` and the preferences runtime test. **None
  of these has been executed**; they must run in CI or by the owner.

### Versioning

- `versionName` `0.1.0` → `1.0.0`; `versionCode` `1` → `100`. The pre-release
  0.1.0 build was never published, so its version code is retired.

## 0.1.0 — never published

Pre-release development state: launcher hot path, settings, Studio, backup and
restore, diagnostics, widgets and onboarding, before Design System v3.2. No
artifact was published or verified.
