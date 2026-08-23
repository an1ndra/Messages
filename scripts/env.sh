#!/usr/bin/env bash
# Shared environment for Messages UI test scripts.
# Source this file from other scripts: source "$(dirname "$0")/env.sh"

export ANDROID_SERIAL=${ANDROID_SERIAL:-emulator-5554}
ADB="${ADB:-$HOME/android/platform-tools/adb}"
PKG="com.anindra.messages"
ACT="$PKG/.MainActivity"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SHOTS_DIR="${SHOTS_DIR:-$PROJECT_DIR/screenshots}"
TMP="/tmp/opencode/messages-tests"
mkdir -p "$SHOTS_DIR" "$TMP"

adb_() { "$ADB" -s "$ANDROID_SERIAL" "$@"; }

# Take a screenshot into screenshots/<name>.png
shot() {
    adb_ exec-out screencap -p > "$SHOTS_DIR/$1.png"
    echo "[screenshot] $SHOTS_DIR/$1.png"
}

# Type text into the focused field (handles spaces)
type_text() {
    local t="${1// /%s}"
    adb_ shell input text "$t"
}

# Dump the current UI hierarchy to $TMP/ui.xml
dump_ui() {
    adb_ shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb_ pull /sdcard/ui.xml "$TMP/ui.xml" >/dev/null 2>&1
    [ -f "$TMP/ui.xml" ]
}

# Find node by text/content-desc and print "x y" of its center, or fail.
# Usage: center_of "Start chat"
center_of() {
    dump_ui || return 1
    local b
    b=$(grep -oE "(text|content-desc)=\"$1\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" \
            "$TMP/ui.xml" 2>/dev/null | head -1 | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
    [ -z "$b" ] && return 1
    local x1 y1 x2 y2
    x1=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1/' <<< "$b")
    y1=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\2/' <<< "$b")
    x2=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\3/' <<< "$b")
    y2=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\4/' <<< "$b")
    echo "$(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))"
}

# Tap the center of a node matching text/content-desc. Returns 0 on success.
tap_text() {
    local c
    c=$(center_of "$1") || { echo "[tap] '$1' not found"; return 1; }
    adb_ shell input tap $c
    echo "[tap] '$1' at ($c)"
}

# Tap the chat input field (EditText) wherever it currently is.
tap_edittext() {
    dump_ui || return 1
    local b
    b=$(grep -oE 'class="android.widget.EditText"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
            "$TMP/ui.xml" 2>/dev/null | head -1 | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
    [ -z "$b" ] && { echo "[tap] no EditText found"; return 1; }
    local x1 y1 x2 y2
    x1=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1/' <<< "$b")
    y1=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\2/' <<< "$b")
    x2=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\3/' <<< "$b")
    y2=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\4/' <<< "$b")
    adb_ shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}

# Center "X Y" of first node whose text contains $1 (retrying dump 3x)
center_of_contains() {
    local query="$1" b i x1 y1 x2 y2
    for i in 1 2 3; do
        dump_ui || { sleep 1; continue; }
        b=$(grep -oE "text=\"[^\"]*$query[^\"]*\"[^>]*bounds=\"[^\"]*\"" \
                "$TMP/ui.xml" 2>/dev/null | head -1 \
            | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
        if [ -n "$b" ]; then
            x1=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1/' <<< "$b")
            y1=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\2/' <<< "$b")
            x2=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\3/' <<< "$b")
            y2=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\4/' <<< "$b")
            echo "$(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))"
            return 0
        fi
        sleep 1
    done
    return 1
}

info() { echo -e "\n=== $* ==="; }
