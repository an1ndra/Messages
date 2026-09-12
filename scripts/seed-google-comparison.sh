#!/usr/bin/env bash
# Seeds an 11-message conversation into the device's SMS provider so the SAME
# scenario can be compared side by side in Google Messages and in this app's
# design-preview screen (--ez open_design_preview true).
#
# Covers, oldest -> newest:
#   - 2 years ago   : two outgoing messages (same run)
#   - 40 days ago   : two incoming messages (same run)
#   - yesterday     : two outgoing messages (same run)
#   - today         : two incoming, then two outgoing, then one incoming
#                     (sender changes and a >1 hour gap -> three runs)
#
# Usage:  scripts/seed-google-comparison.sh [serial]     (default emulator-5556)
#         FORCE=1 scripts/seed-google-comparison.sh      (skip the confirmation)
#
# Writes to the SYSTEM SMS provider, so it needs `adb root` and an emulator.
# Note: message bodies avoid ':' because `content insert` cannot parse a colon
# inside a --bind value (its format is column:type:value).
source "$(dirname "$0")/env.sh"

SERIAL="${1:-emulator-5556}"
ADDR=15559990001

case "$SERIAL" in
  emulator-*) ;;
  *) echo "Refusing to run against '$SERIAL' — emulator serials only." >&2; exit 1 ;;
esac

if [ "${FORCE:-0}" != "1" ]; then
  printf 'Seed a demo conversation into %s'\''s SMS provider? [y/N] ' "$SERIAL"
  read -r ans
  case "$ans" in [yY]*) ;; *) echo "Aborted."; exit 1 ;; esac
fi

info "Requesting root on $SERIAL"
"$ADB" -s "$SERIAL" root >/dev/null 2>&1
sleep 2
if [ "$("$ADB" -s "$SERIAL" shell id 2>/dev/null | grep -c uid=0)" != "1" ]; then
  echo "adb root unavailable on $SERIAL (needs a non-Play image)." >&2
  exit 1
fi

info "Writing $ADDR conversation into the SMS provider"
python3 - "$ADB" "$SERIAL" "$ADDR" <<'PY'
import datetime as dt, subprocess, sys

adb, serial, addr = sys.argv[1], sys.argv[2], sys.argv[3]
now = dt.datetime.now()

def dsh(*args):
    return subprocess.run([adb, "-s", serial, "shell", *args],
                          capture_output=True, text=True)

def q(s):
    """Quote for the DEVICE shell."""
    return "'" + s.replace("'", "'\\''") + "'"

def ts(days_ago, hh, mm):
    d = (now - dt.timedelta(days=days_ago)).replace(
        hour=hh, minute=mm, second=0, microsecond=0)
    return int(d.timestamp() * 1000)

def rel(hours_ago, minutes_ago=0):
    return int((now - dt.timedelta(hours=hours_ago, minutes=minutes_ago))
               .timestamp() * 1000)

conv = [
    (ts(760, 9, 5),  2, "Happy birthday!"),
    (ts(760, 9, 6),  2, "Hope it's a good one"),
    (ts(40, 15, 15), 1, "That was a fun evening"),
    (ts(40, 15, 16), 1, "Let's do it again soon"),
    (ts(1, 12, 30),  2, "Perfect, see you then"),
    (ts(1, 12, 31),  2, "Bringing the photos"),
    (rel(3, 40),     1, "Hey, are you free later?"),
    (rel(3, 39),     1, "Thinking around 7"),
    (rel(3, 5),      2, "works for me"),
    (rel(3, 4),      2, "I'll book a table"),
    (rel(1, 0),      1, "Table booked for tonight"),
]

dsh("content", "delete", "--uri", "content://sms", "--where", f"address={q(addr)}")

for date_ms, mtype, body in conv:
    uri = "content://sms/inbox" if mtype == 1 else "content://sms/sent"
    r = dsh("content", "insert", "--uri", uri,
            "--bind", f"address:s:{q(addr)}",
            "--bind", f"body:s:{q(body)}",
            "--bind", f"date:l:{date_ms}",
            "--bind", f"date_sent:l:{date_ms}",
            "--bind", "read:i:1",
            "--bind", "seen:i:1",
            "--bind", "protocol:i:0",
            "--bind", "reply_path_present:i:0",
            "--bind", "sub_id:i:1",
            "--bind", "creator:s:com.google.android.apps.messaging")
    out = (r.stderr + r.stdout).strip()
    if "ERROR" in out or "sage" in out:
        print(f"  FAILED  {body!r}: {out[:70]}")
    else:
        when = dt.datetime.fromtimestamp(date_ms / 1000).strftime('%Y-%m-%d %H:%M')
        print(f"  {when}  {'IN ' if mtype == 1 else 'OUT'}  {body}")
PY

info "Rows now in the provider"
"$ADB" -s "$SERIAL" shell content query --uri content://sms 2>/dev/null \
  | grep -c "$ADDR" | sed 's/^/  messages: /'

cat <<EOF

Compare in:
  this app    : adb -s $SERIAL shell am start -n $PKG/.MainActivity --ez open_design_preview true
  Google Msgs : restart com.google.android.apps.messaging and open "$ADDR"
EOF
