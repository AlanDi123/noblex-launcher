# Informe final — NOBLEX LAUNCHER

## Entregado

- Proyecto Android Kotlin con identidad de producción fija `io.github.alandi123.noblexlauncher` y variante de depuración separada.
- Launcher para Android TV con catálogo dinámico, categorías, favoritos, opciones de organización, reloj, fondo local con atribución y consulta de entradas TV mediante APIs públicas.
- Comprobación de actualizaciones y flujo PackageInstaller preparado para releases firmados.
- CI, workflow de publicación, documentación en español, instrucciones de rollback y scripts de procesamiento de wallpaper.
- Código publicado en el repositorio público [AlanDi123/noblex-launcher](https://github.com/AlanDi123/noblex-launcher).

## Validación local y en el dispositivo

- `testDebugUnitTest`: 3 pruebas aprobadas.
- `assembleDebug`: completado correctamente. `lintDebug`: completado correctamente; permanecen avisos no bloqueantes de banner vectorial y versiones de dependencias.
- APK verificado como `io.github.alandi123.noblexlauncher.debug`, min SDK 28 y target SDK 30.
- Instalado y abierto en Android TV 11/API 30. La captura confirma 1920×1080; se probaron el foco DPAD, menú contextual y apertura de una aplicación descubierta.
- `scripts/check_rollback.sh` confirmó que Google TV permanece habilitado y es el HOME actual.
- El primer CI falló en la configuración del SDK porque Android setup v3 intentó instalar un paquete obsoleto. Tras actualizar ambos workflows a v4.0.4, las dos ejecuciones posteriores completaron correctamente pruebas, lint, build y carga del APK de depuración.

## Pendiente antes de publicar o activar

- No existe una clave permanente de release con copia de seguridad verificada. Antes de publicar un APK estable, crea esa clave, guarda una copia segura fuera del repositorio y configura los secretos `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` y `ANDROID_KEY_PASSWORD` en GitHub Actions.
- NOBLEX LAUNCHER no se seleccionó como HOME. Google TV continúa como opción de recuperación y HOME predeterminado.

Por estas limitaciones, no se afirma que exista un release firmado, una actualización disponible ni activación permanente del launcher.
