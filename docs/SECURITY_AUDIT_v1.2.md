# Permissions and read-only security audit (1.2.0-eve / relay 1.1.0)

**English** | [简体中文](SECURITY_AUDIT_v1.2.zh-CN.md)

Audit date: 2026-10-10. The first review is in [SECURITY_REVIEW.md](SECURITY_REVIEW.md), this round's review findings in [REVIEW_v1.2.md](REVIEW_v1.2.md).

## 1. Conclusions

| Item | Conclusion | Basis |
|---|---|---|
| Enforced read-only | Pass (automated) | The allowlist is hard-coded; the registry refuses tools outside it; checked again before each call; the APK has no calls for command execution, file writing, force-stopping apps, posting notifications (as a tool) or writing system settings |
| Bypass attempts (aliases, case, batches, duplicate keys, malformed requests, protocol compatibility entries) | Pass (automated) | 46 name variants, batches, duplicate keys, deep nesting, invalid UTF-8, jsonrpc≠2.0 and oversized batches are all refused; the original project's `/sse`, `/tools` and bridges were removed |
| Second-layer privacy switches | Pass (automated + simulated environment) | Only 5 tools on by default; tools that are off are neither listed nor callable; switches can only be changed in the phone's UI |
| Least privilege | Pass (static check) | 9 permissions, all with a purpose; optional permissions are requested only when the user taps a button; only the launcher Activity and the boot broadcast receiver are exported components |
| Listening security | Pass (automated) | The phone's local endpoint can only bind to loopback, and other addresses are refused in code; the relay refuses to start on "a non-loopback address without TLS", and checks again against the actual address after binding |
| Credential storage | Pass (simulated environment), **Keystore on real phones not verified** | AES-256-GCM (Android Keystore); the original value is kept when migration fails; authentication is refused when decryption is impossible, rather than silently resetting |
| Logs | Pass (automated) | No token or device key appears in the relay or harness logs at debug level; phone logs are redacted before writing; exported logs contain no device model or usage records |
| Background running | **Not verified on a real phone** | Only the state machine was verified in a simulated environment (Robolectric); see [DEVICE_CHECKLIST.md](DEVICE_CHECKLIST.md) |

## 2. Every execution entry, checked one by one

| Entry | What it can do | Conclusion |
|---|---|---|
| `POST /mcp` → `McpProtocol` | Only `initialize`, `ping`, `tools/list`, `tools/call`; other methods return -32601 | Read-only |
| `tools/call` → `ToolRegistry.resolve` | Resolves only tools that are allowlisted, registered and turned on by the user; names match exactly | Read-only |
| `GET /status`, `GET /health` | Return the running state, no secrets | Read-only |
| Relay frames (`hello_ack`, `ping`, `http_request`, `error`) | `http_request` is handed to `McpEndpoint` and goes through the same authentication and allowlist; there are no configuration frames | Read-only |
| `MainActivity` (exported, launcher entry) | Reads no intent extras | Cannot be driven by other apps to perform actions |
| `BootReceiver` (exported) | Responds only to `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`, both protected broadcasts other apps cannot fake; the fakeable `QUICKBOOT_POWERON` of 1.1.0 was removed | Can only resume this service |
| `McpServerService` (not exported) | — | Other apps cannot start it |
| The "Stop" button on the notification | An immutable PendingIntent that can only stop this service | — |

### The app's own operations (not MCP tools)

These are all triggered by the user in the app's UI or are needed for the app itself to run, and are not exposed to MCP clients:

- copying the token to the clipboard
- sharing diagnostic logs (system share sheet)
- opening system settings pages (usage access, battery optimisation)
- the foreground service notification
- a reminder notification when start on boot is blocked by the system

## 3. Audit of tool side effects

Besides reading the code tool by tool (see REVIEW_v1.2 §3), `ToolsPolicyTest.everyToolRunsWithoutObservableSideEffects` (simulated environment) turns on all 14 tools and calls each one. Before and after the calls it checks the following, and none changed:

- no Activity or Service was started
- no broadcast was sent
- no notification was posted
- the screen brightness setting was not changed
- no new file appeared in the app's directory

| Tool | Default | Sensitive | Permission needed | Notes |
|---|---|---|---|---|
| `battery` | on | — | None | Reads the sticky battery broadcast (registers no receiver) |
| `get_foreground_app` | on | — | Usage access | By default returns only state / app / package / observed_at / age_seconds; non-current states give no app name |
| `get_screen_state` | on | — | None (usage access optional) | Reads PowerManager, Display and Keyguard in real time |
| `screen_info` | on | — | None | Resolution, refresh rate, brightness setting |
| `health` | on | — | None | Only service state, no secrets |
| `get_app_usage_stats` | off | yes | Usage access | Full usage history |
| `installed_apps` | off | yes | None | List of installed apps |
| `app_info` | off | yes | None | Can probe whether an app is installed |
| `device_info` | off | yes | None | Model, fingerprint |
| `system_properties` | off | yes | None | Now uses only the public `Build` API, no more reflective calls to hidden APIs |
| `permission_state` | off | yes | None | This app's permission configuration |
| `storage_info`, `locale_info`, `time_now` | off | — | None | Low sensitivity, but not in the default-on set |

## 4. Android permissions

