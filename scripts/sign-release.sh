#!/bin/sh
# Sign an unsigned release APK for GitHub and F-Droid.
#
#   sh scripts/sign-release.sh app/build/outputs/apk/release/app-release-unsigned.apk dist/blurttext-0.1.5.apk
#
# Uses Build Tools 34 apksigner with v2 and v3 signatures only. A v1 (JAR)
# signature or a different apksigner can change bytes outside the signing
# block, and then F-Droid's rebuild no longer matches.
#
# The keystore and its password file live outside the repo. Override with
# BLURTTEXT_KEYSTORE and BLURTTEXT_KEYSTORE_PASS_FILE.
set -eu

in=${1:?usage: sign-release.sh <unsigned.apk> <signed.apk>}
out=${2:?usage: sign-release.sh <unsigned.apk> <signed.apk>}
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
apksigner="$sdk/build-tools/34.0.0/apksigner"
ks=${BLURTTEXT_KEYSTORE:-$HOME/.config/blurttext/release.keystore}
pass=${BLURTTEXT_KEYSTORE_PASS_FILE:-$HOME/.config/blurttext/release-password.txt}

[ -x "$apksigner" ] || { echo "missing $apksigner (install Build Tools 34.0.0)" >&2; exit 1; }
mkdir -p "$(dirname "$out")"
"$apksigner" sign --ks "$ks" --ks-key-alias blurttext \
  --ks-pass "file:$pass" \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
  --out "$out" "$in"
rm -f "$out.idsig"

expected=$(tr -d ' \n' < "$root/scripts/expected-release-cert.sha256")
actual=$("$apksigner" verify --print-certs "$out" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
if [ "$actual" != "$expected" ]; then
  echo "signed with the wrong key: $actual (expected $expected)" >&2
  exit 1
fi
echo "Signed $out ($actual)"
