#!/bin/sh
# Check that a published, signed release APK matches an unsigned build of its
# tag, with the same tool F-Droid uses (apksigcopier).
#
#   sh scripts/verify-reproducible.sh v0.1.5 path/to/blurttext-0.1.5.apk
#
# Clones the ref and the LanText submodule into a temporary directory (a
# different path catches path leaks), builds the unsigned release there, and
# asks apksigcopier whether the published signature fits that build.
# apksigcopier is installed into a throwaway venv if it is not on PATH.
set -eu

ref=${1:?usage: verify-reproducible.sh <tag-or-commit> <signed.apk>}
signed=$(realpath "${2:?usage: verify-reproducible.sh <tag-or-commit> <signed.apk>}")
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# apksigcopier calls apksigner; use the same Build Tools as sign-release.sh.
PATH="$sdk/build-tools/34.0.0:$PATH"
if command -v apksigcopier >/dev/null 2>&1; then
  copier=apksigcopier
else
  python3 -m venv "$work/venv"
  "$work/venv/bin/pip" -q install apksigcopier
  copier="$work/venv/bin/apksigcopier"
fi

git clone -q "$root" "$work/src"
git -C "$work/src" -c advice.detachedHead=false checkout -q "$ref"
git -C "$work/src" submodule update -q --init --recursive
printf 'sdk.dir=%s\n' "$sdk" > "$work/src/local.properties"
(cd "$work/src" && ./gradlew --no-daemon -q :app:assembleRelease)

unsigned="$work/src/app/build/outputs/apk/release/app-release-unsigned.apk"
[ -f "$unsigned" ] || { echo "no unsigned APK built" >&2; exit 1; }

if "$copier" compare "$signed" --unsigned "$unsigned"; then
  echo "Reproducible: $ref matches $signed"
else
  echo "MISMATCH between $signed and a rebuild of $ref" >&2
  echo "Diff with: diffoscope (see https://f-droid.org/docs/Reproducible_Builds/)" >&2
  exit 1
fi
