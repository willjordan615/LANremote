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

From 0.3.3, published APK builds require both permanent signing secrets and fail closed if either is missing. The first stable-signed install requires replacing the old debug-signed app; subsequent builds use the same key. Keep the original keystore backed up.

## Native LG TV mode (0.4.0)
Switch between vSeeBox (existing foreground Bluetooth HID service) and LG TV (local Wi-Fi SSAP). Enter the TV private IPv4 address, connect while on the same Wi-Fi, approve the TV certificate on first TLS connection and accept the TV's pairing prompt. TLS certificate is pinned per TV address; changed identities require explicit reset. Older TVs can use an explicitly selected unencrypted legacy mode. Input list, input selection, navigation, audio, channel and power-off commands are implemented. Power-on and direct HDMI-CEC device-specific commands are not implemented; SIMPLINK forwarding is TV-dependent. The TV mode is experimental and requires hardware testing. It does not stop the Bluetooth service when switching tabs.

## Keyboard and mouse buttons (0.4.1)
Bluetooth now advertises a composite keyboard/mouse HID descriptor with unchanged keyboard and media report IDs plus mouse report 3. Back sends a real right click; Esc and left click are available separately. OK still sends Enter. If the box caches the old HID descriptor, remove the phone pairing on the box and phone and pair again. Keyboard opens native text entry; Send types/pastes composed US-layout characters via paced press/release reports. Unsupported characters are rejected before transmission, and the queue is cleared on disconnection. LG mode uses webOS IME insertText, deleteCharacters and sendEnterKey; focus a supported text field first. Live typing is not implemented yet. Hardware verification required.

## Compact remote (0.4.2)
Back restores the original Escape keyboard report; Right click separately sends mouse button 2. OK and left click retain their existing mappings. Both modes use compact 48 dp button rows and collapsible setup, keeping common controls on the main screen. Scrolling remains available for smaller displays, larger fonts and expanded setup. No Bluetooth descriptor or connection changes in this version.

## Quick TV controls (0.4.3)
Both modes have HDMI 1–3 and explicit LG TV power on/off choices. HDMI and power-off use the TV's paired Wi-Fi control session, connecting if needed; Bluetooth is unchanged. Wake-on-LAN requires the TV's active network adapter MAC address saved under LG TV setup, supported networked standby enabled, and a phone on the same subnet. Wake packet transmission is not proof that the TV starts. Backspace, Space, and Tab remain in the vSeeBox keyboard dialog under Keys. Setup moved below the main controls. Hardware testing required.

## Compact icon controls (0.4.4)
The header has a shared TV power icon for both modes. vSeeBox mouse buttons share a rounded two-part control with an inset divider and independent left/right click actions. Both modes use scalable drawn keyboard icons with accessibility labels. TV and Bluetooth status remain near the top. Protocol behavior is unchanged.

## Settings shortcut (0.4.5)
The vacant vSeeBox directional-pad corner now contains a gear icon that expands and scrolls to Bluetooth setup. It remains available while disconnected. The existing Menu command and bottom setup toggle remain unchanged.

## LG settings shortcut (0.4.6)
Tap the gear in vSeeBox mode to switch to LG TV navigation and send MENU to open TV quick settings, connecting first if needed. Hold either mode's gear to open a phone picture-mode selector. Current mode is queried when supported; options are common SDR modes and depend on TV firmware and input. Writes use the direct SSAP settings endpoint only; a rejection or timeout offers normal TV quick settings rather than attempting any permission bypass. Bluetooth setup remains available at the bottom and the connection is not interrupted. The existing vSeeBox Menu command is unchanged. Hardware testing required.

## Shared remote layout (0.4.7)
Both modes now use the same actual controls and group spacing. The selected mode routes navigation, keyboard, volume and media to Bluetooth or LG Wi-Fi. LG mode replaces mouse Left/Right with pointer Click/Exit, Menu with Inputs, and Previous/Next with Rewind/Fast forward. LG Play/Pause offers explicit choices. Power, HDMI and gear always address the LG TV. Gear tap opens native quick settings and hold attempts to open native picture settings; if that launch is rejected or times out, quick settings opens instead. Direct picture-mode writes have been removed. Both connection statuses stay visible. Channel and other TV-specific controls remain in TV setup. Hardware testing required.

## Native dark theme (0.4.8)
Always-dark native Android theme: charcoal background, rounded low-elevation controls, neutral monochrome palette, explicit selected and disabled states, dark app dialogs and system bars. Shared layout and remote protocols unchanged; no extra dependencies. System keyboard and Android installer retain their own themes. Physical visual verification pending.

