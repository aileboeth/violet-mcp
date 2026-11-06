# Second review (1.1.0-eve → 1.2.0-eve)

**English** | [简体中文](REVIEW_v1.2.zh-CN.md)

Review date: 2026-10-10. The subject is the 1.1.0-eve source in the workspace (`mcp-core`, `app`, `relay`, `tests`).
This document first lists the review findings; section 5 is this round's list of changes.

## 1. Implemented features worth keeping

| Feature | Where | Notes |
|---|---|---|
| Code-level read-only allowlist | `ReadOnlyPolicy`, `ToolRegistry`, `McpProtocol.callTool` | Checked once at registration and again at call time. Names match exactly, with no fallback for variants in case, whitespace, bridge prefixes and so on; batch requests go one by one through the same dispatch entry |
| Write capabilities removed from the source | — | No shell, Shizuku, QuickJS, file writing, clipboard-writing tools, notification tools, or `killBackgroundProcesses`; the static APK check confirms this |
| Bearer authentication | `McpEndpoint`, `Secrets` | The token cannot be turned off, 256 bits, compared in constant time; checked before the request body is read |
| UsageStats sensing tools | `UsageTools`, `UsageAggregator` | Event pairing and foreground logic have unit tests |
| WSS relay and certificate pinning | `RelayClient`, `relay/` | Only wss accepted, SPKI fingerprint pinned, double check with device key and token hash |
| Exponential backoff + jitter, immediate reconnect after a network change | `RelayClient`, `McpServerService` | 1→60 seconds, ×0.8–1.2 |
| `specialUse` foreground service, start on boot | Manifest, `BootReceiver` | — |
| Python MCP SDK 1.8.0 / 1.30.0 compatibility | — | Stateless, returns JSON, notifications get 202 |
| Backup exclusion, cleartext traffic forbidden | Manifest | — |

## 2. Parts of this round's requirements not yet implemented

| Requirement | Current state |
|---|---|
| Second-layer privacy switches for tools (sensitive tools off by default) | Currently all tools are on by default |
| Foreground app returns only minimal fields | Currently returns a whole set of fields such as `explanation`, `screen`, `freshness`; with the screen off or locked it gives the package name of the "last foreground app" |
| Encrypted token storage, migration, revoking credentials | Currently stored in plain text in SharedPreferences (excluded from backups, but readable directly after rooting or in device forensics) |
| Insecure listening configurations refuse to start | The phone's "LAN access" sends the token over plain HTTP on `0.0.0.0`; the relay only prints a WARNING when `-client-listen` is a non-local address |
| Configurable connection timeouts and heartbeat interval | Currently hard-coded |
| Per-stage timing | None |
| Three-state service status (running / restricted by the system / stopped) | Only a `running` boolean; after the system kills the process the UI shows "not running", indistinguishable from "stopped by the user" |
| Diagnostics page, security page, latest connection/call information | None |
| Certificate update process | Only one fingerprint can be entered, so changing the certificate always causes a disconnect |

## 3. Real problems in the code

