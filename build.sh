#!/usr/bin/env bash
# ROLEXM build entry point.
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

TOOLS="$HOME/.cache/rolexm-toolchain"

if [ ! -f "$TOOLS/env.sh" ]; then
  echo "==> toolchain missing, provisioning"
  bash ./toolchain/setup_toolchain.sh
fi

# shellcheck disable=SC1091
source "$TOOLS/env.sh"

# ---------------------------------------------------------------------------
# Robolectric's Android framework image.
#
# The unit tests use Robolectric to run real Android framework classes on the
# JVM, because this environment has no emulator (no /dev/kvm, no nested
# virtualisation). The image is ~150 MB, so it is kept in .cache/ - excluded
# from workspace snapshots - and re-fetched here whenever the sandbox resets.
# ---------------------------------------------------------------------------
ROBOLECTRIC_IMAGE="android-all-instrumented-14-robolectric-10818077-i7.jar"
ROBOLECTRIC_DIR="$HOME/.cache/robolectric"
if [ ! -f "$ROBOLECTRIC_DIR/$ROBOLECTRIC_IMAGE" ]; then
  echo "==> fetching Robolectric Android 14 image"
  mkdir -p "$ROBOLECTRIC_DIR"
  curl -fsSL -o "$ROBOLECTRIC_DIR/$ROBOLECTRIC_IMAGE" \
    "https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/14-robolectric-10818077-i7/$ROBOLECTRIC_IMAGE"
fi

# AGP reads the SDK location from local.properties or ANDROID_HOME; write it
# explicitly so builds work from any shell.
echo "sdk.dir=$ANDROID_HOME" > local.properties

echo "==> JAVA_HOME=$JAVA_HOME"
echo "==> ANDROID_HOME=$ANDROID_HOME"
echo "==> gradle $*"

# Use gradle wrapper if available, otherwise fall back to toolchain gradle
if [ -x "./gradlew" ]; then
    exec ./gradlew --no-daemon "$@"
else
    exec gradle --no-daemon "$@"
fi
