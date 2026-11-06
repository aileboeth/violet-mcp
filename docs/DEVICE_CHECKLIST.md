# Real-device verification checklist (Violet MCP 1.4.0; checklist written for 1.2.2-eve)

**English** | [简体中文](DEVICE_CHECKLIST.zh-CN.md)

> **1.4.0**: adds in-app updates, see section U; adds the permission `REQUEST_INSTALL_PACKAGES` (the permission count in B3 becomes 10). 1.3.0 → 1.4.0 installs directly over the old version.

> **1.3.0 (renamed Violet MCP)**: the package name changed to `com.mcp.server.violet`, the app name to "Violet MCP", and the relay was renamed `violet-mcp-relay` 1.2.0. The system treats it as a new app: it cannot be installed over the old `com.mcp.server.eve`; it needs a fresh install, the relay address / device key / certificate fingerprint entered again, and Violet switched to the new token (the old app can be uninstalled). So **section A (upgrading in place) does not apply to 1.3.0**; follow the other sections, replacing `PKG` and the APK path below with:
>
> ```bash
> PKG=com.mcp.server.violet
> $ADB install dist/v1.3.0/violet-mcp-1.3.0-release.apk
> ```
>
> The text below keeps the wording of 1.2.2 (old package name, old file names).

> None of the items below **have been carried out on a real device yet** (the development environment has no Android device connected and cannot run an accelerated emulator).
> Automated tests on a PC and Robolectric simulation can only show the logic is right; they cannot replace background behaviour on a real device.
> After carrying out each item, change the "Result" column from "not verified" to the actual result (pass / fail + what happened), and record the model and system version (do not put them in a public report).

## Preparation

```bash
# On the computer (Windows Git Bash; adjust the adb path)
ADB=D:/android-sdk-public-build/platform-tools/adb.exe
PKG=com.mcp.server.eve
$ADB devices                              # check that the phone is connected and USB debugging is authorised
$ADB install -r dist/v1.2.2-eve/eve-readonly-mcp-1.2.2-eve-release.apk
```

On the phone:

1. Open the app → Settings → enter the relay address, device key and certificate fingerprint → Save and apply → turn on the relay.
2. Settings → Permissions → grant "Usage access".
3. Tap "Start" on the home screen.

On the VPS, use the relay's own health check to confirm the phone is connected:

```bash
curl -s http://127.0.0.1:8787/health; echo      # deviceConnected should be true
```

