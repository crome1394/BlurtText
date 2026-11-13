# Building and releasing

## Build

Java 17 or newer and Android SDK platform 36.

The computer inbox is the [LanText](https://github.com/crome1394/LanText) project, pinned in the `external/lantext` submodule. A checkout at `../lantext` is used only when you pass `-Plantext.local=true`.

```bash
export ANDROID_HOME=$HOME/Android/Sdk
git submodule update --init
./gradlew :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. It has a different signature from the release build, so remove one before installing the other.

## Default SMS app

BlurtText must be the default SMS app to write the system message store.

```bash
# Android 10 and newer
adb shell cmd role add-role-holder android.app.role.SMS app.crome1394.blurttext
# Give the role back to another app, for example Fossify Messages
adb shell cmd role add-role-holder android.app.role.SMS org.fossify.messages
```

On Android 8 and 9, pick the default from the system SMS-app screen.

## Release

Releases are GitHub Releases with the signed APK attached. F-Droid rebuilds the same tag and ships that APK only if its rebuild matches (see [F-DROID.md](../F-DROID.md)), so every release follows these steps exactly.

| | |
|---|---|
| Tag | `v<versionName>`, e.g. `v0.1.5` |
| Asset | `blurttext-<versionName>.apk` |
| Keystore | `~/.local/share/blurttext/blurttext-release.jks`, alias `blurttext` (never change it) |
| Password | `~/.local/share/blurttext/release-password.txt` |
| Cert SHA-256 | `86553f85b064710091bbf1df7e82a9c90ebb6dba310037bcf902d21440c20ad4` |
| Signer | Build Tools **34** `apksigner`, v2 + v3 only |

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add the notes to [CHANGELOG.md](../CHANGELOG.md) and `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (500 characters max).
3. Commit, tag, and build from a **clean clone** of the tag. A build from your working tree can pick up local files and will not match F-Droid's rebuild.

   ```bash
   git tag v0.1.5
   git clone --recurse-submodules --branch v0.1.5 . /tmp/blurttext-release
   cd /tmp/blurttext-release
   printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
   ./gradlew --no-daemon :app:assembleRelease
   ```

4. Sign. The script refuses a key whose certificate doesn't match.

   ```bash
   sh scripts/sign-release.sh \
     /tmp/blurttext-release/app/build/outputs/apk/release/app-release-unsigned.apk \
     dist/blurttext-0.1.5.apk
   ```

5. Check it the way F-Droid will:

   ```bash
   sh scripts/verify-reproducible.sh v0.1.5 dist/blurttext-0.1.5.apk
   ```

6. Push the tag and publish:

   ```bash
   git push origin main v0.1.5
   gh release create v0.1.5 dist/blurttext-0.1.5.apk --title "BlurtText 0.1.5" --notes-file -
   ```
