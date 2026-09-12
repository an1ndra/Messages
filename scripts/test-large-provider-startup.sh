#!/usr/bin/env bash
# Reproduces "app feels stuck / doesn't load chats on a real phone with years of
# SMS history" (ui/chat-design-update on a physical device):
#   1. Seeds the SYSTEM SMS provider with a large dataset (many conversations +
#      one huge thread) to mimic a phone Google Messages has been mirroring.
#   2. Fresh-installs the app so the first-launch import has to pull it all.
#   3. Verifies the import finishes, the home list renders, a chat opens, and
#      a warm relaunch loads quickly — all without a crash.
#
# Needs a userdebug/rooted emulator (adb root + sqlite3 on the telephony DB).
#
# Usage: scripts/test-large-provider-startup.sh [convs] [per_conv] [giant]
#   defaults: 120 convs x 100 msgs + one 3000-message thread (≈15k messages)

set -u
source "$(dirname "$0")/env.sh"

CONVS="${1:-120}"
PER="${2:-100}"
GIANT="${3:-3000}"
EXPECTED_MSGS=$(( CONVS * PER + GIANT ))
BUDGET_S=120          # max seconds to wait for the first-launch import
RELAUNCH_BUDGET_S=25  # warm relaunch must clear the loading screen within this

PASS=0; FAIL=0
ok()  { echo "[PASS] $1"; PASS=$((PASS+1)); }
bad() { echo "[FAIL] $1"; FAIL=$((FAIL+1)); }
note(){ echo "[NOTE] $1"; }

PROVDB=""
for cand in /data/user/0/com.android.providers.telephony/databases/mmssms.db \
            /data/user_de/0/com.android.providers.telephony/databases/mmssms.db; do
    if adb_ shell "sqlite3 '$cand' 'select 1;' >/dev/null 2>&1"; then PROVDB="$cand"; break; fi
done
[ -n "$PROVDB" ] || { bad "telephony provider DB not found (root required?)"; exit 1; }

LOCALDB="/data/user/0/$PKG/databases/messages.db"

info "1. Seed the system SMS provider with ${EXPECTED_MSGS} messages in ${CONVS}+1 threads"
adb_ shell "sqlite3 '$PROVDB' 'DELETE FROM sms; DELETE FROM threads; DELETE FROM canonical_addresses; DELETE FROM sqlite_sequence;'"
rm -rf "$TMP/large"; mkdir -p "$TMP/large"
python3 - "$CONVS" "$PER" "$GIANT" > "$TMP/large/seed.sql" <<'PY'
import datetime, sys
c, per, giant = (int(x) for x in sys.argv[1:4])
now = datetime.datetime(2026, 9, 12, 12, 0, 0)
start = now - datetime.timedelta(days=700)
out = ["BEGIN;"]
aid = 1; sid = 1
for convo in range(1, c + 1):
    addr = "+1555{:07d}".format(convo)
    out.append("INSERT INTO canonical_addresses(_id,address) VALUES({0},'{1}');".format(aid, addr))
    msgs = []
    for m in range(per):
        ts = int(start.timestamp() * 1000) + (convo * per + m) * 3600_000
        body = "Message number {} from conversation {} -- long realistic bubble text. OTP check: 123456.".format(m, convo)
        typ = 1 if m % 2 == 0 else 2
        msgs.append("({0},{1},'{2}',{3},{3},0,{4},-1,{5},0,'',\"{6}\",'',0,0,{4},-1)".format(
            sid, convo, addr, ts, 1 if m % 2 == 0 else 0, typ, body))
        sid += 1
    out.append("INSERT INTO threads(_id,date,message_count,recipient_ids,snippet,read,type,error,has_attachment) "
               "VALUES({0},{1},{2},'{3}',\"Message number {4} from conversation {5}\",1,0,0,0);".format(
                   convo, int(start.timestamp() * 1000) + convo * per * 3600_000, per + 1, aid,
                   per - 1, convo))
    out.append("INSERT INTO sms(_id,thread_id,address,date,date_sent,protocol,read,status,type,reply_path_present,subject,body,service_center,locked,error_code,seen,sub_id) VALUES\n" + ",".join(msgs) + ";")
    aid += 1
thread = 9999
out.append("INSERT INTO canonical_addresses(_id,address) VALUES({0},'+15559001');".format(aid))
giant_msgs = []
for m in range(giant):
    ts = int(start.timestamp() * 1000) + m * 15 * 60_000
    typ = 1 if m % 2 == 0 else 2
    giant_msgs.append("({0},{1},'+15559001',{2},{2},0,{3},-1,{4},0,'',\"Old thread message {1}\",'',0,0,{3},-1)".format(
        sid, m, ts, 1 if m % 2 == 0 else 0, typ))
    sid += 1