Many later steps need "one call through the relay". First run the definition below once on the VPS (change `TOKEN` to the token copied from the phone's "Security" page), then call with `call TOOL_NAME`:

```bash
TOKEN='paste the access token'
call() { curl -s -w '\ntook %{time_total}s\n' -D - http://127.0.0.1:8787/mcp -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"$1\",\"arguments\":{}}}" | grep -E 'Server-Timing|"text"|took'; }
call get_screen_state
```

## A. Installing, upgrading and credentials

| # | Step | Expected | Result |
|---|---|---|---|
| A1 | On a phone with 1.1.0 (or 1.2.0) installed and the relay configured, install 1.2.1 over it | Installs; token unchanged (Eve's configuration needs no change); relay configuration kept; the "Security" page shows "stored encrypted with Android Keystore" | Not verified |
| A2 | After upgrading, do nothing and wait about 1 minute | If 1.1.0 had "Start on boot" on and was running, the service should come back by itself (app-update broadcast) | Not verified |
| A3 | Open the app after upgrading a phone that had "LAN access" on in 1.1.0 | A "LAN access has been removed" notice appears; the local endpoint listens only on 127.0.0.1: `$ADB shell ss -ltn \| grep 1145` shows only 127.0.0.1 | Not verified |
| A4 | Security page → Change token | The old token immediately gets 401; the new token works; the relay needs no restart (the phone reconnects automatically and reports the new token hash) | Not verified |
| A5 | Security page → Revoke remote credentials | The relay shows the device offline; the device key and fingerprint on the settings page are cleared | Not verified |
| A6 | `$ADB shell run-as $PKG cat shared_prefs/mcp_server_settings.xml` | The release package is not debuggable, so the command should fail; when testing with a debug package, the file should contain only `token.enc` / `relay_secret.enc`, no plain-text token | Not verified |
| A7 | `$ADB backup` or a system backup | The backup contains no settings (allowBackup=false) | Not verified |
| A8 | On an Android 9+ phone with 1.2.1 installed (signed with the old key), install 1.2.2 (key rotated) over it | Installs, data kept. `$ADB shell dumpsys package $PKG \| grep -A3 -i "signatures\|signing"` shows the new certificate (SHA-256 `d5a5eeec…`) with a rotation history | Not verified |
| A9 | After A8, try to install 1.2.1 over it: `$ADB install -r -d dist/v1.2.2-eve/eve-readonly-mcp-1.2.2-eve-release.apk` | Fails (signature mismatch or version downgrade); 1.2.2 and its data are unaffected | Not verified |
| A10 | Install 1.2.2 over the old version on an Android 8.0 / 8.1 phone (if such a device is available) | Installs, data kept (v2 is still the old certificate). Record: such devices are not protected by the rotation | Not verified |

## B. Read-only and permissions (re-checked on a real device)

| # | Step | Expected | Result |
|---|---|---|---|
| B1 | Run the full SDK test on the VPS: `python tests/interop/test_mcp_interop.py --url http://127.0.0.1:8787/mcp --token <token> --device` | All pass; only 5 tools listed by default | Not verified |
| B2 | Turn on `get_app_usage_stats` on the "Tools & permissions" page, then run B1 again | The tool appears and can be called; after turning it off it disappears from the list immediately, and calling it directly returns -32602 | Not verified |
| B3 | `$ADB shell dumpsys package $PKG \| grep -A20 "requested permissions"` | Only 9 permissions (see TOOLS.md) | Not verified |
| B4 | After calling every tool, check: brightness, volume, Wi-Fi, Bluetooth, notification shade, clipboard | None changed | Not verified |

## C. Phone sensing

| # | Step | Expected | Result |
|---|---|---|---|
| C1 | Unlock and open the Settings app; 3 seconds later `call get_foreground_app` | `state=current`, app is Settings, `age_seconds` only a few seconds | Not verified |
| C2 | Switch to another app; call again 5 seconds later | app becomes the new app | Not verified |
| C3 | Press the power button to turn the screen off, then call `get_foreground_app` and `get_screen_state` | `state=screen_off`, app null; `screenOn=false` | Not verified |
| C4 | Call with the screen on but sitting on the lock screen | `state=locked`, app null | Not verified |
| C5 | Pull down the notification shade, stay 10 seconds, then call | `current` (the previous app) or `unknown`; record the actual value, to judge the vendor system's event behaviour | Not verified |
| C6 | Turn on `get_app_usage_stats`, use two apps for 1–2 minutes each, then call with `range=last_15m` | Lists both apps with their names, with roughly correct durations | Not verified |
| C7 | Revoke "Usage access" in the system settings, then repeat C1 and C6 | The foreground app returns `state=permission_denied`; usage time returns a `PERMISSION_DENIED` error; the app's home screen shows a prompt to grant it | Not verified |
| C8 | Call after restarting the phone and before the first unlock (if reachable through the relay) | The service is not running yet (credential storage is unavailable before the first unlock); this is expected | Not verified |

## D. Background running

| # | Step | Expected | Result |
|---|---|---|---|
| D1 | After starting, press Home, swipe the app away in recent tasks, wait 5 minutes, then `call health` | Still callable; the service notification is still in the notification shade | Not verified |
| D2 | `$ADB shell dumpsys activity services $PKG \| grep -E "isForeground\|types"` | `isForeground=true`, type specialUse | Not verified |
| D3 | Tap "Stop" on the notification, then `$ADB shell am kill $PKG` and wait 2 minutes | The service is not brought back by the system or the program; the app's home screen shows "service stopped" | Not verified |
| D4 | Turn on "Start on boot", start the service, restart the phone and unlock for the first time | The service comes back by itself within 1 minute and the relay connects; deviceConnected is true in `curl /health` | Not verified |
| D5 | Restart the phone after stopping the service | The service does not start by itself (the user stopped it by hand) | Not verified |
| D6 | Stop this app in the system task manager ("Active apps" at the bottom of the notification shade on Android 13+) | The service stops; the app's home screen shows "the service should be running but has been stopped by the system or failed to start", not "running" | Not verified |
| D7 | Open the app after `$ADB shell cmd appops set $PKG RUN_ANY_IN_BACKGROUND ignore` (simulating the user turning on background restriction) | The diagnostics page shows "the system has restricted this app's background activity"; afterwards run `... allow` to restore | Not verified |
| D8 | Open the app after `$ADB shell am set-standby-bucket $PKG restricted` | The home screen shows "restricted"; afterwards run `... active` to restore | Not verified |
| D9 | Call through the relay after 30 minutes with the screen off (no permanent WakeLock) | Returns within `-request-timeout` (default 20 seconds); record the rtt in Server-Timing | Not verified |
| D10 | Force Doze: `$ADB shell dumpsys deviceidle force-idle`, call through the relay 3 times, then run `$ADB shell dumpsys deviceidle unforce` | Returns, or returns a clear timeout / device offline error (never hangs) | Not verified |
| D11 | Record 12 hours with battery stats: `$ADB shell dumpsys batterystats --reset`, then `$ADB shell dumpsys batterystats $PKG` 12 hours later | Record power use and wake-ups; no permanent WakeLock (`Wake lock` shows only the short per-request locks) | Not verified |

## E. Network

| # | Step | Expected | Result |
|---|---|---|---|
| E1 | While connected, turn off Wi-Fi (switching to mobile data) | The app log shows "default network switched" and "connected" again within seconds; calls succeed | Not verified |
| E2 | Switch from mobile data back to Wi-Fi | Same as above | Not verified |
| E3 | Turn on airplane mode for 1 minute | Status shows "waiting for network", with no repeated connection attempts meanwhile; the relay returns `Device offline` (JSON-RPC -32010) | Not verified |
| E4 | Turn off airplane mode | Reconnects immediately once the network is back (without waiting for the backoff to end) | Not verified |
| E5 | Run `systemctl restart eve-mcp-relay` on the VPS | The phone reconnects by itself within 60 seconds | Not verified |
| E6 | Run `systemctl stop eve-mcp-relay` on the VPS, wait 10 minutes, then start it | Meanwhile the phone retries at backoff intervals (at most once every 60 seconds), without frequent reconnects; it recovers by itself after start | Not verified |
| E7 | Make the phone's network "falsely online" (e.g. connected to a Wi-Fi with no internet) | The relay disconnects after the heartbeat times out (default 90 seconds), the phone's watchdog also disconnects and reconnects; Eve's request gets a timeout error within 20 seconds | Not verified |
| E8 | Turn on the relay on two phones at once with the same credentials | The one that connected later wins; the other shows "replaced by another device" and retries at intervals of 2+ minutes, without the two fighting over the connection | Not verified |
| E9 | 20 concurrent calls with the Python SDK on the VPS (part 1 of `tests/interop/test_resilience.py`) | All succeed, with no mixed-up results | Not verified |

## F. Project Eve compatibility (on the server running Eve)

| # | Step | Expected | Result |
|---|---|---|---|
| F1 | Start Eve after configuring `MCP_SERVERS` per [VIOLET_INTEGRATION.md](VIOLET_INTEGRATION.md) (called EVE_INTEGRATION.md at the time) | Eve lists the 5 default tools | Not verified |
| F2 | Have Eve ask "what app am I using right now" | Calls `get_foreground_app` and the answer matches reality; with the screen off it answers "the screen is off" rather than guessing an app | Not verified |
| F3 | Turn on airplane mode on the phone, then have Eve call a tool | Eve gets a "Device offline" error, Eve itself does not crash, and later calls recover | Not verified |
| F4 | Record the duration of one complete Eve task and compare it with `total=`, `rtt=`, `phone=` in the relay log | The phone MCP part (rtt) should be between milliseconds and a few hundred milliseconds; if Eve's total is far larger, the bottleneck is not in the phone MCP | Not verified |

## G. Credential storage and Keystore (new in 1.2.1)

| # | Step | Expected | Result |
|---|---|---|---|
| G1 | Install 1.1.0 → 1.2.1 over it and open the app | No red "credential storage" card on the "Security" page; the token is the same as before the upgrade; the relay connects | Not verified |
| G2 | Repeat G1 with a debug package, then run `$ADB shell run-as $PKG cat shared_prefs/mcp_server_settings.xml` | Only `token.enc` / `relay_secret.enc` are visible, no plain-text `token` / `relay_secret` | Not verified |
| G3 | Interrupted migration: install the debug 1.1.0 and configure it, then write a configuration with only plain text (or force-stop the app on 1.2.1's first start before the token is read: `$ADB shell am force-stop $PKG`), then open the app | Token unchanged; after reading, the plain text is deleted and only `.enc` remains | Not verified |
| G4 | Simulate ciphertext that cannot be decrypted (debug package): `$ADB shell run-as $PKG sh -c "sed -i 's/token.enc\">v1:/token.enc\">v1:AA/' shared_prefs/mcp_server_settings.xml"`, then `$ADB shell am force-stop $PKG` and restart the service | The home screen shows "saved credentials cannot be decrypted"; calls through the relay with the old token return 401; **no plain-text copy is ever used**; it recovers after changing the token on the "Security" page | Not verified |
| G5 | If a device with an unavailable or faulty Keystore is at hand (some custom systems / old models) | The home screen shows "credentials cannot be stored encrypted; remote authentication is disabled"; all requests return 401 until consent; it recovers only after ticking "I understand the risk" to turn on compatibility mode, with a warning shown constantly; turning compatibility mode off deletes the plain text | Not verified (no device available) |
| G6 | Reconfigure after a factory reset or clearing app data | A new token is generated, and the old one stops working | Not verified |

## U. In-app updates (new in 1.4.0)

Install 1.4.0 first; verifying "updating to the next version" has to wait until a newer release is on GitHub (e.g. 1.4.1).

| # | Step | Expected | Result |
|---|---|---|---|
| U1 | Install 1.4.0 over 1.3.0 | Installs; token and relay configuration kept (same package name and signature) | Not verified |
| U2 | Settings → App updates: tap "Check for updates" (the latest release is 1.4.0) | Shows "already up to date" and the check time | Not verified |
| U3 | When "Install unknown apps" is not allowed | The card has "Allow installing updates → Allow", which opens this app's switch in the system | Not verified |
| U4 | With a newer release, open the app in "automatic" mode (or wait for the next check while the service runs) | Downloads and verifies automatically, then shows the system's "Update" confirmation (with the app in the background, a notification that opens it when tapped); after confirming, the update succeeds, token and relay configuration are kept, and the service comes back by itself within 1 minute | Not verified |
| U5 | When the next version after that comes out on the same phone (the app has already installed itself once), on Android 12+ | Record whether the system stops asking and updates directly | Not verified |
| U6 | "Notify only" mode | Only a "new version available" notification, no download; it downloads only after tapping "Download and install" in the settings | Not verified |
| U7 | Open the app in "check manually" mode and wait 6+ hours | It never checks by itself (no access to api.github.com in `$ADB logcat`) | Not verified |
| U8 | Tap "Check for updates" with no network | Shows "network unreachable or blocked", no crash | Not verified |
| U9 | Tap "Download and install" with a debug package (official releases are release-signed) | After downloading it says "the signature differs from this app", does not install, and deletes the package | Not verified |

## H. Vendor systems: focused checks on OriginOS (vivo / iQOO)

> Menu names and locations differ between OriginOS versions; the names below are the common ones, so go by what the phone actually shows, and record the actual path in the "Result" column when it differs.
> Check other vendor systems (HyperOS / MIUI, ColorOS, HarmonyOS, One UI) with the same items, recording where each setting is.

**Preparation**: in the phone manager (i Manager) → App management → Permission management (or the app details), record the **default values** of the following settings for this app before testing:

- auto-start
- associated start
- high background power use / allow background running
- clean up after locking / background freezing
- network permissions (WLAN / mobile data)

| # | Step | Expected | Result |
|---|---|---|---|
| H1 | Start the service with **default settings**, lock the screen, and run `call health` on the VPS after 10 minutes, 30 minutes and 2 hours | Record whether it responds at each point; when it cannot, the app's home screen should show "restricted" or "stopped", not "running" | Not verified |
| H2 | Turn on "allow high background power use" and "auto-start", turn off "clean up after locking", repeat H1 | Still responds after 2 hours. If it is still cleaned up, record exactly when | Not verified |
| H3 | Set "unrestricted battery" (the "Request" button on the app's settings page), repeat H1 | Record the differences from H2 | Not verified |
| H4 | Turn on power saving or super power saving, lock the screen for 30 minutes | Record the state of the service and relay; interruptions are allowed in power saving, but the UI must show the truth | Not verified |
| H5 | Boot recovery: turn on the app's "Start on boot" and the system's "auto-start", restart the phone and unlock | The service comes back and connects to the relay within 1 minute. If the system blocks it, a "failed to start automatically" notification should arrive, with the reason recorded on the diagnostics page | Not verified |
| H6 | Turn on only the app's "Start on boot", **without** the system's "auto-start", restart the phone | Record the actual result (OriginOS may block the boot broadcast). When blocked, the app must not show as running | Not verified |
| H7 | Clean background apps with OriginOS's "one-tap clean / speed-up ball" | Record whether the service survives; after being cleaned, the app's home screen shows "the service should be running but has been stopped by the system" | Not verified |
| H8 | Network switching: Wi-Fi → mobile data → Wi-Fi, and switching to mobile data after turning off this app's mobile data permission in "network management" | With a normal network it reconnects within seconds; when network access is forbidden it shows "waiting for network" or keeps failing to reconnect, with the reason on the diagnostics page, and does not reconnect so often that it drains the battery | Not verified |
| H9 | Switch networks with the screen off (e.g. walking out of Wi-Fi range) | Time needed to reconnect (record the seconds) | Not verified |
| H10 | Foreground app detection: open WeChat, the browser, Settings and the launcher in turn, staying 10 seconds each, then `call get_foreground_app` | `state=current` each time with the correct `app`; back on the launcher, `package` is the launcher | Not verified |
| H11 | OriginOS-specific screens: the minus-one screen, control centre, notification shade, small-window / floating apps, split screen, atomic widgets, game mode / game assistant | Record the state and app returned on each. Expected: either the correct app or `unknown`, **never a wrong app name** | Not verified |
| H12 | Call `get_foreground_app` and `get_screen_state` with the lock-screen magazine / always-on display (AOD) showing | `screen_off` or `locked`; `displayState` may be DOZE; no app name may be given | Not verified |
| H13 | Open a cloned WeChat (app twin) | Record the package and app returned. It may be the same as the main app; note that in the report | Not verified |
| H14 | Usage time: turn on `get_app_usage_stats`, record 1 hour of use, then compare with the system's "screen time / digital wellbeing" | Within 10% for the main apps; record apps with larger differences | Not verified |
| H15 | 12-hour power use: unrestricted battery, no permanent WakeLock, screen off for 12 hours | Record this app's battery percentage and wake-ups (`dumpsys batterystats`), as the basis for adjusting the relay's `-ping-interval` | Not verified |
