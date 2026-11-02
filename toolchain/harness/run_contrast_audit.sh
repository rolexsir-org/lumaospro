#!/usr/bin/env bash
# Runs ThemeContrastAudit.kt against the REAL ThemeEngine and SemanticPalette.
#
# The audit is compiled together with app/src/main so it resolves the production
# classes, then executed on a JVM. It is not placed in app/src/test for the same
# reason as the coalescer harness: it is a `main()`, not a JUnit test, and the
# project's test task needs JUnit + kotlinx-coroutines-test.
#
# Unlike run_coalescer_harness.sh this compiles the whole main source set, so it
# needs the full app classpath:
#   KOTLIN_HOME  Kotlin compiler install        (default $TOOLS/kotlinc)
#   CP           android.jar + AndroidX + Compose + coroutines + kotlin-stdlib
#   GEN_SRCS     space-separated generated sources (R.kt, BuildConfig.kt) from a
#                Gradle build, e.g. app/build/generated/source/*
#   TOOLS        toolchain cache                 (default $HOME/.cache/lumaos-toolchain)
#   COMPOSE_PLUGIN  path to compose-compiler-plugin.jar (optional)
#
# Easiest way to obtain CP and GEN_SRCS on a machine that can run Gradle:
#   ./build.sh assembleDebug
# then point GEN_SRCS at app/build/generated/source and CP at the resolved
# runtime classpath.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOLS="${TOOLS:-$HOME/.cache/lumaos-toolchain}"
KOTLIN_HOME="${KOTLIN_HOME:-$TOOLS/kotlinc}"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
elif [ -x "$TOOLS/jdk17/bin/java" ]; then
  JAVA="$TOOLS/jdk17/bin/java"
else
  JAVA="java"
fi
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

[ -x "$KOTLIN_HOME/bin/kotlinc" ] || {
  echo "kotlinc not found at $KOTLIN_HOME/bin/kotlinc — set KOTLIN_HOME" >&2; exit 2; }
[ -n "${CP:-}" ] || { echo "CP must list android.jar + AndroidX + Compose jars" >&2; exit 2; }

PLUGIN_ARGS=()
[ -n "${COMPOSE_PLUGIN:-}" ] && PLUGIN_ARGS=(-Xplugin="$COMPOSE_PLUGIN")

# shellcheck disable=SC2086
"$KOTLIN_HOME/bin/kotlinc" -nowarn -no-stdlib -jvm-target 17 \
  -opt-in=androidx.compose.material3.ExperimentalMaterial3Api \
  "${PLUGIN_ARGS[@]}" -cp "$CP" -d "$OUT" \
  $GEN_SRCS \
  "$REPO/toolchain/harness/ThemeContrastAudit.kt" \
  $(find "$REPO/app/src/main/java" -name '*.kt' -o -name '*.java' | grep -v '/java$' | sort)

exec "$JAVA" -cp "$OUT:$CP" com.lumaos.pro.launcher.ui.theme.ThemeContrastAuditKt
