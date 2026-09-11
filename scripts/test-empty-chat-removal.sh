#!/usr/bin/env bash
# Deleting every message in a chat removes the chat itself:
#   - A chat emptied of its messages must not linger at the bottom of the home
#     list; leaving the chat moves it to Trash.
#   - A chat that still holds a draft is kept (so unsent text isn't lost).
#   - Undo keeps the chat, because the message comes back and it isn't empty.
#   - Restoring from Trash brings the conversation back.
#
# Precondition: emulator booted and the app installed.
source "$(dirname "$0")/env.sh"

PASS=0; FAIL=0
info() { echo -e "\n=== $* ==="; }
pass() { echo "[PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }

NUM=15551230888
NUM2=15551230899
NUM3=15551230933
# Unique per run: a re-run must not collide with messages a previous run left
# behind (the Undo case below deliberately leaves one in place).
TAG="E$(date +%s)$$"

db_pull() { adb_ shell "run-as $PKG cat databases/messages.db" > "$TMP/e.db" 2>/dev/null; }

# deleted_at of the newest message whose body contains $1 ("MISSING" if absent).
msg_deleted_at() {
    python3 - "$1" "$TMP/e.db" <<'PY'
import sqlite3, sys
needle, path = sys.argv[1], sys.argv[2]
con = sqlite3.connect(path)
rows = list(con.execute(
    "SELECT deleted_at FROM messages WHERE body LIKE ? ORDER BY id DESC LIMIT 1",
    (f"%{needle}%",)))
print(rows[0][0] if rows else "MISSING")
PY
}

# "<deleted_at> <visible_msgs>" for a conversation address ("MISSING -1" if absent).
conv_state() {
    python3 - "$1" "$TMP/e.db" <<'PY'
import sqlite3, sys
addr, path = sys.argv[1], sys.argv[2]
con = sqlite3.connect(path)
rows = list(con.execute("SELECT id, deleted_at FROM conversations WHERE address=?", (addr,)))
if not rows:
    print("MISSING -1")
    raise SystemExit
cid, deleted_at = rows[0]
n = con.execute(
    "SELECT COUNT(*) FROM messages WHERE conversation_id=? AND deleted_at=0", (cid,)).fetchone()[0]
print(f"{deleted_at} {n}")
PY
}

on_home_list() {  # on_home_list <needle>
    local i
    for i in 1 2 3 4 5 6; do
        dump_ui
        grep -qF "$1" "$TMP/ui.xml" && return 0
        adb_ shell input swipe 500 1600 500 700 300; sleep 0.8
    done
    return 1
}

# Opens a chat and long-press-deletes the newest message containing $1.
open_and_delete() {
    adb_ shell am start -n "$ACT" --es open_conversation_address "$1" >/dev/null
    sleep 3
    dump_ui
    local c
    c=$(center_of "$2")
    if [ -z "$c" ]; then
        return 1
    fi
    local x=${c% *} y=${c#* }
    adb_ shell input swipe "$x" "$y" "$x" "$y" 900; sleep 1.5
    dump_ui
    tap_text "Delete" >/dev/null
    sleep 2
}

MSG1="Only message $TAG A"
MSG2="Only message $TAG B"
MSG3="Only message $TAG C"

info "Seed a one-message chat"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ emu sms send "$NUM" "$MSG1" >/dev/null
sleep 3
db_pull
read -r d0 n0 <<<"$(conv_state "$NUM")"
echo "conversation: deleted_at=$d0 visible_msgs=$n0"
[ "$d0" = "0" ] && [ "$n0" -ge 1 ] && pass "seeded chat has visible message(s)" \
    || fail "seed failed (deleted_at=$d0 msgs=$n0)"

info "Delete the only message"
if open_and_delete "$NUM" "$MSG1"; then
    db_pull
    read -r d1 n1 <<<"$(conv_state "$NUM")"
    m1=$(msg_deleted_at "$MSG1")
    echo "after delete: conv_deleted_at=$d1 visible_msgs=$n1 msg_deleted_at=$m1"
    [ "$n1" = "0" ] && pass "no visible messages left" || fail "messages still visible ($n1)"
    [ "$m1" != "0" ] && [ "$m1" != "MISSING" ] && pass "message soft-deleted in the DB" \
        || fail "message not soft-deleted ($m1)"
    [ "$d1" = "0" ] && pass "chat still present while the Undo window is open" \
        || fail "chat was trashed too early (deleted_at=$d1)"
else
    fail "could not open the chat and delete the message"
fi

info "Leaving the chat trashes the emptied conversation"
dump_ui
tap_desc "Back" >/dev/null 2>&1 || adb_ shell input keyevent 4
sleep 2.5
db_pull
read -r d2 n2 <<<"$(conv_state "$NUM")"
echo "after leaving: deleted_at=$d2 visible_msgs=$n2"
if [ "$d2" != "0" ] && [ "$d2" != "MISSING" ]; then
    pass "emptied chat was moved to trash"
else
    fail "emptied chat not trashed (deleted_at=$d2)"
fi
if on_home_list "$MSG1"; then
    fail "emptied chat still visible on the home list"
else
    pass "emptied chat gone from the home list"
fi

info "It is recoverable from Trash"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" --ez open_settings true >/dev/null
sleep 3
adb_ shell input swipe 500 1900 500 700 400; sleep 0.5
adb_ shell input swipe 500 1900 500 700 400; sleep 1
tap_text "Trash" >/dev/null 2>&1 || tap_contains "Trash" >/dev/null
sleep 2
dump_ui
if grep -qF "$MSG1" "$TMP/ui.xml" || grep -qF "$NUM" "$TMP/ui.xml"; then
    pass "conversation listed in Trash"
else
    fail "conversation not listed in Trash"
fi
tap_contains "Restore" >/dev/null 2>&1 || tap_text "Restore" >/dev/null
sleep 2
db_pull
read -r d3 n3 <<<"$(conv_state "$NUM")"
echo "after restore: deleted_at=$d3 visible_msgs=$n3"
[ "$d3" = "0" ] && pass "restore clears the trashed flag" || fail "restore failed (deleted_at=$d3)"

info "A chat with a draft is kept"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ emu sms send "$NUM2" "$MSG2" >/dev/null
sleep 3
if open_and_delete "$NUM2" "$MSG2"; then
    tap_edittext >/dev/null 2>&1
    sleep 0.8
    type_text "half written" >/dev/null 2>&1
    sleep 0.8
    adb_ shell input keyevent 4; sleep 0.6
    tap_desc "Back" >/dev/null 2>&1 || adb_ shell input keyevent 4
    sleep 2.5
    db_pull
    read -r d4 n4 <<<"$(conv_state "$NUM2")"
    echo "draft chat after leaving: deleted_at=$d4 visible_msgs=$n4"
    [ "$d4" = "0" ] && pass "chat with a draft is kept (not trashed)" \
        || fail "chat with a draft was trashed (deleted_at=$d4)"
else
    fail "could not open the second chat and delete its message"
fi

info "Undo keeps the chat (it is no longer empty)"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ emu sms send "$NUM3" "$MSG3" >/dev/null
sleep 3
adb_ shell am start -n "$ACT" --es open_conversation_address "$NUM3" >/dev/null
sleep 3
dump_ui
c=$(center_of "$MSG3")
if [ -z "$c" ]; then
    fail "third message bubble not found"
else
    adb_ shell input swipe ${c% *} ${c#* } ${c% *} ${c#* } 900; sleep 1.5
    dump_ui
    tap_text "Delete" >/dev/null
    sleep 2
    dump_ui
    tap_text "Undo" >/dev/null
    sleep 2.5
    tap_desc "Back" >/dev/null 2>&1 || adb_ shell input keyevent 4
    sleep 2.5
    db_pull
    read -r d5 n5 <<<"$(conv_state "$NUM3")"
    m5=$(msg_deleted_at "$MSG3")
    echo "after undo+leave: conv_deleted_at=$d5 visible_msgs=$n5 msg_deleted_at=$m5"
    [ "$d5" = "0" ] && pass "chat kept after Undo (not trashed)" \
        || fail "chat trashed despite Undo (deleted_at=$d5)"
    [ "$m5" = "0" ] && pass "message restored by Undo" || fail "message not restored ($m5)"
fi

echo
echo "Results: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
