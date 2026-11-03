# ACCEPTANCE_REPORT.md — Fase 6

Dispositivo: Noblex / Changhong AI PONT, Android 11 (API 30), ARMv7, 1920x1080, ADB inalámbrico.
Paquete probado: `io.github.alandi123.noblexlauncher.debug` 0.2.0-rc.1 (versionCode 20067).
Base: `7bd015f`. Google TV sigue siendo el HOME (limitación de firmware documentada; no se intentó cambiarlo).

**Alcance de las pruebas:** todo lo marcado «ADB» usa `adb shell input`/`monkey`/`uiautomator` y **no** demuestra compatibilidad con el mando físico. Las pruebas con mando, HDMI y reinicio quedan **PENDING** (ver `MANUAL_TV_CHECKLIST.md`).

| ID | Prueba | Esperado | Resultado real | Estado |
|---|---|---|---|---|
| A-01 | Copia de preferencias (Ajustes → Respaldo → Exportar) | JSON válido fuera del repo | Exportado y copiado a `~/NoblexProject/backup/prefs/phase6-original.json` (chmod 600); claves format/version/strings/ints/stringSets válidas | PASS (ADB) |
| B-01 | Inventario | Sin duplicados; coincide con PackageManager | 23 apps únicas en la UI; PackageManager (LEANBACK+LAUNCHER) 24 incluyendo NOBLEX; contadores por categoría suman 23 | PASS (ADB) |
| B-02 | Lanzar las 23 apps | Cada una abre | Todas arrancan salvo: `com.mediatek.tv.service` y `com.mediatek.wwtv.setupwizard` (sin ventana; es de sistema), `com.directv.dtvlatam` (su proceso se reinicia/sale a Google TV; propio de la app), `com.homwee.mmp.fileexplorer` (muestra diálogo de permisos; se cerró con ATRÁS sin conceder). Sin FATAL ni ANR | PARTIAL (ADB) |
| B-03 | Refrescar tras instalar/desinstalar | Lista se actualiza | No se instaló ni desinstaló nada en el TV; se confirma solo por código (receptor de paquetes) | PENDING |
| C-01 | Crear categoría | Se guarda | `custom_categories` actualizado | PASS (ADB) |
| C-02 | Renombrar | Se guarda | Renombrada a `ZZRenamed` | PASS (ADB) |
| C-03 | Asignar app a categoría | Persistente | `category.com.homwee.aipont=ZZRenamed` | PASS (ADB) |
| C-04 | Eliminar categoría | No desinstala; limpia asignación | App sigue instalada, asignación y categoría eliminadas | PASS (ADB) |
| C-05 | Reordenar | Persiste | `order` con 23 entradas sin duplicados; «Mover después» en frontera de favoritos no hace nada (diseño) | PASS (ADB) |
| C-06 | Persistencia tras `force-stop` | Igual estado | 22 apps visibles, favorito primero, orden conservado | PASS (ADB) |
| D-01 | Favorito añadir/quitar | Se refleja y persiste | Aparece primero y describe «favorita»; persiste | PASS (ADB) |
| D-02 | Ocultar y restaurar | Persiste; estado vacío | `hidden=[com.android.documentsui]` y luego vacío; mensaje «No hay aplicaciones ocultas» con foco en Volver | PASS (ADB) |
| E-01 | Fondo incluido/persistencia | Persiste tras `force-stop` | Verificado en Fase 2/5 | PASS (ADB) |
| E-02 | Elegir imagen nueva con vista previa | Vista previa y confirmación | No automatizable por ADB (selector del sistema de archivos, sin mando). Sin ejecutar en esta fase | PENDING |
| E-03 | Imagen inválida | Fallback al fondo incluido, sin crash | Solo por código: `decodeWallpaper` cae al recurso incluido y captura `OutOfMemoryError`; tamaño decodificado limitado a 1920×1080 | PARTIAL (código) |
| F-01 | Estado Wi-Fi | Real | «Conectado · con internet»; diálogo explica SSID oculto sin permiso de ubicación; la red no cambió | PASS (ADB) |
| F-02 | Bluetooth y accesos a ajustes oficiales con ATRÁS | Volver a NOBLEX | No re-ejecutado en esta fase para no tocar emparejamientos | PENDING |
| F-03 | Navegación de diálogos con DPAD | Foco visible, sin trampas | Verificado en diálogos de categorías, ocultas, respaldo, Ayuda, Wi-Fi. Nota: ATRÁS en un panel vuelve a la pantalla principal, no al panel padre | PASS (ADB) |
| G-01 | Exportar → importar (ida y vuelta) | Estado original restaurado | `launcher.xml` posterior coincide con el original (favoritos/ocultas vacíos, `wallpaper_dim=0`, `intro_seen=true`) | PASS (ADB) |
| G-02 | Introducción no reaparece; Ayuda accesible | Sí | Verificado en Fase 5 y tras `force-stop` | PASS (ADB) |

## Recursos y estabilidad (ADB, arranque en frío tras `force-stop`)

| Métrica | Valor |
|---|---|
| Arranque en frío (`am start -W`) | TotalTime 2033 ms |
| PSS total | 71 422 KB (≈ 70 MB) |
| Private Dirty total | 42 460 KB (≈ 41 MB) |
| RSS | 115 532 KB |
| Java heap / Native heap (PSS) | 7 280 KB / 21 308 KB |
| CPU en reposo | Sistema 351 % idle de 400 %; el proceso no aparece entre los 5 primeros |
| FATAL / ANR en logcat | 0 |

Comparación: el valor «≈ 40 MB» de la Fase 5 coincide con el **Private Dirty** (41 MB), no con el PSS total (70 MB), que incluye bibliotecas compartidas del sistema. No se considera regresión; no se optimizó. El método de medición de aquella fase no se registró, por lo que la comparación es indicativa.

## Actualizador

32 pruebas unitarias en total, 0 fallos; nuevas en `UpdateVerifierTest` más las existentes del analizador, URL, SemVer, política y caché. Ver `UPDATES.md`.
Cubierto por pruebas: manifiesto válido, campos ausentes, versionCode inválido, applicationId incorrecto, URL inválida, hash incorrecto, huella de certificado distinta o ausente, tamaño distinto/truncado, versión anterior, JSON malformado.
No cubierto por pruebas unitarias (requiere Android o red): fallo de red real, almacenamiento insuficiente, cancelación del usuario, fallo del `PackageInstaller` (manejados por `captureResult`/`finally` en `UpdateManager`; revisión de código).
Prueba de extremo a extremo: **BLOCKED** hasta que exista la clave permanente y una publicación oficial (ver `SIGNING.md`).

## Estado por categoría

- Verificado automáticamente: pruebas unitarias, lint, compilación debug.
- Verificado en el TV por ADB: tabla anterior.
- Verificado físicamente: nada.
- Pendiente: mando físico, HDMI, reinicio, selector de fondo, refresco al instalar apps, Bluetooth.
- Bloqueado por infraestructura: firma permanente, publicación, prueba de actualización real.
- Limitación de firmware conocida: INICIO abre Google TV.
