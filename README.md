# SPELUNKR for Shield

An Android TV app for the Nvidia Shield that opens straight into [spelunkr.tv](https://spelunkr.tv):
24/7 synchronized channels of old Internet Archive video. Pick the tile on the home screen, a CRT
"switch-on" line flashes, and you're watching whatever is on right now.

It's a thin wrapper: one full-screen WebView on the live site. The site already handles the TV
remote, the schedule and the sync, so site updates show up here automatically.

Package: `com.jameshiggins.spelunkrtv`

## What the app adds

- A home-screen banner and the `LEANBACK_LAUNCHER` entry, so it shows up as a normal TV app.
- Video and sound start without a click first (`mediaPlaybackRequiresUserGesture = false`).
- Keeps the screen awake and hides the system bars.
- **Back** is sent to the page first, as Escape, which the site treats as its own Back (closing the
  guide, menus, the "leave?" card). If the page doesn't use it, the app goes back in history, then exits.
- Leaving the app pauses playback, like switching the TV off.
- With no network you get a **NO SIGNAL** screen with static. It retries every 10 seconds, or right away
  when you press OK.

## Remote

| Shield remote | On the site |
|---|---|
| D-pad | move around / up and down change channel while watching |
| OK | select |
| Back | site Back, then exit |
| Play/Pause | play/pause (when the remote sends it) |

## Build

Needs JDK 17 and the Android SDK (platform 34 + build-tools). `local.properties` points at `C:\Android`.

```powershell
.\gradlew.bat --no-daemon assembleDebug   # → app\build\outputs\apk\debug\app-debug.apk
```

## Install on the Shield

Turn on Developer options → Network debugging on the Shield, then:

```powershell
adb connect 192.168.50.195:5555
adb -s 192.168.50.195:5555 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s 192.168.50.195:5555 shell am start -n com.jameshiggins.spelunkrtv/.MainActivity
```

## Notes

- spelunkr.tv is someone else's site. This wrapper is fine for your own Shield; publishing it to the
  Play Store would need the site owner's OK.
- Some archive.org files may use codecs the Shield's WebView can't play. Most are standard MP4.
