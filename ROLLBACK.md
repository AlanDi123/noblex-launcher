# Recuperación

Script verificado en el TV (Google TV debe estar instalado y habilitado; el script se niega si no lo está):

```
ANDROID_SERIAL=<ip:puerto> scripts/tv_home.sh status
ANDROID_SERIAL=<ip:puerto> scripts/tv_home.sh restore-google
```

`set-noblex` guarda antes la salida del resolvedor HOME en `~/NoblexProject/backup/`. No deshabilita ni desinstala nada. Si algo falla, usa el procedimiento manual de abajo.

El despliegue no desinstala, deshabilita ni reemplaza el launcher original.

Para volver a Google TV, abre Ajustes de Android → Aplicaciones → Aplicaciones predeterminadas → Aplicación de inicio y selecciona Google TV. Si el TV presenta un selector HOME, elige el launcher original.

No uses scripts que borren paquetes o datos. El script de comprobación de rollback previsto para el proyecto debe limitarse a verificar que el launcher original sigue instalado/habilitado y describir la selección manual de HOME; no debe cambiar preferencias del sistema automáticamente.

## Verificación

`scripts/check_rollback.sh` (solo lectura) confirma que Google TV sigue instalado, habilitado y como HOME. Si el launcher falla, HOME siempre puede volver a Google TV con el selector del sistema; desinstalar NOBLEX LAUNCHER (`adb uninstall io.github.alandi123.noblexlauncher`) no afecta a las apps del sistema. Tras desinstalar se pierden sus preferencias (favoritos, categorías, fondo).
