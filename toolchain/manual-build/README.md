# Historical manual build pipeline (not for production releases)

> **Do not use this pipeline for a public Play release.** It is a historical
> sandbox workaround, not the current release path. It bypasses Gradle/AGP,
> resolves some libraries from cached third-party builds rather than the exact
> catalog pins, does not apply the app's R8/resource-shrinking configuration,
> and does not run the current test/lint/device checks. No output from this
> pipeline is present or verified in the current checkout. A structurally
> valid bundle from this script is not sufficient evidence of a production
> release.
>
> Use the current Gradle source and `./build.sh bundleRelease` for release
> preparation. The owner must run the build, tests, lint, signature checks, and
> device/Play testing before upload.

The script remains documented only for provenance and recovery of older
sandbox-generated artifacts. It assembles resources and code without Gradle/AGP
using cached tools and dependencies; paths and cache contents are environment-
specific.

## Phases (`python3 toolchain/manual-build/build.py [all|libs|manifest|resources|codegen|kotlin|dex|aab|apk|verify]`)

1. **libs** — extract the AAR/JAR closure resolved by `resolve.py` from vendored Gradle caches.
2. **manifest** — merge app + library manifests (androidx.startup initializers etc.), substitute
   `${applicationId}`.
3. **resources** — `aapt2 compile` app + library res; `aapt2 link` twice: binary (`base.apk`,
   `R.txt`) and `--proto-format` (`proto.zip` for the AAB).
4. **codegen** — generate `R.kt` (app + every library's `R`, IDs taken from the linked table) and
   `BuildConfig.kt`.
5. **kotlin** — `kotlinc` 2.2.21 with the matching Compose compiler plugin (`-Xplugin`).
6. **dex** — D8 8.13.6 (`--release --min-api 26`).
7. **aab** — assemble `BundleConfig.pb` + `base/manifest|resources.pb|res|dex`, then sign the
   JAR scheme with `sign.mjs`.
8. **apk** — `base.apk` + dex, aligned and signed v1+v2+v3 by `sign.mjs`.
9. **verify** — bundletool `validate`, MANIFEST digest check, `ApkVerifier` (via `verify.kt`),
   `aapt2 dump badging`, SHA-256SUMS.

## Tool provenance

| Tool | Source |
|---|---|
| Temurin JRE 21.0.8 (java, keytool) | PyPI `jdk4py==21.0.8.2` (manylinux wheel) |
| Kotlin compiler 2.2.21 + Compose compiler plugin + stdlib | npm `kotlin-compiler@2.2.21` |
| aapt2 (2.20-15009934, linux x64) | npm `aaptjs3@2.0.2` |
| D8/R8 8.13.6 | `com.android.tools.build:builder:8.13.0.jar` (AGP's own, from a vendored Gradle cache) |
| android-36.jar | GitHub `Sable/android-platforms` (android-36/android.jar) |
| bundletool 1.17.2 (validate) | npm `bundletoolheavy@1.1.8` |
| JAR-v1 + APK v1/v2/v3 signing (pure JS, incl. zipalign) | npm `apk_sign_ts@1.0.1` (+ `jks-js`, `node-forge`, `node-int64`, `pako`) |
| apksig 8.13.0 (verifier) | `com.android.tools.build:apksig:8.13.0.jar` (vendored cache) |
| androidx/Compose/Kotlin library closure | Gradle caches committed in public repos (see `resolve.py` CACHE_ROOTS) |

`resolve.py` walks Gradle Module Metadata (`.module`, JSON) + POMs across the merged caches and
writes `resolved.json` (the artifact list `build.py` consumes). Because caches are partially
committed (some versions ship only metadata, others only binaries), the resolver falls back to the
nearest version that actually carries a binary and carries explicit dependency edges for a few
artifacts whose `.module` files are missing (`EXTRA_DEPS`).

## Known limitations

- Dex is **not** R8-minified and resources are not shrunk; this does not match
  the current Gradle release configuration.
- `bundletool build-apks` could not run in the historical sandbox, so the APK
  was packaged directly from linked resources and dex instead of through a
  bundletool universal-splits round-trip.
- The old report claimed `bundletool validate` passed for a prior AAB, but that
  binary is absent here. This is historical provenance only; it does not
  validate current source, Play acceptance, or an artifact in this repository.
- AAB signing used the JAR scheme. Regardless of that format-level detail, only
  a newly generated and independently verified current Gradle AAB should be
  considered for release.
