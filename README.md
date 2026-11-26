# LAN Remote 0.2
Local Wi-Fi remote for Android TV Remote v2 devices. vSeeBox V2 compatibility is not yet confirmed.

## Run on Android
Install Termux from https://termux.dev/en/ and run:
```sh
pkg update
pkg install nodejs git
git clone https://github.com/willjordan615/LANremote.git
cd LANremote
npm install
npm start
```
Open http://127.0.0.1:8765 on your phone. Keep Termux running. Use the same Wi-Fi as the TV, select a discovered device or enter its private IP, and pair with the displayed code.

The server listens only on loopback. Credentials stay in `.remote-keys.json` and must not be shared. Requires Android TV Remote v2 service (6466/6467); not all Android TV boxes support it. Bluetooth is not included. No hardware testing has been performed. The JavaScript protocol library is @kud/androidtv-remote (MIT). Run `npm run check` for smoke tests.

## Discovery
Refresh searches mDNS TV/cast/HTTP and SSDP advertisements. Scan Wi-Fi checks responding IPs in your own phone's local /24 (up to 254 addresses, no external targets). If the phone address cannot be detected, enter it from Android Wi-Fi settings. Scan only your own network. A ping or advertisement is not proof of Android TV remote support; devices that block ping and do not advertise may still be missing. A network using a non-/24 mask may not be fully represented. On Android, multicast and ping may be restricted. A TV remote label is an advertisement, not a successful pairing. Use the router device list as fallback.

## Bluetooth Android prototype (separate app)
Source in `android/`; GitHub Actions builds a debug APK and publishes it under Releases. Android 9+ and device-side Bluetooth HID support required. Enable Bluetooth remote in the app, make phone discoverable, use a USB mouse on the TV box to open Bluetooth settings and pair the phone. Return to app and choose the paired box if it does not connect automatically. Keep app foreground. Arrow keys and Enter are the first test; other mappings may vary. No internet permission or server required. The native APK does not replace the existing Node Wi-Fi remote. This is experimental and not hardware tested. Debug signing may differ between CI builds, requiring uninstall before installing a later build.

Bluetooth 0.3.1: connection is owned by a connected-device foreground service with a persistent notification and Stop control. It remembers only the last successfully connected host, and attempts to reconnect on return if Android disconnected it. Device-specific background behavior still needs physical testing. A newly signed debug APK may require uninstalling the previous version first; Bluetooth pairing may then need repeating.

## In-app updates (0.3.2+)
The app checks the latest GitHub release on launch and offers a download if its version code is newer. Use Check / install update to install after download. It checks the package name, version and signing certificates; Android still requires installation confirmation. No silent installation. For a stable signing identity, generate a private PKCS12 keystore (alias `lanremote`) and configure Actions secrets `LANREMOTE_SIGNING_KEY` (base64 of keystore) and `LANREMOTE_SIGNING_PASSWORD` (its password). Keep the keystore and password private and backed up; never commit or share them. The same password is used for the key and store. Without both secrets, builds use debug signing and may require manual reinstall. When switching from debug to stable signing, one final manual reinstall is required. Increment versionCode for each update. Hardware and installation flow require device testing.
