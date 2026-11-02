# NOBLEX LAUNCHER

Launcher Android TV independiente, sin publicidad ni recomendaciones patrocinadas. Diseñado para navegar con control remoto y funcionar sin conexión; solo la comprobación de actualizaciones requiere internet.

> NOBLEX LAUNCHER es un proyecto comunitario independiente y no está afiliado a Noblex, Changhong, Google ni Android TV.

## Estado

Versión inicial de desarrollo (`0.1.0`). El APK de depuración se compiló e instaló en el TV para validar la interfaz, el foco DPAD, el menú contextual y la apertura de aplicaciones. Sigue siendo una prueba: Google TV permanece como HOME predeterminado y no existe una publicación de actualización hasta que se cree el repositorio remoto y se configure una clave de release protegida.

## Funciones

- HOME Android TV y catálogo de aplicaciones consultado al sistema.
- Categorías editables, favoritos, orden y ocultación persistentes.
- Reloj local de 24 horas, fecha y foco visible para control remoto.
- Fondo estático local con atribución y herramienta de procesamiento.
- Entradas informadas por `TvInputManager`, sin intents HDMI inventados.
- Comprobación manual/periódica de releases, validación SHA-256 y entrega a PackageInstaller.

## Construcción

Requiere JDK 17, Android SDK Platform 35, Android Build Tools 35 y Gradle Wrapper.

```sh
./gradlew testDebugUnitTest lint assembleDebug
```

Para instalar de forma manual en un dispositivo conectado:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

No cambies el HOME predeterminado hasta validar la aplicación. Mantén instalado y habilitado el launcher original.

## Seguridad de publicación

La identidad de paquete es `io.github.alandi123.noblexlauncher` y no debe cambiar. No se incluyen claves, contraseñas, APK del fabricante, identificadores del televisor ni credenciales. Los releases deben usar una clave permanente respaldada de forma segura; Android requiere confirmación del usuario para instalar actualizaciones.

Consulta `INSTALLATION.md`, `UPDATES.md`, `ROLLBACK.md` y `COMPATIBILITY.md` antes de distribuir o instalar una versión estable.
