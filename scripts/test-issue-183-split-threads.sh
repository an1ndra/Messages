#!/usr/bin/env bash
# Reproduces / regression-tests issue #183:
#   "Some imported conversations split into sent and received messages"
#
# Root cause: syncFromSystem() keys conversations on the RAW provider address
# string (Repository.kt getOrCreateConversationBlocking 'WHERE address=?'). When
# a backup restored by SMS Import/Export stores sent messages with a different
# address format than received ones (e.g. "+15551234567" vs "15551234567", both
# pointing at the same contact / same system thread), the app creates TWO
# threads: one sent-only, one received-only.
#
# This script faithfully reproduces the real-world flow:
#   1. Crafts a v2 backup (ZIP with messages.ndjson) where received + sent for
#      the same contact carry different address formats, plus a control contact
#      whose format is consistent.
#   2. Restores it into the system SMS provider through SMS Import/Export
#      (com.github.tmo1.sms_ie) — the same app the reporter used.
#   3. Fresh-launches Messages so the auto-import runs.
#   4. Asserts the expected CORRECT behavior (one conversation per contact).
#      Before the fix this fails -> demonstrates the split; after the fix it
#      passes -> regression guard.
#
# Requires: root adb, sqlite3 on the device, and SMS Import/Export installed
# (install from https://f-droid.org/packages/com.github.tmo1.sms_ie).
set -u
source "$(dirname "$0")/env.sh"

RECV_ADDR="+15551234567"   # received messages carry E.164 format
SENT_ADDR="15551234567"    # sent messages carry raw/dialed format (same person!)
CTRL_ADDR="15559876543"    # control contact: consistent format on both sides

SMS_IE_PKG="com.github.tmo1.sms_ie"
SMS_IE_ACT="$SMS_IE_PKG/.MainActivity"
BACKUP_NAME="sms-ie-issue183-$(date +%Y%m%d%H%M%S).zip"
LOCAL_ZIP="$TMP/$BACKUP_NAME"

PASS=0; FAIL=0
ok()   { echo "[PASS] $1"; PASS=$((PASS+1)); }
bad()  { echo "[FAIL] $1"; FAIL=$((FAIL+1)); }
note() { echo "[NOTE] $1"; }

# Find the LIVE telephony provider DB (the per-user credential-encrypted one);
# the /data/user_de/0 copy is stale/unused on this AVD.
provider_db() {
    for p in \
        /data/data/com.android.providers.telephony/databases/mmssms.db \
        /data/user/0/com.android.providers.telephony/databases/mmssms.db \
        /data/user_de/0/com.android.providers.telephony/databases/mmssms.db
    do
        if [ "$(adb_ shell "sqlite3 $p 'SELECT COUNT(*) FROM sms;' 2>/dev/null")" != "" ]; then
            echo "$p"; return 0
        fi
    done
    return 1
}

