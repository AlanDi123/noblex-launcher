# Plan del proyecto

1. Auditar el entorno, el TV, la resolución efectiva, el launcher HOME, las aplicaciones visibles, las entradas y la integridad del respaldo.
2. Confirmar compatibilidad de Android Views/Compose, APIs de TV, restricciones de HOME y límites de entradas del fabricante.
3. Crear una aplicación Kotlin ligera, independiente del fabricante, con identidad fija `io.github.alandi123.noblexlauncher`.
4. Implementar pantalla principal, descubrimiento de aplicaciones, navegación DPAD, favoritos, categorías, preferencias y fondo local con licencia.
5. Añadir integración de entradas únicamente mediante APIs verificadas y explicar cualquier función privilegiada no disponible.
6. Incorporar comprobación de releases HTTPS, compatibilidad, SHA-256 y flujo PackageInstaller.
7. Ejecutar build, pruebas, lint y validación real en Android TV 11/API 30 sin eliminar ni desactivar componentes del fabricante.
8. Cambiar HOME de forma reversible solo después de validar; conservar instrucciones de recuperación.
9. Publicar releases solo después de proteger y respaldar la identidad de firma permanente.

## Límites

No se modifica firmware, particiones, Verified Boot, SELinux, servicios ni datos de otras aplicaciones. No se deshabilita Google TV ni sus recomendaciones. Un APK de publicación no se considera estable si la clave de firma no tiene respaldo recuperable.
