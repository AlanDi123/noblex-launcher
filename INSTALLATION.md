# Instalación

> Estado actual: el candidato debug está instalado, pero Google TV sigue como HOME (ver `FINAL_REPORT.md`). Para migrar al paquete de producción exporta primero tus preferencias desde Ajustes → Respaldo y migración (archivo local en `Download/NoblexLauncher`) e impórtalas en el nuevo paquete.

1. Confirma que el TV original sigue disponible y que el respaldo de sus paquetes está íntegro.
2. Instala el APK de NOBLEX LAUNCHER sin desinstalar ni deshabilitar Google TV ni recomendaciones.
3. Ábrelo manualmente y prueba DPAD, BACK, aplicaciones, categorías, favoritos, ajustes e inputs.
4. Cambia la aplicación HOME desde el selector/Ajustes de Android únicamente cuando las pruebas hayan pasado.
5. Si Android exige selección en pantalla, usa ese selector; no fuerces cambios de paquetes del sistema mediante ADB.

Una instalación local de prueba debe usar el mismo certificado que las versiones posteriores si comparte el mismo `applicationId`. Verifica cualquier APK con `scripts/verify_apk.sh` antes de instalarlo. No instales una versión de depuración firmada con una clave temporal como primera instalación de producción.
