#!/usr/bin/env bash
# Per-message Delete with Undo (long-press a chat bubble):
#   - The bubble menu offers Copy / Forward / Lock / Delete.
#   - Delete hides just that message; a "Message deleted" snackbar with an
#     "Undo" action appears.
#   - Undo brings the message back, in place.
#   - Delete is a soft delete (messages.deleted_at), so the row survives and a
#     later system re-sync does not resurrect it.
#
# Precondition: emulator booted and the app installed.
source "$(dirname "$0")/env.sh"

PASS=0; FAIL=0
info() { echo -e "\n=== $* ==="; }
pass() { echo "[PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }

# Unique per run: re-running must not collide with earlier copies of the message.
MARK="DLT$(date +%s)$$"
NUM=15551230177

db_pull() { adb_ shell "run-as $PKG cat databases/messages.db" > "$TMP/msg.db" 2>/dev/null; }

# Poll the hierarchy until $1 appears (up to ~12s). 0 = found.
wait_for_text() {
    local i
    for i in $(seq 1 12); do
        dump_ui
        grep -qF "$1" "$TMP/ui.xml" && return 0
        sleep 1
    done
    return 1
}

# Poll the hierarchy until $1 disappears (up to ~12s). 0 = gone.
wait_for_text_gone() {
    local i
    for i in $(seq 1 12); do
        dump_ui
        grep -qF "$1" "$TMP/ui.xml" || return 0
        sleep 1
    done
    return 1
}

# Prints deleted_at for the message whose body contains $1 ("MISSING" if absent).
deleted_at_of() {
    python3 - "$1" "$TMP/msg.db" <<'PY'
import sqlite3, sys
needle, path = sys.argv[1], sys.argv[2]
con = sqlite3.connect(path)
r = list(con.execute(
    "SELECT deleted_at FROM messages WHERE body LIKE ? ORDER BY id DESC LIMIT 1",
    (f"%{needle}%",)))
print(r[0][0] if r else "MISSING")
PY
}

info "Injecting a message to delete"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ emu sms send "$NUM" "Keep me $MARK please" >/dev/null
sleep 3

info "Opening the conversation directly"
adb_ shell am start -n "$ACT" --es open_conversation_address "$NUM" >/dev/null
if wait_for_text "Keep me $MARK please"; then
    pass "message bubble visible before delete"
else
    echo "[FAIL] message bubble never appeared"
    echo; echo "Results: $PASS passed, $FAIL failed"; exit 1
fi
db_pull
before=$(deleted_at_of "$MARK")
echo "deleted_at before = $before"
if [ "$before" = "0" ]; then
    pass "message is present and not deleted in the DB"
else
    fail "unexpected deleted_at before delete ($before)"
fi

info "Long-pressing the bubble to open the menu"
c=$(center_of "Keep me $MARK please")
if [ -z "$c" ]; then
    echo "[FAIL] could not locate the bubble"
    echo; echo "Results: $PASS passed, $FAIL failed"; exit 1
fi
x=${c% *}; y=${c#* }
adb_ shell input swipe "$x" "$y" "$x" "$y" 900
sleep 1.5
dump_ui
for item in Copy Forward Lock Delete; do
    if grep -q "text=\"$item\"" "$TMP/ui.xml"; then
        pass "menu shows '$item'"
    else
        fail "menu missing '$item'"
    fi
done

info "Tapping Delete"
if tap_text "Delete"; then
    sleep 2
else
    fail "could not tap Delete"
fi
if wait_for_text_gone "Keep me $MARK please"; then
    pass "message hidden from the chat after delete"
else
    fail "message still visible after delete"
fi
db_pull
after=$(deleted_at_of "$MARK")
echo "deleted_at after delete = $after"
if [ "$after" != "0" ] && [ "$after" != "MISSING" ]; then
    pass "row kept but soft-deleted in the DB"
else
    fail "expected a soft-deleted row, got '$after'"
fi

info "Undo returns the message"
dump_ui
if grep -q "text=\"Undo\"" "$TMP/ui.xml"; then
    pass "snackbar offers Undo"
    tap_text "Undo"
    if wait_for_text "Keep me $MARK please"; then
        pass "message restored in the chat by Undo"
    else
        fail "message not restored by Undo"
    fi
    db_pull
    undone=$(deleted_at_of "$MARK")
    echo "deleted_at after undo = $undone"
    if [ "$undone" = "0" ]; then
        pass "deleted_at cleared in the DB after Undo"
    else
        fail "deleted_at still set after Undo ($undone)"
    fi
else
    fail "no Undo action shown after delete"
fi

# leave a clean state + return home
adb_ shell input keyevent 4; sleep 1.5

echo
echo "Results: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
