# Registro de desarrollo

## Fase 3 — migración, validación y acabado visual

Hecho y verificado en el TV:
- Auditoría del sistema (`SYSTEM_AUDIT.md`) e inventario de aplicaciones (`APPLICATION_INVENTORY.md`).
- Diálogos propios (`PanelDialog`), píldoras Wi-Fi/Bluetooth con estado real, sección Herramientas, gestor de categorías, exportar/importar preferencias, `scripts/tv_home.sh`.
- Pruebas unitarias, lint y build debug correctos; memoria ≈63 MB PSS.

Migración de HOME: **no lograda**. `cmd package set-home-activity` fue aceptado y el rol `android.app.role.HOME` apuntaba al launcher debug, pero al pulsar HOME (y con `am start -a MAIN -c HOME`) el sistema seguía iniciando `com.google.android.tvlauncher/.MainActivity`. Se revirtió con `restore-google`; Google TV es el HOME y el titular del rol.

Pendiente:
1. Manual: probar elegir NOBLEX en Ajustes de Android → Aplicaciones → Aplicación de inicio o en el selector HOME y comprobar si persiste.
2. Sin verificar: cambio HDMI con fuente real, mando físico, renombrar categoría y ajuste del brillo en el TV, elegir imagen de fondo.
3. Clave de firma de producción y release público (a propósito no hechos).

## Auditoría inicial

- Confirmado Android 11/API 30, ARMv7, low-RAM, 1920×1080 a 320 dpi; Google TV sigue como HOME.
- Verificadas las rutas de los paquetes y comparados SHA-256 de los APK respaldados con los del dispositivo: todos coinciden; archivos ZIP legibles.
- TvInputManager expone entradas HDMI, compuesto y sintonizador; el cambio global no se presume disponible para una app normal.
- GitHub CLI está autenticado. Se creó y publicó `https://github.com/AlanDi123/noblex-launcher`; el código no incluye APK propietarios ni material de firma.

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
- La primera ejecución de CI falló durante la configuración del SDK porque Android setup v3 intentó instalar el paquete obsoleto `tools`. Tras actualizar ambos workflows a v4.0.4, las dos ejecuciones posteriores completaron correctamente las pruebas, lint, build y carga del APK de depuración.
