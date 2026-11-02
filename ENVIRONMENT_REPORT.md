# Informe de entorno

## Computadora de desarrollo

- Sistema: LMDE 6.
- Java: OpenJDK 17.0.21.
- ADB: 1.0.41 (Debian 29.0.6).
- Git: 2.39.5.
- GitHub CLI: 2.23.0; sesión autenticada disponible.
- Gradle y Android SDK platforms/build-tools no estaban instalados al inicio. Se instaló Gradle 8.9 mediante wrapper y Android SDK Platform 35/Build Tools 35.0.0 con herramientas oficiales.
- Espacio libre observado: aproximadamente 52 GB.

## Proyecto y distribución

- No existían código fuente ni repositorio local en `NoblexProject`; esa carpeta contiene el respaldo del TV.
- `https://github.com/AlanDi123/noblex-launcher` se creó como repositorio público y el código fuente se publicó en `main`.
- El nuevo proyecto vive en `~/noblex-launcher`. El respaldo propietario permanece separado.
- No se ha configurado una clave de firma ni secretos de GitHub Actions. Un release estable queda bloqueado hasta la preparación y respaldo seguro de esa identidad.

## Dispositivo

Se detectó un único dispositivo Android TV disponible por ADB. Se instaló solo la variante de depuración (`.debug`) y se comprobó su inicio; Google TV sigue habilitado y como HOME. El informe técnico contiene los datos de plataforma y verificación.
