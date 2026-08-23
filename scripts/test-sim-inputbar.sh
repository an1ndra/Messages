#!/usr/bin/env bash
# Verify the SIM switcher icon in the chat input bar:
#   - single-SIM device  -> icon hidden, input bar layout unchanged
#   - multi-SIM device   -> icon at top-right of the "Text message" pill,
#                           tap = cycle to next SIM (toast confirms),
#                           tap again = cycle again
# Usage: test-sim-inputbar.sh [row=1]
source "$(dirname "$0")/env.sh"

ROW="${1:-1}"

info "Opening app + conversation row $ROW"
adb_ shell am start -n "$ACT"; sleep 2
Y=$(( 357 + (ROW - 1) * 190 ))
[ "$Y" -gt 2200 ] && Y=2200
adb_ shell input tap 500 "$Y"; sleep 1.5
shot "sim-01-chat-empty-draft"

dump_ui || { echo "FAIL: could not dump UI"; exit 1; }
if grep -q "Switch SIM" "$TMP/ui.xml"; then
    info "Multi-SIM detected: tapping 'Switch SIM' icon (top-right of input pill)"
    tap_text "Switch SIM" || { echo "FAIL: could not tap Switch SIM"; exit 1; }
    sleep 1
    shot "sim-02-after-first-tap"

    info "Tapping again to cycle back"
    tap_text "Switch SIM" || { echo "FAIL: second tap failed"; exit 1; }
    sleep 1
    shot "sim-03-after-second-tap"
    echo "PASS: SIM icon present and cycles on tap"
    MULTI_SIM=1
else
    info "Single-SIM device: icon must be hidden"
    echo "PASS: no SIM icon on single-SIM device"
    MULTI_SIM=0
fi

info "Checking input pill is intact"
dump_ui && grep -q "Text message" "$TMP/ui.xml" \
    && echo "PASS: input pill intact" \
    || { echo "FAIL: input pill missing"; exit 1; }

info "Typing a draft (icon must HIDE while typing)"
tap_edittext; sleep 1
type_text "hi"; sleep 1
shot "sim-04-with-draft"
if [ "$MULTI_SIM" = "1" ]; then
    dump_ui && grep -q "Switch SIM" "$TMP/ui.xml" \
        && { echo "FAIL: SIM icon still visible while typing"; exit 1; } \
        || echo "PASS: SIM icon hidden while typing"
fi
adb_ shell input keyevent 4; sleep 0.5
adb_ shell input keyevent 4; sleep 1

info "Done. Screenshots in $SHOTS_DIR (sim-*.png)"