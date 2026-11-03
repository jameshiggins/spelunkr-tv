# SPELUNKR for Shield

An Android TV app for the Nvidia Shield that opens straight into [spelunkr.tv](https://spelunkr.tv):
24/7 synchronized channels of old Internet Archive video. Pick the tile on the home screen, a CRT
"switch-on" line flashes, and you're watching whatever is on right now, full screen, like a '90s
cable box: up/down flips channels through a burst of static, and a channel bar slides in along the
bottom for a few seconds on every change.

It's a thin wrapper: one full-screen WebView on the live site in its **TV mode** (`spelunkr.tv/?tv`).
The site already handles the TV remote, the full-screen picture, the channel bar, the Prevue-style
guide, the schedule and the sync, so site updates show up here automatically.

Package: `com.jameshiggins.spelunkrtv`

## What the app adds

- A home-screen banner and the `LEANBACK_LAUNCHER` entry, so it shows up as a normal TV app.
- Always loads the site with `?tv`, and re-adds it on any navigation that drops it.
- Tunes in by itself on launch: skips the splash / release note / guide and goes to the picture on
  the last channel watched.
- Video and sound start without a click first (`mediaPlaybackRequiresUserGesture = false`).
- Keeps the screen awake and hides the system bars.
- **Back** like a TV remote: while watching it brings up the guide; from the guide it leaves the app.
  Any open menu or dialog gets the Back first, so it closes instead of exiting.
- Leaving the app pauses playback, like switching the TV off.
- With no network you get a **NO SIGNAL** screen with static. It retries every 10 seconds, or right away
  when you press OK.

## Remote

| Shield remote | While watching | In the guide |
|---|---|---|
| Up / Down | channel up / down | move through channels |
| Left / Right | channel bar keys (pause, 15s, guide, spelunk) | move through the grid |
| OK | show the bar / press a key | watch the highlighted channel |
| Back | guide | exit the app |
| Number keys (if your remote has them) | direct-dial a channel | |

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
