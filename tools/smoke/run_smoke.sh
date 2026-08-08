#!/usr/bin/env bash
# Aprism real-game smoke harness (Windows).
# Launches a genuine Minecraft 26.2 instance with the Aprism javaagent and a
# sample .aje mod, then asserts the Aprism lifecycle completed inside the
# running game. See tools/smoke/README.md.
#
# Requirements:
#   - a built Aprism fat agent jar (run ./gradlew :aprism-loader-core:shadowJar)
#   - the smoke environment (client.jar, natives/, gamedir/) prepared under
#     build/smoke  (see tools/smoke/setup_smoke_env.sh)
#   - JDK 25 (set APRISM_JAVA_HOME or JAVA_HOME to a JDK 25)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

SMOKE_DIR="$REPO_ROOT/build/smoke"
GAMEDIR="$SMOKE_DIR/gamedir"
LOG="$SMOKE_DIR/smoke.log"
MARKER="APRISM_SMOKE_RUN"

# Windows java.exe needs drive-letter paths (C:/...), not POSIX (/c/...).
# Convert once and use the Windows forms for everything passed to the JVM.
if command -v cygpath >/dev/null 2>&1; then
  W_SMOKE_DIR="$(cygpath -m "$SMOKE_DIR")"
  W_GAMEDIR="$(cygpath -m "$GAMEDIR")"
else
  W_SMOKE_DIR="$SMOKE_DIR"
  W_GAMEDIR="$GAMEDIR"
fi

# Version comes from gradle.properties so the harness always matches the build.
VERSION="$(sed -n 's/^aprismVersion = //p' "$REPO_ROOT/gradle.properties" | tr -d '\r')"
if [ -z "$VERSION" ]; then
  echo "FAIL: could not read aprismVersion from gradle.properties" >&2
  exit 1
fi
AGENT_JAR_POSIX="$REPO_ROOT/aprism-loader-core/build/libs/Aprism-${VERSION}-JE-26.2.jar"
if command -v cygpath >/dev/null 2>&1; then
  AGENT_JAR="$(cygpath -m "$AGENT_JAR_POSIX")"
else
  AGENT_JAR="$AGENT_JAR_POSIX"
fi

JAVA_BIN="${APRISM_JAVA_HOME:-${JAVA_HOME:-}}/bin/java.exe"
if [ ! -x "$JAVA_BIN" ]; then
  echo "FAIL: JDK 25 not found at $JAVA_BIN" >&2
  echo "      Set APRISM_JAVA_HOME (or JAVA_HOME) to a JDK 25 installation." >&2
  exit 1
fi

fail() { echo "SMOKE FAIL: $*" >&2; exit 1; }

# Kill any Minecraft process from a previous smoke run (matched by our marker).
if command -v pkill >/dev/null 2>&1; then
  pkill -f "$MARKER" >/dev/null 2>&1 || true
fi
sleep 1

# Pre-flight checks
[ -d "$GAMEDIR" ] || fail "game directory missing: $GAMEDIR"
[ -f "$SMOKE_DIR/client.jar" ] || fail "client.jar missing: $SMOKE_DIR/client.jar (run setup_smoke_env.sh)"
[ -d "$SMOKE_DIR/natives/windows/x64" ] || fail "natives missing: $SMOKE_DIR/natives/windows/x64"
[ -f "$AGENT_JAR_POSIX" ] || fail "agent jar missing: $AGENT_JAR_POSIX (run ./gradlew :aprism-loader-core:shadowJar)"
MOD_COUNT=$(find "$GAMEDIR/mods" -maxdepth 1 -name "*.aje" 2>/dev/null | wc -l | tr -d ' ')
[ "$MOD_COUNT" -gt 0 ] || fail "no .aje mods in $GAMEDIR/mods (place a sample .aje)"
echo "Smoke: version=$VERSION agent=$(basename "$AGENT_JAR") mods=$MOD_COUNT"

# Reuse the proven classpath recorded by a previous manual launch.
CLASSPATH_LINE="$(grep "client.jar" "$SMOKE_DIR/launch_args.txt" | head -1 | tr -d '\r')"
[ -n "$CLASSPATH_LINE" ] || fail "classpath not found in $SMOKE_DIR/launch_args.txt"

# Build the agent argument file (@file form, one arg per line).
AGENT_ARGS="$SMOKE_DIR/agent_args.txt"
{
  echo "-Xmx2G"
  echo "-Djava.library.path=$W_SMOKE_DIR/natives/windows/x64"
  echo "-javaagent:$AGENT_JAR=aprismVersion=$VERSION;mcEdit=JE;mcVersion=26.2;gameRoot=$W_GAMEDIR"
  echo "-Daprism.smoke.marker=$MARKER"
  echo "-cp"
  echo "$CLASSPATH_LINE"
  echo "net.minecraft.client.main.Main"
  echo "--version"
  echo "26.2"
  echo "--gameDir"
  echo "$W_GAMEDIR"
  echo "--assetsDir"
  echo "$W_GAMEDIR/assets"
  echo "--assetIndex"
  echo "32"
  echo "--versionType"
  echo "release"
  echo "--accessToken"
  echo "0"
} > "$AGENT_ARGS"

rm -f "$LOG"
echo "Smoke: launching real Minecraft 26.2 with Aprism agent..."
"$JAVA_BIN" "@$AGENT_ARGS" > "$LOG" 2>&1 &
GAME_PID=$!

# Poll the game log for the completion marker.
TIMEOUT_SECS="${APRISM_SMOKE_TIMEOUT:-150}"
FOUND=0
for _ in $(seq 1 "$TIMEOUT_SECS"); do
  sleep 1
  if grep -q "\[ExampleMod\] onComplete" "$LOG" 2>/dev/null; then
    FOUND=1
    break
  fi
done

# Stop the game regardless of outcome.
if command -v pkill >/dev/null 2>&1; then
  pkill -f "$MARKER" >/dev/null 2>&1 || true
else
  kill "$GAME_PID" >/dev/null 2>&1 || true
fi

if [ "$FOUND" -ne 1 ]; then
  echo "--- last 40 log lines ---" >&2
  tail -40 "$LOG" >&2 || true
  fail "ExampleMod onComplete not observed within ${TIMEOUT_SECS}s"
fi

echo "Smoke: lifecycle assertions..."
grep -q "Loaded 1 Aprism extension(s)\|Loaded 0 Aprism extension(s)" "$LOG" || fail "extension phase line missing"
grep -q "Loaded [0-9][0-9]* mod(s)" "$LOG" || fail "mod load line missing"
grep -q "\[ExampleMod\] onPreInitialize" "$LOG" || fail "onPreInitialize missing"
grep -q "\[ExampleMod\] onInitialize" "$LOG" || fail "onInitialize missing"
grep -q "\[ExampleMod\] onSetup" "$LOG" || fail "onSetup missing"
grep -q "\[ExampleMod\] onComplete" "$LOG" || fail "onComplete missing"
grep -q "Aprism Load Report ($VERSION)" "$LOG" || fail "Load Report banner missing for $VERSION"
grep -q "failed 0" "$LOG" || fail "load report reports failures"

echo "SMOKE PASS: real-game Aprism lifecycle verified ($VERSION)"
exit 0
