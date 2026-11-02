# Fase 4 — Registro del launcher como HOME: causa raíz

Dispositivo: Noblex / Changhong AI PONT, Android TV 11 (API 30), `192.168.0.190:5555` (redescubierto dinámicamente).
Paquete probado: `io.github.alandi123.noblexlauncher.debug`.

## 1. Causa raíz exacta

**NOBLEX ya era un candidato HOME válido. Pierde siempre ante Google TV por una regla estándar de AOSP, no por un error del manifiesto.**

- `PackageManagerService.chooseBestActivity()` (android-11.0.0_r48): si el primer resultado tiene mayor `priority` que el segundo, devuelve ese resultado **sin consultar** actividades preferidas ni el rol (`r0.priority != r1.priority ... return query.get(0)`).
- El filtro HOME de Google TV (`com.google.android.tvlauncher/.MainActivity`) tiene `mPriority=2` (app privilegiada de sistema). NOBLEX tiene `priority=0`.
- `ComponentResolver.adjustPriority()`: las apps no privilegiadas **nunca** pueden definir una prioridad >0 (se fuerza a 0). Por tanto, declarar `android:priority` en NOBLEX no sirve.
- Evidencia en el TV: con `ROLE_HOME` concedido a NOBLEX por el flujo oficial y con la preferencia `mAlways=true` guardada (conjunto completo de 3 candidatos), `resolve-activity` y la tecla INICIO siguen resolviendo `com.google.android.tvlauncher/.MainActivity` (log: `START ... cat=[HOME] cmp=com.google.android.tvlauncher/.MainActivity from uid 0`).
- La ausencia de pantalla «Aplicación de inicio» es un factor aparte (firmware); no es la causa de que INICIO no abra NOBLEX.

Un launcher de usuario solo podría ganar con una prioridad de sistema (instalación privilegiada en la partición del sistema), lo cual está prohibido (sin root, sin tocar particiones). Por ello **no hay corrección segura dentro de una app de usuario**, y no se inventó ningún workaround.

## 2. Manifiesto antes y después

Antes: un único `<intent-filter>` con `MAIN + HOME + DEFAULT + LEANBACK_LAUNCHER` en `LauncherActivity`.

Después (forma estándar de launchers Android TV): `LauncherActivity` con filtro `MAIN + HOME + DEFAULT`, más `<activity-alias android:name=".LeanbackEntry">` (habilitado, exportado, con banner/icono/label) con `MAIN + LEANBACK_LAUNCHER`.
Revisado: el manifiesto fuente, el fusionado debug/release y el instalado coinciden; actividad exportada y habilitada; applicationId `...debug`; sin placeholders ni variantes que la excluyan.

## 3. Consultas finales (APK instalado)

- `query-activities HOME`: 3 → Google TV (prio 2), NOBLEX `LauncherActivity` (prio 0), `FallbackHome` (-1000).
- `query-activities LEANBACK_LAUNCHER`: 21 (las 20 anteriores + `LeanbackEntry` de NOBLEX).
- `resolve-activity HOME`: `com.google.android.tvlauncher/.MainActivity` (prio 2).

## 4. ROLE_HOME

- `isRoleAvailable(ROLE_HOME)`: sí (la pantalla `RequestRoleActivity` de PermissionController se abrió: «¿Quieres establecer a NOBLEX LAUNCHER como tu aplicación de inicio predeterminada?»).
- NOBLEX cumple los requisitos (aparece como opción).
- Titular antes: `com.google.android.tvlauncher`. Tras aceptar el flujo oficial: `io.github.alandi123.noblexlauncher.debug`. Restaurado después a Google TV.

## 5. Comportamiento físico de INICIO

Con el rol concedido y la preferencia guardada, INICIO (tecla enviada por ADB, también desde YouTube TV) abrió Google TV. NOBLEX **no** abre con INICIO. No se probó el mando físico (se usó `KEYCODE_HOME`, que sigue la misma ruta de PhoneWindowManager).

## 6. Reinicio

No se realizó. Dado que la resolución falla antes de consultar preferencias, un reinicio no cambia el resultado y arriesga el enlace ADB inalámbrico. Estado: no probado.

## 7. Archivos modificados

`app/src/main/AndroidManifest.xml`, `HomeRole.kt` (nuevo), `LauncherActivity.kt` (ajuste «Establecer como launcher predeterminado»), y documentación.

## 8. Compilación y pruebas

`testDebugUnitTest`, `assembleDebug` y `lintDebug` correctos (ver DEVELOPMENT_LOG.md).

## 9. ¿Puede NOBLEX ser el launcher predeterminado?

**No** en este firmware sin privilegios de sistema. Google TV sigue siendo el inicio (verificado con `scripts/tv_home.sh status`). NOBLEX se usa desde Aplicaciones.

## 10. Limitación restante

Prioridad 2 de Google TV frente al tope 0 para apps no privilegiadas. Opciones fuera de alcance de esta fase (requieren decisión explícita del usuario y están restringidas por las reglas del proyecto): instalación como app de sistema/privilegiada, o desinstalar/deshabilitar Google TV (prohibido).

## Recuperación

`ANDROID_SERIAL=<ip:puerto> scripts/tv_home.sh restore-google` (verificado en esta fase).
