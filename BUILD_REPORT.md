# LumaOS Pro build and release-readiness report

## Verification boundary

This report distinguishes prior historical verification from the current
source. The old build logs belonged to an earlier source/toolchain state and
have been removed so they cannot be mistaken for current release evidence. Per
the project owner's request, no build, test, lint, APK/AAB verification, or
device test was run for the current changes.

In the current workspace, the documented baseline command was attempted before
modification. `./build.sh test` initially could not start because the script did
not have its executable bit set; the build and setup scripts have since been
made executable. The command was then retried and reached provisioning, but
could not download JDK 17 (`api.adoptium.net` is not reachable from this
sandbox). No Java, Gradle, Android SDK, or `adb` executable is available in the
current PATH, and no local toolchain cache was present. Consequently,
current-source tests, lint, debug/release APK builds, and device installation
could not be run here.

The current source still needs a fresh lint run; no lint result is claimed here.

## Release configuration and Play readiness

- The app is configured for Android 16 / API 36. Google Play's new-app and
  update target requirement became API 36 on August 31, 2026; the previous API
  34 target was a submission blocker. This configuration still needs a fresh
  build and a policy check at submission time.
- AGP/Kotlin/Gradle and the provisioned SDK packages were aligned to versions
  that support API 36. This configuration has not been built in this workspace.
- Release signing no longer falls back to the debug key. Without a complete,
  existing local upload keystore, release outputs are unsigned and not suitable
  for Play upload. No private signing material is present here.
- The Play AAB task is `bundleRelease`; Play App Signing must be configured in
  Play Console. No current-source AAB was generated or is present in this
  checkout.
- The in-app privacy statement and documentation now reflect local app-list and
  diagnostics use plus Android's cloud/device-transfer backup behavior and the
  optional external Android speech-recognition flow. The publisher still needs
  a reviewed, public privacy-policy URL and must complete an accurate Play
  Console Data safety form. Neither publisher details nor a public policy URL
  are available in this repository, so these have not been fabricated.

## Outstanding real-device verification

1. Build and install the updated debug APK on a supported Android device; verify
   that both the app-drawer/Play Store LAUNCHER entry and the Android HOME role
   resolve to LumaOS Pro. Test first launch/onboarding, Home-role selection,
   default-launcher return behavior, app discovery, app launch, search, A–Z
   navigation, drawer dismissal, gestures, folders, dock actions, and saved
   layout after process recreation.
2. Check the clock, wallpaper contrast, edge-to-edge/system-bar icons, icon grid,
   and dock in both themes, across compact and tall displays and larger system
   font scales. Robolectric does not render pixels or validate these details.
3. Confirm theme and appearance settings update immediately and persist, in
   particular that the home-row count changes visible row sizing while all home
   items remain scroll-accessible.
4. Run unit tests, Android lint, `assembleDebug`, and `assembleRelease` from the
   updated source. Confirm release shrinking and signing configuration on a
   machine with the documented Android toolchain.
5. Measure startup, frame pacing, and memory on a real low-memory phone. The
   widget host is implemented, but widget-provider consent, configuration,
   resizing, and interaction need physical-device coverage. LumaOS Pro Shield and
   the optional Brave bridge are outside this project version's scope.

## Release blockers

Release remains blocked pending owner-run current-source build/test/lint,
physical-device validation, a correctly signed AAB/upload key, Play Console App
Signing and Data safety setup, a reviewed and hosted privacy policy with the
publisher's contact details, real-device store screenshots, and trademark/package
availability checks for LumaOS Pro and `com.lumaos.pro.launcher`. The icon and
feature graphic are supplied, but have not been reviewed in Play Console. These
are unverified/product-owner prerequisites, not claims of Play approval or
evidence of a specific code failure. No signing secrets, AAB, or APK are stored
or included in this repository.