## Portable setup (0.4.9)
Bluetooth targets are explicitly selected by bonded address, not device name. Known TV/computer classes are prioritized (heuristic only); all paired devices remain available. A saved target is never overwritten by an incoming connection; unselected hosts receive no input. Existing last-host settings migrate as the initial selection. Nicknames are local; Forget target does not unpair Android devices. LG setup no longer defaults to a household address. Find LG TVs sends the standard webOS SSDP search over local Wi-Fi and presents matching response addresses for explicit selection; pairing and certificate approval are unchanged. TV must be on, discovery can fail on isolated Wi-Fi, and manual IP remains supported. No discovery information leaves the local network. Hardware testing needed for multiple Bluetooth hosts and LG discovery.

## Shared Wi-Fi remote (0.5.0)
On a directly connected phone, Bluetooth setup → Share over Wi-Fi creates a private invitation. Copy it securely to another phone, then Join Wi-Fi remote. Guest controls use the same layout and disable direct Bluetooth reconnection until explicitly selecting Use direct Bluetooth. Hosting uses the existing foreground service; stop sharing or change Bluetooth target to revoke the invitation. Hosts bind only to private Wi-Fi IPv4, guests must share its subnet. Commands and replies use AES-GCM with random per-connection challenges, random nonces and direction binding; credentials are randomly generated 256-bit invitation keys, not short PINs. Relay accepts only existing remote commands and validated US keyboard text, uses bounded queues, no cloud service or port forwarding. Invitations are shared access credentials, not individual device approvals; rotating requires stopping and restarting sharing. Invitation includes host IP; if it changes, share again. Automatic host discovery and individual revocation remain follow-up features. No automatic Bluetooth takeover. Physical two-phone verification pending.

## Relay discovery (0.5.1)
While Wi-Fi sharing is enabled, the Bluetooth host advertises a native DNS-SD service on the local network under the selected target name or nickname. Find / join host automatically searches for same-subnet relay hosts, shows name and address, and supports invitation-based fallback. Discovery advertises only a non-secret session identifier derived from the random invitation key, never the key. First-time guests still need the private invitation; returning guests can select a discovered matching session without copying it again. Relay commands still require authenticated encryption. Stopping sharing removes the advertisement and revokes the invitation. Discovery is best-effort on networks that allow mDNS; host IP changes may require restarting sharing. No silent Bluetooth takeover or automatic authorization. Hardware test pending.

## Host approval (0.5.2)
Find / join host now requests authorization from the discovered host. Both screens show a six-digit comparison code derived from ephemeral P-256 ECDH and a committed transcript; the guest confirms the match, the host approves. Only then is the existing random sharing-session invitation encrypted to the guest. Host receives an in-app prompt or notification to open the app; if notifications are disabled, open the host app manually. One request at a time, bounded wait and five-second admission spacing. Manual invitation remains available. Both phones should update; previously saved invitation remains valid for the same sharing session. Stopping sharing revokes all guests together, not individually. This is a new pairing protocol requiring real-device testing, not an independently audited security protocol.

## Automatic LG wake address (0.5.3)
After TV pairing, request network interface MACs and connection state over the paired SSAP session. Choose the active interface matching the TV IP, or an unambiguous connected interface when IP is absent. Do not guess between wired and Wi-Fi MACs. Save the address locally, preserve manual overrides, and hide manual MAC entry under manual power-on setup. Set up TV power on retries learning. Firmware may refuse either request; the TV's mobile wake setting still needs to be enabled and power-on tested. No ARP scraping or unauthenticated fallback.

## Household sharing and lock-screen controls (0.6.0)
Approved guests receive individual credentials saved by target, with a stable sharing identity. Stop sharing pauses access; Revoke all resets all credentials, including manual invitations. Older approval sessions require one new pairing. Manual invitations still share a single credential. Enable checks Wi-Fi hosts before starting Bluetooth; guests never automatically take over Bluetooth and try rediscovering their remembered host after address changes. Direct Bluetooth remains explicit. Optional lock-screen notification opens a restricted remote activity (navigation, volume and playback only), never dismisses the keyguard, never starts Bluetooth, and exposes no settings or credentials. Android lock-screen notification restrictions apply. Device-level integration testing required.

On launch without a ready connection or saved guest, discovery checks for shared hosts without automatically starting Bluetooth. Enable checks again before starting a direct connection. Explicit direct selection bypasses discovery. Credential tests cover distinct host and guest identities, address changes and legacy invitation parsing.

## Power-on diagnostics (0.6.1)
Power on opens a live diagnostic report; TV setup → Power-on diagnostics shows the last completed report per TV and allows copying or running a test. Validates the saved MAC, explicitly binds to local Wi-Fi, checks the configured TV control port before sending three wake packets, then polls that same port for up to 30 seconds. UDP send success is not delivery confirmation; a reachable TCP port is not screen-state verification or authenticated TV identity. No broad network scanning or automatic pairing. Fixes the startup discovery field initialization compile error in the last 0.6.0 commit. Tests validate magic packet bytes and subnet broadcast calculation. Physical TV verification remains necessary.

