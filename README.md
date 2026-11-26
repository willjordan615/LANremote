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
