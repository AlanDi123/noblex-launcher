# Actualizaciones

Las versiones se distribuyen por GitHub Releases (`AlanDi123/noblex-launcher`). La app consulta `releases/latest` (que excluye pre-releases y borradores) sin autenticación ni tokens.

## Modelo de seguridad del cliente

1. **Manifiesto** (`update-manifest.json`, máx. 512 KB): `applicationId` correcto; `version` SemVer estricta (`X.Y.Z` o `-alpha|beta|rc.N`); tag `v<version>`; `versionCode` igual al derivado de la versión; `sizeBytes` 1 B–80 MB; `sha256` de 64 hex; notas ≤ 4000 caracteres.
2. **URL**: solo `https://github.com/AlanDi123/noblex-launcher/releases/download/<tag>/noblex-launcher-<version>.apk`, sin query, fragmento, usuario ni puerto. Las redirecciones se siguen manualmente (máx. 5) y solo hacia `github.com`, `objects.githubusercontent.com` o `release-assets.githubusercontent.com`, siempre HTTPS.
3. **Política**: solo se ofrece si `versionCode` y SemVer son mayores a lo instalado; un versionCode mayor con SemVer no mayor se rechaza.
4. **Descarga** a `cache/updates/*.part`; se comprueba tamaño y SHA-256, se borra lo incompleto/erróneo y solo entonces se renombra a `.apk`.
5. **Archivo**: el APK debe tener el mismo paquete, versionCode y versionName que el manifiesto y un certificado de firma que coincida con el de la app instalada.
6. **Instalación**: sesión de `PackageInstaller`; si Android exige «instalar apps desconocidas», se abre su pantalla de permiso; el usuario confirma en el diálogo del sistema. Los datos del usuario se conservan porque es una actualización del mismo paquete con la misma firma.
7. **Limpieza**: archivos desconocidos, `.part` de más de 1 h, APKs ya instalados u obsoletos (> 7 días) se eliminan al iniciar y tras cada comprobación.
8. **Frecuencia**: un `WorkManager` periódico (red requerida) con espera mínima entre comprobaciones y máximo 3 reintentos.

## Esquema de versiones

`versionCode = (MAJOR·10000 + MINOR·100 + PATCH)·100 + slot`; slot 99 = estable, `alpha.N` = N, `beta.N` = 33+N, `rc.N` = 66+N (N 1–32). Ej.: `0.2.0-rc.1` → 20067; `0.2.0` → 20099; `1.2.3` → 1020399. Gradle, `scripts/verify_apk.sh`, el workflow y `SemanticVersion.kt` usan la misma fórmula (cubierta por pruebas).

## Plan seguro de prueba extremo a extremo (sin release falso)

Prerrequisitos: clave permanente creada y respaldada, secretos y `RELEASE_CERT_SHA256` configurados (ver SIGNING.md).

1. Instala en el TV un APK firmado con la clave permanente (p. ej. `0.2.0` estable local verificado con `scripts/verify_apk.sh`; no publicado).
2. Sube la versión en `CHANGELOG.md` y crea un tag **pre-release** `v0.2.1-rc.1`. El workflow publica un *pre-release*, que `releases/latest` ignora: ningún usuario recibe la oferta.
3. Descarga los assets del pre-release, ejecuta `scripts/verify_apk.sh` y compara `sha256`/`sizeBytes` con `update-manifest.json`.
4. Instala ese APK con `adb install -r` en el TV y confirma que los datos (favoritos, categorías, fondo) se conservan.
5. Prueba de camino completo: cuando exista un release estable real posterior al instalado, usa «Buscar actualizaciones» en el TV y confirma que pide permiso/confirmación y conserva los datos.
6. Pruebas negativas (locales, sin publicar): manifiesto con SHA-256 alterado, APK truncado, URL de otro repositorio y APK con otra firma deben fallar; están cubiertas por pruebas unitarias del parser, la política y la caché.

No se debe publicar un release estable solo para probar el actualizador.
