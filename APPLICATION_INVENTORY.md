# APPLICATION_INVENTORY.md

Method: `adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c <category>` for `LEANBACK_LAUNCHER` and `LAUNCHER`, compared with `AppDiscovery.kt` (union of both categories, one entry per package, leanback entry preferred, the launcher itself excluded). Because the launcher uses exactly that union, every package below appears in NOBLEX LAUNCHER (built-in category "Sistema" holds system packages the user did not assign elsewhere; the launcher additionally shows a few system tools such as `mtktvapi_agent` that only declare a plain LAUNCHER activity).

Launch test: each main activity was started with `am start -n` (the same component the launcher fires), waited 6 s, the focused window was read from `dumpsys window`, then BACK was pressed twice. "Opens" = the app's own window took focus. Playback, logins, purchases and account actions were **not** performed.

| Package | Launch activity | Opens | Returns with BACK x2 | Notes (not launcher bugs) |
|---|---|---|---|---|
| ar.onvideo | telefonica…LauncherActivity | yes | stays in app | app handles BACK itself |
| com.amazon.amazonvideo.livingroom | IgnitionActivity | yes | yes | login/subscription required |
| com.android.documentsui | FilesActivity | yes | yes | |
| com.android.vending | TvMainActivity | yes | yes | |
| com.crunchyroll.crunchyroid | SplashActivity → MainActivity | yes | stays in app | splash first, then main |
| com.directv.dtvlatam | SplashActivity | yes | yes | account required |
| com.disney.disneyplus | MainActivity | yes | stays in app | app handles BACK itself |
| com.google.android.play.games | Pano onboarding | yes | yes | onboarding screen |
| com.google.android.videos | TvHomeActivity | yes | yes | |
| com.google.android.youtube.tv | MainActivity | yes | yes | |
| com.google.android.youtube.tvmusic | resolves into YouTube TV MainActivity | yes | stays in app | OS routes to YouTube TV |
| com.homwee.aipont | MainActivity | yes | yes | manufacturer app |
| com.homwee.chtvmanager | MainActivity | yes | yes | manufacturer app |
| com.homwee.help | MainActivity | yes | yes | manufacturer app |
| com.homwee.mmp.fileexplorer | ChangHongFileExplorerActivity | yes (storage permission prompt first) | stays in app | permission prompt left untouched |
| com.homwee.multiscreenshare | MainActivity | yes | yes | |
| com.mediatek.wwtv.tvcenter | TurnkeyUiMainActivity | yes | stays in app | live TV; exit with HOME |
| com.mstar.android.tv.disclaimercustomization | MainActivity | yes | stays in app | |
| com.netflix.ninja | MainActivity | yes | stays in app | app handles BACK itself |
| com.spotify.tv.android | MainActivity | yes | yes | login required |
| com.wbd.stream | BeamActivity | yes | yes | login required |
| com.mediatek.tv.service (NetflixLauncher), com.mediatek.wwtv.setupwizard (Bluetooth*), com.homwee.aipont ChannelMainActivity | — | not launched | — | internal / setup-wizard entries; deliberately not exercised |

"Stays in app" means two BACK presses did not leave a full-screen app that manages its own back stack; this is app behaviour. The HOME key was not part of this test because Google TV is still the default HOME at this point (see FINAL_REPORT.md for the HOME test).

Result: 21/21 exercised packages opened their own activity; no launcher crash or ANR in logcat. Icons/banners: leanback banners are used when declared, otherwise the app icon is shown inside the card.
