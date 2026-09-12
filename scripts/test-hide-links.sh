#!/usr/bin/env bash
# "Hide links from messages" regression (Settings → Advanced):
#   - The new "Hide links from messages" toggle is present.
#   - Turning it ON force-disables "Highlight links" and "Link open warning".
#   - While it is ON, tapping either dependent switch does nothing (disabled).
#   - Turning it OFF leaves both off; "Link open warning" stays disabled until
#     "Highlight links" is turned back on.
#   - While it is ON the URL is removed entirely (no placeholder) from the chat
#     bubble AND from the home-list preview; both return once it is OFF.
#   - Restores the defaults (hide=off, highlight=on, warning=on) at the end.
#
# Precondition: emulator booted and the app installed.
source "$(dirname "$0")/env.sh"

PASS=0; FAIL=0
info() { echo -e "\n=== $* ==="; }
pass() { echo "[PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }

pref_get() {
    adb_ shell "run-as $PKG cat shared_prefs/messages_settings.xml" 2>/dev/null \
        | grep -oE "name=\"$1\" value=\"[^\"]*\"" | grep -oE 'value="[^"]*"' | cut -d'"' -f2
}
pref_on() { [ "$(pref_get "$1")" = "true" ]; }
want_pref() {
    local name="$1" want="$2" cur=off
    pref_on "$name" && cur=on
    if [ "$cur" = "$want" ]; then
        pass "$name is $want"
    else
        fail "$name expected $want, got $cur"
    fi
}
ensure_switch() {
    local label="$1" pref="$2" want="$3" cur=off
    pref_on "$pref" && cur=on
    if [ "$cur" != "$want" ]; then
        tap_switch_near "$label" || return 1
        sleep 0.7
    fi
}
launch_settings() {
    adb_ shell am force-stop "$PKG"; sleep 1
    adb_ shell am start -n "$ACT" --ez open_settings true; sleep 3
}
open_home() {
    adb_ shell am force-stop "$PKG"; sleep 1
    adb_ shell am start -n "$ACT"; sleep 3
}
# Scroll the home list until a row containing $1 is on screen (0 = found).
find_list_row() {
    local i
    for i in 1 2 3 4 5 6; do
        dump_ui
        grep -qF "$1" "$TMP/ui.xml" && return 0
        adb_ shell input swipe 500 1600 500 700 300; sleep 0.8
    done
    return 1
}
open_advanced() {
    adb_ shell input swipe 500 1900 500 700 400; sleep 0.5
    adb_ shell input swipe 500 1900 500 700 400; sleep 1
    tap_text "Advanced" || tap_contains "Advanced"
    sleep 1.5
}

# Long-press the bubble whose displayed text contains $1, tap Copy, then paste
# into the compose input and leave the resulting EditText text in $PASTED_TEXT.
# Reads it back through the app (the clipboard can't be dumped reliably from
# adb here), so the caller can assert what the user actually copied.
PASTED_TEXT=
copy_bubble_into_input() {
    local q b x1 y1 y2 lx ly i
    PASTED_TEXT=
    q=$(re_escape "$1")
    for i in 1 2 3; do
        dump_ui && break
        sleep 1
    done
    b=$(grep -oE "(text|content-desc)=\"[^\"]*$q[^\"]*\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" \
        "$TMP/ui.xml" 2>/dev/null | head -1 | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
    [ -z "$b" ] && { echo "[copy] bubble '$1' not found"; return 1; }
    x1=$(sed -E 's/\[([0-9]+),([0-9]+)\].*/\1/' <<< "$b")
    y1=$(sed -E 's/\[[0-9]+,([0-9]+)\].*/\1/' <<< "$b")
    y2=$(sed -E 's/.*\]\[[0-9]+,([0-9]+)\]/\1/' <<< "$b")
    # Long-press the bubble's left padding rather than the text itself: a
    # highlighted URL takes the gesture at its own coordinates and pops the
    # link warning instead of the message menu.
    lx=$((x1 - 25)); [ $lx -lt 32 ] && lx=32
    ly=$(( (y1 + y2) / 2 ))
    adb_ shell input motionevent DOWN $lx $ly
    sleep 1.2
    adb_ shell input motionevent UP $lx $ly
    sleep 1.5
    if ! tap_text "Copy" >/dev/null 2>&1; then
        # fallback: retry with the swipe-style long press once.
        adb_ shell input swipe $lx $ly $lx $ly 900
        sleep 1.5
        tap_text "Copy" >/dev/null 2>&1 || return 1
    fi
    sleep 0.8
    tap_edittext >/dev/null 2>&1 || return 1
    sleep 1
    adb_ shell input keyevent 279   # KEYCODE_PASTE
    sleep 1.5
    for i in 1 2 3; do
        dump_ui && break
        sleep 1
    done
    PASTED_TEXT=$(python3 -c '
import re, sys
best = ""
for m in re.finditer(r"<node [^>]*>", open(sys.argv[1], errors="replace").read()):
    tag = m.group(0)
    if "class=\"android.widget.EditText\"" in tag:
        t = re.search(r"text=\"([^\"]*)\"", tag)
        best = t.group(1) if t else ""
        break
sys.stdout.write(best)
' "$TMP/ui.xml")
    [ -n "$PASTED_TEXT" ]
}

info "Opening Settings → Advanced"
launch_settings
open_advanced
dump_ui
if grep -q "Hide links from messages" "$TMP/ui.xml"; then
    pass "Advanced screen shows 'Hide links from messages'"
else
    fail "'Hide links from messages' row missing"
fi

info "Resetting to defaults"
ensure_switch "Hide links from messages" hide_links off
ensure_switch "Highlight links" highlight_links on
ensure_switch "Link open warning" link_open_warning_enabled on
want_pref hide_links off
want_pref highlight_links on
want_pref link_open_warning_enabled on

info "Turning 'Hide links from messages' ON"
ensure_switch "Hide links from messages" hide_links on
want_pref hide_links on
want_pref highlight_links off
want_pref link_open_warning_enabled off

info "Dependents are disabled while 'Hide links' is ON"
tap_switch_near "Highlight links"; sleep 0.7
want_pref highlight_links off
tap_switch_near "Link open warning"; sleep 0.7
want_pref link_open_warning_enabled off

info "URL is stripped from the home-list preview and the chat bubble while ON"
# Dedicated number: no saved draft or contact name, either of which would replace
# the snippet on the home list and mask the preview this section asserts on.
TEST_NUM=15551230777
# Unique per run so earlier copies of this test message can't satisfy the greps.
DEMO_TAG="HIDE$(date +%s)$$"
DEMO_BODY="Demo $DEMO_TAG https://www.example.com/deals for you"
DEMO_STRIPPED="Demo $DEMO_TAG for you"
adb_ emu sms send "$TEST_NUM" "$DEMO_BODY" >/dev/null
sleep 3
open_home
if find_list_row "$DEMO_TAG"; then
    pass "home list row for the message is visible"
else
    fail "home list row for the message was never found"
fi
if grep -qF "$DEMO_STRIPPED" "$TMP/ui.xml"; then
    pass "home list preview shows the message without the URL"
else
    fail "home list preview missing '$DEMO_STRIPPED'"
fi
if grep -qF "https://www.example.com/deals" "$TMP/ui.xml"; then
    fail "raw URL still visible on the home list"
else
    pass "raw URL not shown on the home list"
fi

info "Opening that conversation"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" --es open_conversation_address "$TEST_NUM" >/dev/null
sleep 3
dump_ui
if grep -qF "$DEMO_STRIPPED" "$TMP/ui.xml"; then
    pass "chat bubble shows the message without the URL"
else
    fail "chat bubble missing '$DEMO_STRIPPED'"
fi
if grep -qF "https://www.example.com/deals" "$TMP/ui.xml"; then
    fail "raw URL still visible in the chat bubble"
else
    pass "raw URL not shown in the chat bubble"
fi
if grep -qF "[Link hidden]" "$TMP/ui.xml"; then
    fail "a placeholder is still shown in place of the link"
else
    pass "no placeholder left in place of the link"
fi

info "A chat full of link messages renders every one redacted"
# Regression for the flash: produceState kept its previous value across key
# changes, so enabling the option painted each bubble's raw URL for a frame
# before swapping in the redacted text. Every URL must be redacted at once.
MANY_NUM=15551230855
MANY_TAG="MANY$(date +%s)$$"
for i in 1 2 3 4 5 6; do
    adb_ emu sms send "$MANY_NUM" "Msg $MANY_TAG-$i see https://www.example.com/page$i now" >/dev/null
    sleep 0.6
done
sleep 3
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" --es open_conversation_address "$MANY_NUM" >/dev/null
sleep 3.5
dump_ui
urls=$(grep -oE 'https://www\.example\.com/page[0-9]' "$TMP/ui.xml" | sort -u | wc -l)
echo "raw URLs still rendered: $urls"
if [ "$urls" -eq 0 ]; then
    pass "no URLs rendered in the multi-link chat"
else
    fail "$urls URL(s) still rendered in the multi-link chat"
fi
seen=0
for i in 1 2 3 4 5 6; do
    grep -qF "Msg $MANY_TAG-$i see" "$TMP/ui.xml" && seen=$((seen + 1))
done
echo "redacted bubbles on screen: $seen/6"
if [ "$seen" -ge 5 ]; then
    pass "link messages render with the URL stripped"
else
    fail "only $seen/6 redacted link messages visible"
fi

info "Copy steps in the message body really hide the URL from the clipboard"
copy_bubble_into_input "Msg $MANY_TAG-1 see"
case "$PASTED_TEXT" in
    *"Msg $MANY_TAG-1 see"*) pass "copied text matches the redacted bubble" ;;
    *) fail "copied text missing '$MANY_TAG-1 see' (got: '$PASTED_TEXT')" ;;
esac
case "$PASTED_TEXT" in
    *example.com*|*https:*|*\.com*) fail "copied text leaks the URL (got: '$PASTED_TEXT')" ;;
    *) pass "copied text has no URL" ;;
