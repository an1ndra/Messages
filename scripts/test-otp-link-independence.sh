#!/usr/bin/env bash
# OTP/link highlight independence (Settings → Advanced):
#   - OTP digits must stay highlighted even when "Highlight links" is OFF
#     (turning the link toggle off used to drop OTP highlighting too, because
#     rememberLinkedText skipped the whole annotated builder when the toggle
#     was off).
#   - 'Hide links from messages' ON force-disables the link toggles; the URL
#     is stripped but OTP digits must stay highlighted/rendered.
#   - "Highlight links" only controls URLs: ON + warning ON -> tapping a URL
#     bubble shows the "Caution: external link" dialog; OFF -> the same tap
#     opens nothing.
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
tap_contains() { local c; c=$(center_of_contains "$1") || return 1; adb_ shell input tap $c; }

# Flip a toggle to a wanted state and re-check the pref (retry up to 3x so a
# cold-start navigation transition can't swallow the tap coordinates).
ensure_switch() {
    local label="$1" pref="$2" want="$3" i cur
    for i in 1 2 3; do
        cur=off
        [ "$(pref_get "$pref")" = "true" ] && cur=on
        [ "$cur" = "$want" ] && return 0
        tap_switch_near "$label" || return 1
        sleep 0.8
    done
    return 1
}
want_pref() {
    local name="$1" want="$2" cur=off
    [ "$(pref_get "$name")" = "true" ] && cur=on
    if [ "$cur" = "$want" ]; then
        pass "$name is $want"
    else
        fail "$name expected $want, got $cur"
    fi
}

launch_settings() {
    adb_ shell am force-stop "$PKG"; sleep 1
    adb_ shell am start -n "$ACT" --ez open_settings true; sleep 3
}
open_advanced() {
    adb_ shell input swipe 500 1900 500 700 400; sleep 0.5
    adb_ shell input swipe 500 1900 500 700 400; sleep 1
    tap_text "Advanced" || tap_contains "Advanced"
    sleep 2
}
open_chat() {
    adb_ shell am force-stop "$PKG"; sleep 1
    adb_ shell am start -n "$ACT" --es open_conversation_address "$TEST_NUM" >/dev/null
    sleep 3
}

TEST_NUM=15551230888
# Unique per run so earlier copies of this test message can't satisfy the greps.
DEMO_TAG="OPTLINK$(date +%s)$$"
DEMO_URL="https://www.example.com/verify-otp"

info "Resetting defaults"
launch_settings
open_advanced
ensure_switch "Hide links from messages" hide_links off
ensure_switch "Highlight links" highlight_links on
ensure_switch "Link open warning" link_open_warning_enabled on
want_pref hide_links off
want_pref highlight_links on
want_pref link_open_warning_enabled on

info "Injecting an OTP-only message and a URL-only message (own bubbles)"
adb_ emu sms send "$TEST_NUM" "$DEMO_TAG Your OTP is 482913" >/dev/null; sleep 1
adb_ emu sms send "$TEST_NUM" "$DEMO_URL" >/dev/null
sleep 3

info "With highlight ON + warning ON: tapping the URL bubble shows the Caution dialog"
open_chat
dump_ui
grep -qF "482913" "$TMP/ui.xml" && pass "OTP digits rendered in chat" || fail "OTP digits missing from chat"
grep -qF "$DEMO_URL" "$TMP/ui.xml" && pass "URL bubble rendered in chat" || fail "URL bubble missing from chat"
tap_text "$DEMO_URL"; sleep 1.5
dump_ui
grep -qF "Caution: external link" "$TMP/ui.xml" \
    && pass "Caution dialog shown when URL tapped (highlight ON)" \
    || fail "Caution dialog missing when URL tapped (highlight ON)"
adb_ shell input keyevent 4; sleep 1

info "With 'Hide links from messages' ON: URL stripped, OTP still rendered"
launch_settings
open_advanced
ensure_switch "Hide links from messages" hide_links on
want_pref hide_links on
want_pref highlight_links off
want_pref link_open_warning_enabled off
open_chat
dump_ui
grep -qF "482913" "$TMP/ui.xml" \
    && pass "OTP digits still visible with hide ON" \
    || fail "OTP digits missing with hide ON"
if grep -qF "$DEMO_URL" "$TMP/ui.xml"; then
    fail "URL still rendered with hide ON"
else
    pass "URL stripped from chat with hide ON"
fi
adb_ shell input keyevent 4; sleep 1

info "Turning 'Hide links' OFF, then 'Highlight links' OFF"
launch_settings
open_advanced
ensure_switch "Hide links from messages" hide_links off
ensure_switch "Highlight links" highlight_links off
want_pref hide_links off
want_pref highlight_links off
want_pref link_open_warning_enabled off

info "With highlight OFF + hide OFF: tapping the URL opens nothing and OTP stays rendered"
open_chat
dump_ui
grep -qF "482913" "$TMP/ui.xml" \
    && pass "OTP digits still visible with highlight OFF" \
    || fail "OTP digits missing with highlight OFF"
tap_text "$DEMO_URL"; sleep 1.5
dump_ui
if grep -qF "Caution: external link" "$TMP/ui.xml"; then
    fail "Caution dialog appeared when URL tapped with highlight OFF"
else
    pass "no Caution dialog when URL tapped with highlight OFF"
fi
adb_ shell input keyevent 4; sleep 1

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