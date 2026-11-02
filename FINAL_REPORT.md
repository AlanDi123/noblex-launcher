# Informe final — NOBLEX LAUNCHER 0.2.0-rc.1 (candidato de release, no publicado)

## Fase 3 — estado (migración temporal del candidato debug)

**¿El TV abre NOBLEX LAUNCHER por defecto? No.** Google TV sigue siendo el HOME. Evidencia: tras `scripts/tv_home.sh set-noblex` el rol `android.app.role.HOME` listó `io.github.alandi123.noblexlauncher.debug`, pero `ActivityTaskManager` siguió iniciando `com.google.android.tvlauncher/.MainActivity` al pulsar HOME. Se ejecutó `restore-google` y se verificó (`holders=com.google.android.tvlauncher`). Paso manual restante: elegir NOBLEX como aplicación de inicio desde los ajustes/selector del TV.

| Categoría | Elementos |
|---|---|
| Probado y funcionando | Pastillas Wi-Fi/Bluetooth y accesos oficiales con retorno por BACK; Ajustes; categorías (crear, persistir tras force-stop, eliminar); favoritos; ocultar/mostrar; menú contextual; Herramientas; lista de entradas; exportar e importar preferencias; lanzamiento de 21 paquetes; memoria ≈63 MB; tests unitarios, lint, build debug |
| Probado parcialmente | Renombrar categoría; mover; brillo; selector de fondo (solo se abre) |
| No probado | Mando físico; cambio HDMI con fuente real; TalkBack; otros televisores |
| No soportado por Android/hardware | SSID de Wi-Fi (requiere permiso de ubicación); `BLUETOOTH_SETTINGS` no existe en este TV (se usa «Agregar accesorio»); HOME por shell no respetado por este firmware |
| Requiere acción manual | Elegir NOBLEX como inicio; clave de firma de producción y release; probar HDMI con una fuente conectada |

Detalles: `SYSTEM_AUDIT.md`, `APPLICATION_INVENTORY.md`, `REMOTE_CONTROL_TESTS.md`, `DEVELOPMENT_LOG.md`.

## Entregado en la fase 2

- Auditoría completa y corrección por severidad: ver `RELEASE_READINESS.md`.
- Interfaz: carcasa persistente con foco conservado, fondo legible y ligero, tarjetas 212×160 dp, diálogos oscuros, categorías editables.
- Funciones: descubrimiento dinámico en segundo plano, favoritos, orden, categorías (crear/renombrar/eliminar), ocultas, recientes, fondo y atenuación, reloj, entradas TV.
- Actualizador reforzado (SemVer, versionCode creciente, applicationId, tamaño, SHA-256, URL oficial, redirecciones acotadas, `.part`, firma y paquete del APK, PackageInstaller, limpieza de caché). Ver `UPDATES.md`.
- Firma: procedimiento en `SIGNING.md`; `scripts/verify_apk.sh` y `scripts/make_update_manifest.py`. Workflow de release endurecido y no ejecutado.

## Verificación realizada

- `testDebugUnitTest`: 20 pruebas, 0 fallos. `lintDebug` y `assembleDebug`: correctos (avisos no bloqueantes).
- `assembleRelease` con R8, firmado con una clave **desechable** (ya eliminada): compila; `apksigner verify` correcto (esquema v2); `aapt2 dump badging`: paquete `io.github.alandi123.noblexlauncher`, `0.2.0-rc.1`, versionCode 20067 (coincide con la fórmula), min SDK 28, target 30. `verify_apk.sh` rechaza una huella distinta (código 2). `make_update_manifest.py` generó un manifiesto coherente (no publicado).
- En el TV (debug): instalación, captura 1920×1080 inspeccionada, DPAD, abrir una app y volver, HOME → Google TV, fondo persistente tras `force-stop`, PSS ≈80 MB.
- `scripts/check_rollback.sh`/consulta de HOME: Google TV sigue siendo HOME y está habilitado.
- Entradas: el intent `TvContract` abrió TurnkeyUi con sesión activa en HDMI HW5 según `dumpsys tv_input`; **no** hay confirmación visual (captura de vídeo protegida).

## No verificado

- Cambio de entrada con señal real (solo estado del sistema).
- Flujo completo del actualizador contra un release real (no existe ninguno; sin release falso).
- TalkBack y dispositivos distintos al Noblex.
- Los workflows de release y CI modificados se ejecutan por primera vez al publicar/empujar.

## Requisitos pendientes

1. Crear y respaldar la clave permanente (`SIGNING.md`).
2. Configurar `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` y la variable `RELEASE_CERT_SHA256`.
3. Crear un tag pre-release `v0.2.x-rc.N` y ejecutar el plan de `UPDATES.md`.
4. Confirmar visualmente el cambio HDMI.
5. Decidir cuándo establecer NOBLEX LAUNCHER como HOME (no se ha cambiado; Google TV intacto).
