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
open_advanced() {
    adb_ shell input swipe 500 1900 500 700 400; sleep 0.5
    adb_ shell input swipe 500 1900 500 700 400; sleep 1
    tap_text "Advanced" || tap_contains "Advanced"
    sleep 1.5
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
TEST_NUM=15551230004
DEMO_BODY="Demo https://www.example.com/deals for you"
adb_ emu sms send "$TEST_NUM" "$DEMO_BODY" >/dev/null
sleep 3
open_home
dump_ui
if grep -qF "Demo for you" "$TMP/ui.xml"; then
    pass "home list preview shows the message without the URL"
else
    fail "home list preview missing 'Demo for you'"
fi
if grep -qF "https://www.example.com/deals" "$TMP/ui.xml"; then
    fail "raw URL still visible on the home list"
else
    pass "raw URL not shown on the home list"
fi

tap_text "Demo for you" || tap_contains "Demo for you"
sleep 2.5
dump_ui
if grep -qF "Demo for you" "$TMP/ui.xml"; then
    pass "chat bubble shows the message without the URL"
else
    fail "chat bubble missing 'Demo for you'"
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
open_home
dump_ui
tap_text "$DEMO_BODY" || tap_contains "Demo https://www.example.com/deals"
sleep 2.5
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
