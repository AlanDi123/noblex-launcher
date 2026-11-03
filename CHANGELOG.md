# Registro de cambios

## 0.2.0-rc.1 — Candidato de release (no publicado)

- Fase 6: pruebas de aceptación por ADB (ver ACCEPTANCE_REPORT.md), `UpdateVerifier` (comprobaciones de hash, tamaño, certificado e identidad extraídas y probadas), nuevas pruebas unitarias, MANUAL_TV_CHECKLIST.md y lista de firma para el propietario en SIGNING.md.

- Fase 3: diálogos propios, píldoras Wi-Fi/Bluetooth con estado real, sección Herramientas, gestor de categorías, exportar/importar preferencias, `scripts/tv_home.sh`, categorías por defecto actualizadas.

- Fase 4: filtro HOME separado del de LEANBACK (`activity-alias LeanbackEntry`) y ajuste «Establecer como launcher predeterminado» con `RoleManager`. Causa de que INICIO no abra NOBLEX: ver PHASE4_HOME_REPORT.md.
- HOME persistente: el contenido ya no se reconstruye en cada `onResume`; el foco se conserva.
- Descubrimiento de apps en segundo plano, con recarga al instalar/desinstalar paquetes; incluye actividades leanback sin categoría DEFAULT y banners.
- Reloj alineado al minuto y activo solo con la pantalla visible; fondo decodificado fuera del hilo principal y liberado con `onTrimMemory`.
- Fondo más legible (luma media ≈66/255 en lugar de 18) y atenuación por defecto 10 % (máx. 70 %); opción «Restaurar fondo original»; persistencia verificada tras `force-stop`.
- Tarjetas más grandes (212×160 dp), estrella de favorito, diálogos con tema oscuro.
- Categorías: crear, renombrar y eliminar; nombres reservados validados. «Recientes» ordenado por uso. Reordenar respeta la lista visible y la frontera de favoritos.
- Entradas TV: estado real (conectada/en espera/desconectada) y cambio mediante `TvContract` (ver COMPATIBILITY.md).
- Actualizaciones: validación SemVer, versionCode derivado y siempre creciente, applicationId, tamaño, SHA-256, URL oficial exacta, redirecciones con lista de hosts, descarga `.part` con limpieza, comprobación de paquete/versión/certificado del APK, PackageInstaller con confirmación del usuario, limpieza de caché y trabajo periódico con reintentos acotados.
- Release workflow: solo tags SemVer, versionCode derivado de la versión, APK `noblex-launcher-<versión>.apk`, huella de certificado fijada, notas por versión y `update-manifest.json` con `sizeBytes`.
- CI compila también el release con R8; regla ProGuard corregida para `UpdateWorker`.
- Pruebas unitarias ampliadas (parser, SemVer, URL, política, caché, lógica de lanzador).

## 0.1.0 — Desarrollo inicial

- Pantalla HOME en Kotlin con navegación de control remoto.
- Descubrimiento de apps, favoritos, categorías, orden y visibilidad.
- Consulta de entradas TV y flujo inicial de actualización.

- Fase 5: pantalla de bienvenida (primera ejecución, descartable de forma permanente) y «Ajustes → Ayuda» que explican que en este televisor INICIO sigue abriendo Google TV; el ajuste de rol ya no promete que INICIO abrirá NOBLEX; «Brillo del fondo» aclara que solo oscurece el fondo del launcher, no el brillo del televisor; se quitó la pista «INICIO: volver aquí» por engañosa.