| # | Severity | Problem | Where |
|---|---|---|---|
| R1 | High | LAN mode sends the Bearer token over plain HTTP, so anyone on the same network segment can sniff it | `McpServerService.restartLocal` |
| R2 | High | The relay still starts with `-client-listen 0.0.0.0:8787` without TLS, sending the token in plain text over the internet | `relay/main.go` |
| R3 | Medium | Token and device key stored in plain text | `Settings.kt` |
| R4 | Medium | After the user taps "Stop", the app-update broadcast (`MY_PACKAGE_REPLACED`) or a `START_STICKY` restart still brings the service back; the "run state the user wants" is not recorded | `BootReceiver`, `McpServerService` |
| R5 | Medium | On Android 12 and above, `startForeground` may throw `ForegroundServiceStartNotAllowedException` (e.g. when the system's sticky restart happens in the background); it is not caught and causes a crash | `McpServerService.enterForeground` |
| R6 | Medium | When two devices (or two instances on one device) use the same credentials, the relay keeps replacing one with the other. The replaced side reconnects with backoff starting at 1 second, so they keep displacing each other in a loop | `RelayClient` |
| R7 | Medium | A certificate fingerprint mismatch is treated as an ordinary network error and retried with backoff from 1 second; the UI does not warn of a possible man-in-the-middle either | `RelayClient.describe` |
| R8 | Medium | Keeps trying to connect with backoff even without a network (needlessly waking the CPU and radio) | `RelayClient` |
| R9 | Medium | When the network keeps flapping, every `reconnectNow` immediately creates a new connection, with no debounce | `RelayClient.reconnectNow` |
| R10 | Medium | The phone cannot see the relay's WebSocket heartbeat (OkHttp does not expose it). With a half-open connection, it can only notice through its own ping every 20 seconds, and that timer pauses with the screen off | `RelayClient` |
| R11 | Medium | The relay's default timeout waiting for the phone is 60 seconds, longer than the Python SDK's default HTTP timeout (30 seconds). With a half-open phone connection, Eve first reports a client timeout and gets no clear reason | `relay` |
| R12 | Medium | Extremely deep JSON nesting (e.g. 100,000 levels of `[`) overflows `JSONTokener`'s stack. `StackOverflowError` is not an `Exception` and is currently not caught | `McpProtocol.handleBody` |
| R13 | Low | Every request calls `Settings.token()` and every log line calls `Settings.secrets()`; after switching to Keystore encryption, each would do a decryption IPC | `McpEndpoint` / `LogStore` |
| R14 | Low | `system_properties` calls the hidden API `android.os.SystemProperties` through reflection | `SystemTools` |
| R15 | Low | When settings change, `RelayClient.stop()` blocks the main thread for up to 3 seconds | `McpServerService.restartRelay` |
| R16 | Low | `AppLabels.homePackages` queries PackageManager on every call | `AppTools` |
| R17 | Low | With "keep running with WakeLock" on, a PARTIAL_WAKE_LOCK is held indefinitely, while handling a single request has no short WakeLock at all | `WakeLockHelper` |

### Side effects of each tool, re-checked

| Tool | System API called | Side effects | Conclusion |
|---|---|---|---|
| `battery` | `registerReceiver(null, BATTERY_CHANGED)` | With a null receiver it only reads the sticky broadcast and registers nothing | Read-only |
| `storage_info` | `StatFs` | None | Read-only |
| `screen_info` | `DisplayManager`, `Settings.System.getInt` | None | Read-only |
| `device_info`, `locale_info`, `time_now` | `Build`, `Locale`, `TimeZone` | None | Read-only |
| `system_properties` | Reflective `SystemProperties.get` | No writes, but uses a non-SDK interface | Read-only; changed this round to use only the public `Build` API |
| `app_info`, `installed_apps` | `PackageManager` query methods | None (the app name cache lives only in process memory) | Read-only |
| `get_foreground_app`, `get_app_usage_stats` | `UsageStatsManager.queryEvents` / `queryAndAggregateUsageStats` | None | Read-only; but it can see the full usage history, so it needs a privacy switch |
| `get_screen_state` | `PowerManager`, `KeyguardManager`, `DisplayManager` | None | Read-only |
| `health`, `permission_state` | AppOps checks, PowerManager | None | Read-only |

### Non-tool entry points

| Entry | Conclusion |
|---|---|
| `/health`, `/status` | Read-only, no secrets |
| Relay frames | Only four kinds are handled: `hello_ack`, `ping`, `http_request`, `error`; there are no configuration frames at all |
| Exported components | Only `MainActivity` (the launcher entry, reads no intent extras) and `BootReceiver` (responds only to protected system broadcasts). The service is not exported; the PendingIntent in the notification is immutable |

## 4. What cannot be verified in the current environment

No Android device is connected to this machine and hardware virtualisation is not enabled, so an accelerated emulator cannot run. The following can only be listed as "not verified":

- long-term background survival, Doze, screen-off networking, start on boot and FGS start restrictions on real vendor systems
- switching between Wi-Fi and mobile data, recovery after the phone loses its network
- real system behaviour after usage access is revoked (Robolectric can only simulate AppOps return values)
- Android Keystore encryption and decryption on a real device (Robolectric has no AndroidKeyStore provider, so only the migration logic and failure branches can be tested)

## 5. This round's change plan (by priority)

1. **Security**:
   - Remove plain-text LAN listening; `LocalHttpServer` throws on a non-loopback address and refuses to start.
   - The relay refuses to start on "a non-loopback address without TLS".
   - Fix R12's deeply nested JSON.
   - `system_properties` uses only public APIs.
2. **Privacy**:
   - Two layers of tool switches; only 5 tools on by default, sensitive tools authorised separately.
   - `get_foreground_app` returns only minimal fields by default and distinguishes the states current / screen_off / locked / unknown / permission_denied.
   - Switches can only be changed in the app's UI, never by MCP requests.
3. **Credentials**:
   - Encrypt the token and device key with Android Keystore AES-GCM.
   - Migrate safely from plain text: encrypt first and verify the original value decrypts, only then delete the plain text; if any step fails, keep the original value.
   - When the key is lost or corrupt, refuse authentication and tell the user, never silently generating a new token.
   - Provide three operations: rotate, revoke remote credentials, clear all authentication configuration.
4. **Relay**:
   - Pause reconnecting without a network; reconnect right away after a debounce when the network returns.
   - On being replaced (4000) and on a certificate fingerprint mismatch, go into a long backoff and say so clearly in the UI.
   - Support two certificate fingerprints, for a smooth transition when changing certificates.
   - Make the heartbeat an application-level ping started by the relay, with a watchdog on the phone.
   - Configurable timeout parameters; the relay's request timeout defaults to 20 seconds; when the device is briefly offline, wait at most 5 seconds before returning 503.
5. **Background running**:
   - Record the run state the user wants, fixing R4.
   - Catch foreground service start failures (R5) and show a notice.
   - The UI shows the three-state service status and detects background restrictions.
   - Hold a short WakeLock only while handling a single request.
6. **Performance**:
   - Both the relay and the phone record per-stage timings with a monotonic clock, output through the `Server-Timing` response header and logs.
   - Adjustable log level.
   - Cache the token, launcher package names and so on, to avoid repeated queries per request.
7. **UI**:
   - Add status information to the home screen.
   - Add a permissions page (tool switches), a diagnostics page and a security page.
8. **Tests and documentation**:
   - Add the matching unit tests, new Robolectric (simulated environment) tests, and extend the Python end-to-end tests.
   - Update the documentation, adding upgrade and rollback instructions.
