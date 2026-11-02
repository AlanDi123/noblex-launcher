#!/usr/bin/env bash
# Verifies a built APK without printing any secret.
# Usage: scripts/verify_apk.sh APK [EXPECTED_CERT_SHA256]
#   Checks: signature validity (v2/v3), package name, versionName/versionCode consistency with
#   the derived scheme, minSdk/targetSdk and, when given, the pinned signing-certificate SHA-256.
set -euo pipefail

APK="${1:?usage: verify_apk.sh APK [EXPECTED_CERT_SHA256]}"
EXPECTED_CERT="${2:-}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
TOOLS="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
EXPECTED_ID="io.github.alandi123.noblexlauncher"

test -s "$APK" || { echo "APK not found: $APK" >&2; exit 1; }

echo "== Signature"
CERTS="$("$TOOLS/apksigner" verify --verbose --print-certs "$APK")"
echo "$CERTS" | grep -E "^(Verifies|Verified using|Number of signers)" || true
FINGERPRINT="$(echo "$CERTS" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1)"
echo "Certificate SHA-256: ${FINGERPRINT:-unknown}"
if echo "$CERTS" | grep -q "Android Debug"; then
  echo "NOTE: signed with the Android debug certificate (not valid for releases)."
fi
if [ -n "$EXPECTED_CERT" ]; then
  EXPECTED_NORMALIZED="$(echo "$EXPECTED_CERT" | tr -d ': ' | tr 'A-F' 'a-f')"
  if [ "$FINGERPRINT" != "$EXPECTED_NORMALIZED" ]; then
    echo "FAIL: certificate does not match the pinned fingerprint." >&2
    exit 2
  fi
  echo "Pinned certificate: OK"
fi

echo "== Manifest"
BADGING="$("$TOOLS/aapt2" dump badging "$APK")"
PACKAGE_LINE="$(echo "$BADGING" | grep '^package:')"
NAME="$(echo "$PACKAGE_LINE" | sed -n "s/.* name='\([^']*\)'.*/\1/p")"
VERSION_CODE="$(echo "$PACKAGE_LINE" | sed -n "s/.* versionCode='\([^']*\)'.*/\1/p")"
VERSION_NAME="$(echo "$PACKAGE_LINE" | sed -n "s/.* versionName='\([^']*\)'.*/\1/p")"
echo "package=$NAME versionName=$VERSION_NAME versionCode=$VERSION_CODE"
echo "$BADGING" | grep -E "^(minSdkVersion|sdkVersion|targetSdkVersion|native-code)" || echo "native-code: none"
case "$NAME" in
  "$EXPECTED_ID"|"$EXPECTED_ID.debug") ;;
  *) echo "FAIL: unexpected package name." >&2; exit 3 ;;
esac
DERIVED="$(python3 - "$VERSION_NAME" <<'PY'
import re, sys
m = re.fullmatch(r"(0|[1-9]\d?)\.(0|[1-9]\d?)\.(0|[1-9]\d?)(?:-(alpha|beta|rc)\.([1-9]\d?))?", sys.argv[1])
if not m:
    sys.exit("invalid")
major, minor, patch, kind, number = m.groups()
slot = 99 if kind is None else ["alpha", "beta", "rc"].index(kind) * 33 + int(number)
print((int(major) * 10000 + int(minor) * 100 + int(patch)) * 100 + slot)
PY
)"
if [ "$DERIVED" != "$VERSION_CODE" ]; then
  echo "FAIL: versionCode $VERSION_CODE does not match the derived value $DERIVED." >&2
  exit 4
fi
echo "versionCode matches versionName: OK"
echo "== Result: OK"
