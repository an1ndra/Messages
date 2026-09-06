#!/usr/bin/env bash
# Download the latest GitHub release APK and install it on the emulator.
# Usage: scripts/install-release.sh [tag]   (default: latest release)
set -e
source "$(dirname "$0")/env.sh"

REPO="an1ndra/Messages"
TAG="${1:-latest}"

if [ "$TAG" = "latest" ]; then
    APK_URL=$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" \
        | grep -oE '"browser_download_url": *"[^"]+\.apk"' | head -1 \
        | grep -oE 'https://[^"]+')
else
    APK_URL="https://github.com/$REPO/releases/download/$TAG/Messages-${TAG#v}.apk"
fi

[ -n "$APK_URL" ] || { echo "[install] no APK found in the release" >&2; exit 1; }
echo "[install] fetching $TAG from GitHub"
APK_LOCAL="$TMP/release.apk"
curl -fsSL -o "$APK_LOCAL" "$APK_URL"
[ -s "$APK_LOCAL" ] || { echo "[install] download empty/failed" >&2; exit 1; }

echo "[install] sha256: $(sha256sum "$APK_LOCAL" | awk '{print $1}')"
if adb_ install -r "$APK_LOCAL"; then
    echo "[install] installed $TAG over existing app"
else
    echo "[install] signature mismatch — removing old app first"
    adb_ uninstall "$PKG" >/dev/null 2>&1 || true
    adb_ install "$APK_LOCAL"
fi
echo "[install] ok ($TAG)"