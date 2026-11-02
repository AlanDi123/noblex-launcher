# REMOTE_CONTROL_TESTS.md

Device: Noblex AI PONT, Android TV 11, debug package `io.github.alandi123.noblexlauncher.debug`, 1920x1080.
Input method: `adb shell input keyevent` (equivalent to the remote's key codes) plus UI Automator dumps to read the focused view; screenshots inspected at 1920x1080. A physical remote was not available to the agent, so physical-remote behaviour is "Not tested" beyond what the same key events cover.

| Button/action | Expected result | Actual result | Status |
|---|---|---|---|
| DPAD_DOWN / DPAD_UP from header and cards | Focus moves between header pills, chips and cards | Focus moved header → chips → cards as expected; first card focused after DOWN | OK |
| DPAD_LEFT / DPAD_RIGHT on chips | Focus walks the chip row, row scrolls | Worked, selection follows focus | OK |
| DPAD_CENTER / ENTER on chip | Selects category | Category changes, tools/inputs sections open | OK |
| Category selectors (Favoritos, Todas, Recientes, Streaming, Películas y TV, Música, Juegos, Entretenimiento, Herramientas, Sistema, Otras) | Show matching apps | Verified Todas, Entretenimiento (empty-state), Sistema, Herramientas, Entradas | OK (others use the same code path) |
| Application card + DPAD_CENTER | Launches app | All 21 launchable packages launched (see APPLICATION_INVENTORY.md) | OK |
| MENU on a card | Context menu | Panel with favorite / category / move / hide | OK |
| Add to favorites | Saved to prefs | `favorites` contains package | OK |
| Remove from favorites | Removed | `favorites` empty | OK |
| Hide application | Disappears, stored | `hidden` contains package | OK |
| Show hidden application | Reappears | Restored from Ajustes → Aplicaciones ocultas | OK |
| Assign category / Move before-after | Persist | Menu entries open; assignment path covered by unit tests; on-device reorder not separately exercised | Partial |
| Create category | New custom category | "ZZPrueba" created via keyboard + ENTER, persisted in prefs | OK |
| Category persistence | Survives force-stop | Still in `custom_categories` after `am force-stop` | OK |
| Rename category | New name saved | Dialog opens (same input dialog as create) | Partial (not completed on device) |
| Delete category | Removed after confirmation | "¿Eliminar «ZZPrueba»?" → removed from prefs; no test categories remain | OK |
| Wi-Fi pill / tool | Status panel + official settings | "Conectado · con internet"; "Abrir ajustes de Wi-Fi" opened `NetworkActivity`; BACK returned to launcher | OK |
| Bluetooth pill / tool | Adapter state + official screen | "Activado"; "Agregar accesorio" opened `AddAccessoryActivity`; BACK returned; nothing paired | OK |
| Ajustes pill | Settings panel | All 7 entries listed and readable | OK |
| Fondo y brillo | Wallpaper menu | Opens; brightness shows current dim | OK |
| Brightness slider | Live percentage | Opens (value 0 %); slider adjust not exercised with ◀ ▶ | Partial |
| Wallpaper picker | Android picker | Opens Files app (documentsui) | OK to open; choosing an image not tested |
| Respaldo → Exportar | JSON written | File created in app folder and `Download/NoblexLauncher` | OK |
| Respaldo → Importar | Restores | Imported the exported file, confirmation shown, returned to launcher | OK |
| TV en vivo / Entradas HDMI | Opens TV app / input list | List ("Entradas de TV") opens | OK (list only) |
| HDMI input switching | Switch to external source | No HDMI source attached; protected video not capturable | Not verified |
| Centro de actualizaciones | Honest status | Reports no public release (no fake update) | OK (from earlier phase, panel unchanged) |
| About / restore instructions | Info panel | Opens | OK |
| BACK from panels | Closes panel | Returns to the launcher | OK |
| BACK on home | Returns to "Todas" | Stays on launcher (never exits HOME) | OK |
| HOME key | Returns to default HOME | Opens the system default (Google TV; see FINAL_REPORT.md) | OK |
| Volume / power | Not intercepted | No handler registered | OK |

Fixes found during these tests: chips had no horizontal padding; empty-category state made the layout jump; the category-name dialog could not be saved with the keyboard (now saves on IME Done / ENTER and DPAD_DOWN from the field reaches the buttons).
