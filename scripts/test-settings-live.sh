#!/usr/bin/env bash
# Verifies that Settings toggles apply LIVE (no force-stop / app restart):
#   - Unread at top   -> list reorders immediately
#   - Swipe actions   -> swiping enabled/disabled immediately
#   - Drafts          -> "Draft:" prefix shown/hidden immediately
#   - Archiving       -> bottom-sheet "Archive" item shown/hidden immediately
#   - Pinned          -> bottom-sheet "Pin" item shown/hidden immediately
# Uses a disposable injected conversation + the seeded demo data.
# Ends with every toggle restored to ON.
source "$(dirname "$0")/env.sh"

FAIL=0
pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; FAIL=1; }
info() { echo -e "\n== $* =="; }

DISPOSABLE="+1555-123-0998"
UNREAD_TOP_LABEL="Unread at top"
SWIPE_LABEL="Swipe actions"
DRAFTS_LABEL="Drafts"
ARCH_LABEL="Archiving"
PIN_LABEL="Pinned conversations"

# ---------- navigation helpers ----------
on_settings() { dump_ui && grep -q 'text="General settings"' "$TMP/ui.xml"; }
on_home() { dump_ui && grep -q 'text="Messages"' "$TMP/ui.xml"; }
# force-press back until we're on the conversations home list (max 4)
go_home() {
    local i
    for i in 1 2 3 4; do
        if on_home; then return 0; fi
        adb_ shell input keyevent 4; sleep 1
    done
    on_home
}
ensure_settings() {
    local i
    go_home || { echo "[warn] could not reach home list"; return 1; }
    for i in 1 2 3; do
        if on_settings; then return 0; fi
        adb_ shell input tap 975 226; sleep 2
        on_settings && return 0
    done
    return 1
}
home_top() { adb_ shell input swipe 540 500 540 2200 200; sleep 1.2; }
settings_top() { adb_ shell input swipe 540 600 540 2100 300; sleep 1.2; }

# scroll down in Settings until $1 is present (up to 14 gentle swipes)
await_label() {
    local i
    for i in $(seq 1 14); do
        dump_ui && grep -q "text=\"$1\"" "$TMP/ui.xml" && return 0
        adb_ shell input swipe 540 1700 540 900 250; sleep 0.6
    done
    return 1
}

# prints true|false (exits 3 if not found) for the switch beside Settings label $1
switch_val() {
    dump_ui || return 2
    python3 - "$1" "$TMP/ui.xml" <<'PY'
import re, sys
label, xml = sys.argv[1], sys.argv[2]
d = open(xml).read()
m = re.search(r'text="' + re.escape(label) + r'"[^>]*bounds="\[[0-9]+,([0-9]+)\]\[', d)
if not m:
    sys.exit(3)
y = int(m.group(1))
best = None
# node attributes are not in a fixed order, so scan each node via its bounds
# and look up checkable/checked independently.
for n in re.finditer(r'<node[^>]*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"[^>]*>', d):
    x1, y1, x2, y2 = (int(v) for v in n.groups())
    tag = n.group(0)
    if x1 >= 700 and 'checkable="true"' in tag and 'checked="' in tag:
        dist = abs(y1 - y)
        if best is None or dist < best[0]:
            best = (dist, re.search(r'checked="(true|false)"', tag).group(1))
print(best[1] if best else "SWITCH-MISSING")
if not best:
    sys.exit(3)
PY
}

# set switch for Settings label $1 to $2 (true|false)
set_switch() {
    local label="$1" want="$2" cur
    ensure_settings || { echo "[warn] settings did not open"; return 1; }
    settings_top
    await_label "$label" || { echo "[warn] Settings row '$label' not found"; return 1; }
    cur=$(switch_val "$label") || { echo "[warn] no switch for '$label'"; return 1; }
    if [ "$cur" != "$want" ]; then
        tap_switch_near "$label"
        sleep 1
        cur=$(switch_val "$label") || { echo "[warn] switch for '$label' lost after tap"; return 1; }
        [ "$cur" = "$want" ] && echo "[$label] -> $want" || { echo "[warn] $label toggle failed (now $cur)"; return 1; }
    else
        echo "[$label] already $want"
    fi
}

# phone/address shown as the FIRST row of the home list, or empty
first_row() {
    dump_ui || { echo ""; return 1; }
    python3 - "$TMP/ui.xml" <<'PY'
import re, sys
d = open(sys.argv[1]).read()
for m in re.finditer(r'text="(\+?[\d\-]{6,})"[^>]*bounds="\[[0-9]+,([0-9]+)\]\[', d):
    if int(m.group(2)) >= 200:
        print(m.group(1)); break
PY
}

# ---------- 1. Unread at top ----------
info "1. Unread at top reorders the list live"
set_switch "$UNREAD_TOP_LABEL" true || exit 1
go_home; home_top
ROW_ON=$(first_row)
[ -n "$ROW_ON" ] && pass "unread-at-top ON: first row = $ROW_ON" || fail "could not read first row"

set_switch "$UNREAD_TOP_LABEL" false || exit 1
go_home; home_top
ROW_OFF=$(first_row)
if [ -n "$ROW_ON" ] && [ -n "$ROW_OFF" ] && [ "$ROW_ON" != "$ROW_OFF" ]; then
    pass "unread-at-top OFF: list reordered live ('$ROW_ON' -> '$ROW_OFF')"
else
    fail "unread-at-top OFF: list did NOT reorder (on='$ROW_ON' off='$ROW_OFF')"
