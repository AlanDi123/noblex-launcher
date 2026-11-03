# Compatibilidad

## Objetivos

- Android TV/Google TV con Android 9 (API 28) o posterior; target SDK 30.
- Prueba primaria: Noblex / Changhong AI PONT, Android 11 (API 30), ARMv7, 1920×1080 (densidad 320 dpi, RAM reducida).
- Sin bibliotecas nativas propias.

## Verificado en el Noblex conectado (0.2.0-rc.1 debug)

- Instalación, arranque, foco DPAD, apertura de una app y regreso con HOME.
- Fondo persistente tras `force-stop`; Google TV sigue siendo HOME (`scripts/check_rollback.sh`).
- Memoria: ≈40–63 MB de PSS (Fase 5 ≈40 MB; antes ≈80 MB) en el proceso del launcher con el fondo cargado.

## Entradas de TV y cambio de HDMI

Investigación de solo lectura (sin modificar paquetes):

- Plataforma MediaTek (`m7322`). `com.mediatek.tvinput` publica entradas TIF estándar: HDMI HW4–HW7 (conectadas/en espera), compuesto HW1 (desconectado) y sintonizadores HW0/HW8.
- `com.mediatek.wwtv.tvcenter` (LiveTV, app del sistema) atiende `ACTION_VIEW` sobre `content://android.media.tv/passthrough/<id>`.
- **Verificado por estado del sistema:** lanzar esa intención desde `am start` abrió TurnkeyUi y `dumpsys tv_input` mostró una sesión activa en la entrada HW5 (HDMI); HOME volvió a Google TV.
- **No verificado visualmente:** las capturas de vídeo HDMI están protegidas (salen vacías), por lo que falta confirmar con una fuente real conectada que se ve la señal. Mientras tanto la función está implementada con la API pública `TvContract` y se considera *implementada, pendiente de confirmación visual en el TV*.
- No se usan APIs ocultas, `INJECT_EVENTS`, `HdmiControlManager` privado ni apps del fabricante modificadas.

## Limitaciones

Otros dispositivos Android TV, Google TV, resoluciones y ABI son objetivos no certificados hasta probarlos. El comportamiento del selector HOME depende del fabricante; el launcher no se ha establecido como HOME.

## Portabilidad (Fase 5)

- Núcleo genérico (cualquier Android TV API 28+): descubrimiento de apps, favoritos, categorías, fondo, respaldo, atajos de Ajustes, `RoleManager`, actualizaciones por GitHub. Sin dependencias de Google TV ni de servicios Noblex; APK sin código nativo (independiente de ABI).
- Específico del fabricante/plataforma: solo el cambio de entradas usa `TvContract` público y la app `com.mediatek.wwtv.tvcenter` si existe; si falta, se muestra un aviso.
- Permisos: INTERNET, ACCESS_NETWORK_STATE, BLUETOOTH (≤API 30) y REQUEST_INSTALL_PACKAGES. No se pide ubicación: el SSID de Wi-Fi no se muestra, solo «conectado/con internet».
- Degradación: sin selector de rol, sin pantalla de Bluetooth/Wi-Fi o sin selector de imágenes se muestra una explicación en lugar de fallar. El brillo del televisor no se controla (el ajuste solo oscurece el fondo).
- No se afirma compatibilidad universal: solo está probado el Noblex AI PONT.
