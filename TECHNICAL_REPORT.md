# Informe técnico del dispositivo

## Plataforma comprobada

- Fabricante interno: Changhong; producto `ISDB93CH`; dispositivo `sindorim`; SoC reportado `m7322`.
- Android 11, API 30; build `RTO2.231030.002`, incremental `11027116`.
- ABI primaria: `armeabi-v7a`; configuración low-RAM habilitada.
- `wm size`: 1920×1080; densidad: 320 dpi.
- HOME actual: `com.google.android.tvlauncher/.MainActivity`.
- El launcher Google y el paquete de recomendaciones permanecen instalados y habilitados.
- Se compararon SHA-256 de los seis APK base/split respaldados contra las rutas instaladas; todos coinciden. Los archivos también pasan prueba de integridad ZIP.

## Aplicaciones e inputs

- PackageManager devolvió 20 actividades `LEANBACK_LAUNCHER`, incluyendo apps instaladas, TV del fabricante y Google TV.
- El catálogo de la app se consulta dinámicamente; no usa esta lista como catálogo fijo ni requiere `QUERY_ALL_PACKAGES`.
- `dumpsys tv_input` reporta entradas HDMI 1–4, compuesto y sintonizador, junto con entradas internas de Google Videos. Se filtrarán inputs passthrough y se mostrarán sus nombres del framework.
- Se observó una app de TV MediaTek con actividad de inicio. No se usará una API privada ni se simulará un intent HDMI; la app de launcher solo ofrecerá abrir la app TV si está visible/instalada.
- Android protege el cambio global de fuente con privilegios del sistema en muchas implementaciones. La selección real debe comprobarse en el dispositivo; si no está permitida, se informará sin presentar botones falsos.

## HOME y rollback

Se instaló `io.github.alandi123.noblexlauncher.debug` para pruebas. La captura del TV confirmó la interfaz a 1920×1080; la navegación DPAD, el menú contextual y la apertura de una app descubierta se comprobaron sin errores fatales. El package debug no se seleccionó como HOME.

El script `scripts/check_rollback.sh` confirmó después de la instalación que `com.google.android.tvlauncher` permanece habilitado y es el HOME resuelto. No se desactivaron paquetes ni se cambió la preferencia HOME. La selección futura, si se autoriza, debe hacerse mediante el selector/Ajustes Android y puede revertirse eligiendo Google TV.

## Resolución alternativa

Otra propiedad reportaba 3840×2160, mientras que Android WindowManager confirma 1920×1080 físico. La captura de pantalla del TV mide 1920×1080, por lo que la interfaz se validó en esa resolución efectiva.

## Fuente del wallpaper

Se descargó la foto desde la CDN oficial de Unsplash enlazada desde la página de Kyle Larivee. El original y el proceso de recorte/oscurecimiento se guardan bajo `wallpapers/`; el recurso 1920×1080 se incluye localmente en la app.
