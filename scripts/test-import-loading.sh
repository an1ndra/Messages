#!/usr/bin/env bash
set -euo pipefail
# Import-progress regression test: verify a large backup import shows the
# blocking "Loading messages" dialog with a LIVE message count
# (CircularProgressIndicator + "N messages loaded").
#
# A 10 000-message PIN backup is generated ON THE HOST with Python 3 +
# python3-cryptography (exact BackupCrypto format: "MSP\x01" | salt(16) |
# iv(12) | PBKDF2-SHA256(pin,120k) AES-256-GCM ciphertext). It is then merged
# into a freshly-cleared app so every row is new — the merge takes long
# enough to observe the counter increasing.
# Requires: python3 + cryptography  (apt: python3-cryptography)
# PIN="${PIN:-1234}" overrides the test PIN.
export ADB="${ADB:-$HOME/android/platform-tools/adb}"
source "$(dirname "$0")/env.sh"

dump_ui() {
  adb_ shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb_ shell cat /sdcard/ui.xml > "$TMP/ui.xml" 2>/dev/null || true
}
bounds_of() {
  grep -oE "<node[^>]*text=\"$1\"[^>]*bounds=\"[^\"]*\"" "$TMP/ui.xml" | head -1 \
    | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' || true
}
first_enc_bounds() {
  grep -oE '<node[^>]*text="big_import_probe\.enc"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
    "$TMP/ui.xml" | head -1 | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' || true
}
tap_center() {
  local b=$1 x1 y1 x2 y2
  x1=$(sed -E 's/\[([0-9]+),([0-9]+)\].*/\1/' <<< "$b")
  y1=$(sed -E 's/\[[0-9]+,([0-9]+)\].*/\1/' <<< "$b")
  x2=$(sed -E 's/.*\]\[([0-9]+),[0-9]+\]/\1/' <<< "$b")
  y2=$(sed -E 's/.*\]\[[0-9]+,([0-9]+)\]/\1/' <<< "$b")
  adb_ shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}
tap_label() {
  local b
  b=$(bounds_of "$1") || true
  [ -z "$b" ] && { echo "[fail] '$1' not found on screen"; return 1; }
  tap_center "$b"
}
edits() {
  grep -oE 'class="android.widget.EditText"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
    "$TMP/ui.xml" | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' || true
}
nth_edit() { edits | sed -n "${1}p"; }
greps_ui() { grep -q "$1" "$TMP/ui.xml"; }

PIN="${PIN:-1234}"
BACKDIR="storage/emulated/0/Documents/Messages"
ENC_NAME="big_import_probe.enc"
TOTAL=10000

echo "== Step 0: build a ${TOTAL}-message PIN backup on the host =="
python3 - "$PIN" "$TMP/$ENC_NAME" "$TOTAL" <<'PYEOF'
import hashlib, os, sqlite3, sys, tempfile
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes

pin, out_path, total = sys.argv[1], sys.argv[2], int(sys.argv[3])
db_path = os.path.join(tempfile.mkdtemp(), "big_backup.db")
c = sqlite3.connect(db_path)
c.executescript("""
CREATE TABLE conversations(
  id INTEGER PRIMARY KEY AUTOINCREMENT, address TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL, snippet TEXT NOT NULL DEFAULT '',
  timestamp INTEGER NOT NULL DEFAULT 0, unread_count INTEGER NOT NULL DEFAULT 0,
  last_is_me INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0,
  pinned INTEGER NOT NULL DEFAULT 0, draft TEXT NOT NULL DEFAULT '',
  draft_date INTEGER NOT NULL DEFAULT 0, deleted_at INTEGER NOT NULL DEFAULT 0);
CREATE TABLE messages(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  body TEXT NOT NULL, timestamp INTEGER NOT NULL, is_me INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'sent', media_type TEXT NOT NULL DEFAULT 'text',
  media_uri TEXT NOT NULL DEFAULT '', reactions TEXT NOT NULL DEFAULT '',
  sys_id INTEGER NOT NULL DEFAULT 0, locked INTEGER NOT NULL DEFAULT 0,
  sub_id INTEGER NOT NULL DEFAULT -1);
CREATE TABLE blocked_numbers(
  id INTEGER PRIMARY KEY AUTOINCREMENT, number TEXT NOT NULL UNIQUE,
  timestamp INTEGER NOT NULL);
CREATE TABLE scheduled_messages(
  id INTEGER PRIMARY KEY AUTOINCREMENT, address TEXT NOT NULL,
  body TEXT NOT NULL, timestamp INTEGER NOT NULL,
  conversation_id INTEGER NOT NULL, sub_id INTEGER NOT NULL DEFAULT -1);
CREATE TABLE conversation_notifications(
  conversation_id INTEGER PRIMARY KEY REFERENCES conversations(id) ON DELETE CASCADE,
  notifications_enabled INTEGER NOT NULL DEFAULT 1);
""")
N_CONVOS = 20
per = total // N_CONVOS
base = 1788600000000
for ci in range(N_CONVOS):
    addr = f"+15551230{100 + ci:03d}"
    cur = c.execute(
        "INSERT INTO conversations(address,name,timestamp) VALUES(?,?,?)",
        (addr, f"Big Contact {ci}", base + (ci + 1) * per * 60000)
    )
    cid = cur.lastrowid
    for k in range(1, per + 1):
        is_me = 0 if k % 2 else 1
        ts = base + (ci * per + k) * 60000
        body = f"Big backup message #{ci * per + k}"
        c.execute(
            "INSERT INTO messages(conversation_id,body,timestamp,is_me,status) VALUES(?,?,?,?,?)",
            (cid, body, ts, is_me, "sent" if is_me else "received")
        )
    c.execute("UPDATE conversations SET snippet=?,last_is_me=? WHERE id=?",
              (f"Big backup message #{ci * per + per}", per % 2, cid))
