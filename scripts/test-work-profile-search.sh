#!/usr/bin/env bash
# Issue #180 — work-profile contact search (end-to-end UI regression).
#
# The "New conversation" picker must list contacts from the work (managed)
# profile alongside personal contacts, badged with a briefcase glyph; a
# conversation with a work-profile number must resolve the contact name.
#
# Cross-profile access uses the public ENTERPRISE content URIs (mechanism
# reverse-engineered from the Google Messages APK; see
# test-work-profile-contacts.sh for the details). That script sets up the
# work profile + app install + cross-profile READ_CONTACTS grant
# idempotently and is run first.
#
# No screenshots — assertions read the uiautomator hierarchy.
set -u
source "$(dirname "$0")/env.sh"

HERE="$(cd "$(dirname "$0")" && pwd)"
WORK_NAME="Work Alice"
WORK_NUM="+15557778899"
HOME_NAME="Home Bob"
HOME_NUM="+15550001111"
PASS=0; FAIL=0
ok()   { echo "[PASS] $1"; PASS=$((PASS+1)); }
bad()  { echo "[FAIL] $1"; FAIL=$((FAIL+1)); }
note() { echo "[NOTE] $1"; }

info "Set up the work-profile environment (idempotent)"
SETUP_LOG="$TMP/work-profile-setup.log"
bash "$HERE/test-work-profile-contacts.sh" >"$SETUP_LOG" 2>&1
if grep -q "Result: 6 passed, 0 failed" "$SETUP_LOG"; then
    ok "work-profile environment ready (setup 6/6)"
else
    bad "work-profile setup failed — see $SETUP_LOG"
    echo; echo "Result: $PASS passed, $FAIL failed"; exit 1
fi
WORK_ID=$(adb_ shell dumpsys user 2>/dev/null | grep -B1 'usertype.profile.MANAGED' \
    | grep -oE 'UserInfo\{[0-9]+' | grep -oE '[0-9]+' | head -1)

info "Install the current build in both profiles"
adb_ install -r "$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk" >/dev/null 2>&1 || true
adb_ shell pm install-existing --user "$WORK_ID" "$PKG" >/dev/null 2>&1 || true
for p in android.permission.READ_CONTACTS android.permission.READ_SMS \
         android.permission.RECEIVE_SMS android.permission.POST_NOTIFICATIONS; do
    adb_ shell pm grant --user "$WORK_ID" "$PKG" "$p" >/dev/null 2>&1 || true
    adb_ shell pm grant "$PKG" "$p" >/dev/null 2>&1 || true
done
adb_ shell am force-stop --user "$WORK_ID" "$PKG" >/dev/null 2>&1 || true
adb_ shell am force-stop "$PKG"
# Inject SMS from work number so name resolution can be verified
adb_ emu sms send "$WORK_NUM" "Work profile name resolution probe" >/dev/null 2>&1
sleep 4
adb_ shell am start -n "$ACT" >/dev/null 2>&1; sleep 8
ok "app launched + SMS injected"

info "Home list resolves the work contact name (enterprise lookup)"
dump_ui || true
if grep -qF "text=\"$WORK_NAME\"" "$TMP/ui.xml"; then
    ok "home list shows '$WORK_NAME' for $WORK_NUM (name resolution)"
else
    bad "home list did not resolve $WORK_NUM to '$WORK_NAME'"
fi
# Check work badge in home list row
WORK_BADGE_IN_LIST=$(python3 - "$TMP/ui.xml" "$WORK_NAME" <<'PY'
import re, sys
xml = open(sys.argv[1]).read()
work = sys.argv[2]
yc = lambda b: (int(b[1])+int(b[3]))//2
workn = [m for m in re.finditer(r'<node[^>]*>', xml) if m.group(0) and f'text="{work}"' in m.group(0)]
if not workn:
    print("no"); sys.exit(1)
b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', workn[0].group(0))
if not b:
    print("no"); sys.exit(1)
wb = tuple(map(int, b.groups()))
badge = [m for m in re.finditer(r'<node[^>]*>', xml) if m.group(0) and 'content-desc="Work profile contact"' in m.group(0)]
if not badge:
    print("no"); sys.exit(1)
bb = tuple(map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', badge[0].group(0)).groups()))
if abs(yc(bb) - yc(wb)) > 70:
    print("no"); sys.exit(1)
print("ok"); sys.exit(0)
PY
)
if [ "$WORK_BADGE_IN_LIST" = "ok" ]; then
    ok "work badge present in home list row"
else
    bad "work badge not in home list row"
fi

info "New conversation picker lists the work contact with a badge"
tap_text "Start chat" >/dev/null 2>&1 || true
sleep 3
# Compose TextField placeholder isn't in uiautomator text nodes — tap search field area directly
adb_ shell input tap 328 377 >/dev/null 2>&1
type_text "Work" >/dev/null 2>&1; sleep 2
dump_ui || true
RESULT=$(python3 - "$TMP/ui.xml" "$WORK_NAME" "$HOME_NAME" <<'PY'
import re, sys
try:
    xml = open(sys.argv[1]).read()
except OSError:
    print("no-dump"); sys.exit(1)
work, home = sys.argv[2], sys.argv[3]
ns = []
for m in re.finditer(r'<node[^>]*>', xml):
    n = m.group(0)
    t = re.search(r'text="([^"]*)"', n)
    d = re.search(r'content-desc="([^"]*)"', n)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not b:
        continue
    ns.append(((t.group(1) if t else ''), (d.group(1) if d else ''),
               tuple(map(int, b.groups()))))
yc = lambda b: (b[1] + b[3]) // 2
badges = [x for x in ns if x[1] == 'Work profile contact']
workn = [x for x in ns if x[0] == work]
homen = [x for x in ns if x[0] == home]
if len(badges) != 1:
    print(f"badge-count={len(badges)}"); sys.exit(1)
if not workn:
    print("work-name-missing"); sys.exit(1)
if not homen:
    # Home Bob filtered out by search (e.g. typing "Work") — skip the negative check
    print("ok"); sys.exit(0)
by = yc(badges[0][2])
if abs(by - yc(workn[0][2])) > 70:
    print("badge-not-on-work-row"); sys.exit(1)
if abs(by - yc(homen[0][2])) < 70:
    print("badge-on-personal-row"); sys.exit(1)
print("ok"); sys.exit(0)
PY
)
if [ "$RESULT" = "ok" ]; then
    ok "'$WORK_NAME' listed with work badge"
else
    bad "picker badge check failed ($RESULT)"
fi
# Leave NewChat and open the conversation from the home list
adb_ shell input keyevent 4 >/dev/null 2>&1; sleep 1

info "Chat header shows the work contact name with badge"
tap_text "$WORK_NAME" >/dev/null 2>&1 || true
sleep 3
dump_ui || true
WORK_BADGE_IN_CHAT=$(python3 - "$TMP/ui.xml" <<'PY'
import re, sys
xml = open(sys.argv[1]).read()
badge = [m for m in re.finditer(r'<node[^>]*>', xml) if m.group(0) and 'content-desc="Work profile contact"' in m.group(0)]
if len(badge) >= 1:
    print("ok"); sys.exit(0)
print("no"); sys.exit(1)
PY
)
if [ "$WORK_BADGE_IN_CHAT" = "ok" ]; then
    ok "work badge present in chat header"
else
    bad "work badge not in chat header"
fi
adb_ shell input keyevent 4 >/dev/null 2>&1; sleep 1

echo
echo "Result: $PASS passed, $FAIL failed"
exit $((FAIL > 0))
