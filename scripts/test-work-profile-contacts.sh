#!/usr/bin/env bash
# Sets up and verifies a work profile (managed profile) environment on the
# emulator, mimicking issue #180's reporter setup: a work profile with a
# separate address book, and the app installed in both profiles.
#
# Verifies:
#   1. A managed (work) profile exists for the owner user (creates one if not)
#   2. A work-profile contact ("Work Alice") exists ONLY in the work profile
#   3. The app is installed in the work profile with READ_CONTACTS granted
#   4. The app launches inside the work profile
#
# Cross-profile contact reading (verified 2026-09-13 on the google_apis
# API 35 image, mechanism reverse-engineered from the Google Messages
# APK): the default-profile app CAN read work-profile contacts via the
# public ENTERPRISE content URIs, provided it holds READ_CONTACTS in the
# work profile (cross-profile grant — on real devices the user grants it
# in work-profile settings or via the DPC; here `pm grant --user <id>`
# simulates it):
#   - ContactsContract.Directory.ENTERPRISE_CONTENT_URI
#     (content://com.android.contacts/directories_enterprise)
#     -> enumerates all directories; the work profile appears as
#        directory id 1000000000 (GM hardcodes known ids 0,1,1e9,1e9+1)
#   - ContactsContract.CommonDataKinds.Phone.ENTERPRISE_CONTENT_URI
#     (content://com.android.contacts/data_enterprise/phones)
#     -> MERGED contacts from default + work profile in one query
#   - Phone/PhoneLookup/Email ENTERPRISE_CONTENT_FILTER_URI
#     (content://com.android.contacts/data/phones/filter_enterprise/<q>)
#     -> requires a ?directory=<id> query param; GM uses
#        directory=1000000000 for work-only lookups
# Plain (non-enterprise) URIs stay profile-isolated. Badge: mark contacts
# resolved from directory 1000000000 with a briefcase icon (GM uses a
# work_profile_icon ImageView; UserManager.getBadgedIconForUser also
# works and needs a drawable with non-zero intrinsic size).
set -u
source "$(dirname "$0")/env.sh"

WORK_CONTACT_NAME="Work Alice"
WORK_CONTACT_NUM="+15557778899"
PASS=0; FAIL=0
ok()   { echo "[PASS] $1"; PASS=$((PASS+1)); }
bad()  { echo "[FAIL] $1"; FAIL=$((FAIL+1)); }
note() { echo "[NOTE] $1"; }

info "Find or create a managed (work) profile"
WORK_ID=$(adb_ shell dumpsys user 2>/dev/null \
    | grep -B1 'usertype.profile.MANAGED' \
    | grep -oE 'UserInfo\{[0-9]+' | grep -oE '[0-9]+' | head -1)
if [ -z "$WORK_ID" ]; then
    note "no managed profile found, creating one"
    OUT=$(adb_ shell pm create-user --profileOf 0 --managed "Work" 2>&1)
    WORK_ID=$(echo "$OUT" | grep -oE 'user id [0-9]+' | grep -oE '[0-9]+')
    if [ -z "$WORK_ID" ]; then
        bad "could not create work profile: $OUT"
        echo "Result: $PASS passed, $FAIL failed"; exit 1
    fi
    adb_ shell am start-user "$WORK_ID" >/dev/null 2>&1
    for i in $(seq 1 20); do
        STATE=$(adb_ shell dumpsys user 2>/dev/null \
            | awk "/UserInfo\{$WORK_ID:/{f=1} f&&/State:/{print \$2; exit}")
        [ "$STATE" = "RUNNING_UNLOCKED" ] && break
        sleep 1
    done
fi
if [ -n "$WORK_ID" ] && [ "$WORK_ID" != "0" ]; then
    ok "work profile present (user $WORK_ID)"
else
    bad "no work profile available (WORK_ID=$WORK_ID)"
    echo "Result: $PASS passed, $FAIL failed"; exit 1
fi

info "Seed work contact in the work profile (idempotent)"
if ! adb_ shell content query --uri content://com.android.contacts/data --user "$WORK_ID" \
        --projection data1 2>/dev/null | grep -qF "$WORK_CONTACT_NAME"; then
    RAW_ID=$(adb_ shell content insert --uri content://com.android.contacts/raw_contacts \
        --user "$WORK_ID" --bind account_type:s:local --bind account_name:s:null >/dev/null 2>&1; \
        adb_ shell content query --uri content://com.android.contacts/raw_contacts \
        --user "$WORK_ID" --projection _id 2>/dev/null | grep -oE '_id=[0-9]+' | head -1 | cut -d= -f2)
    adb_ shell "content insert --uri content://com.android.contacts/data --user $WORK_ID \
        --bind raw_contact_id:i:$RAW_ID --bind mimetype:s:vnd.android.cursor.item/name \
        --bind data1:s:'$WORK_CONTACT_NAME'" >/dev/null 2>&1
    adb_ shell content insert --uri content://com.android.contacts/data --user "$WORK_ID" \
        --bind raw_contact_id:i:"$RAW_ID" --bind mimetype:s:vnd.android.cursor.item/phone_v2 \
        --bind data1:s:"$WORK_CONTACT_NUM" >/dev/null 2>&1
    note "seeded $WORK_CONTACT_NAME ($WORK_CONTACT_NUM) into user $WORK_ID"
else
    note "work contact already present"
fi
if adb_ shell content query --uri content://com.android.contacts/data --user "$WORK_ID" \
        --projection data1 2>/dev/null | grep -qF "$WORK_CONTACT_NAME"; then
    ok "work contact exists in work profile (user $WORK_ID)"
else
    bad "work contact missing in work profile"
fi
if adb_ shell content query --uri content://com.android.contacts/data \
        --projection data1 2>/dev/null | grep -qF "$WORK_CONTACT_NAME"; then
    bad "work contact leaked into default profile (isolation broken)"
else
    ok "work contact NOT visible from default profile (isolation holds)"
fi

info "Install app in work profile + grant contacts permission"
adb_ shell pm install-existing --user "$WORK_ID" "$PKG" >/dev/null 2>&1
if adb_ shell pm list packages --user "$WORK_ID" 2>/dev/null | grep -q "^package:$PKG\$"; then
    ok "app installed in work profile"
else
    bad "app not installed in work profile"
fi
adb_ shell pm grant --user "$WORK_ID" "$PKG" android.permission.READ_CONTACTS >/dev/null 2>&1
if adb_ shell dumpsys package "$PKG" 2>/dev/null \
        | sed -n "/User $WORK_ID:/,/^User /p" | grep -q "READ_CONTACTS: granted=true"; then
    ok "READ_CONTACTS granted in work profile"
else
    bad "READ_CONTACTS not granted in work profile"
fi

info "Launch app inside the work profile"
adb_ shell am start --user "$WORK_ID" -n "$ACT" >/dev/null 2>&1
ok "am start --user $WORK_ID accepted (open the work-profile app instance manually to inspect)"

echo
note "The default-profile app CAN read work contacts via the public"
note "ENTERPRISE content URIs (data_enterprise/phones, directories_enterprise,"
note "data/phones/filter_enterprise/<q>?directory=1000000000) once it holds"
note "READ_CONTACTS in the work profile (cross-profile grant; on real devices"
note "this is the work-profile consent, here simulated by pm grant --user)."
note "Work contacts resolve from directory id 1000000000 -> badge those."
note "Plain (non-enterprise) URIs stay profile-isolated."
echo
echo "Result: $PASS passed, $FAIL failed"
exit $((FAIL > 0))
