# Instalación

> Estado actual: el candidato debug está instalado, pero Google TV sigue como HOME (ver `FINAL_REPORT.md`). Para migrar al paquete de producción exporta primero tus preferencias desde Ajustes → Respaldo y migración (archivo local en `Download/NoblexLauncher`) e impórtalas en el nuevo paquete.

1. Confirma que el TV original sigue disponible y que el respaldo de sus paquetes está íntegro.
2. Instala el APK de NOBLEX LAUNCHER sin desinstalar ni deshabilitar Google TV ni recomendaciones.
3. Ábrelo manualmente y prueba DPAD, BACK, aplicaciones, categorías, favoritos, ajustes e inputs.
4. En el Noblex AI PONT (Android 11) el botón INICIO siempre abre Google TV: es una limitación del firmware y no se puede cambiar sin modificar el sistema. Abre NOBLEX desde la lista de aplicaciones de Google TV.
5. No fuerces cambios de paquetes del sistema mediante ADB.

**Compatibilidad:** requiere Android 9 (API 28) o superior. El único dispositivo probado es el Noblex AI PONT (Android TV 11, ARMv7). En otros Android TV o Google TV no se ha probado; las funciones de HOME, entradas HDMI y las APIs de MediaTek/Changhong pueden no estar disponibles (ver `COMPATIBILITY.md`).

**Actualizar:** desde Ajustes → Actualizaciones (solo desde el repositorio oficial; se verifica SHA-256, identidad y certificado). **Recuperar:** ver `ROLLBACK.md`.

Una instalación local de prueba debe usar el mismo certificado que las versiones posteriores si comparte el mismo `applicationId`. Verifica cualquier APK con `scripts/verify_apk.sh` antes de instalarlo. No instales una versión de depuración firmada con una clave temporal como primera instalación de producción.
