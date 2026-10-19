#!/usr/bin/env bash
# Project ROLEXM — sandbox toolchain provisioning
# Installs JDK 17, Gradle, and a minimal Android SDK.
# Everything lives under $TOOLS (.cache) so it is excluded from workspace
# snapshots and never bloats the persisted project source.
set -euo pipefail

TOOLS="${TOOLS:-$HOME/.cache/rolexm-toolchain}"
mkdir -p "$TOOLS"
cd "$TOOLS"

echo "==> TOOLS=$TOOLS"

# ---------- JDK 17 (AGP 8.x / Gradle 8.x require it; sandbox ships JDK 11) ----------
if [ ! -x "$TOOLS/jdk17/bin/javac" ]; then
  echo "==> downloading JDK 17"
  curl -fsSL -o jdk17.tar.gz \
    "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  mkdir -p jdk17
  tar -xzf jdk17.tar.gz -C jdk17 --strip-components=1
  rm -f jdk17.tar.gz
fi
export JAVA_HOME="$TOOLS/jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
java -version 2>&1 | head -1

# ---------- Gradle ----------
GRADLE_VERSION=8.7
if [ ! -x "$TOOLS/gradle-$GRADLE_VERSION/bin/gradle" ]; then
  echo "==> downloading Gradle $GRADLE_VERSION"
  curl -fsSL -o gradle.zip \
    "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  unzip -q -o gradle.zip
  rm -f gradle.zip
fi
GRADLE="$TOOLS/gradle-$GRADLE_VERSION/bin/gradle"

# ---------- Android SDK ----------
export ANDROID_HOME="$TOOLS/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
mkdir -p "$ANDROID_HOME"

if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "==> downloading Android cmdline-tools"
  curl -fsSL -o cmdline.zip \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  unzip -q cmdline.zip -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -f cmdline.zip
fi

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "==> installing SDK packages"
yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager --install \
  "platform-tools" \
  "platforms;android-34" \
  "build-tools;34.0.0" >/dev/null

echo "==> SDK contents"
ls "$ANDROID_HOME"

# ---------- persist env for later shells ----------
cat > "$TOOLS/env.sh" <<EOF
export JAVA_HOME="$TOOLS/jdk17"
export ANDROID_HOME="$ANDROID_HOME"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$TOOLS/gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$TOOLS/gradle-$GRADLE_VERSION/bin:\$PATH"
EOF

echo "==> DONE"
cat "$TOOLS/env.sh"
