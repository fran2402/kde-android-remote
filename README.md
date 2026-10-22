# KDE Remote

A TV-style remote for your Linux PC, built for Android with Material 3 Expressive, wallpaper-based
Material You colours and Google Sans Flex (fully rounded). It talks to the PC through
[KDE Connect](https://kdeconnect.kde.org/).

| Control | What the PC receives |
| --- | --- |
| D-pad arrows / OK | Arrow keys / Enter (hold an arrow to repeat) |
| Back | Esc |
| Home | `` ` `` (backtick) |
| Play / pause | MPRIS play/pause on the playing (or first) media player |
| Volume slider / mute | Default audio output volume and mute |
| Power | Your "Suspend" Run Command (see below) |
| Touchpad mode | Pointer movement, tap = click, two-finger tap = right click, two fingers = scroll |
| Keyboard mode | Typed text, Esc, Tab, Backspace, Enter |

## How it connects

The app is its own KDE Connect device: it shows up in KDE Connect on the PC like a phone would,
alongside (not through) the official KDE Connect Android app. It speaks the KDE Connect LAN
protocol (v8, also works with v7 desktops): UDP discovery on port 1716, a TCP connection upgraded
to TLS, then pairing with a verification code.

## Setup

1. Install the APK from the latest **Build APK** workflow run (Actions tab → run → `kde-remote-apk`).
2. Make sure KDE Connect is running on the PC and the phone is on the same Wi-Fi.
3. Open the app, tap **Choose device**, pick your PC and accept the pairing request on the PC.
   If the PC doesn't show up, use **Add device by IP**.
4. On the PC, in KDE Connect settings for this device, make sure **Remote input**,
   **Multimedia control receiver**, **Run commands** and **Remote system volume** are enabled.
   On Wayland, the first key press asks for remote-control permission on the PC.
5. For the power button, add a Run Command in KDE Connect on the PC named **Suspend** with the
   command `systemctl suspend`. Until then the power button shows a **Set up** shortcut that opens
   that settings page on the PC.

## Building

```sh
./gradlew assembleDebug
```

Requires JDK 17+ and the Android SDK (compile SDK 36). Minimum Android version is 8.0 (API 26);
wallpaper colours need Android 12+, older versions use a KDE-blue palette.

Google Sans Flex is licensed under the SIL Open Font License, see `licenses/GoogleSansFlex-OFL.txt`.
