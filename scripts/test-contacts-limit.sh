#!/usr/bin/env bash
# Issue #98 regression: NewChatScreen contact list must NOT truncate at 200.
#
# Seeds enough uniquely-named contacts ("ZzqNNN" — sort last alphabetically)
# to push the device past 200 total, then opens New conversation and searches
# for the LAST one. Under the old code the row never appears (list capped at
# 200); with the fix it must show.
#
# Seeding runs in parallel workers (each owns a disjoint raw_contact id range).
#
# Run: scripts/test-contacts-limit.sh
set -euo pipefail
cd "$(dirname "$0")"
source ./env.sh

TARGET_TOTAL=215      # total contacts after seeding must exceed 200
MIN_SEED=45           # keep the test meaningful even on contact-heavy devices
WORKERS=8
PREFIX="Zzq"

cleanup() {
    info "Cleanup: removing $PREFIX% seeded contacts"
    adb_ shell content query --uri content://com.android.contacts/data \
        --projection raw_contact_id --where "\"display_name LIKE '$PREFIX%'\"" 2>/dev/null \
        | grep -oE 'raw_contact_id=[0-9]+' | cut -d= -f2 | sort -un | while read -r rid; do
            adb_ shell content delete --uri content://com.android.contacts/raw_contacts \
                --where "\"_id=$rid\"" >/dev/null 2>&1 || true
        done
    echo "[cleanup] done"
}
trap cleanup EXIT

fail() { echo "FAIL: $*"; exit 1; }
pass() { echo "PASS: $*"; }

info "Counting existing contacts"
EXISTING=$(adb_ shell content query --uri content://com.android.contacts/raw_contacts \
    --projection _id 2>/dev/null | grep -oE '_id=[0-9]+' | cut -d= -f2 | sort -un | wc -l)
NEED=$((TARGET_TOTAL - EXISTING))
[ "$NEED" -lt "$MIN_SEED" ] && NEED=$MIN_SEED
[ "$NEED" -gt 400 ] && NEED=400
MAXID=$(adb_ shell content query --uri content://com.android.contacts/raw_contacts \
    --projection _id 2>/dev/null | grep -oE '_id=[0-9]+' | cut -d= -f2 | sort -n | tail -1)
BASE_RID=$(( ${MAXID:-0} + 1 ))
CHUNK=$(( (NEED + WORKERS - 1) / WORKERS ))
echo "[seed] existing=$EXISTING seeding=$NEED base_rid=$BASE_RID chunk=$CHUNK"

seed_worker() {
    local from=$1 to=$2 rid=$3
    adb_ shell "i=$from; end=$to; rid=$rid;
    while [ \$i -le \$end ]; do
      n=\$(printf %03d \$i)
      content insert --uri content://com.android.contacts/raw_contacts >/dev/null
      content insert --uri content://com.android.contacts/data \
        --bind raw_contact_id:i:\$rid --bind mimetype:s:vnd.android.cursor.item/name \
        --bind data1:s:$PREFIX\$n >/dev/null
      content insert --uri content://com.android.contacts/data \
        --bind raw_contact_id:i:\$rid --bind mimetype:s:vnd.android.cursor.item/phone_v2 \
        --bind data1:s:5550100\$n >/dev/null
      if [ \$((i % 25)) -eq 0 ]; then echo \"[worker] \$i/\$end\"; fi
      i=\$((i + 1)); rid=\$((rid + 1))
    done; echo worker-done"
}

info "Seeding in $WORKERS parallel workers"
for w in $(seq 0 $((WORKERS - 1))); do
    from=$((w * CHUNK + 1))
    to=$(( (w + 1) * CHUNK ))
    [ "$to" -gt "$NEED" ] && to=$NEED
    [ "$from" -gt "$NEED" ] && continue
    seed_worker "$from" "$to" "$((BASE_RID + w * CHUNK))" &
done
wait
echo "[seed] all workers done"

info "Launch app → New conversation"
bash ./grant-permissions.sh >/dev/null 2>&1 || true
adb_ shell am force-stop "$PKG"
sleep 1
adb_ shell am start -n "$ACT" >/dev/null
sleep 4
tap_text "Start chat" || {
    # Compose FAB sometimes missing from uiautomator tree (see new-message.sh)
    echo "[tap] FAB not in a11y tree; fallback coords"
    adb_ shell input tap 940 2260
}
sleep 2

info "Search for last-seeded contact '${PREFIX}$(printf %03d "$NEED")'"
tap_text "Enter name or phone number" || fail "search field not found"
LAST="${PREFIX}$(printf %03d "$NEED")"
type_text "$LAST"
sleep 2

dump_ui || fail "could not dump UI"
if grep -q "text=\"$LAST\"" "$TMP/ui.xml"; then
    pass "'$LAST' visible — no 200-contact truncation (issue #98 fixed)"
else
    fail "'$LAST' missing from picker — list still truncated at 200?"
fi
shot "contacts-limit-$LAST"
