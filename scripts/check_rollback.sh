#!/usr/bin/env sh
set -eu

ADB="${ADB:-adb}"
DEVICE="$("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
if [ -z "$DEVICE" ]; then
    echo "No hay un dispositivo ADB conectado." >&2
    exit 1
fi

echo "Dispositivo detectado: $DEVICE"
echo "Google TV:"
"$ADB" -s "$DEVICE" shell pm list packages -e com.google.android.tvlauncher
echo "HOME actual:"
"$ADB" -s "$DEVICE" shell cmd package resolve-activity --brief \
    -a android.intent.action.MAIN -c android.intent.category.HOME
echo "Recuperación: Ajustes de Android → Aplicaciones → Aplicaciones predeterminadas → Aplicación de inicio → Google TV."
