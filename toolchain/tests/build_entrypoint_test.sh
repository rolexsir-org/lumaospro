#!/usr/bin/env bash
# Exercise build.sh's tool selection and cache handling without downloading a
# toolchain or producing an APK. The fake commands exist only in a temp dir.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/project/gradle/wrapper" "$TMP/home/.cache/lumaos-toolchain" "$TMP/bin"
cp "$ROOT/build.sh" "$TMP/project/build.sh"
cat > "$TMP/home/.cache/lumaos-toolchain/env.sh" <<ENV
export JAVA_HOME="$TMP/java"
export ANDROID_HOME="$TMP/sdk"
export PATH="$TMP/bin:\$PATH"
ENV
cat > "$TMP/bin/gradle" <<'FAKE'
#!/usr/bin/env bash
echo "PATH_GRADLE $*"
FAKE
cat > "$TMP/project/gradlew" <<'FAKE'
#!/usr/bin/env bash
echo "WRAPPER $*"
FAKE
cat > "$TMP/bin/curl" <<'FAKE'
#!/usr/bin/env bash
# Simulate a failed download that leaves partial bytes, as real curl can.
while [[ $# -gt 0 ]]; do
    if [[ "$1" == -o ]]; then shift; printf partial > "$1"; fi
    shift
done
exit 22
FAKE
chmod +x "$TMP/bin/gradle" "$TMP/bin/curl" "$TMP/project/gradlew"
cd "$TMP/project"

output="$(HOME="$TMP/home" ./build.sh assembleDebug)"
[[ "$output" == *"PATH_GRADLE --no-daemon assembleDebug"* ]]
[[ "$output" != *"fetching Robolectric"* ]]

touch gradle/wrapper/gradle-wrapper.jar
output="$(HOME="$TMP/home" ./build.sh lint)"
[[ "$output" == *"WRAPPER --no-daemon lint"* ]]

mkdir -p "$TMP/home/.cache/lumaos-toolchain/gradle-8.13/bin"
cat > "$TMP/home/.cache/lumaos-toolchain/gradle-8.13/bin/gradle" <<'FAKE'
#!/usr/bin/env bash
echo "PROVISIONED_GRADLE $*"
FAKE
chmod +x "$TMP/home/.cache/lumaos-toolchain/gradle-8.13/bin/gradle"
output="$(HOME="$TMP/home" ./build.sh bundleRelease)"
[[ "$output" == *"PROVISIONED_GRADLE --no-daemon bundleRelease"* ]]

if HOME="$TMP/home" ./build.sh test > "$TMP/failed-download.log" 2>&1; then
    echo "Expected a failed Robolectric download" >&2
    exit 1
fi
[[ ! -e "$TMP/home/.cache/robolectric/android-all-instrumented-14-robolectric-10818077-i7.jar" ]]
[[ -z "$(find "$TMP/home/.cache/robolectric" -name '*.tmp.*' -print -quit)" ]]
echo "build entrypoint checks passed"
