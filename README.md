# lMessages

Android SMS and MMS app. It is the phone's default texting app, and a computer on the same Wi-Fi can read and send through it. No account. Nothing is uploaded.

Open it if you want one messenger for texts, pictures, GIFs, and a browser inbox, without a carrier app in the middle.

- SMS, multipart SMS, and MMS (pictures, GIFs, video, voice) through Android's telephony provider
- Swipe a conversation to mark it unread or delete it, with undo
- Several color palettes, light and dark, and a text-size control
- Multi-SIM send, scheduled texts, block list, and a keyword filter
- Computer inbox on your LAN, including GIF search when a KLIPY key is saved on the phone
- Apache-2.0

## What you need

- Android 8.0 or newer (API 26). The package id is `app.messages`.
- A phone with telephony. Wi-Fi-only tablets are not a target.
- This app set as the default SMS app, or it cannot write the system message store.
- No carrier SDK. Sending uses `SmsManager` and the MMSC, size limits, and proxy from the phone's own carrier configuration. On Android 8, where that configuration API is missing, MMS falls back to a 300 KB / 1024 px budget.

Layouts are in density-independent pixels. Rows, settings, and the action ribbon scroll instead of assuming a Pixel-sized screen. A narrow phone keeps the footer icons and shortens the unread label. This has been checked on a Pixel 9, not on a separate small device.

A few pieces need a newer release, and older phones keep the rest:

| Piece | Needs |
| --- | --- |
| Animated GIFs in a thread | Android 9. Android 8 shows a still frame. |
| In-app default-SMS prompt | Android 10 uses the system role. Android 8 and 9 use the older default-SMS screen. |
| Dynamic wallpaper color | Android 12 |
| System photo picker | Android 13. Older versions use the document picker. |
| Exact-alarm permission screen | Android 12 |
| Local-network permission for the computer inbox | Android 16 |

## Build

Java 17 and Android SDK platform 36.

The computer-inbox code is the [LanText](https://github.com/crome1394/LanText) project, checked out next to this one. The build reads `../lantext/app/src/main`. It does not copy LanText into git.

```bash
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Default SMS app

```bash
adb shell cmd role add-role-holder android.app.role.SMS app.messages
```

Give the role back to Fossify Messages:

```bash
adb shell cmd role add-role-holder android.app.role.SMS org.fossify.messages
```

On Android 8 and 9, set the default from the system SMS-app screen. `cmd role` is for Android 10 and newer.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
