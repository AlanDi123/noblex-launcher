# Compatibilidad

## Objetivos

- Android TV/Google TV con Android 9 (API 28) o posterior.
- Prueba primaria: Noblex / Changhong AI PONT, Android 11 (API 30), ARMv7, 1920×1080.
- No se usan bibliotecas nativas de terceros; el APK debería poder ejecutarse en ARMv7, ARM64 y otras ABI compatibles con Android.

## Verificado en el Noblex conectado

- Reporta API 30, Android 11 y ABI `armeabi-v7a`.
- Resolución física reportada por `wm size`: 1920×1080; densidad: 320 dpi.
- Configuración low-RAM habilitada.
- `TvInputManager` informa entradas HDMI, compuesto y sintonizador.
- La app de TV MediaTek tiene actividad de inicio visible para Android.

La pantalla de 3840×2160 descrita por otra propiedad aún requiere contrastar con captura de pantalla y dimensiones de ventana antes de fijar decisiones de diseño.

## Limitaciones

Una aplicación normal puede descubrir entradas públicas de `TvInputManager`, pero no se presume que pueda cambiar globalmente HDMI. La app muestra el estado detectado y ofrece abrir la app de TV instalada. No se invocan APIs ocultas ni comandos del fabricante no verificados.

Los demás dispositivos Android TV, Google TV, resoluciones, proveedores de TV y ABI son objetivos no certificados hasta probarlos.
