# Desarrollo

- Kotlin y vistas Android nativas; no se añade una capa UI pesada al dispositivo low-RAM.
- SDK mínimo: API 28. Compilación: API 35. Validación primaria: API 30.
- La lista de apps procede de actividades `LEANBACK_LAUNCHER`/`LAUNCHER` visibles para PackageManager; no se codifica un catálogo de paquetes de streaming.
- Preferencias locales mediante SharedPreferences. No hay analytics, publicidad ni servicios cloud.
- Las funciones específicas del fabricante deben usar adaptadores aislados y APIs/actividades verificadas.

Comandos de validación:

```sh
./gradlew testDebugUnitTest lint assembleDebug
```

No versionar `local.properties`, respaldos APK, keystores, contraseñas, tokens ni propiedades locales de firma.
