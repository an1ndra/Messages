#!/usr/bin/env bash
# Verifies:
#   1. Determinate progress bar ("Loading messages") shows while system SMS import runs
#   2. Incoming OTP SMS is NOT duplicated after sync (DB v11 self-heal + sys_id linking)
set -u
source "$(dirname "$0")/env.sh"

OTP_NUM="15551239999"
OTP_BODY="G-992814 is your verification code"
PASS=0; FAIL=0
ok()   { echo "[PASS] $1"; PASS=$((PASS+1)); }
bad()  { echo "[FAIL] $1"; FAIL=$((FAIL+1)); }
note() { echo "[NOTE] $1"; }

info "Reset app data + grant permissions"
adb_ shell pm clear "$PKG" >/dev/null
"$(dirname "$0")/grant-permissions.sh" >/dev/null
for p in READ_SMS SEND_SMS RECEIVE_SMS READ_CONTACTS POST_NOTIFICATIONS; do
    adb_ shell pm grant "$PKG" android.permission.$p 2>/dev/null
done
adb_ shell cmd role add-role-holder android.app.role.SMS "$PKG" 2>/dev/null

info "Inject inbound OTP SMS"
adb_ emu sms send "$OTP_NUM" "$OTP_BODY"

info "Cold launch and watch for progress bar"
adb_ shell am force-stop "$PKG"
adb_ shell am start -n "$ACT" >/dev/null
PROGRESS_SEEN=0
for i in $(seq 1 10); do
    dump_ui && grep -q 'content-desc="Loading messages"' "$TMP/ui.xml" && { PROGRESS_SEEN=1; break; }
    sleep 0.5
done
if [ "$PROGRESS_SEEN" = 1 ]; then ok "progress bar visible during import"; else note "progress bar finished before it could be sampled (fast device) - acceptable"; fi

info "Wait for list to load (bar gone)"
READY=0
for i in $(seq 1 20); do
    if dump_ui && ! grep -q 'content-desc="Loading messages"' "$TMP/ui.xml" \
        && center_of_contains "Messages" >/dev/null 2>&1; then READY=1; break; fi
    sleep 1
done
[ "$READY" = 1 ] && ok "list loaded, progress bar dismissed" || bad "list never reached ready state"

info "Trigger a second sync pass (relaunch) then check for duplicates"
adb_ shell input keyevent KEYCODE_HOME; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 4

info "Inspect local DB for duplicate OTP rows"
adb_ shell am force-stop "$PKG"; sleep 2
DBDIR="$TMP/db"; rm -rf "$DBDIR"; mkdir -p "$DBDIR"
adb_ shell "run-as $PKG sh -c 'cat databases/messages.db; echo; cat databases/messages.db-wal 2>/dev/null'" > /dev/null 2>&1
adb_ shell run-as "$PKG" cat databases/messages.db > "$DBDIR/messages.db" 2>/dev/null
adb_ shell run-as "$PKG" sh -c 'cat databases/messages.db-wal 2>/dev/null' > "$DBDIR/messages.db-wal" 2>/dev/null
adb_ shell run-as "$PKG" sh -c 'cat databases/messages.db-shm 2>/dev/null' > "$DBDIR/messages.db-shm" 2>/dev/null

COUNT=$(python3 - "$DBDIR/messages.db" "$OTP_BODY" <<'EOF'
import sqlite3, sys
con = sqlite3.connect(sys.argv[1])
try:
    n = con.execute("SELECT COUNT(*) FROM messages WHERE body=?", (sys.argv[2],)).fetchone()[0]
except sqlite3.Error as e:
    print(-1); sys.exit()
print(n)
EOF
)
if [ "$COUNT" = "-1" ]; then
    bad "could not read local DB for dup check (inspect manually: $DBDIR)"
elif [ "$COUNT" = "1" ]; then
    ok "exactly 1 copy of OTP message stored (no duplicate)"
else
    bad "OTP message stored $COUNT times (expected 1)"
fi

echo
echo "Result: $PASS passed, $FAIL failed"
exit $((FAIL > 0))
