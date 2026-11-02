# SYSTEM_AUDIT.md — Noblex / Changhong AI PONT

Read-only inspection performed over wireless ADB (address discovered with `adb devices -l`, never hard-coded).
Nothing was changed on the television during the audit: Wi-Fi, Bluetooth pairings, network settings and system packages were not touched.

## Device

| Item | Value |
|---|---|
| Model | AI_PONT (Noblex / Changhong / Homwee, build ISDB93CH, RTO2.231030.002) |
| Android | 11 (API 30), security patch 2023-11-01 |
| SoC | MediaTek m7322, 32-bit `armeabi-v7a` |
| Low RAM flag | `ro.config.low_ram=true` |
| RAM | ~1.38 GB total, ~455 MB available when idle |
| Storage (`/data`) | 4.38 GB total, 3.07 GB used, ~1.2 GB free |
| Display | 1920x1080, density 320 |
| Features | `leanback`, `leanback_only`, `live_tv`, `hdmi.cec`, `bluetooth`, `bluetooth_le`, `wifi`, `wifi.direct` |
| Packages | 124 installed |

## Connectivity

| Check | Result |
|---|---|
| Wi-Fi hardware / state | Present, enabled, connected (2.4 GHz, strong signal) |
| Default network | `wlan0`, VALIDATED, DNS reachable, `ping google.com` works |
| Bluetooth hardware / state | Present, adapter ON (`BluetoothAdapter.STATE_ON`) |
| Bonded devices | None reported at audit time |

Android restrictions found (documented in the UI, not worked around):

* The Wi-Fi SSID is only available to apps with location permission. The launcher shows only "Conectado · con internet" (from `ConnectivityManager` capabilities) and says so.
* There is **no** `android.settings.BLUETOOTH_SETTINGS` handler on this TV build. The launcher offers the official `com.google.android.intent.action.CONNECT_INPUT` screen ("Agregar accesorio", `AddAccessoryActivity`). It only scans; nothing is paired without the user choosing it. Tested: it opens, and BACK returns to the launcher.
* `android.settings.WIFI_SETTINGS` resolves to `com.android.tv.settings.connectivity.NetworkActivity`. Tested: it opens, and BACK returns to the launcher.

## HOME handlers

1. `com.google.android.tvlauncher` (Google TV) — original default.
2. `io.github.alandi123.noblexlauncher.debug/.LauncherActivity` — this project (debug candidate).
3. `com.android.tv.settings` `FallbackHome`.

`com.google.android.tvlauncher` and `com.google.android.tvrecommendations` remain installed and enabled.

## TV inputs (MediaTek `tv_input`)

| Input | Kind | State at audit |
|---|---|---|
| HW0, HW8 | Tuners | 0 (not connected) |
| HW1 | Composite | 2 |
| HW4, HW5, HW6, HW7 | HDMI | 1 (reported as available/connected by the framework) |

The state numbers come from the framework only. **No real HDMI source was attached during this session**, so no input switching was visually verified (see FINAL_REPORT.md).

## Settings activities available through supported intents

`android.settings.SETTINGS`, `android.settings.HOME_SETTINGS`, `android.settings.WIFI_SETTINGS`, `android.settings.APPLICATION_SETTINGS`-style entry points exposed by `com.android.tv.settings`; `com.android.documentsui` provides the document picker used for backup import/wallpaper selection.
A guessed `SLICE_SETTINGS` URI only showed a spinner and was rejected as unverified.

## Launcher resource use

| Measurement | Value |
|---|---|
| PSS in background | ~37 MB |
| PSS in foreground (earlier measurement) | ~80 MB |

See FINAL_REPORT.md for the final measurement after this phase's changes.