| Permission | When needed | When requested |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Relay connection, detecting network changes | At install (normal permissions) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Long-running service | At install |
| `RECEIVE_BOOT_COMPLETED` | Resume after boot (takes effect only after the user turns on "Start on boot") | At install |
| `WAKE_LOCK` | A short WakeLock (≤10 seconds) while handling a single request; a permanent WakeLock is off by default | At install |
| `POST_NOTIFICATIONS` | Showing the foreground service notification (optional) | The first time the user taps "Start" |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | "Unrestricted battery" for this app only (optional); global battery saving does not need to be turned off | When the user taps "Request" on the settings page |
| `PACKAGE_USAGE_STATS` | Foreground app, usage time | Turned on by the user by hand in the system settings (AppOps) |

Permissions not needed: root, Shizuku, accessibility, all-files access, `QUERY_ALL_PACKAGES`, overlay, `WRITE_SETTINGS`, phone, SMS, location, camera, microphone.
`allowBackup=false` and the data extraction rules exclude all data; `usesCleartextTraffic=false`.

### The `specialUse` foreground service

- **Fit**: the app needs to keep a connection to the user's own relay open for a long time, which matches none of the predefined types such as dataSync, mediaPlayback, location or connectedDevice, so it uses `specialUse` and declares the purpose in the manifest (`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`).
- **Platform policy**: distributing through Google Play requires a `specialUse` purpose review; this project is sideloaded.
- **Limits**: since Android 15 `dataSync` is limited to 6 hours a day and cannot start from `BOOT_COMPLETED`; `specialUse` is subject to neither. But that **does not mean it lives forever**:
  - the user can stop the service in the task manager;
  - the system may still reclaim the process under memory pressure;
  - vendor systems may restrict background running or networking;
  - Android 12+ may refuse to start a foreground service from the background.
  These situations show as "restricted" or "stopped" in the UI, never as "connected".

## 5. Credentials

| Credential | Storage | Rotation | Revocation |
|---|---|---|---|
| MCP access token (256 bits) | Keystore AES-GCM; falls back to plain text when Keystore is unavailable, with a notice in the UI | "Change token" on the Security page (the old token stops working immediately; the relay updates the token hash in sync) | "Clear all" on the Security page |
| Relay device key | Same as above | Change device.secret on the VPS, then enter the new value on the phone | "Revoke remote credentials" on the Security page |
| Relay certificate fingerprint | Plain text (public information) | Two fingerprints can be entered at once for a smooth certificate change | Same as above |
| Relay device.secret (VPS) | A file with mode 0600; only SHA-256 is compared | Delete the file and restart the relay | — |

**Migration**: 1.1.0's plain-text token is encrypted on first read and verified to decrypt to the original value, and only then is the plain text deleted; the token value itself does not change, so Project Eve's configuration needs no change.

**Compatibility handling**: (the 1.2.0 description, superseded by 1.2.1) when Keystore encryption failed, 1.2.0 **silently** switched to plain text, and when decryption failed it used a leftover plain-text copy. Both were fixed in 1.2.1 to fail closed, and compatibility mode needs the user's explicit consent; see [SECURITY_RISK_v1.2.1.md](SECURITY_RISK_v1.2.1.md).

## 6. Relay

- The `-client-listen` address must be loopback, or `-client-tls` must be on as well; otherwise it refuses to start. After binding it checks again against the actual address.
- `127.0.0.1:8787` on the VPS is reachable only by processes on that machine. Other local users on the same VPS can connect to the port but still need the token.
- The device entry must use TLS. The phone accepts only `wss://`. On a certificate fingerprint mismatch it goes into a long backoff and shows the fingerprint the server presented for manual checking, and **never accepts an unknown certificate automatically**.
- Only allowlisted request and response headers are forwarded. `Server-Timing` is reassembled by the relay, and non-numeric values are dropped, preventing response header injection.
- When the device is briefly offline, times out or disconnects mid-request, a JSON-RPC error is returned (HTTP 200, with the original request id). That way the Python MCP SDK fails only this one call and does not end the whole session. Authentication failures still return HTTP 401.

## 7. Remaining risks

| Risk | Notes | Mitigation |
|---|---|---|
| Background behaviour on real phones not verified | Vendor systems, Doze, long screen-off periods, network changes, start on boot | Verify item by item on a real phone following [DEVICE_CHECKLIST.md](DEVICE_CHECKLIST.md) |
| Keystore compatibility | Some models may have a faulty Keystore implementation | (Since 1.2.1) remote authentication is disabled with a prominent notice, and compatibility mode is entered only with the user's explicit consent; a failed migration loses no configuration |
| The token is visible after TLS termination | The relay can see the Bearer token Eve sends | The relay is deployed on the user's own VPS and listens only locally |
| The local endpoint has no TLS | Other apps on the same phone can try to connect to `127.0.0.1` | The token is still required; it is a 256-bit random value compared in constant time |
| Eve sessions across a relay restart | In local tests the same session could keep being used after a relay restart; on a real network, the behaviour when the long-lived HTTP connection drops may differ | Eve should re-establish the session on a connection error (see [VIOLET_INTEGRATION.md](VIOLET_INTEGRATION.md), called EVE_INTEGRATION.md at the time) |
