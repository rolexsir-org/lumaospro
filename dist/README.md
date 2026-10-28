# Release outputs

No signed, Play-uploadable APK or AAB is checked into this directory. Build and
verify release outputs locally; do not treat any superseded checksum report as
evidence for the current LumaOS Pro source.

During the current recovery verification, an **unsigned** release AAB and APK
were produced here from the repaired source. The AAB/APK are deliberately
gitignored (`*.aab`, `*.apk`) and none of the generated `dist/` outputs is
committed. There is no production keystore or
`keystore.properties` in this checkout, so no output in this directory is
release-signed. See `RECOVERY_REPORT.md` for the exact verification boundary,
artifact hashes, and the remaining signing/device work.