esac

info "Turning 'Hide links from messages' OFF"
launch_settings
open_advanced
ensure_switch "Hide links from messages" hide_links off
want_pref hide_links off
want_pref highlight_links off
want_pref link_open_warning_enabled off

info "'Link open warning' stays disabled while 'Highlight links' is OFF"
tap_switch_near "Link open warning"; sleep 0.7
want_pref link_open_warning_enabled off

info "'Link open warning' can be enabled once 'Highlight links' is ON"
ensure_switch "Highlight links" highlight_links on
want_pref highlight_links on
tap_switch_near "Link open warning"; sleep 0.7
want_pref link_open_warning_enabled on

info "Chat shows the URL again once hide is OFF"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" --es open_conversation_address "$TEST_NUM" >/dev/null
sleep 3
dump_ui
if grep -qF "https://www.example.com/deals" "$TMP/ui.xml"; then
    pass "raw URL visible again with hide OFF"
else
    fail "raw URL missing with hide OFF"
fi
if grep -qF "[Link hidden]" "$TMP/ui.xml"; then
    fail "placeholder still present with hide OFF"
else
    pass "no placeholder with hide OFF"
fi
info "Copy includes the URL again once hide is OFF"
RESTORE_NUM=15551230666
RESTORE_TAG="COPYURL$(date +%s)$$"
adb_ emu sms send "$RESTORE_NUM" "See $RESTORE_TAG at https://www.example.com/deals now" >/dev/null
sleep 2.5
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" --es open_conversation_address "$RESTORE_NUM" >/dev/null
sleep 3.5
copy_bubble_into_input "$RESTORE_TAG"
case "$PASTED_TEXT" in
    *"https://www.example.com/deals"*) pass "copied text includes the URL with hide OFF" ;;
    *) fail "copied text missing the URL with hide OFF (got: '$PASTED_TEXT')" ;;
esac

info "Restoring defaults"
launch_settings
open_advanced
ensure_switch "Hide links from messages" hide_links off
ensure_switch "Highlight links" highlight_links on
ensure_switch "Link open warning" link_open_warning_enabled on
want_pref hide_links off
want_pref highlight_links on
want_pref link_open_warning_enabled on

echo
echo "Results: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