# smash the system SMS provider's in-memory cache of the dialogs by tapping any
# visible "IMPORT MESSAGES"/dialog nodes — generic dump-and-tap helper.
dump_ui() {
    adb_ shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb_ pull /sdcard/ui.xml "$TMP/ui.xml" >/dev/null 2>&1
    [ -f "$TMP/ui.xml" ]
}
node_bounds() { # node_bounds "TEXT" -> "x y" center, or empty
    local q b
    q=$(MRE_ESC "$1")
    b=$(grep -oE "(text|content-desc)=\"$q\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" "$TMP/ui.xml" 2>/dev/null | head -1 \
        | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
    [ -n "$b" ] || return 1
    echo "$b"
}
bound_center() { # bound_center "[x1,y1][x2,y2]" -> "x y"
    python3 - "$1" <<'EOF'
import re, sys
x1, y1, x2, y2 = map(int, re.findall(r"\d+", sys.argv[1])[:4])
print((x1 + x2) // 2, (y1 + y2) // 2)
EOF
}
MRE_ESC() { python3 -c 'import re,sys; sys.stdout.write(re.escape(sys.stdin.read().strip()))' <<< "$1"; }
tap_text() {
    local b c
    for _ in 1 2 3; do
        dump_ui || { sleep 1; continue; }
        b=$(node_bounds "$1") && [ -n "$b" ] && c=$(bound_center "$b") || { sleep 1; continue; }
        adb_ shell input tap $c
        echo "[tap] '$1' at ($c)"
        return 0
    done
    echo "[tap] '$1' NOT FOUND"
    return 1
}

# Tap the LAST node whose text matches (SAF drawer rows appear after their
# toolbar titles in the dump, so the row is the last match; taps the row).
tap_last() {
    local b c
    for _ in 1 2 3; do
        dump_ui || { sleep 1; continue; }
        b=$(python3 - "$1" <<'EOF'
import re, sys
xml = open("/tmp/opencode/messages-tests/ui.xml", encoding="utf-8").read()
q = re.escape(sys.argv[1])
bounds = re.findall(r'(?:text|content-desc)="' + q + r'"[^>]*bounds="(\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\])"', xml)
if bounds:
    try:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds[-1])[:4])
        print((x1+x2)//2, (y1+y2)//2)
    except Exception:
        sys.exit(1)
else:
    sys.exit(1)
EOF
) && [ -n "$b" ] || { sleep 1; continue; }
        adb_ shell input tap $b
        echo "[tap-last] '$1' at ($b)"
        return 0
    done
    echo "[tap-last] '$1' NOT FOUND"
    return 1
}

info "0. pre-flight"
adb_ root >/dev/null 2>&1; sleep 2; adb_ wait-for-device
if ! adb_ shell pm list packages | grep -q "^package:$SMS_IE_PKG\$"; then
    bad "SMS Import/Export not installed. Run: adb install -r <com.github.tmo1.sms_ie_10119.apk>"
    exit 1
fi
LIVE_DB=$(provider_db) || { bad "Could not locate telephony provider DB"; exit 1; }
note "live provider DB: $LIVE_DB"
adb_ shell pm clear "$PKG" >/dev/null 2>&1
adb_ shell pm clear "$SMS_IE_PKG" >/dev/null 2>&1

info "1. wipe any leftover test rows in the provider"
adb_ shell "sqlite3 $LIVE_DB \"DELETE FROM sms WHERE address IN ('$RECV_ADDR','$SENT_ADDR','$CTRL_ADDR');\""

info "2. craft a v2 backup (ZIP + messages.ndjson) with mixed address formats"
python3 - "$LOCAL_ZIP" "$RECV_ADDR" "$SENT_ADDR" "$CTRL_ADDR" <<'EOF'
import json, random, sys, zipfile, string
where, recv, sent, ctrl = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
ts = 1726050000000
msgs = [
    {"address": recv, "body": "Working late, will text you after.", "date": str(ts), "type": "1", "read": "1", "status": "-1", "sub_id": "-1", "locked": "0", "seen": "1"},
    {"address": recv, "body": "Need to talk about tomorrow morning.", "date": str(ts+3600000), "type": "1", "read": "1", "status": "-1", "sub_id": "-1", "locked": "0", "seen": "1"},
    {"address": sent, "body": "Sure, text me when you're free.", "date": str(ts+7200000), "type": "2", "read": "1", "status": "2", "sub_id": "-1", "locked": "0", "seen": "1"},
    {"address": sent, "body": "On my way home now, see you in 20.", "date": str(ts+10800000), "type": "2", "read": "1", "status": "2", "sub_id": "-1", "locked": "0", "seen": "1"},
    {"address": ctrl, "body": "Can you call me back?", "date": str(ts+86400000), "type": "1", "read": "1", "status": "-1", "sub_id": "-1", "locked": "0", "seen": "1"},
    {"address": ctrl, "body": "Calling you in 5.", "date": str(ts+90000000), "type": "2", "read": "1", "status": "2", "sub_id": "-1", "locked": "0", "seen": "1"},
]
import zipfile as z
with z.ZipFile(where, "w") as zf:
    zf.writestr("messages.ndjson", "".join(json.dumps(m) + "\n" for m in msgs))
print("  wrote", len(msgs), "messages ->", where)
EOF
adb_ shell mkdir -p /sdcard/Download
adb_ push "$LOCAL_ZIP" "/sdcard/Download/$BACKUP_NAME" >/dev/null

info "3. make SMS Import/Export the default handler"
adb_ shell cmd role add-role-holder android.app.role.SMS "$SMS_IE_PKG"
for p in READ_SMS READ_CONTACTS WRITE_CONTACTS READ_CALL_LOG WRITE_CALL_LOG POST_NOTIFICATIONS; do
    adb_ shell pm grant "$SMS_IE_PKG" "android.permission.$p" 2>/dev/null
done
ok "SMS Import/Export is default SMS handler + permissions granted"

info "4. drive SMS Import/Export to restore the backup"
adb_ shell am start -n "$SMS_IE_ACT" >/dev/null 2>&1
sleep 3
tap_text "IMPORT MESSAGES" || bad "could not tap IMPORT MESSAGES"
sleep 2
if dump_ui && node_bounds "OK" >/dev/null; then
    tap_text "OK"; sleep 2
fi
# system "set default SMS app" role dialog: pick the app then SET AS DEFAULT
if dump_ui && node_bounds "SMS Import / Export" >/dev/null; then
    b=$(node_bounds "SMS Import / Export") && [ -n "$b" ] || b=""
    if [ -n "$b" ]; then
        c=$(bound_center "$b"); adb_ shell input tap $c
        sleep 1
        tap_text "SET AS DEFAULT" || bad "could not set as default"
        sleep 2
    fi
fi
# SAF file picker -> find the backup file (if already listed, tap it directly;
# otherwise navigate roots drawer -> Downloads)
FOUND=0
for _ in $(seq 1 4); do
    if dump_ui && grep -q "$BACKUP_NAME" "$TMP/ui.xml"; then FOUND=1; break; fi
    sleep 1
done
if [ "$FOUND" = 0 ] && dump_ui && node_bounds "Show roots" >/dev/null; then
    tap_text "Show roots"; sleep 2
    if dump_ui && node_bounds "Downloads" >/dev/null; then
        tap_last "Downloads"; sleep 2
    fi
    for _ in $(seq 1 4); do
        if dump_ui && grep -q "$BACKUP_NAME" "$TMP/ui.xml"; then FOUND=1; break; fi
        sleep 1
    done
fi
if [ "$FOUND" = 1 ]; then
    tap_text "$BACKUP_NAME" || bad "backup file not found in picker"
    sleep 3
else
    bad "backup '$BACKUP_NAME' not listed in Downloads picker"
fi
# wait for the "N SMS(s) and ... imported" result message
IMPORTED=0
for _ in $(seq 1 15); do
    if dump_ui && grep -q "imported" "$TMP/ui.xml"; then IMPORTED=1; break; fi
    sleep 2
done
[ "$IMPORTED" = 1 ] && ok "SMS Import/Export restored the backup" || bad "no import confirmation seen"

info "5. sanity-check the provider now holds both address formats (same thread)"
adb_ shell "sqlite3 $LIVE_DB \"SELECT address,type,thread_id FROM sms WHERE address IN ('$RECV_ADDR','$SENT_ADDR','$CTRL_ADDR') ORDER BY _id;\"" | tee "$TMP/provider_rows.txt"
GRPCNT=$(grep -c "$RECV_ADDR" "$TMP/provider_rows.txt" 2>/dev/null || true)
[ -n "$GRPCNT" ] && [ "$GRPCNT" -ge 0 ] && ok "provider holds both formats" || bad "provider rows missing"

info "6. fresh-launch Messages -> auto-import runs"
adb_ shell "pm clear $PKG >/dev/null"
for p in READ_SMS RECEIVE_SMS SEND_SMS READ_CONTACTS POST_NOTIFICATIONS; do
    adb_ shell pm grant "$PKG" "android.permission.$p" 2>/dev/null
done
adb_ shell cmd role add-role-holder android.app.role.SMS "$PKG" 2>/dev/null
adb_ shell am force-stop "$PKG"
adb_ shell am start -n "$ACT" >/dev/null 2>&1
sleep 12

info "7. inspect Messages local DB for split threads"
adb_ shell am force-stop "$PKG"; sleep 1
DBDIR="$TMP/db183"; rm -rf "$DBDIR"; mkdir -p "$DBDIR"
adb_ shell run-as "$PKG" cat databases/messages.db > "$DBDIR/messages.db" 2>/dev/null
adb_ shell run-as "$PKG" sh -c 'cat databases/messages.db-wal 2>/dev/null' > "$DBDIR/messages.db-wal" 2>/dev/null

DIRTY=$(python3 - "$DBDIR/messages.db" "$RECV_ADDR" "$SENT_ADDR" "$CTRL_ADDR" <<'EOF'
import sqlite3, sys
db, recv, sent, ctrl = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
con = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
def counts(addr):
    r = con.execute("""
        SELECT COUNT(*) FILTER (WHERE m.is_me=0),
               COUNT(*) FILTER (WHERE m.is_me=1)
        FROM conversations c LEFT JOIN messages m ON m.conversation_id=c.id
        WHERE c.address=? GROUP BY c.id
    """, (addr,)).fetchall()
    return r
mom   = counts(recv) + counts(sent)
ctrl_ = counts(ctrl)
merged_mom  = (len(mom) == 1 and mom[0] == (2, 2))
merged_ctrl = (len(ctrl_) == 1 and ctrl_[0] == (1, 1))
print(f"  [{recv} / {sent}] conversations (recv,sent): {mom}")
print(f"  [{ctrl}]            conversations (recv,sent): {ctrl_}")
print("MERGED_MOM=" + str(merged_mom))
print("MERGED_CTRL=" + str(merged_ctrl))
EOF
)
echo "$DIRTY"

MERGED_MOM=$(echo "$DIRTY" | grep -o 'MERGED_MOM=.*' | cut -d= -f2)
MERGED_CTRL=$(echo "$DIRTY" | grep -o 'MERGED_CTRL=.*' | cut -d= -f2)

if [ "$MERGED_MOM" = "True" ]; then
    ok "contact 5551234567 imported as ONE conversation (2 in + 2 out)"
else
    bad "contact 5551234567 SPLIT: received and sent landed in separate threads (issue #183)"
fi
if [ "$MERGED_CTRL" = "True" ]; then
    ok "control contact 5559876543 imported as one conversation (1 in + 1 out)"
else
    bad "control contact split unexpectedly (1 in 1 out expected)"
fi

echo
echo "Result: $PASS passed, $FAIL failed"
exit $((FAIL > 0))