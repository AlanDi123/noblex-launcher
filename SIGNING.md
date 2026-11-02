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