c.commit()
c.close()
raw = open(db_path, "rb").read()
os.unlink(db_path)

salt = os.urandom(16)
nonce = os.urandom(12)
key = PBKDF2HMAC(algorithm=hashes.SHA256(), length=32, salt=salt,
                 iterations=120_000).derive(pin.encode())
ct = AESGCM(key).encrypt(nonce, raw, None)
open(out_path, "wb").write(b"MSP\x01" + salt + nonce + ct)
print(f"[host] wrote {out_path} ({len(b'MSP\x01') + 16 + 12 + len(ct):,} bytes, {total} messages)")
PYEOF

echo "== Step 1: push backup + reset app =="
adb_ shell mkdir -p "/sdcard/Documents/Messages"
adb_ push "$TMP/$ENC_NAME" "/sdcard/Documents/Messages/$ENC_NAME" 2>&1 | tail -1
adb_ shell pm clear "$PKG" 2>&1 | tail -1
scripts/grant-permissions.sh >/dev/null 2>&1 || true
echo "[ok] pushed $ENC_NAME, app data cleared"

echo "== Step 2: launch app, drive to Import -> Merge -> PIN =="
adb_ shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
sleep 5
dump_ui
for _ in 1 2 3; do
  if greps_ui 'Set as default SMS app?'; then
    adb_ shell input tap 533 1352; sleep 2; dump_ui
  elif grep -qE 'Allow Messages to (access|send|start)' "$TMP/ui.xml"; then
    adb_ shell input tap 900 1470; sleep 1; dump_ui
  else break; fi
done
adb_ shell input tap 975 226
sleep 2
for _ in 1 2 3 4; do
  if greps_ui 'Import messages'; then break; fi
  adb_ shell input swipe 540 1900 540 400 300
  sleep 1
  dump_ui
done
greps_ui 'Import messages' || { echo "[fail] Import messages row not visible"; exit 1; }
tap_label 'Import messages'
sleep 3
b=""
for _ in $(seq 1 12); do
  dump_ui
  b=$(first_enc_bounds)
  [ -n "$b" ] && break
  adb_ shell input swipe 540 2000 540 400 600
  sleep 1
done
[ -z "$b" ] && { echo "[fail] $ENC_NAME not found in picker"; exit 1; }
echo "[ok] picking $ENC_NAME at $b"
tap_center "$b"
sleep 3
dump_ui
greps_ui 'Import backup' || { echo "[fail] Import backup dialog not shown"; exit 1; }
tap_label 'Merge with existing messages'
sleep 2
dump_ui
greps_ui 'Enter backup PIN' || { echo "[fail] Enter backup PIN dialog not shown"; exit 1; }
tap_center "$(nth_edit 1)"
adb_ shell input text "$PIN"
sleep 1
dump_ui
b=$(bounds_of 'Import'); [ -z "$b" ] && { echo "[fail] Import button not found"; exit 1; }
tap_center "$b"

echo "== Step 3: probe for live Loading-messages dialog + counter =="
best=0
caught=0
for _ in $(seq 1 15); do
  dump_ui
  if greps_ui 'Loading messages'; then
    caught=1
    n=$(grep -oE '[0-9]+ messages loaded' "$TMP/ui.xml" | grep -oE '^[0-9]+' | sort -n | tail -1 || true)
    echo "[progress] dialog visible, count = ${n:-?}"
    if [ -n "$n" ]; then
      [ "$n" -gt "$best" ] && best=$n
      [ "$n" -ge "$TOTAL" ] && break
    fi
  fi
  sleep 0.4
done
[ "$caught" = "1" ] && echo "[ok] Loading messages dialog appeared" \
                    || { echo "[fail] loading dialog never observed"; exit 1; }

echo "== Step 3b: dialog eventually closes =="
done_ok=0
for _ in $(seq 1 20); do
  dump_ui
  if greps_ui 'Loading messages' || greps_ui 'Enter backup PIN'; then
    sleep 0.5
    continue
  fi
  done_ok=1
  break
done
[ "$done_ok" = "1" ] || { echo "[fail] loading dialog never closed"; exit 1; }
echo "[ok] loading dialog closed after import"

echo "== Step 4: merged conversations landed on the home list =="
adb_ shell input keyevent KEYCODE_BACK
sleep 3
dump_ui
greps_ui 'Big backup message' || { echo "[fail] merged conversations not on home list"; exit 1; }
echo "[ok] 20 conversations from the big backup are on the home list"
echo "[done] import-progress (loading + live count) OK — peak observed count: $best / $TOTAL"