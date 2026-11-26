# LAN Remote 0.1
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
