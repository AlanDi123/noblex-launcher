#!/usr/bin/env bash
# Switch the Android TV default HOME between Google TV and NOBLEX LAUNCHER, or show its status.
# Only uses `cmd package set-home-activity` (a supported shell command). It never disables,
# uninstalls or modifies any package, and never touches Wi-Fi or Bluetooth.
#
#   scripts/tv_home.sh status
#   scripts/tv_home.sh set-noblex [--package io.github.alandi123.noblexlauncher.debug]
#   scripts/tv_home.sh restore-google
#
# Device selection: ANDROID_SERIAL=<ip:port> or, when exactly one device is attached, that device.
# If no device is attached and TV_HOST=<ip[:port]> is set, `adb connect` is attempted.
set -euo pipefail

GOOGLE_PKG="com.google.android.tvlauncher"
GOOGLE_ACTIVITY="com.google.android.tvlauncher.MainActivity"
NOBLEX_PKG="io.github.alandi123.noblexlauncher.debug"
NOBLEX_ACTIVITY="io.github.alandi123.noblexlauncher.LauncherActivity"
BACKUP_DIR="${NOBLEX_BACKUP_DIR:-$HOME/NoblexProject/backup}"

command_name="${1:-status}"
shift || true
while [ $# -gt 0 ]; do
  case "$1" in
    --package) NOBLEX_PKG="$2"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 64 ;;
  esac
done

command -v adb >/dev/null || { echo "adb not found in PATH" >&2; exit 69; }

if [ -z "${ANDROID_SERIAL:-}" ]; then
  mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  if [ "${#devices[@]}" -eq 0 ] && [ -n "${TV_HOST:-}" ]; then
    adb connect "$TV_HOST" >/dev/null || true
    mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  fi
  if [ "${#devices[@]}" -ne 1 ]; then
    echo "Expected exactly one adb device, found ${#devices[@]}. Set ANDROID_SERIAL or TV_HOST." >&2
    exit 69
  fi
  export ANDROID_SERIAL="${devices[0]}"
fi
tvsh() { adb shell "$@" | tr -d '\r'; }

resolved_home() {
  tvsh cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME \
    | awk -F= '/^ *packageName=/ {print $2; exit}'
}

# Output is captured first: `grep -q` would close the pipe early and trip pipefail.
has_package() {
  local listing
  listing="$(tvsh pm list packages "${2:-}")"
  grep -qx "package:$1" <<<"$listing"
}

require_enabled() {
  if ! has_package "$1" -e; then
    echo "Refusing to continue: $1 is not installed and enabled." >&2
    exit 70
  fi
}

case "$command_name" in
  status)
    echo "Device:           $ANDROID_SERIAL"
    echo "Current HOME:     $(resolved_home)"
    echo "Google TV:        $(has_package "$GOOGLE_PKG" -e && echo installed+enabled || echo MISSING-OR-DISABLED)"
    echo "NOBLEX ($NOBLEX_PKG): $(has_package "$NOBLEX_PKG" && echo installed || echo not-installed)"
    ;;
  set-noblex)
    require_enabled "$GOOGLE_PKG"
    require_enabled "$NOBLEX_PKG"
    homes="$(tvsh cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME)"
    if ! grep -q "packageName=$NOBLEX_PKG" <<<"$homes"; then
      echo "Refusing: $NOBLEX_PKG does not declare a HOME activity." >&2
      exit 70
    fi
    mkdir -p "$BACKUP_DIR"
    stamp="$(date +%Y%m%d-%H%M%S)"
    tvsh cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME \
      > "$BACKUP_DIR/home-resolver-before-$stamp.txt"
    tvsh cmd package set-home-activity "$NOBLEX_PKG/$NOBLEX_ACTIVITY"
    [ "$(resolved_home)" = "$NOBLEX_PKG" ] || { echo "HOME did not change; run restore-google if needed." >&2; exit 71; }
    echo "Default HOME is now $NOBLEX_PKG. Previous resolver saved in $BACKUP_DIR."
    ;;
  restore-google)
    require_enabled "$GOOGLE_PKG"
    tvsh cmd package set-home-activity "$GOOGLE_PKG/$GOOGLE_ACTIVITY"
    [ "$(resolved_home)" = "$GOOGLE_PKG" ] || { echo "Google TV is not the HOME handler yet." >&2; exit 71; }
    echo "Default HOME restored to Google TV ($GOOGLE_PKG)."
    ;;
  *)
    echo "Usage: $0 {status|set-noblex [--package PKG]|restore-google}" >&2
    exit 64
    ;;
esac
