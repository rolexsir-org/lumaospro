# LumaOS Pro 1.0.0 — release preparation status

**Status: NOT RELEASE-READY.** This is a preparation checklist, not evidence of a
verified release or Play approval. The current repository does not contain a
verified APK or AAB.

## Configured application identity

| Setting | Value |
|---|---|
| Application ID | `com.lumaos.pro.launcher` (provisional; verify availability before first upload) |
| versionCode / versionName | `100` / `1.0.0` |
| minSdk | 26 (Android 8.0) |
| targetSdk / compileSdk | 36 (Android 16) |
| Play package | Android App Bundle (`bundleRelease`) |

Confirm the application ID is available and is the permanent package name before
creating the Play listing. `versionCode` follows
`major * 100 + minor * 10 + patch` (1.0.0 → 100, 1.0.1 → 101, 1.1.0 → 110):
increment it for every later upload. The pre-release 0.1.0 build (`versionCode`
1) was never published, so its number is retired rather than reused.

## What 1.0.0 contains

- **Design System v3.2** — Signature, Atmosphere and Monolith, with authored
  colour, geometry, material, typography, motion and spacing tokens transcribed
  from the design bundle in `docs/design/v3.2/`. Fresh installs follow the
  system appearance: Signature in dark, Zen in light.
- **Retained palettes** — Aurora, Zen, Void, Obsidian, Graphite and Chroma keep
  their storage keys, seeds, geometry and bundled artwork; no saved choice or
  exported backup is rewritten by the upgrade.
- The launcher itself: Home-role handling, app drawer and search, folders, dock,
  widgets, context profiles and scheduled switching, backup/restore, diagnostics
  and the Studio.

The design-system tokens are enforced by `DesignTokensParityTest`; the theme
behaviour is covered by `DesignSystemV32Test`, `ThemeEngineTest` and
`DesignSystemTest`. None of these tests has been executed in this workspace —
see the verification boundary in [BUILD_REPORT.md](BUILD_REPORT.md).

## Artifact and signing status

- No `dist/app-release.aab`, `dist/app-release.apk`, or current Gradle output is
  present in this checkout. `dist/SHA256SUMS` previously referenced missing
  binaries and has been removed rather than presented as valid checksums.
- No upload keystore or `keystore.properties` is present. Release signing is
  intentionally optional and never falls back to the debug key.
- The old artifact report described a separate manual build with dependency
  substitutions and without R8/resource shrinking. Its reported hashes cannot
  be checked against files in this checkout and must not be used as current
  release evidence.

When preparing a release, configure a dedicated upload key using
[keystore/README.md](keystore/README.md), build the AAB from the current Gradle
source with `./build.sh bundleRelease`, then independently verify its package
identity, version, signature, and checksum. Enroll in Play App Signing and keep
secure backups of the upload key. Do not commit the private key, credentials,
APK, or AAB.

## Remaining release gates

1. Run a fresh current-source test suite (including the design-system tests),
   lint, debug/release build, and AAB verification. These checks have not been
   run for this source; they are left to the project owner for testing and
   release.
2. Install from a Play internal/closed test track on physical devices. Validate
   first launch and onboarding, both app-drawer and Home-role entry points,
   widgets, backups, accessibility, notifications, profile schedules, and
   performance on representative low-memory hardware.
3. Complete Play App Signing, Data safety, app-content and rating declarations,
   developer/support contact details, the public privacy-policy URL, and the
   store listing. Capture real screenshots; the supplied icon and feature
   graphic are not screenshots.
4. Confirm trademark clearance for the selected LumaOS Pro brand and verify
   that `com.lumaos.pro.launcher` is available before creating the Play listing.
   Review account-specific Play testing requirements in Play Console.

See [BUILD_REPORT.md](BUILD_REPORT.md) for the verification boundary and
[README.md](README.md) for the project and privacy behavior. A local successful
build is not Play approval.
