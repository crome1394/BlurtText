# Changelog

## 0.1.3

- The name is BlurtText on the phone, in settings, in notifications, and in the browser inbox.
- A like, love, or other reaction is shown on the original message.
- Swipe to delete, or to mark a thread read or unread, once the row has crossed about a quarter of the screen. A read thread offers Mark as unread. An unread thread offers Mark as read.
- New message opens in a card over the screen behind it.
- The computer inbox groups conversations with the same Group conversations setting as the phone.
- The computer address is https://blurttext.lan and the port.
- The install id is app.crome1394.blurttext. Kotlin code stays in app.messages.
- About opens https://github.com/crome1394/BlurtText.
- Footer icons grow with the footer height and sit at the top of the bar.
- A group reply that arrives with only the sender is filed on that person's recent group thread.

## 0.1.2

- The launcher name is BlurtText.
- The conversation list no longer draws a line between threads.
- The line under a name is the latest message. A picture label is used only when that message has no text.
- A group message stays in one thread. A reply goes to everyone in the group, and a reply from one of them comes back to that same thread.

## 0.1.1

- GIF tiles in the computer inbox load through the phone, so the pictures show in the browser.
- A text or picture sent from the computer inbox is saved in the conversation, including a text to your own number.
- The inbox filter has a Contacts heading above people and numbers.
- The composer offers a SIM choice only when more than one SIM is active.
- A new install starts from the current phone layout: Ocean, cyan launcher icon, striped inbox, search at the bottom, left swipe Delete, right swipe Unread, footer height 68, and Archive hidden on the ribbon. Dynamic color is off. The SIM chip and the sent/received line on each message are off.
- About links to the GitHub project, shows the Apache License 2.0, and links to the test APK.

## 0.1.0

- First published source. SMS and MMS, the conversation list, and the computer inbox.
- SMS and MMS through the system telephony provider, with no carrier-specific APN or MMSC.
- `minSdk` 26. Newer-only calls (role manager, carrier MMS limits, animated GIFs, dynamic color, the photo picker) are skipped or replaced on older releases.
- Inbox, conversation, swipe actions, ribbon, footer filter, themes, multi-SIM, scheduled texts, and a LAN computer inbox.
