# Courier for Android

Courier is an Android client for a certain very familiar blue-bubble ecosystem. It pairs with a Mac-side bridge, keeps a fast local cache of your conversations, and gives you a native Android UI for browsing, reading, reacting, and replying without pretending your phone is something it is not.

## The short version

1. Install the Mac bridge from the [Courier Bridge releases](https://github.com/build-rip/courier-bridge/releases).
2. If you like your sideloading a little more civilized, install [Obtainium](https://github.com/ImranR98/Obtainium/releases) and then [add Courier with Obtainium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium%3A%2F%2Fadd%2Fhttps%3A%2F%2Fgithub.com%2Fbuild-rip%2Fcourier-android).
3. Start the bridge on your Mac and open its local web UI.
4. Pair this Android app by scanning the QR code or entering the host URL and pairing code manually.
5. Courier pulls conversation data from the bridge, stores a local cache on-device, and stays fresh through a live socket connection.

## What the bridge actually does

The bridge is the sneaky part.

On macOS, it combines read access to your Messages database with Accessibility-powered automation of Apple's chat app. That lets it observe conversations, trigger sends, apply reactions, and generally do the polite little bits of desktop puppeteering that a database alone cannot handle.

Once it has that access, the bridge exposes everything as an authenticated API for this Android app:

- HTTP endpoints for pairing, sync, sending, reactions, receipts, and attachments
- a WebSocket stream for live updates
- attachment downloads straight from the Mac to the phone

In other words: your Mac does the awkward backstage work, and this app gets to be the charming front of house.

## How this app works

Courier for Android is a local-first client.

- Pairing stores a bridge host, device identity, refresh token, and short-lived access token in DataStore.
- Chats, messages, reactions, participants, and attachment metadata live in a Room cache database.
- Attachment files are downloaded into app-private storage when needed.
- A foreground service maintains the WebSocket connection so new activity can land quickly.
- On reconnect, the app performs a sync pass to catch up on anything it missed while offline.

The bridge is the source of truth. The Android app is the fast, pleasant, pocketable replica.

## Main pieces

- `MainActivity` starts the app, handles pairing deep links, and boots the sync service.
- `ui/pairing` handles QR scanning and manual bridge setup.
- `ui/chatlist` and `ui/chatdetail` render the conversation UI.
- `data/remote` contains the authenticated API and WebSocket clients.
- `data/local` contains the Room cache.
- `service/WebSocketForegroundService.kt` keeps the bridge connection alive.

## Requirements

- Android 8.0+ (`minSdk 26`)
- a Mac running the Courier Bridge
- the Android device and the Mac bridge reachable over the same network, or otherwise routable
- permissions for notifications, camera (for QR pairing), and optionally contacts enrichment

## Development

Build a debug APK with:

```bash
./gradlew assembleDebug
```

Install it with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Install with Obtainium

If you want release tracking without babysitting GitHub manually, first install [Obtainium](https://github.com/ImranR98/Obtainium/releases), then use this link to add Courier:

- [Add Courier in Obtainium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium%3A%2F%2Fadd%2Fhttps%3A%2F%2Fgithub.com%2Fbuild-rip%2Fcourier-android)

## Notes

- This app talks only to your paired bridge. There is no hosted cloud backend hiding in the curtains.
- If the bridge goes offline, the app keeps its local cache and reconnects when the bridge returns.
- Pairing can also happen through a `courier://pair?...` deep link if the bridge provides one.

If you are here to trace the whole trick from phone to Mac to database to automation and back again: yes, that is exactly the trick.
