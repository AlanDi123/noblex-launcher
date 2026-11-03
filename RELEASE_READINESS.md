# Release readiness — NOBLEX LAUNCHER 0.2.0-rc.1

Auditoría de todo el repositorio (Kotlin, recursos, manifiesto, Gradle, workflows). Estado: **Corregido** = cambio en el código y verificado por prueba/compilación/TV; **Abierto** = requiere acción externa.

## Crítico

| # | Hallazgo | Estado |
|---|----------|--------|
| C1 | El updater no validaba que el versionCode aumentara, ni SemVer, ni el certificado del APK descargado; podía instalarse un APK con otra firma o una versión anterior. | Corregido: `UpdatePolicy`, `SemanticVersion`, `UpdateManager.verifyArchive` (paquete, versionCode, versionName, certificado). Pruebas unitarias. |
| C2 | Las redirecciones de descarga se seguían automáticamente a cualquier host y la URL se validaba de forma duplicada. | Corregido: `ReleaseUrlPolicy` (ruta exacta del repositorio; redirecciones manuales con lista de hosts). |
| C3 | El workflow de release usaba `GITHUB_RUN_NUMBER` como versionCode (no ligado a la versión), no validaba tag/versión ni la firma contra una huella fija. | Corregido: versionCode derivado de la versión, tags SemVer, `verify_apk.sh` con huella fijada. Sin ejecutar (no hay secretos). |
| C4 | No existe clave de firma permanente ni respaldo. | **Abierto**: ver SIGNING.md. Bloquea el primer release. |

## Alto

| # | Hallazgo | Estado |
|---|----------|--------|
| A1 | `onResume` reconstruía toda la vista y ejecutaba el descubrimiento en el hilo principal; el foco se perdía y `onCreate` duplicaba el trabajo. | Corregido: carcasa persistente, `refreshContent(focusTag)`, descubrimiento en un executor. Verificado en TV. |
| A2 | Fondo decodificado en el hilo principal, sin límite de tamaño coherente (`inSampleSize` dejaba imágenes >2× el destino) y sin liberar memoria. | Corregido: decodificación fuera del hilo, ≤1920×1080, liberación en `onTrimMemory`. PSS ≈80 MB. |
| A3 | Fondo demasiado oscuro (luma media 18/255, máx. 65) más 38 % de atenuación adicional. | Corregido: luma ≈66, atenuación por defecto 10 %. Verificado en captura (luma de pantalla ≈76). |
| A4 | El descubrimiento usaba `MATCH_DEFAULT_ONLY`, que puede omitir actividades leanback. | Corregido (flags 0, preferencia por leanback). |
| A5 | Sin `.part`, sin limpieza de descargas incompletas ni de APK obsoletos. | Corregido: `UpdateCache` + pruebas. |
| A6 | Regla ProGuard apuntaba a una clase inexistente (`UpdateCheckWorker`); la build R8 nunca se había probado. | Corregido la regla; ver «Compilación release» abajo. |
| A7 | `testInstrumentationRunner` inválido en Gradle. | Corregido. |

## Medio

| # | Hallazgo | Estado |
|---|----------|--------|
| M1 | El reloj seguía activo con la pantalla detenida y no se alineaba al minuto. | Corregido (solo entre `onStart` y `onStop`). |
| M2 | «Recientes» no se ordenaba por uso; mover arriba/abajo ignoraba favoritos-primero y la vista filtrada. | Corregido + `LauncherLogicTest`. |
| M3 | No se podían renombrar/eliminar categorías; nombres reservados podían colisionar. | Corregido. |
| M4 | BACK cerraba el HOME; sin `launchMode`. | Corregido (`singleTask`, BACK vuelve a «Todas»). |
| M5 | Cooldown del updater se marcaba antes del éxito; el worker reintentaba sin límite; se usaba `ACTION_INSTALL_PACKAGE` obsoleto y FileProvider. | Corregido: PackageInstaller + receptor, máx. 3 reintentos. |
| M6 | Notas de release = todo el CHANGELOG; sin `sizeBytes`; APK `app-release.apk`. | Corregido (sección por versión, `noblex-launcher-<versión>.apk`). |
| M7 | Diálogos con tema predeterminado, incoherentes con el diseño. | Corregido (`LauncherDialog`). |

## Bajo / informativo

| # | Hallazgo | Estado |
|---|----------|--------|
| L1 | Acciones de GitHub fijadas por tag, no por SHA. | Abierto (opcional). |
| L2 | Lint: tamaño de `tv_banner`, versiones de dependencias. | Abierto, no bloqueante. |
| L3 | Los chips de categoría largos quedan justos con 10+ categorías (se desplazan horizontalmente). | Abierto, cosmético. |
| L4 | Cambio de HDMI implementado vía `TvContract`; verificado por estado del sistema pero no visualmente (captura protegida). | Abierto: confirmar con una fuente real. |
| L5 | Sin pruebas instrumentadas automáticas; las pruebas de mando/foco se hicieron por ADB. | Abierto. |
| L6 | Accesibilidad: tarjetas enfocables con etiqueta de nombre; texto ≥ 20 sp a 10 pies. Sin TalkBack verificado. | Parcial. |

## Verificación realizada

- `testDebugUnitTest`, `lintDebug`, `assembleDebug`: correctos (ver FINAL_REPORT.md).
- TV: instalación, DPAD, abrir app, HOME → Google TV, persistencia del fondo tras `force-stop`, captura 1920×1080.
- Release R8 firmado con clave **desechable** y `apksigner`: ver FINAL_REPORT.md.

## Requisitos pendientes antes de publicar

1. Crear y respaldar la clave permanente (SIGNING.md).
2. Configurar 4 secretos y la variable `RELEASE_CERT_SHA256`.
3. Crear deliberadamente el primer tag (empezando por un pre-release `-rc`).
4. Prueba de actualización real extremo a extremo (UPDATES.md).
5. Confirmar visualmente el cambio HDMI.
6. Decidir cuándo establecer HOME (no se ha cambiado).

## Estado por fase (actualizado en la Fase 5)

| Área | Estado |
|------|--------|
| Fases 1–2 (auditoría, updater, workflow) | PASS: pruebas unitarias, lint, build, CI verde |
| Fase 3 (conectividad, herramientas, categorías, respaldo) | PASS por ADB en el TV; PARTIAL en mando físico |
| Fase 4 (HOME) | PASS el registro y el rol; **FIRMWARE**: INICIO siempre abre Google TV (prioridad 2 vs. 0), sin solución sin instalación privilegiada |
| Fase 5 (bienvenida/ayuda, honestidad de brillo y rol) | PASS por ADB (captura 1920×1080, persistencia tras `force-stop`) |
| Mando físico (DPAD/OK/ATRÁS/INICIO/volumen) | PENDING: ver lista en REMOTE_CONTROL_TESTS.md |
| HDMI con fuente real | PENDING |
| Reinicio del TV | PENDING (no se probó para no perder el ADB inalámbrico) |
| Clave permanente, secretos, update E2E | PENDING (bloquea el release estable) |

## Estado tras la Fase 6

- Verificado por ADB: ver ACCEPTANCE_REPORT.md. Verificado físicamente: nada.
- PENDING: mando físico, HDMI, reinicio, selector de fondo, refresco al instalar apps, Bluetooth.
- BLOCKED: clave de firma permanente, secretos de GitHub, `RELEASE_CERT_SHA256`, primera pre-release y prueba de actualización real.
- Limitación de firmware: INICIO abre Google TV.
- No hay release publicada y no debe publicarse hasta cerrar lo anterior.
