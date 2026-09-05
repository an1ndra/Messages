#!/usr/bin/env bash
# Build (if needed) and install the debug APK on the emulator.
set -e
source "$(dirname "$0")/env.sh"

cd "$PROJECT_DIR"
# Use the project's JDK 21 regardless of any JAVA_HOME the caller exported
# (the shell may point at a missing JDK). Fall back to the wrapper dist if
# the .local JDK isn't present.
for cand in \
  "$HOME/.local/java/"jdk-21.* \
  "$HOME/tools/jdk21" \
  "$JAVA_HOME" ; do
  if [ -x "$cand/bin/java" ]; then export JAVA_HOME="$cand"; break; fi
done
./gradlew assembleDebug --quiet
adb_ install -r app/build/outputs/apk/debug/app-debug.apk
echo "[ok] installed"
