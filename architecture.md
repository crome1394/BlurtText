# BlurtText architecture

BlurtText is the phone's default SMS and MMS app. The install id is `app.crome1394.blurttext`. Kotlin sources stay in the `app.messages` package. A computer on the same Wi-Fi can read and send through the phone. There is no account, and message bodies are not uploaded.

The launcher name, notifications, computer page, and browser inbox use BlurtText. This install id is separate from an older `app.messages` install, so settings and pairing do not move across. Set this app as the default SMS app after installing it. Source: [GitHub](https://github.com/crome1394/BlurtText).

## Modules

`:app` is the phone UI, the default-SMS role, sending, and receiving.

`:lantext` is the computer inbox inside BlurtText. The Gradle build reads `../lantext/app/src/main` at compile time and does not copy that tree into this git repo. `MessagesApp` extends `LanTextApp`, so the web server, GIF search, and MMS composer live in the same process. The screens and the browser call that inbox BlurtText.

## Screens

Jetpack Compose. `MessagesRoot` shows one main screen at a time. Settings and New message are cards over that screen. The grip on each card changes its height.

| Screen | Role |
| --- | --- |
| Inbox | Conversation list, search, filter, selection ribbon |
| Thread | One conversation and the composer |
| Compose | A new message, including several recipients, in a card over the screen behind it |
| Settings | Appearance, swipe actions, SIM, block list, computer inbox, about, in a card |
| Archive, Blocked, Scheduled | The side lists |

Settings and drafts are in DataStore (`messages-local`). Pins, scheduled sends, and the KLIPY key live there too. Messages themselves stay in Android's telephony provider.

## Sending

`ThreadScreen` and the new-message screen call `MessagesViewModel.send`. A reply inside a thread also asks `addressesFor` for the full recipient list, so a group reply is not cut down to the last sender.

`SendPipeline` then picks the transport:

- One recipient and no attachment: SMS through `SmsManager`, including multipart SMS. The row is inserted first and updated when the sent callback arrives.
- More than one recipient, or any picture, GIF, audio, or video: one MMS. Every recipient is a To address. The carrier MMSC, proxy, and size limit come from the phone. The sent message is stored on the thread for that set of people.

This phone's own number is removed from a group send. A message that has no other recipient, such as a text to yourself, is left unchanged.

## Receiving

SMS arrives at `SmsDeliverReceiver`, is written to the inbox, and posts a notification unless the sender is blocked or the body matches a blocked keyword.

MMS arrives as a WAP push at `MmsWapPushReceiver`:

1. `MmsNotificationParser` reads the download URL and the sender.
2. A placeholder row is inserted.
3. `SmsManager.downloadMultimediaMessage` fetches the PDU.
4. `MmsRetrieveParser` reads the picture or text and every To and Cc address.
5. Those addresses are stored on the message. The message is moved onto the thread for the whole group.

A one-to-one picture lists only this phone besides the sender. That stays a one-person thread. A group lists the other people as well. The thread is that set with this phone removed, so a later reply from any of them is the same set and lands in the same thread.

Some carriers deliver a group reply with only the sender filled in. While that person belongs to one group that has had a message in the last 12 hours, the reply is filed on that group thread. A plain SMS stays on the one-to-one conversation.

On startup, `repairGroupThreads` does the same move for recent messages that already have every recipient stored. It also learns this phone's number from one-to-one messages that name it as the only recipient for two different senders, and from the SIM when Android exposes that number. `READ_PHONE_NUMBERS` is requested for the SIM lookup.

A plain SMS is still a one-to-one conversation. Group replies that the carrier delivers as MMS join the shared thread.

## Conversation list

The list reads `content://mms-sms/conversations`. The line under the name is the newest message. Android keeps an attachment flag on the thread after any picture, and the snippet column is often blank after that, so the flag is not used as the preview. A picture, GIF, video, or audio label is shown only when the newest message has no text.

There is no divider between rows. A contact photo stays in the avatar.

## Computer inbox

The computer inbox is part of BlurtText. It binds on an allowed Wi-Fi network and serves the browser UI, which is also labeled BlurtText. The address is `https://blurttext.lan` and the port (8743 unless it was changed). The numeric address on the phone still works. A new name issues a new certificate, so the browser warning appears again. The inbox list uses the phone's Group conversations setting. Sends from the browser go through the same telephony provider. The phone must be the default SMS app before it can write messages.

## Build

Java 17 and Android SDK platform 36.

```bash
export JAVA_HOME=$HOME/tools/jdk-17
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. `adb install -r` updates this install id. It does not replace an older `app.messages` app.

```bash
adb shell cmd role add-role-holder android.app.role.SMS app.crome1394.blurttext
```
