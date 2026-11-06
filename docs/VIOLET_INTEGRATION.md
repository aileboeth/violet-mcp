# Connecting Violet: example

**English** | [简体中文](VIOLET_INTEGRATION.zh-CN.md)

Violet's Tools page in the panel can add an MCP server directly (enter the address and the `Authorization` request header, then classify the tools and grant them to agents); it can also be written in `MCP_SERVERS` (a JSON array) in the configuration file. Use `android-phone` as the server name; Violet ships a classification template for these 14 read-only tools.
To Violet this server is just an ordinary Streamable HTTP MCP endpoint, and **Violet's code needs no changes**.

## 1. Configuration

Having the relay and Violet on the same machine is recommended (the relay listens only on `127.0.0.1:8787`):

| Item | Value |
|---|---|
| URL | `http://127.0.0.1:8787/mcp` |
| Transport | Streamable HTTP |
| Request header | `Authorization: Bearer <access token copied from the phone app's Security page>` |

Sketch (**the field names follow Violet's existing schema**; this only shows which three items are needed):

```json
[
  {
    "name": "android-phone",
    "transport": "streamable_http",
    "url": "http://127.0.0.1:8787/mcp",
    "headers": { "Authorization": "Bearer access-token-copied-from-the-phone" }
  }
]
```

When upgrading from 1.1.0 to 1.2.0, neither the token nor the URL changes, so Violet's configuration needs no changes.

## 2. Equivalent Python code (Python MCP SDK 1.x)

```python
import asyncio
from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client
from mcp.shared.exceptions import McpError

URL = "http://127.0.0.1:8787/mcp"
HEADERS = {"Authorization": "Bearer access-token-copied-from-the-phone"}

async def main():
    async with streamablehttp_client(URL, headers=HEADERS) as (read, write, _):
        async with ClientSession(read, write) as s:
            await s.initialize()
            print([t.name for t in (await s.list_tools()).tools])
            try:
                r = await s.call_tool("get_foreground_app", {})
                print(r.structuredContent)  # {"state": "current", "app": "...", "package": "...", ...}
            except McpError as e:
                # Phone briefly offline / timeout: only this call failed, the session can still be used
                print("Device unavailable for now:", e.error.code, e.error.message)

asyncio.run(main())
```

Tested SDK versions: 1.8.0 (negotiated protocol 2024-11-05) and 1.30.0 (2025-11-25).

**Note**: when the relay process is completely unreachable (e.g. the relay on the VPS stopped):

- SDK 1.30.0 raises a connection error within about 3 seconds;
- SDK 1.8.0 does not raise, and waits until the caller's own timeout.

With the old SDK, add an outer timeout: `await asyncio.wait_for(s.initialize(), 15)`, `await asyncio.wait_for(s.call_tool(...), 30)`.

## 3. Return values and errors

| Situation | What Violet gets | Session still usable |
|---|---|---|
| Normal | Tool result (`content` text + `structuredContent`) | Yes |
| Internal tool error, bad arguments, usage access not granted | Tool result with `isError: true`, `errorCode` gives the reason (e.g. `PERMISSION_DENIED`) | Yes |
| The tool was turned off by the user on the phone, or a non-existent tool was called | `McpError -32602 Unknown tool` | Yes |
| Phone offline (still not connected after `-offline-wait`) | `McpError -32010 Device offline` | Yes |
| The phone did not answer within `-request-timeout` (default 20 seconds) | `McpError -32011 Device did not respond within 20s`; the relay also probes the connection and drops it if dead, so the phone reconnects | Yes |
| The phone disconnected mid-request | `McpError -32012` | Yes |
| Wrong token | HTTP 401; the SDK raises an exception and ends the session | No (a configuration error) |
| The relay process is not running | Connection refused; the SDK raises an exception | No; Violet should re-establish the session on the next call |

`error.data.retryable` is `true` for `-32010`, `-32011` and `-32012`.

## 4. Reading the foreground app

By default `get_foreground_app` returns only:

```json
{"state": "current", "app": "WeChat", "package": "com.tencent.mm", "observed_at": "2026-10-10T10:02:11.000+08:00", "age_seconds": 75}
```

(`app` is the app's label as shown on the phone, so it is in the phone's language.)

| state | Meaning | app / package |
|---|---|---|
| `current` | Screen on, unlocked, and no leave event since the app came to the foreground | Given |
| `screen_off` | Screen off | null |
| `locked` | Sitting on the lock screen | null |
| `unknown` | Cannot be determined (e.g. the app left the foreground, the notification shade is open, or no recent events) | null |
| `permission_denied` | The phone has not granted usage access | null |

`observed_at` is the time of the latest system event supporting the conclusion. In the `current` state it is when the app came to the foreground, and a large `age_seconds` only means the user has stayed in that app for a long time.
If the user turned on "Foreground app: detailed mode" on the phone, non-current states also return `last_app`, `last_package` and `reason`.

## 5. Finding latency

The relay adds a `Server-Timing` header to every response and logs the same values (milliseconds, monotonic clock):

```
Server-Timing: relay;dur=12.4, wait_device;dur=0.0, rtt;dur=11.9, phone;dur=3.1, tool;dur=2.0
```

| Field | Meaning |
|---|---|
| `relay` | Total time from the request entering the relay to the response being ready |
| `wait_device` | Time spent waiting for the phone to come online (0 when the phone is online) |
| `rtt` | From the relay sending to the phone to receiving the phone's response (including the network round trip) |
| `phone` | From the phone receiving the request to sending its response |
| `tool` | The tool execution itself |

On the relay: `journalctl -u violet-mcp-relay | grep "client POST"`; each line has `total=`, `rtt=`, `phone=`, `tool=`.
If one Violet task takes a long time (e.g. the roughly 198 seconds observed earlier) while `total` here is only tens of milliseconds, the bottleneck is not in the phone MCP link.
