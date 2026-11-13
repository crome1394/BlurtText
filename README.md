<div align="center">

<img src="fastlane/metadata/android/en-US/images/icon.png" width="96" alt="BlurtText icon">

# BlurtText

An SMS and MMS app for Android with a private computer inbox on your own Wi-Fi.

[![Release](https://img.shields.io/github/v/release/crome1394/blurttext-android?label=release&color=75fbfd)](https://github.com/crome1394/blurttext-android/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/crome1394/blurttext-android/total?color=9ece6a)](https://github.com/crome1394/blurttext-android/releases)
[![F-Droid](https://img.shields.io/badge/F--Droid-coming%20soon-1976d2?logo=fdroid&logoColor=white)](F-DROID.md)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3ddc84?logo=android&logoColor=white)](docs/compatibility.md)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7f52ff?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285f4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![License: Apache 2.0](https://img.shields.io/github/license/crome1394/blurttext-android?color=bb9af7)](LICENSE)

</div>

Set BlurtText as your default texting app, then open `https://blurttext.lan:8743` on a computer on the same Wi-Fi to read and reply from a real keyboard. No account, no cloud, and message bodies never leave your network.

<p align="center">
  <img src="docs/screenshots/inbox.webp" width="200" alt="Inbox">
  <img src="docs/screenshots/thread.webp" width="200" alt="Conversation">
  <img src="docs/screenshots/computer.webp" width="200" alt="Computer inbox pairing">
</p>

## Download

Get `blurttext-<version>.apk` from **[Releases](https://github.com/crome1394/blurttext-android/releases/latest)**, install it, and set BlurtText as the default SMS app. Android 8.0 or newer on a phone with telephony. An F-Droid listing is [in preparation](F-DROID.md).

## Features

| | |
|---|---|
| **Messaging** | SMS, long SMS, and MMS (pictures, GIFs, video, voice) through Android's own message store; no carrier SDK |
| **Groups** | A group stays in one thread, and a reply goes to everyone |
| **Inbox** | Swipe to delete or mark read/unread, with undo from Trash; pin, archive, search, filter |
| **Sending** | Multi-SIM, scheduled texts, attach a photo or your location |
| **Blocking** | Block list and keyword filter |
| **Look** | Nine color palettes, light and dark, launcher icon colors, text size |
| **Computer inbox** | Browser inbox on allowed Wi-Fi networks, paired by PIN or QR; stops when the phone leaves |
| **GIFs** | Optional KLIPY search when you save your own key |

## Screenshots

| New message | Appearance | Light theme |
|:---:|:---:|:---:|
| <img src="docs/screenshots/compose.webp" width="200" alt="New message card with contact suggestion"> | <img src="docs/screenshots/appearance.webp" width="200" alt="Appearance settings with color palettes"> | <img src="docs/screenshots/light.webp" width="200" alt="Inbox in the light Fern palette"> |

Screenshots use made-up contacts and messages on the Android emulator.

## Quick start

1. Install the APK and accept the default-SMS prompt.
2. **Settings → BlurtText**: turn on **Web access** and allow your home Wi-Fi.
3. Open the address shown on the phone in a browser, accept the certificate once, and enter the PIN.

## Documentation

| Doc | What's in it |
|---|---|
| [Compatibility](docs/compatibility.md) | Android versions, per-version features, permissions |
| [Building and releasing](docs/building.md) | Build, default-SMS commands, signed release steps |
| [Architecture](ARCHITECTURE.md) | Modules, screens, sending and receiving, group threads |
| [F-Droid](F-DROID.md) | Reproducible-build setup and submission checklist |
| [Changelog](CHANGELOG.md) | What changed in each release |

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE). The computer inbox is [LanText](https://github.com/crome1394/LanText), included as a submodule.
