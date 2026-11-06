# Review of the original project's architecture and security boundaries / change plan

**English** | [简体中文](SECURITY_REVIEW.zh-CN.md)

Subject: <https://github.com/Cazriel/mcp-server> (commit `42113fa`)
Review date: 2026-10-09
Goal: turn it into an **Android read-only MCP server** for Project Eve.

---

## 1. Architecture of the original project

```
MainActivity / SettingsActivity / ToolsActivity / BridgeActivity / AboutActivity   (Compose UI)
        │ startForegroundService
        ▼
McpServerService (foreground service dataSync, START_STICKY) ──► BoreClient (bore.pub plain-text TCP tunnel)
        │                                                    └─► FloatWindowService (overlay window)
        ▼
McpHttpServer (hand-written HTTP/1.1, 0.0.0.0:1145)
   ├─ POST /mcp      → McpHandler (JSON-RPC) → ToolKit → 46 ToolDefinitions
   │                                          └─► BridgeManager (forwards to remote MCP)
   ├─ GET  /sse      (incomplete implementation of the old HTTP+SSE transport)
   ├─ GET  / /status / /info / /meta / /health / /tools   (none authenticated)
BootReceiver (BOOT_COMPLETED → start the service)
```

| Component | Role | Action |
|---|---|---|
| `McpHttpServer` | Streamable HTTP transport | **Kept and rewritten** as `mcp-core/LocalHttpServer` (fixing the defects below) |
| `McpHandler` / `RpcMessage` | JSON-RPC / MCP method dispatch | **Kept and rewritten** as `mcp-core/McpProtocol` |
| `ToolKit` / `ToolDefinition` / `ParamValidator` / `ErrorCodes` | Tool registration, argument validation | **Kept**, moved into `mcp-core`, with a read-only allowlist added |
| `McpServerService` | Foreground service, keep-alive | **Kept**, FGS type changed to `specialUse` |
| `BootReceiver` / `WakeLockHelper` / `LogStore` | Start on boot, wake locks, logs | **Kept** (LogStore gains secret redaction) |
| Read-only tools in `SystemTools` / `AppTools` / `MetaTools` / `UtilityTools` | Device / battery / storage / screen / app information | **Kept** (sensitive fields such as the serial number removed) |
| `FileTools` / `Workspace` | Reading, writing, deleting and changing files, compression, downloads | Removed (writes; reading needs `MANAGE_EXTERNAL_STORAGE`) |
| `ScriptTools` (shell / Shizuku / root / QuickJS) | Arbitrary command and script execution | Removed (including the Shizuku and QuickJS dependencies and provider) |
| `NetTools` | Arbitrary HTTP requests, short links | Removed (usable as an SSRF / data exfiltration channel) |
| `CommunicationTools` | Clipboard read/write, posting notifications | Removed (writes device state; background clipboard reading is unavailable on Android 10+ anyway) |
| `CryptoTools`, `json_format`, `text_convert` | Pure computation tools | Removed (unrelated to phone sensing; the LLM can do these itself) |
| `stop_app` / `running_processes` | Force-stopping apps / process list | Removed (the former changes state; the latter only sees its own process on Android 7+) |
| `batch` / `continue` | Batch calls / batch file reading | Removed (`batch` bypasses the dispatch layer's checks, `continue` can read files at any path, **with no workspace check**) |
| `BridgeManager` / `BridgeActivity` / `BridgeToolsActivity` | Merging and exposing a remote MCP's tools | Removed (remote tool capabilities are not bound by the local read-only constraint) |
| `BoreClient` | bore NAT traversal | Removed, replaced by a TLS relay (see §3) |
| `FloatWindowService` | Overlay window | Removed (needs `SYSTEM_ALERT_WINDOW`, not required for keep-alive) |

The purpose of every removed component was confirmed; no core component of unclear purpose was removed.

## 2. List of security problems (original project)

| # | Severity | Problem | Where |
|---|---|---|---|
| S1 | Critical | **Authentication off by default** (`token_enabled` defaults to `false`), and the service listens on `0.0.0.0`, so anyone on the same network segment can call tools such as shell | `Settings.tokenEnabled` |
| S2 | Critical | **`GET /` and `/status` are unauthenticated and return the token in plain text** (`Net.statusJson` writes `"token"`), so even with authentication on it can simply be read | `Net.statusJson` |
| S3 | Critical | `shell` (app / Shizuku / root), `mcp_javascript`, file deletion/modification, `stop_app` and others can change the device directly | Various tools |
| S4 | High | The `continue` tool does no workspace check and can read any accessible file | `MetaTools.continueBatch` |
| S5 | High | `http_request` / `web_download` can make arbitrary requests from the phone (SSRF, accessing the internal network, exfiltrating data) | `NetTools` / `FileTools` |
| S6 | High | The bore tunnel is **plain-text TCP**, with a randomly assigned public port anyone can connect to; the Bearer token crosses the internet in plain text; the public `bore.pub` service has no authentication | `BoreClient` |
| S7 | High | `/tools` and `/info` are unauthenticated and publish the full tool list | `McpHttpServer` |
| S8 | Medium | The token is only 16 hex characters (64 bits), compared in non-constant time; `md5(token)` is also accepted as an equivalent credential | `McpHttpServer.authorized` |
| S9 | Medium | Bridging can expose any tool of a remote MCP to clients, bypassing the local permission model | `BridgeManager` |
| S10 | Medium | `Access-Control-Allow-Origin: *` without checking `Origin`, a DNS rebinding risk (the MCP spec requires checking Origin) | `McpHttpServer` |
| S11 | Medium | Tool call logs record the first 120 characters of arguments (may contain sensitive data) | `McpServerService` |
| S12 | Medium | `allowBackup=true`, so the token and other configuration go into system/ADB backups | Manifest |
| S13 | Low | `Content-Length` is read in characters rather than bytes, so request bodies containing CJK text hang or are truncated; no request body size limit; unbounded thread pool | `McpHttpServer` |

### MCP protocol compatibility problems (affecting Python MCP SDK 1.x)

| # | Problem |
|---|---|
| P1 | When the client sends `Accept: application/json, text/event-stream` (the Python SDK's default), the server returns SSE **without closing the connection** and generates a new `mcp-session-id` each time; older SDKs wait forever for the stream to end |
| P2 | Notifications (e.g. `notifications/initialized`) get 200 + `{"id":null,...}`, while the spec requires `202 Accepted` with no body |
| P3 | `protocolVersion` echoes the client's version back as is, with no negotiation |
| P4 | Unknown tools return `-32601`; the spec example is `-32602` |
| P5 | `GET /mcp` and `DELETE /mcp` return 404, while the spec requires 405 when unsupported |

### Android platform problems

| # | Problem |
|---|---|
| A1 | `targetSdk 35` + foreground service type `dataSync`: on Android 15 dataSync may run at most 6 hours per 24 hours and is then force-stopped by the system; and Android 15 forbids starting a dataSync foreground service from `BOOT_COMPLETED` → **both start on boot and long-term background running break** |
| A2 | Declares many high-privilege permissions: `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, `READ_PHONE_STATE`, `USE_FULL_SCREEN_INTENT`, plus a Shizuku provider exposed to other apps |
| A3 | `usesCleartextTraffic=true` |

## 3. Remote connection: evaluating bore and the alternative

**Conclusion: the bore approach does not meet the requirements** (S6) — no TLS; the public bore.pub has no authentication; the phone's port is exposed as is on a random public port; the token is off by default.

### Alternative: a TLS WebSocket relay the phone connects out to on a VPS

```
Project Eve (Python MCP SDK)                VPS: eve-mcp-relay (Go)                       Android phone
        │  POST /mcp                                  │                                        │
        │  Authorization: Bearer <MCP token>          │                                        │
        ├────────────────────────────────────────────►│ ① check Bearer: SHA-256, then a        │
        │  (plain text on 127.0.0.1 on the same       │    constant-time comparison with the   │
        │   machine; TLS possible across machines)    │    hash the phone reported             │
        │                                             │ ② wrap into an http_request frame      │
        │                                             │═══════ wss:// (TLS, phone connects out)│
        │                                             │   device auth: Authorization: Bearer   │
        │                                             │   <device secret>; phone can pin cert  │
        │                                             │                                        │ ③ checks the MCP token again locally
        │                                             │                                        │ ④ McpProtocol → read-only tools
        │◄────────────────────────────────────────────┤◄═══════════ http_response frame ═══════│
```

- **No public IP needed on the phone**: the phone opens an outbound `wss://` connection to the VPS, which works behind NAT and on mobile networks; no VPN needed.
- **TLS**: the device channel enforces `wss://` (the app refuses `ws://`). The VPS can use a Let's Encrypt certificate (system CA check + hostname check), or the self-signed certificate the relay generates automatically, with the **SPKI SHA-256 certificate fingerprint** entered on the phone for pinning.
- **Two-factor authentication**:
  - Phone → relay: a separate device secret (the relay stores only its SHA-256).
  - Eve → relay → phone: the MCP Bearer token. The relay blocks unauthenticated requests using the token hash the phone reported (unauthenticated traffic never reaches the phone); the phone checks again in constant time. The relay never stores the plain-text token.
  - When the phone is not connected the relay returns 503, and 401 when unauthenticated; the relay itself has no tools or tool list.
- **Reconnecting after disconnects**: exponential backoff (1 s → 60 s, with jitter); reconnects immediately after Android's default network changes (Wi-Fi ↔ mobile data); WebSocket pings both ways (20 s), and no response in either direction within 60 s counts as disconnected.
- **Logs**: neither the phone nor the relay logs the Authorization header, token, device secret or request bodies; as a safety net, LogStore redacts the current token / secret before writing.
- To Eve the relay is just an ordinary Streamable HTTP MCP endpoint; `MCP_SERVERS` only needs the URL and Authorization header, and **Eve's code needs no changes**.

## 4. Change plan

### 4.1 Module layout

| Module | Contents |
|---|---|
| `mcp-core` (pure Kotlin/JVM, no Android dependency) | HTTP parsing and the local server, MCP/JSON-RPC protocol, authentication, read-only allowlist registry, argument validation, relay client, usage time aggregation. The same code can be tested on a PC with JVM unit tests and the Python SDK directly |
| `app` (Android) | Read-only tool implementations, UsageStats tools, foreground service, start on boot, network change listening, UI |
| `relay` (Go) | The VPS relay `eve-mcp-relay` |
| `tests/interop` (Python) | Compatibility and security tests with Python MCP SDK 1.x |

### 4.2 Read-only allowlist (double restriction)

1. `ReadOnlyPolicy.ALLOWED_TOOLS` is an allowlist hard-coded in the code; `ToolRegistry.register()` **throws right away** on a name outside it (failing at startup rather than registering quietly).
2. Both `tools/list` and `tools/call` look only at this registry; names outside the allowlist (including the original project's `shell`, `write_file` and so on) always return the same `-32602` error as an "unknown tool", revealing nothing about whether the tool ever existed.
3. The source of the write tools and the Shizuku/QuickJS dependencies were removed; tests check that the APK has none of these classes and no `Runtime.exec` / `ProcessBuilder` calls.
4. Every tool carries `annotations.readOnlyHint=true` and `destructiveHint=false`.

### 4.3 New sensing tools (UsageStatsManager)

| Tool | Data source | Freshness rule |
|---|---|---|
| `get_foreground_app` | `queryEvents` (ACTIVITY_RESUMED/PAUSED, SCREEN_*, KEYGUARD_*) + PowerManager / KeyguardManager | Marked `status=current` only for "screen on + unlocked + the app's last event is RESUMED with no PAUSED after it"; otherwise `recent` (last in the foreground, not necessarily now), with the event time and seconds since then |
| `get_app_usage_stats` | Foreground time in the range computed by pairing events (optionally the system aggregate `queryAndAggregateUsageStats`) | Returns the range, query time and earliest available event time; data counts only up to the query moment; a range older than the events the system keeps is marked "data incomplete" |
| `get_screen_state` | PowerManager.isInteractive, Display.state, KeyguardManager | Read in real time (`realtime=true`); with usage access, the time of the latest screen on/off event is included |

The user has to grant "Usage access" (`PACKAGE_USAGE_STATS`, AppOps) by hand in the system settings; no root and no accessibility service are needed.

### 4.4 Permission changes

| Permission | Action | Reason |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Kept | Local server, relay, network change listening |
| `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Kept | Background running, start on boot |
| `FOREGROUND_SERVICE_DATA_SYNC` | Changed to `FOREGROUND_SERVICE_SPECIAL_USE` | See A1 |
| `PACKAGE_USAGE_STATS` | Added | UsageStatsManager (granted by the user by hand) |
| `MANAGE_EXTERNAL_STORAGE`, `READ/WRITE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, `READ_PHONE_STATE`, `USE_FULL_SCREEN_INTENT`, `ACCESS_WIFI_STATE` | Removed | The matching tools were removed; package visibility uses `<queries>` declaring only the launcher intent |
| Shizuku provider | Removed | — |
| `usesCleartextTraffic` / `allowBackup` | Changed to `false` | S12, A3 |

### 4.5 Local HTTP endpoint

- Always requires the Bearer token (cannot be turned off); the token becomes a 256-bit random value; constant-time comparison; the md5 equivalent credential is removed.
- Listens only on `127.0.0.1` by default (usable with `adb forward` for debugging); LAN listening has to be turned on by hand in the settings.
- Any request with an `Origin` header is refused (403), and CORS is removed.
- Only `POST /mcp`, `GET /health` (returns only `{"ok":true}`) and `GET /status` (authenticated, contains no secrets) are kept; `GET/DELETE /mcp` return 405; `/sse`, `/tools` and `/info` are removed.
- Request bodies are read in bytes, up to 1 MiB, request headers up to 16 KiB, with a fixed-size thread pool.
- Stateless Streamable HTTP: requests always get `application/json`, notifications get 202; the protocol version is negotiated from the supported list.