## Multiple TV wake addresses (0.6.2)
On pairing or power-on setup, store valid wired and wireless addresses separately, even when the active interface cannot be determined. Retain previously learned addresses if a later request is denied or incomplete, and preserve manual overrides. Power on sends three magic packets per unique normalized address, manual first when present, then TV Wi-Fi and Ethernet. Diagnostics list sources and per-address send counts; reachability cannot identify which address woke the TV. This does not query router credentials or infer MACs. Unit tests cover deduplication, ordering, invalid addresses and manual preservation. Older TVs may still require the actual router-reported address manually.

## Manual wake override (0.6.3)
Manual override is exclusive: only the manually configured MAC is sent. Without it, the paired TV's learned Wi-Fi and Ethernet MACs are both used, deduplicated; an older saved automatic address is used only if neither learned address exists. Manual power-on setup offers Use automatic to clear the override without discarding the two learned addresses. The manual field no longer displays a learned address as if manually entered. Tests cover exclusive override, automatic candidates, missing addresses and migration fallback.

## Router MAC guidance (0.6.4)
If TV network requests yield no usable wake address and there is no cached or manual address, TV setup and Power on direct the user to the TV's MAC in their router or mesh app's connected-device list. Enter TV MAC from router opens a guided manual override dialog. Unconfirmed wake attempts also explain this fallback without claiming the MAC is necessarily incorrect. Never ask for the router's own MAC or credentials. Existing automatic/manual selection remains unchanged.

## Explicit remote session (0.6.5)
Start remote session in the normal app while unlocked, then use the device power button to lock it. Android's showWhenLocked activity support may retain the limited remote on wake; no keyguard dismissal, forced wake, screen-on flag, overlay or authentication workaround is used. Session supports box navigation, clicks, menu, volume and playback, and existing LG paired TV navigation, volume, playback, HDMI and power. Saved TV identity and pairing are required; no configuration, trust or pairing dialogs, keyboard, diagnostics or credential changes are exposed in the session. Close exits the session. Notification launch may require unlocking. OEM behavior must be verified on a physical phone.

## Collapsible touchpad (0.6.6)
Touchpad opens and closes in the normal and limited remote-session interfaces, above existing click controls. Single-finger drag sends relative mouse deltas and short tap clicks. Existing Bluetooth HID descriptor is unchanged; encrypted relay adds a bounded three-byte motion command, and LG uses the paired pointer socket. Movement is throttled and stale relay motion is dropped rather than building a queue; both relay phones need the update. LG right button still means Exit, not a native right mouse click. Two-finger scrolling and sensitivity configuration are not included. Physical pointer behavior must be tested.

## Bluetooth mouse delivery diagnostics (0.6.7)
Relative movement shares the HID input queue, with short pacing and no button release frame for relative movement. One pending motion report is allowed; busy motion is dropped rather than replayed late. Bluetooth setup → Mouse diagnostics on the direct host shows Android accepted/rejected/busy counts and reported HID protocol, with a test cursor button. The existing descriptor is unchanged. If reports are accepted but the box ignores movement, re-pairing may refresh a cached descriptor; hardware verification is still required.

## Mouse compatibility and device power (0.6.8)
Bluetooth report 3 now uses conventional four-byte mouse input (buttons, relative X/Y, neutral wheel), including click and release packets. Keyboard/media report IDs and formats unchanged. Re-pairing may be required because the box can cache the descriptor. Diagnostics count click presses and rejection as well as movement. Header power follows selected device: box sends consumer Power (0x30), TV opens existing on/off dialog; hold for TV power in either mode. Box standby wake depends on Bluetooth availability and firmware. The gear no longer switches device modes implicitly; in box mode it sends the existing Menu command, in LG mode quick settings. Holding opens LG picture settings without changing mode. Remote session adds box power. Hardware compatibility is not established by unit tests.

## Box click recovery (0.6.9)
Restore the exact pre-touchpad HID descriptor and original three-byte click press/release format, including GET_REPORT and shutdown releases. Do not enable box movement by default. Bluetooth setup has an explicit experimental box cursor checkbox (both relay host and guest must enable it). Mouse clicks purge pending motion before enqueueing, and continue through the original press/release scheduler. LG pointer stays enabled. If the box cached the 0.6.8 four-byte descriptor, pairing may need refreshing using a working physical remote. Test click functionality before relying on the app in a menu that requires mouse input. No physical validation yet.

## Restore saved connections (0.6.10)
On normal app start, independently retry the saved LG TV pairing and restore the last selected Bluetooth target after Wi-Fi-first discovery returns no host. Saved relay credentials take precedence; an available shared host or a discovery error will not silently start Bluetooth. Automatic Bluetooth starts only with existing permissions and enabled Bluetooth. Explicit Stop pauses automatic box reconnect until Enable, selection or direct/join mode; TV Disconnect pauses automatic TV reconnect until explicit Connect. Store successful LG IP and transport separately, migrate existing saved pairing, and use existing TV key and certificate without new trust dialogs during automatic restore. A TV that is asleep is not powered on automatically. Wake success can restore its connection. No mouse protocol changes in this release. Physical reconnect behavior needs testing.
