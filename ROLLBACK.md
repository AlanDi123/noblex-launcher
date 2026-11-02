# Recuperación

El despliegue no desinstala, deshabilita ni reemplaza el launcher original.

Para volver a Google TV, abre Ajustes de Android → Aplicaciones → Aplicaciones predeterminadas → Aplicación de inicio y selecciona Google TV. Si el TV presenta un selector HOME, elige el launcher original.

No uses scripts que borren paquetes o datos. El script de comprobación de rollback previsto para el proyecto debe limitarse a verificar que el launcher original sigue instalado/habilitado y describir la selección manual de HOME; no debe cambiar preferencias del sistema automáticamente.

## Verificación

`scripts/check_rollback.sh` (solo lectura) confirma que Google TV sigue instalado, habilitado y como HOME. Si el launcher falla, HOME siempre puede volver a Google TV con el selector del sistema; desinstalar NOBLEX LAUNCHER (`adb uninstall io.github.alandi123.noblexlauncher`) no afecta a las apps del sistema. Tras desinstalar se pierden sus preferencias (favoritos, categorías, fondo).
