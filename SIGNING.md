# Firma permanente de releases

Android solo actualiza una app si el APK nuevo está firmado con el **mismo certificado** que el instalado. Si pierdes la clave, los TV con la app instalada no podrán actualizarse: habría que desinstalar (y perder los datos). Por eso la clave se crea una sola vez y se respalda **antes** de publicar.

Estado: **no existe clave permanente ni release firmado.** Este documento es el procedimiento; ningún paso se ha ejecutado con una clave real.

## 1. Crear la clave (en tu equipo, nunca en el repositorio)

```bash
umask 077
mkdir -p ~/noblex-signing && cd ~/noblex-signing
keytool -genkeypair -v -storetype PKCS12 -keystore noblex-release.jks \
  -alias noblex-release -keyalg RSA -keysize 4096 -validity 10950 \
  -dname "CN=NOBLEX LAUNCHER, O=AlanDi123"
```

`keytool` pide la contraseña de forma interactiva (no la pases por argumento ni la imprimas). Usa una contraseña larga única; en PKCS12 la contraseña de clave y de almacén son la misma.

## 2. Guardado y respaldo

1. Gestor de contraseñas: contraseña, alias y huella SHA-256.
2. Al menos **dos copias cifradas** del `.jks` en medios distintos (por ejemplo un USB guardado fuera de casa y un almacenamiento cifrado). Guarda `sha256sum noblex-release.jks` junto a cada copia.
3. **Prueba de restauración**: copia el respaldo a una carpeta temporal y ejecuta `keytool -list -keystore copia.jks` para confirmar que abre y que la huella coincide. Borra la copia temporal.
4. Nunca lo subas a git (`*.jks` y `*.keystore` están en `.gitignore`), a chats ni a tickets.

No publiques ningún release hasta completar los pasos 1–3.

## 3. Huella del certificado

```bash
keytool -list -v -keystore noblex-release.jks -alias noblex-release | grep "SHA256:"
```

Guárdala: se fija en GitHub como variable (`RELEASE_CERT_SHA256`) y el workflow rechaza cualquier APK firmado con otra clave.

## 4. Configurar GitHub

```bash
base64 -w0 noblex-release.jks | gh secret set ANDROID_KEYSTORE_BASE64 -R AlanDi123/noblex-launcher
gh secret set ANDROID_KEYSTORE_PASSWORD -R AlanDi123/noblex-launcher   # pega la contraseña cuando la pida
gh secret set ANDROID_KEY_ALIAS -R AlanDi123/noblex-launcher --body noblex-release
gh secret set ANDROID_KEY_PASSWORD -R AlanDi123/noblex-launcher        # misma contraseña
gh variable set RELEASE_CERT_SHA256 -R AlanDi123/noblex-launcher --body "<huella sin ':' en minúsculas>"
```

Recomendado: restringe el workflow de release a tags protegidos o a un *environment* con aprobación manual.

## 5. Verificación de un APK firmado

```bash
scripts/verify_apk.sh noblex-launcher-X.Y.Z.apk <huella-sha256>
```

Comprueba firma v2/v3 válida, huella fijada, `applicationId`, y que `versionCode` coincide con `versionName`. El updater de la app además exige en el TV que el certificado del APK descargado coincida con el de la app instalada.

## 6. Rotación y límites

- Android admite rotación mediante APK Signature Scheme v3 (*proof-of-rotation*, `apksigner rotate`), pero solo los dispositivos con Android 9+ la aceptan y la cadena debe prepararse con la clave **antigua**. Sin la clave antigua no hay rotación posible.
- Una clave comprometida o perdida obliga a publicar otro `applicationId` o a desinstalar la app existente.
- Mientras no exista una cadena de rotación probada, el updater de la app rechaza certificados distintos (comportamiento intencional).

## 7. Claves de prueba

Para probar `assembleRelease` o `apksigner` localmente se usa una clave **desechable** en un directorio temporal, que se elimina después. Nunca se reutiliza para publicar.

## 8. Lista de comprobación del propietario (en este orden)

- [ ] 1. Crear la clave en tu equipo con `keytool` (sección 1). La contraseña se escribe solo en el terminal, nunca en chats ni en comandos.
- [ ] 2. Guardar contraseña, alias y huella en el gestor de contraseñas.
- [ ] 3. Hacer dos copias cifradas del `.jks` y **probar la restauración** (sección 2).
- [ ] 4. Obtener la huella SHA-256 (sección 3).
- [ ] 5. Configurar los 4 secretos `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` y la variable `RELEASE_CERT_SHA256` (sección 4).
- [ ] 6. Confirmar con `gh secret list` y `gh variable list` que los cinco nombres existen (no muestran valores).
- [ ] 7. Publicar primero una **pre-release** (`vX.Y.Z-rc.N`): el workflow usa `--prerelease` si la etiqueta contiene `-`.
- [ ] 8. Descargar el APK y ejecutar `scripts/verify_apk.sh` (sección 5).

Reglas del workflow de release (revisadas en la Fase 6): permisos `contents: write` solo en ese workflow; el `versionCode` se deriva de la versión SemVer de la etiqueta (siempre creciente al subir la versión); el APK se llama `noblex-launcher-<versión>.apk`; el manifiesto `update-manifest.json` incluye applicationId, versión, versionCode, minSdk, ABIs, URL oficial, SHA-256 y tamaño; el workflow rechaza un APK cuya huella no coincida con `RELEASE_CERT_SHA256`.

Qué falta hoy (bloqueado): la clave permanente, los secretos y la variable. Por eso la prueba de actualización real de extremo a extremo no se ha podido ejecutar. No instales sobre tu app un APK firmado con una clave de prueba: Android rechazaría la actualización o te obligaría a desinstalar.
