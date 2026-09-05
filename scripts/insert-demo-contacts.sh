#!/usr/bin/env bash
# Insert F-Droid demo contacts into the emulator's Contacts provider so
# NewChatScreen / contact picker has populated data. Run on host.
set -e
ADB=~/android/platform-tools/adb
SER=emulator-5554
CONTACTS_DB=/data/user/0/com.android.providers.contacts/databases/contacts2.db

contacts=(
  "Sarah|+15551230010"
  "Mom|+15551230020"
  "Work|+15551230030"
  "Jake|+15551230040"
  "Emma|+15551230050"
  "Dad|+15551230060"
  "Pizza Palace|+15551230070"
  "Alex|+15551230080"
  "Dr. Patel|+15551230090"
  "Gym Buddy|+15551230100"
  "Tarun|+15551230004"
)

# get id of a raw_contact by unique sourceid
raw_id() {
  local sid="$1"
  "$ADB" -s "$SER" shell "su 0 sqlite3 $CONTACTS_DB \"select _id from raw_contacts where sourceid='$sid';\"" 2>/dev/null
}

i=0
for entry in "${contacts[@]}"; do
  i=$((i+1))
  name="${entry%%|*}"
  num="${entry##*|}"
  sid="demo-$i"

  rid=$(raw_id "$sid")
  if [ -z "$rid" ]; then
    "$ADB" -s "$SER" shell "content insert --uri content://com.android.contacts/raw_contacts --bind account_name:s:demo --bind account_type:s:com.local --bind sourceid:s:$sid" >/dev/null
    rid=$(raw_id "$sid")
    [ -z "$rid" ] && { echo "ERROR raw for $name"; exit 1; }
  fi

  # name
  "$ADB" -s "$SER" shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:l:$rid --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:$name --bind data2:s:$name --bind data3:s:$name" >/dev/null
  # phone (fixed value) — supports 2nd/3rd alt number
  "$ADB" -s "$SER" shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:l:$rid --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:$num --bind data2:l:0" >/dev/null

  echo "  ok: $name ($num) rawid=$rid"
done
echo "=== provider state ==="
"$ADB" -s "$SER" shell "su 0 sqlite3 $CONTACTS_DB 'select rc._id,rc.display_name,d.data1 from raw_contacts rc left join data d on d.raw_contact_id=rc._id and d.mimetype_id=5;'" 2>&1