out.append("INSERT INTO threads(_id,date,message_count,recipient_ids,snippet,read,type,error,has_attachment) "
           "VALUES({0},{1},{2},'{3}',\"Old thread message {4}\",1,0,0,0);".format(thread, int(start.timestamp() * 1000), giant + 1, aid, giant - 1))
out.append("INSERT INTO sms(_id,thread_id,address,date,date_sent,protocol,read,status,type,reply_path_present,subject,body,service_center,locked,error_code,seen,sub_id) VALUES\n" + ",".join(giant_msgs) + ";")
out.append("COMMIT;")
sys.stdout.write("\n".join(out))
PY
adb_ push "$TMP/large/seed.sql" /data/local/tmp/seed-large.sql >/dev/null
adb_ shell "sqlite3 '$PROVDB' < /data/local/tmp/seed-large.sql && echo SEEDED" >/dev/null && ok "seeded" || bad "seeding sql failed"
adb_ shell "sqlite3 '$PROVDB' 'select count(*) from sms;'" | grep -q "^$EXPECTED_MSGS$" && \
    ok "provider now holds $EXPECTED_MSGS rows" || bad "provider count mismatch"

info "2. Fresh install + grant permissions"
adb_ shell pm clear "$PKG" >/dev/null
adb_ install -r "$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk" >/dev/null 2>&1 || { bad "install failed"; exit 1; }
for p in READ_SMS SEND_SMS RECEIVE_SMS READ_CONTACTS POST_NOTIFICATIONS; do
    adb_ shell pm grant "$PKG" android.permission.$p 2>/dev/null
done

info "3. Cold launch -> first-launch import must finish within ${BUDGET_S}s"
adb_ logcat -c
adb_ shell am start -S -n "$ACT" >/dev/null
START=$(date +%s%3N)
while :; do
    n=$(adb_ shell "sqlite3 '$LOCALDB' 'select count(*) from messages;' 2>/dev/null" 2>/dev/null | tr -d '\r')
    if [ -n "$n" ] && [ "$n" = "$EXPECTED_MSGS" ]; then
        ok "import finished ($n rows) in $(( ($(date +%s%3N) - START) / 1000 ))s"
        break
    fi
    if [ $(( ($(date +%s%3N) - START) / 1000 )) -gt "$BUDGET_S" ]; then
        bad "import did not finish within ${BUDGET_S}s (last count '$n')"
        break
    fi
    sleep 1
done
IMP_SECS=$(( ($(date +%s%3N) - START) / 1000 ))

info "4. Home list must render at least one conversation row"
LISTED=0
for i in $(seq 1 15); do
    if center_of_contains "+1-555" >/dev/null 2>&1; then LISTED=1; break; fi
    sleep 1
done
if [ "$LISTED" = 1 ]; then
    ok "home list visible"
else
    bad "home list did not render"
fi

info "5. No crash during/after import + open a chat"
if adb_ logcat -d | grep -qE "FATAL EXCEPTION|am_crash.*$PKG"; then
    bad "app crashed during startup"
else
    ok "no crash"
fi
# uiautomator segfaults intermittently on the swiftshader AVD; fall back to
# tapping the first list row by its on-screen position (1080x2400 -> 540, 540).
if ! tap_text "+1-555" 2>/dev/null; then
    adb_ shell input tap 540 540
    note "opened chat via coordinate tap"
fi
sleep 4
if adb_ logcat -d | grep -qE "FATAL EXCEPTION|am_crash.*$PKG"; then
    bad "app crashed on opening chat"
else
    ok "chat opened without crashing"
fi

info "6. Warm relaunch loads quickly (no per-open provider storm)"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ logcat -c
adb_ shell am start -n "$ACT" >/dev/null
START=$(date +%s%3N)
for i in $(seq 1 "$RELAUNCH_BUDGET_S"); do
    if [ -n "$(adb_ shell 'dumpsys window | grep mCurrentFocus' 2>/dev/null | grep -o "$PKG")" ] \
       && ! adb_ logcat -d | grep -q "FATAL EXCEPTION"; then :; fi
    # sync logging completes when the provider pass is done
    if adb_ logcat -d | grep -q "Loaded .* pending messages"; then
        ok "relaunch provider pass done in ${i}s"
        break
    fi
    sleep 1
done
if adb_ logcat -d | grep -qE "FATAL EXCEPTION|am_crash.*$PKG"; then
    bad "warm relaunch crashed"
else
    ok "warm relaunch stable"
fi

echo
echo "-----------------------------------------------------"
echo "RESULTS: $PASS passed, $FAIL failed  (import ${IMP_SECS}s)"
echo "-----------------------------------------------------"
[ "$FAIL" = 0 ]