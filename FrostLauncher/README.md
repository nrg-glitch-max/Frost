# Frost Launcher

Ad-free, dependency-free Android TV launcher with a frosted-glass look.

- Wi-Fi and Settings buttons in the top bar (Wi-Fi shows Connected / Offline)
- Themes button: 8 wallpapers + 10 color palettes
- Rearrange apps: hold OK (or press Menu) on an app, move with the arrow keys, press OK to place. Order is saved.
- Back never exits the launcher

## Get the APK

**Option A: GitHub (no Android Studio needed)**
1. Create a new GitHub repo and upload everything in this folder (keep the `.github` folder).
2. Open the repo's **Actions** tab, run **Build APK** (it also runs on every push).
3. Download the `FrostLauncher-apk` artifact, unzip it, and you have `app-debug.apk`.

**Option B: Android Studio**
Open this folder, wait for Gradle sync, then Build > Build APK(s).
APK path: `app/build/outputs/apk/debug/app-debug.apk`

## Install on the Moto TV
- Easiest: put the APK on a USB drive or cloud storage and open it with a file manager app on the TV (enable "Install unknown apps" for that app), or
- `adb connect <tv-ip>` then `adb install app-debug.apk`

Frost Launcher will appear in the TV's apps list. Open it from there.

## Make the Home button open it
Android TV doesn't always offer a "default home app" chooser. If Home still opens the stock launcher, disable the stock one over ADB (reversible):

```
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

To undo: `adb shell pm enable com.google.android.tvlauncher`

(On some devices the stock launcher is `com.google.android.leanbacklauncher` or `com.google.android.apps.tv.launcherx` instead.)
