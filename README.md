# BlurtText

Android SMS and MMS app. It is the phone's default texting app, and a computer on the same Wi-Fi can read and send through it. No account. Nothing is uploaded.

Open it if you want one messenger for texts, pictures, GIFs, and a browser inbox, without a carrier app in the middle.

Source: [github.com/crome1394/BlurtText](https://github.com/crome1394/BlurtText). A test build is on the [latest GitHub release](https://github.com/crome1394/BlurtText/releases/latest). That file is a debug APK for sideloading.

- SMS, multipart SMS, and MMS (pictures, GIFs, video, voice) through Android's telephony provider
- Swipe a conversation to mark it read or unread, or to delete it. A read thread offers Mark as unread. An unread thread offers Mark as read. A delete can be undone from Trash.
- Several color palettes, light and dark, and a text-size control
- Multi-SIM send, scheduled texts, block list, and a keyword filter
- Computer inbox at `https://blurttext.lan:8743` on your LAN, including GIF search when a KLIPY key is saved on the phone. The numeric address on the phone still works.
- Apache-2.0

## What you need

- Android 8.0 or newer (API 26). The install id is `app.crome1394.blurttext`. Kotlin sources stay in the `app.messages` package.
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

The computer inbox is part of BlurtText. Its sources are read from `../lantext/app/src/main`, checked out next to this project. They are not copied into this git repo.

```bash
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Default SMS app

```bash
adb shell cmd role add-role-holder android.app.role.SMS app.crome1394.blurttext
```

Give the role back to Fossify Messages:

```bash
adb shell cmd role add-role-holder android.app.role.SMS org.fossify.messages
```

On Android 8 and 9, set the default from the system SMS-app screen. `cmd role` is for Android 10 and newer.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