fi

set_switch "$UNREAD_TOP_LABEL" true || exit 1
go_home; home_top
[ "$(first_row)" = "$ROW_ON" ] && pass "unread-at-top ON: original order restored" \
    || fail "unread-at-top ON: original order NOT restored"

# ---------- 2. Swipe actions ----------
info "2. Swipe actions apply live"
adb_ emu sms send "$DISPOSABLE" "swipe probe" >/dev/null 2>&1; sleep 2
go_home

row_y() { c=$(center_of_contains "$1"); echo "${c##* }"; }

swipe_left() { adb_ shell input swipe 950 "$1" 120 "$1" 900; sleep 1.2; }

set_switch "$SWIPE_LABEL" false || exit 1
go_home
Y=$(row_y "0998")
[ -n "$Y" ] || { fail "swipe OFF: disposable row missing"; adb_ emu sms send "$DISPOSABLE" "swipe probe" >/dev/null 2>&1; sleep 2; }
if [ -n "$Y" ]; then
    swipe_left "$Y"
    if center_of_contains "0998" >/dev/null; then
        pass "swipe actions OFF: left swipe did NOT move the conversation"
    else
        fail "swipe actions OFF: swipe still trashed the conversation"
        adb_ emu sms send "$DISPOSABLE" "swipe probe" >/dev/null 2>&1; sleep 2
    fi
fi

set_switch "$SWIPE_LABEL" true || exit 1
go_home
if center_of_contains "0998" >/dev/null; then
    Y=$(row_y "0998")
    swipe_left "$Y"
    if center_of_contains "0998" >/dev/null && grep -q "moved to trash" "$TMP/ui.xml"; then
        fail "swipe actions ON: row NOT removed"
    elif grep -q "moved to trash" "$TMP/ui.xml"; then
        pass "swipe actions ON: swipe trashes conversation (snackbar + removed)"
        tap_text Undo; sleep 1.5
    else
        fail "swipe actions ON: no 'moved to trash' snackbar"
        adb_ emu sms send "$DISPOSABLE" "swipe probe" >/dev/null 2>&1; sleep 2
    fi
else
    fail "swipe actions ON: disposable row missing before enable test"
fi

# ---------- 3. Drafts ----------
info "3. Drafts toggle shows/hides 'Draft:' prefix live"
set_switch "$DRAFTS_LABEL" true || exit 1
go_home
if dump_ui && grep -q "Draft: draft verification" "$TMP/ui.xml"; then
    pass "Drafts ON: draft preview visible on home list"
else
    fail "Drafts ON: draft preview missing (create a draft first?)"
fi

set_switch "$DRAFTS_LABEL" false || exit 1
go_home
if dump_ui && ! grep -q "Draft: draft verification" "$TMP/ui.xml"; then
    pass "Drafts OFF: draft preview hidden live (no restart)"
else
    fail "Drafts OFF: draft preview still visible"
fi

set_switch "$DRAFTS_LABEL" true || exit 1
go_home
if dump_ui && grep -q "Draft: draft verification" "$TMP/ui.xml"; then
    pass "Drafts ON: preview restored"
else
    fail "Drafts ON: preview NOT restored"
fi

# ---------- 4. Archiving + 5. Pinned (bottom sheet) ----------
info "4. Archiving + 5. Pinned items in long-press sheet apply live"
set_switch "$ARCH_LABEL" true || exit 1
set_switch "$PIN_LABEL" true || exit 1
go_home
sheet_items() {
    local c xy
    c=$(center_of_contains "live device only message" || center_of_contains "Big backup message")
    xy=($c)
    adb_ shell input swipe "${xy[0]}" "${xy[1]}" "${xy[0]}" "${xy[1]}" 900
    sleep 1.5
    dump_ui
}
sheet_items
grep -q 'text="Pin"' "$TMP/ui.xml" && pass "ON: 'Pin' in sheet" || fail "ON: 'Pin' missing"
grep -q 'text="Archive"' "$TMP/ui.xml" && pass "ON: 'Archive' in sheet" || fail "ON: 'Archive' missing"
adb_ shell input keyevent 4; sleep 1

set_switch "$ARCH_LABEL" false || exit 1
set_switch "$PIN_LABEL" false || exit 1
go_home
sheet_items
grep -q 'text="Pin"' "$TMP/ui.xml" && fail "Pinned OFF: 'Pin' STILL in sheet" || pass "Pinned OFF: 'Pin' gone (live)"
grep -q 'text="Archive"' "$TMP/ui.xml" && fail "Archiving OFF: 'Archive' STILL in sheet" || pass "Archiving OFF: 'Archive' gone (live)"
adb_ shell input keyevent 4; sleep 1

set_switch "$ARCH_LABEL" true || exit 1
set_switch "$PIN_LABEL" true || exit 1
go_home
sheet_items
grep -q 'text="Pin"' "$TMP/ui.xml" && pass "ON: 'Pin' restored" || fail "ON: 'Pin' NOT restored"
grep -q 'text="Archive"' "$TMP/ui.xml" && pass "ON: 'Archive' restored" || fail "ON: 'Archive' NOT restored"
adb_ shell input keyevent 4; sleep 1

info "final state (all toggles restored ON)"
go_home

[ "$FAIL" = "0" ] && echo -e "\n== ALL SETTINGS-LIVE PASSED ==" || echo -e "\n== SOME CHECKS FAILED =="
exit $FAIL