# Actualizaciones

Las versiones se distribuyen mediante GitHub Releases. La app consulta la publicación más reciente sin autenticación, valida un manifiesto adjunto y comprueba la identidad de aplicación, la versión, el Android mínimo, la arquitectura, la URL HTTPS de la publicación y el SHA-256 del APK antes de abrir PackageInstaller.

Android no permite asumir instalación silenciosa. El usuario debe autorizar instalaciones de fuentes desconocidas cuando corresponda y confirmar cada instalación en el diálogo del sistema.

Cada release debe adjuntar `update-manifest.json` y un APK firmado con la identidad permanente. El manifiesto de una versión publicada debe contener datos reales de esa publicación; no se deben incluir URLs o checksums ficticios. El cliente no contiene tokens GitHub.

La publicación estable queda bloqueada hasta asegurar y respaldar la clave privada fuera del repositorio. Configure secretos de CI directamente en GitHub; nunca los agregue al código o a archivos versionados.
