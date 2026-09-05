#!/usr/bin/env bash
# Refreshes the F-Droid/README screenshots against the current build.
# Captures 20 shots: 10 core (home/chat/settings/reply x dark/light + settings scroll)
# and 10 feature shots (scheduled picker, trash, contact details, new chat, archive — dark+light).
#
# Requires:
#   - demo data seeded (10 conversations, 33 messages)
#   - demo contacts present in the Contacts provider (scripts/insert-demo-contacts.sh)
#   - archiving feature enabled in Settings
#   - at least one conversation already in trash (delete one via long-press)
#
# All tap targets are derived from live uiautomator dumps (the app may render
# edge-to-edge OR below the status bar depending on system state, so hardcoded
# coordinates would be fragile). Sizes/shots assume 1080x2400 @ 420dpi.
set -euo pipefail
cd "$(dirname "$0")"
source ./env.sh
if [ ! -x "$ADB" ]; then export ADB="$HOME/android/platform-tools/adb"; fi

SHOTS="$PROJECT_DIR/screenshots/fdroid"
mkdir -p "$SHOTS"

launch() { adb_ shell am force-stop "$PKG"; sleep 1; adb_ shell am start -n "$ACT" >/dev/null; sleep 4; }

dismiss_onboarding() {
    for _ in 1 2 3; do
        dump_ui >/dev/null
        C=$(center_of_contains "Not now") && { adb_ shell input tap $C; sleep 1.5; continue; }
        C=$(center_of_contains "ALLOW") && { adb_ shell input tap $C; sleep 1.5; continue; }
        break
    done
    sleep 1
}

back() { adb_ shell input keyevent KEYCODE_BACK; sleep 1.5; }

ensure_home() {
    for _ in 1 2 3 4; do
        dump_ui >/dev/null
        if grep -q 'content-desc="Search"' "$TMP/ui.xml" || grep -q 'text="Messages"' "$TMP/ui.xml"; then
            return 0
        fi
        back
    done
    dump_ui >/dev/null; return 1
}

