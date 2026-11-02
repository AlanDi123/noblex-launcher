# Registro de desarrollo

## Auditoría inicial

- Confirmado Android 11/API 30, ARMv7, low-RAM, 1920×1080 a 320 dpi; Google TV sigue como HOME.
- Verificadas las rutas de los paquetes y comparados SHA-256 de los APK respaldados con los del dispositivo: todos coinciden; archivos ZIP legibles.
- TvInputManager expone entradas HDMI, compuesto y sintonizador; el cambio global no se presume disponible para una app normal.
- GitHub CLI está autenticado. La consulta inicial no encontró el repositorio solicitado; una comprobación posterior no pudo conectar con `api.github.com`.

## Proyecto y assets

- Creado proyecto Android Kotlin con vistas nativas, identidad fija y variante de depuración separada.
- Descargada la fotografía licenciada desde la CDN oficial enlazada por Unsplash; agregada atribución y script Pillow para producir JPEG 1920×1080.
- Instalados Gradle 8.9 y Android SDK Platform/Build Tools 35.
- Ninguna modificación al TV, HOME predeterminado o paquetes del sistema durante esta etapa.

## Estado de validación

- `testDebugUnitTest`: 3 pruebas aprobadas; `assembleDebug`: correcto.
- `lintDebug`: correcto; quedan avisos informativos sobre el tamaño del banner vectorial y versiones de dependencias.
- APK verificado con package `io.github.alandi123.noblexlauncher.debug`, min SDK 28 y target SDK 30.
- Instalado y abierto en el TV sin cambiar HOME. Se comprobó la interfaz 1920×1080, foco DPAD, menú contextual y apertura de una app descubierta.
- El script de rollback confirma `com.google.android.tvlauncher` habilitado y como HOME actual.
- No existe clave privada de release ni respaldo externo verificado. No crear un release estable ni instalar el package de producción hasta resolverlo.
- No se creó ni publicó el repositorio remoto: la API de GitHub no respondió durante el último intento.
