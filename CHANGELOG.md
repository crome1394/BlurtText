# Changelog

## 0.1.1

- GIF tiles in the computer inbox load through the phone, so the pictures show in the browser.
- A text or picture sent from the computer inbox is saved in the conversation, including a text to your own number.
- The inbox filter has a Contacts heading above people and numbers.
- The composer offers a SIM choice only when more than one SIM is active.
- A new install starts from the current phone layout: Ocean, cyan launcher icon, striped inbox, search at the bottom, left swipe Delete, right swipe Unread, footer height 68, and Archive hidden on the ribbon. Dynamic color is off. The SIM chip and the sent/received line on each message are off.
- About links to the GitHub project, shows the Apache License 2.0, and links to the test APK.

## 0.1.0

- First published source. The launcher name and the notification header are lMessages.
- SMS and MMS through the system telephony provider, with no carrier-specific APN or MMSC.
- `minSdk` 26. Newer-only calls (role manager, carrier MMS limits, animated GIFs, dynamic color, the photo picker) are skipped or replaced on older releases.
- Inbox, conversation, swipe actions, ribbon, footer filter, themes, multi-SIM, scheduled texts, and a LAN computer inbox.