# center of first node whose text equals $1
rowxy() {
    dump_ui >/dev/null
    python3 - "$TMP/ui.xml" "$1" <<'PY'
import re,sys
xml=open(sys.argv[1]).read()
m=re.search(r'<node[^>]*text="'+re.escape(sys.argv[2])+r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if m: print((int(m.group(1))+int(m.group(3)))//2,(int(m.group(2))+int(m.group(4)))//2)
else: exit(1)
PY
}

# center of first node whose content-desc equals $1
cdxy() {
    dump_ui >/dev/null
    python3 - "$TMP/ui.xml" "$1" <<'PY'
import re,sys
xml=open(sys.argv[1]).read()
m=re.search(r'<node[^>]*content-desc="'+re.escape(sys.argv[2])+r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if m: print((int(m.group(1))+int(m.group(3)))//2,(int(m.group(2))+int(m.group(4)))//2)
else: exit(1)
PY
}

tapxy() { adb_ shell input tap "$1" "$2"; sleep 2; }
tap_cd() { local c; c=$(cdxy "$1") || { echo "  !! cd '$1' not found"; return 1; }; tapxy ${c% *} ${c#* }; }

# settings avatar sits just right of the Search icon in the home top bar
tap_settings() {
    dump_ui >/dev/null
    local c
    c=$(python3 -c "
import re
xml=open('$TMP/ui.xml').read()
m=re.search(r'<node[^>]*content-desc=\"Search\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', xml)
if m: print(int(m.group(3))+95,(int(m.group(2))+int(m.group(4)))//2)
") || return 1
    tapxy ${c% *} ${c#* }
}

# chat-header avatar: clickable node to the right of the Back arrow
tap_chat_avatar() {
    dump_ui >/dev/null
    local c
    c=$(python3 -c "
import re
xml=open('$TMP/ui.xml').read()
b=re.search(r'<node[^>]*content-desc=\"Back\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', xml)
if not b: exit(0)
bx1,by1,bx2,by2=map(int,b.groups())
for m in re.finditer(r'<node[^>]*clickable=\"true\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', xml):
    x1,y1,x2,y2=map(int,m.groups())
    if x1>bx2 and x1<bx2+340 and y2<=by2+120:
        print((x1+x2)//2,(y1+y2)//2); break
")
    if [ -z "$c" ]; then echo "  !! chat avatar not found"; return 1; fi
    tapxy ${c% *} ${c#* }
}

screencap_to() { adb_ exec-out screencap -p > "$SHOTS/$1"; echo "  ✓ $1"; }

# verify current screen shows $1 (exact text match on the a11y tree)
verify() {
    dump_ui >/dev/null
    if python3 - "$TMP/ui.xml" "$1" <<'PY'
import re,sys
sys.exit(0 if re.search(r'text="[^"]*'+re.escape(sys.argv[2])+r'[^"]*"', open(sys.argv[1]).read()) else 1)
PY
    then echo "  OK  [$1]"
    else echo "  !!  MISSING [$1]"
    fi
}

# verify current screen shows at least one of the given texts
verify_any() {
    dump_ui >/dev/null
    for m in "$@"; do
        if python3 - "$TMP/ui.xml" "$m" <<'PY'
import re,sys
sys.exit(0 if re.search(r'text="[^"]*'+re.escape(sys.argv[2])+r'[^"]*"', open(sys.argv[1]).read()) else 1)
PY
        then echo "  OK  [$m]"; return 0; fi
    done
    echo "  !!  MISSING [$*]"; return 1
}

open_chat() { local c; c=$(rowxy "$1") || { echo "  !! row '$1' not found"; return 1; }; tapxy ${c% *} ${c#* }; }

focus_input() {
    dump_ui >/dev/null
    read x y <<< "$(python3 -c "
import re
xml=open('$TMP/ui.xml').read()
m=re.search(r'<node[^>]*class=\"android.widget.EditText\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', xml)
print((int(m.group(1))+int(m.group(3)))//2,(int(m.group(2))+int(m.group(4)))//2)")"
    adb_ shell input tap "$x" "$y"; sleep 1.5
}

clear_input() {
    adb_ shell input keyevent KEYCODE_MOVE_END
    for _ in $(seq 1 40); do adb_ shell input keyevent KEYCODE_DEL; done
    sleep 0.5
}

scheduled_picker() { # open $1 chat, type text, long-press Send
    open_chat "$1" || return 1
    focus_input; clear_input
    adb_ shell input text "Pick%s up%s milk"; sleep 1
    dump_ui >/dev/null
    read sx sy <<< "$(python3 -c "
import re
xml=open('$TMP/ui.xml').read()
m=re.search(r'<node[^>]*content-desc=\"Send\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', xml)
print((int(m.group(1))+int(m.group(3)))//2,(int(m.group(2))+int(m.group(4)))//2)")"
    adb_ shell input swipe "$sx" "$sy" "$sx" "$sy" 800; sleep 2
}

dark()  { adb_ shell cmd uimode night yes; sleep 1.5; }
light() { adb_ shell cmd uimode night no;  sleep 1.5; }

# ============ CORE: home / chat / reply / settings ============
dark;  launch; dismiss_onboarding; ensure_home
adb_ shell input swipe 540 1800 540 900 350; sleep 0.5
screencap_to 01-home-dark.png;  verify "Messages"
open_chat "Dad";                 screencap_to 02-chat-dark.png;  verify "Text message"
back; ensure_home

open_chat "Sarah"; focus_input; clear_input
adb_ shell input text "Hey%s everrything%s ok%s"; sleep 1
screencap_to 07-reply-dark.png;  verify "Hey"
back; back; ensure_home

tap_settings;                    screencap_to 06-settings-dark.png;      verify "General settings"
adb_ shell input swipe 540 1700 540 700 250; sleep 1.5
screencap_to 10-settings-scroll-dark.png; verify_any "App lock" "Privacy mode" "Number blocking" "Trash" "Backup messages"
back

light; launch; dismiss_onboarding; ensure_home
adb_ shell input swipe 540 1800 540 900 350; sleep 0.5
screencap_to 03-home-light.png;  verify "Messages"
open_chat "Dad";                 screencap_to 04-chat-light.png;  verify "Text message"
back; ensure_home

open_chat "Emma"; focus_input; clear_input
adb_ shell input text "Hey%s mom"; sleep 1
screencap_to 08-reply-light.png; verify "Hey"
back; back; ensure_home

tap_settings;                    screencap_to 05-settings-light.png;      verify "General settings"
adb_ shell input swipe 540 1700 540 700 250; sleep 1.5
screencap_to 09-settings-scroll-light.png; verify_any "App lock" "Privacy mode" "Number blocking" "Trash" "Backup messages"
back

# ============ SCHEDULED PICKER ============
dark;  launch; dismiss_onboarding; ensure_home
scheduled_picker "Mom";          screencap_to 11-scheduled-dark.png; verify "Select date"
back; back; ensure_home
light; launch; dismiss_onboarding; ensure_home
scheduled_picker "Mom";          screencap_to 12-scheduled-light.png; verify "Select date"
back; back; ensure_home

# ============ CONTACT DETAILS ============
dark;  launch; dismiss_onboarding; ensure_home
open_chat "Alex"; tap_chat_avatar
screencap_to 15-contact-details-dark.png; verify "Add people"
back; back; ensure_home
light; launch; dismiss_onboarding; ensure_home
open_chat "Dr. Patel"; tap_chat_avatar
screencap_to 16-contact-details-light.png; verify "Add people"
back; back; ensure_home

# ============ NEW CHAT ============
dark;  launch; dismiss_onboarding; ensure_home
adb_ shell input tap 862 2158;   screencap_to 17-new-chat-dark.png; verify "Enter name or phone"
back; ensure_home
light; launch; dismiss_onboarding; ensure_home
adb_ shell input tap 862 2158;   screencap_to 18-new-chat-light.png; verify "Enter name or phone"
back; ensure_home

# ============ TRASH ============
dark;  launch; dismiss_onboarding; ensure_home
tap_settings
for _ in 1 2; do adb_ shell input swipe 540 1700 540 700 250; sleep 1.5; done
c=$(rowxy "Trash") || { echo "!! Trash row not found"; exit 1; }
tapxy ${c% *} ${c#* }
screencap_to 13-trash-dark.png;  verify "Empty trash"
back; back; ensure_home
light; launch; dismiss_onboarding; ensure_home
tap_settings
for _ in 1 2; do adb_ shell input swipe 540 1700 540 700 250; sleep 1.5; done
c=$(rowxy "Trash") || exit 1
tapxy ${c% *} ${c#* }
screencap_to 14-trash-light.png; verify "Empty trash"
back; back; ensure_home

# ============ ARCHIVE ============
dark;  launch; dismiss_onboarding; ensure_home
c=$(rowxy "Work") || c=""
if [ -n "$c" ]; then
    adb_ shell input swipe ${c% *} ${c#* } ${c% *} ${c#* } 950; sleep 2
    a=$(rowxy "Archive") && tapxy ${a% *} ${a#* }
fi
sleep 1
tap_cd "Archived" || back
sleep 1
screencap_to 19-archive-dark.png;  verify "Archived"
tap_cd "Archived" || back
light; launch; dismiss_onboarding; ensure_home
tap_cd "Archived" || back
sleep 1
screencap_to 20-archive-light.png; verify "Archived"
tap_cd "Archived" || back

echo ""
echo "=== DONE ==="
ls -lh "$SHOTS"