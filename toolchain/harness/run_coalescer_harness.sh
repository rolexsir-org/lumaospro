#!/usr/bin/env bash
# Runs RefreshCoalescerHarness.kt against the REAL production
# app/src/main/java/com/lumaos/pro/launcher/core/util/RefreshCoalescer.kt.
#
# The harness is compiled in the same kotlinc module as the production class so
# that `internal` is visible, then executed on a real JVM. It is deliberately NOT
# placed in app/src/test: this repository's test task needs JUnit and
# kotlinx-coroutines-test, and putting a `main()` there would make it look like a
# test that the runner silently skips.
#
# Requires: a JDK on PATH (or JAVA_HOME), a Kotlin compiler, and
# kotlinx-coroutines-core-jvm + kotlin-stdlib on the classpath.
#   KOTLIN_HOME  Kotlin compiler install  (default $TOOLS/kotlinc)
#   CP           extra classpath entries   (default $TOOLS/lib/*.jar)
#   TOOLS        toolchain cache           (default $HOME/.cache/lumaos-toolchain)
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOLS="${TOOLS:-$HOME/.cache/lumaos-toolchain}"
KOTLIN_HOME="${KOTLIN_HOME:-$TOOLS/kotlinc}"
# Prefer an explicit JAVA_HOME, then the JDK that setup_toolchain.sh installs,
# then whatever `java` is already on PATH.
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
elif [ -x "$TOOLS/jdk17/bin/java" ]; then
  JAVA="$TOOLS/jdk17/bin/java"
else
  JAVA="java"
fi
command -v "$JAVA" >/dev/null 2>&1 || [ -x "$JAVA" ] || {
  echo "no JDK found — set JAVA_HOME or run toolchain/setup_toolchain.sh" >&2; exit 2; }
CP="${CP:-$(ls "$TOOLS"/lib/*.jar 2>/dev/null | tr '\n' ':')}"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

[ -x "$KOTLIN_HOME/bin/kotlinc" ] || {
  echo "kotlinc not found at $KOTLIN_HOME/bin/kotlinc — set KOTLIN_HOME" >&2; exit 2; }
[ -n "$CP" ] || { echo "no classpath jars found — set CP" >&2; exit 2; }

"$KOTLIN_HOME/bin/kotlinc" -nowarn -no-stdlib -jvm-target 17 -cp "$CP" -d "$OUT" \
  "$REPO/app/src/main/java/com/lumaos/pro/launcher/core/util/RefreshCoalescer.kt" \
  "$REPO/toolchain/harness/RefreshCoalescerHarness.kt"

exec "$JAVA" -cp "$OUT:$CP" com.lumaos.pro.launcher.core.util.RefreshCoalescerHarnessKt
