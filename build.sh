#!/usr/bin/env bash
# LumaOS Pro build entry point.
#
# The Android SDK / Gradle / JDK toolchain lives outside the persisted
# workspace (under ~/.cache) so it never inflates the project snapshot. This
# script re-provisions it if the sandbox was reset, then runs Gradle.
#
#   ./build.sh assembleDebug
#   ./build.sh test
#   ./build.sh lint
#   ./build.sh assembleRelease
set -euo pipefail
cd "$(dirname "$0")"

# AGP 8+ throws AndroidLocationsException if both ANDROID_PREFS_ROOT and ANDROID_USER_HOME are set.
unset ANDROID_PREFS_ROOT

TOOLS="$HOME/.cache/lumaos-toolchain"

if [ ! -f "$TOOLS/env.sh" ]; then
  echo "==> toolchain missing, provisioning"
  bash ./toolchain/setup_toolchain.sh
fi

# shellcheck disable=SC1091
source "$TOOLS/env.sh"

# Gradle itself reads GRADLE_USER_HOME, not a gradle.properties key. Give it a
# writable default when the provisioned env does not set one explicitly.
: "${GRADLE_USER_HOME:=$HOME/.gradle}"
export GRADLE_USER_HOME

# Robolectric's framework image is only needed by unit-test/check tasks, not
# by assembling, linting, or bundling. Keep it in the same user cache that the
# Gradle test configuration uses; never leave a partial download as a cache hit.
needs_robolectric=false
for arg in "$@"; do
  task="${arg##*:}"
  case "$task" in
    test*|check) needs_robolectric=true ;;
  esac
done
ROBOLECTRIC_IMAGE="android-all-instrumented-14-robolectric-10818077-i7.jar"
ROBOLECTRIC_DIR="${ROBOLECTRIC_DEPENDENCY_DIR:-$HOME/.cache/robolectric}"
if $needs_robolectric && [ ! -s "$ROBOLECTRIC_DIR/$ROBOLECTRIC_IMAGE" ]; then
  echo "==> fetching Robolectric Android 14 image"
  mkdir -p "$ROBOLECTRIC_DIR"
  download="$ROBOLECTRIC_DIR/$ROBOLECTRIC_IMAGE.tmp.$$"
  if ! curl -fsSL -o "$download" \
    "https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/14-robolectric-10818077-i7/$ROBOLECTRIC_IMAGE"; then
    rm -f "$download"
    exit 1
  fi
  mv "$download" "$ROBOLECTRIC_DIR/$ROBOLECTRIC_IMAGE"
fi
export ROBOLECTRIC_DEPENDENCY_DIR="$ROBOLECTRIC_DIR"

# AGP reads the SDK location from local.properties or ANDROID_HOME; write it
# explicitly so builds work from any shell.
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Map bare 'test' to 'testDebugUnitTest' so the JVM unit + Robolectric suite runs
# against the unminified debug variant.
args=()
for arg in "$@"; do
  if [ "$arg" = "test" ]; then
    args+=("testDebugUnitTest")
  else
    args+=("$arg")
  fi
done

echo "==> JAVA_HOME=$JAVA_HOME"
echo "==> ANDROID_HOME=$ANDROID_HOME"
echo "==> gradle ${args[*]}"

# Prefer the exact provisioned Gradle so the wrapper does not download a
# second copy. Otherwise use a complete wrapper, then a Gradle on PATH.
if [ -x "$TOOLS/gradle-8.13/bin/gradle" ]; then
    exec "$TOOLS/gradle-8.13/bin/gradle" --no-daemon "${args[@]}"
elif [ -x "./gradlew" ] && [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    exec ./gradlew --no-daemon "${args[@]}"
else
    exec gradle --no-daemon "${args[@]}"
fi
