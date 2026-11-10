# Release outputs

No signed, Play-uploadable APK or AAB is checked into this directory. Build and
verify release outputs locally; do not treat any superseded checksum report as
evidence for the current LumaOS Pro source.

This directory is intentionally empty apart from this file. An earlier recovery
pass produced an **unsigned** release AAB and APK here; those artifacts are
deliberately gitignored (`*.aab`, `*.apk`) and none of them is committed, so
nothing in this directory is evidence about the current source. There is no
production keystore or `keystore.properties` in this checkout, so no output here
is release-signed.

To produce and verify release outputs from the current tree, run
`./build.sh assembleRelease` or `./build.sh bundleRelease` (outputs land under
`app/build/outputs/`, not here) and work through the release checklist in
`README.md` and sections C and E of `docs/QA_CHECKLIST.md`.
