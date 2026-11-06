<div align="center">

# Violet MCP (read-only phone state for Android)

**English** | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Español](README.es.md) | [Français](README.fr.md) | [Deutsch](README.de.md) | [Русский](README.ru.md) | [Português](README.pt-BR.md)

[![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?logo=android&logoColor=white)](#)
[![MCP](https://img.shields.io/badge/MCP-Streamable_HTTP-blue)](https://modelcontextprotocol.io/)

</div>

Violet MCP is a read-only Android [MCP](https://modelcontextprotocol.io/) server for **[Violet](https://github.com/aileboeth/violet-companion)**, a self-hosted AI companion. Once it is on your phone, Violet can know the phone's state within the limits you allow, for example:

- battery, whether the screen is on, the app you are using and how long you have used it today;
- the next alarm, the song that is playing, whether headphones are connected;
- whether you are at home or out, how many steps you walked today, your calendar for today and tomorrow.

With this she understands better what you are doing right now; for example, she can say good morning the first time you unlock your phone in the morning. **It cannot change anything on the phone.**

Based on [Cazriel/mcp-server](https://github.com/Cazriel/mcp-server), with these changes:

- **Kept**: Streamable HTTP, Bearer authentication, running in the background and starting on boot.
- **Removed**: everything that can change the device's state.
- **Added**: read-only tools that sense the phone's state.
- **Replaced**: the original plain-text tunnel became a TLS relay that the phone connects out to, on your own VPS.

Current version: app **Violet MCP 1.4.3** (package `com.mcp.server.violet`), relay **violet-mcp-relay 1.2.0**.

The app's interface is currently in Chinese.

## Features

- **Read-only**:
  - All 21 tools are hard-coded in an allowlist.
  - Command execution, Shizuku, JavaScript, file reading and writing, app control, clipboard, reading notifications and arbitrary HTTP requests have been removed from the source code. They do not appear in `tools/list` and cannot be called.
- **A switch for every tool**:
  - Only 7 non-sensitive tools are on by default: battery, screen state, foreground app, display info, health check, next alarm, headphones / Bluetooth.
  - Usage time, now playing, network and place, steps, calendar, installed apps, device identifiers and others have to be turned on by you on the phone's **Tools & permissions** page. Each tool says what she can know once it is on, and the ones that need a system permission have a **Grant** button.
  - MCP requests cannot change these switches.
- **No guessing**: when the screen is off, the phone is locked or the answer is uncertain, it does not guess which app you are using, and says why.
- **Places only by the names you give**: you can name a Wi-Fi network, e.g. "home" or "office". The Wi-Fi name (SSID) is only compared on the phone and is never sent out.
- **Phone events**:
  - It records unlocks (and how long the screen was off), charging, plugging headphones in or out, and arriving at or leaving a place you named.
  - Violet fetches them once a minute; the relay needs no changes.
- **Remote access without a VPN or public IP**:
  - The phone connects out with `wss://` to the relay on your VPS.
  - Two-factor authentication with a device key and an MCP token, plus certificate pinning (old and new fingerprints can be stored together to make certificate changes easy).
  - Reconnects automatically after a disconnect, and immediately when the network changes.
  - Since 1.4.2 it can connect to up to 3 relay servers at once, each with its own switch.
- **Credential safety**: the token and device key are stored encrypted with the Android Keystore and can be rotated, revoked or wiped.
- **Background running**:
  - Uses a `specialUse` foreground service.
  - The service has three states: running, restricted and stopped. Once you stop it by hand, it is not started again automatically.
- **In-app updates**:
  - **Settings → App updates**: choose "automatic" (default), "notify only" or "check manually".
  - Downloads only from this repository's GitHub Releases, then checks `SHA256SUMS.txt`, the package name, the version number and the signing certificate; if any of them is wrong, nothing is installed.
  - The first time, you need to allow this app to "install unknown apps" in the system settings.
- **No root, no accessibility permission.**

> Used daily on real phones. Each manufacturer limits background apps differently, so after the first install it is worth going through [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.md): unrestricted battery, start on boot, still connected with the screen off.

## Quick start

**1. Install the relay on your server**

- Violet installed with the one-line command (recommended): on the Violet control panel's **Tools & phone** page, click **Install relay**. When it is done, the page lists the relay address, device key and certificate fingerprint to enter on the phone, each with a copy button.
- Otherwise: deploy it by hand following [docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.md).

**2. Install and set up the app on your phone**

1. Install the latest `violet-mcp-<version>-release.apk` from [Releases](https://github.com/aileboeth/violet-mcp/releases/latest) (the app is called "Violet MCP"). Later versions can be updated from inside the app.
2. Open the app → **Settings → Permissions**: allow "usage access", and turning on "unrestricted battery" is recommended too. Then choose the tools to share with Violet on the **Tools & permissions** page.
3. Under **Remote access**, enter the relay address, device key and certificate fingerprint from step 1 and turn the connection on.
4. Go back to the home screen and tap **Start**.

**3. Connect it to Violet**

Copy the access token on the phone's **Security** page and paste it into the **Tools & phone** page of the Violet control panel. That's it. If you deployed the relay by hand, add the MCP server as described in [docs/VIOLET_INTEGRATION.md](docs/VIOLET_INTEGRATION.md).

**Local debugging** (without the relay):

```bash
adb forward tcp:1145 tcp:1145
python tests/interop/test_mcp_interop.py --url http://127.0.0.1:1145/mcp --token <token> --device
```

## Documentation

| Document | Contents |
|---|---|
| [docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.md) | Deploying the VPS relay by hand, phone setup, changing certificates (copy-paste commands) |
| [docs/VIOLET_INTEGRATION.md](docs/VIOLET_INTEGRATION.md) | Connecting to Violet, error codes, finding latency |
| [docs/TOOLS.md](docs/TOOLS.md) | Tool list, default switches, returned fields, required Android permissions |
| [docs/UPGRADE.md](docs/UPGRADE.md) | Upgrading and rolling back (including moving from the old Eve read-only MCP to Violet MCP) |
| [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.md) | Checklist for real phones |
| [docs/HANDOFF.md](docs/HANDOFF.md) | Source handoff, building in a clean environment, signing templates (**do not just zip up the working directory**) |
| [docs/TEST_REPORT.md](docs/TEST_REPORT.md) | Automated test results for each version |
| [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md), [docs/REVIEW_v1.2.md](docs/REVIEW_v1.2.md), [docs/SECURITY_AUDIT_v1.2.md](docs/SECURITY_AUDIT_v1.2.md), [docs/SECURITY_RISK_v1.2.1.md](docs/SECURITY_RISK_v1.2.1.md), [docs/SIGNING_KEY_INCIDENT.md](docs/SIGNING_KEY_INCIDENT.md) | Past security reviews and the signing key rotation record (written before the rename, so they use the version and file names of that time) |

## Architecture

```
┌──────────────── Android phone (Violet MCP, com.mcp.server.violet) ───────┐
│ McpServerService (foreground service specialUse, start on boot,          │
│                   network change listener)                               │
│   ├─ LocalHttpServer  127.0.0.1:1145 (this device only)                  │
│   └─ RelayClient ×1–3 ═══ wss:// outbound (device key + cert pin) ═══╗   │
│            ↓ both paths enter the same                               ║   │
│   McpEndpoint (Bearer auth, rejects requests with Origin)            ║   │
│   McpProtocol (JSON-RPC, stateless) → ToolRegistry (read-only list)  ║   │
│     → UsageTools / SystemTools / AppTools / LifeTools / MetaTools    ║   │
└──────────────────────────────────────────────────────────────────────╫───┘
                                                                       ║
┌──────────────── VPS: violet-mcp-relay (Go) ──────────────────────────╨───┐
│ Device entry (TLS WebSocket, port chosen at install)                     │
│ 127.0.0.1:8787  MCP entry /mcp ◄── Violet (Python MCP SDK)               │
│ Unauthenticated requests get 401 here and are not forwarded;             │
│ the relay itself has no tools                                            │
└──────────────────────────────────────────────────────────────────────────┘
```

| Directory | Contents |
|---|---|
| `mcp-core/` | Pure Kotlin/JVM: MCP protocol, HTTP server, authentication, read-only allowlist, relay client, usage time calculation. Shared by the app and the PC tests |
| `app/` | Android: tool implementations, foreground service, start on boot, UI |
| `relay/` | The Go relay `violet-mcp-relay` and its systemd configuration |
| `tests/` | Python MCP SDK interop / security tests, PC end-to-end scripts, static APK checks |

## Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| `POST` | `/mcp` | Bearer | Streamable HTTP (stateless, responds with `application/json`; notifications get 202) |
| `GET` | `/status` | Bearer | Service status (no secrets) |
| `GET` | `/health` | none | Returns only `{"ok":true}` |
| `GET` / `DELETE` | `/mcp` | Bearer | 405 (no server push stream and no sessions) |

Security limits on the endpoints:

- The token cannot be turned off. It is a 256-bit random value compared in constant time.
- Any request with an `Origin` header gets 403.
- Request body up to 1 MiB, JSON nesting depth up to 64, batch requests up to 32 items.
- The local endpoint can bind only to a loopback address.
- Responses from `/mcp` carry `Server-Timing`, showing how long each stage took.

## Building from source

Requirements: JDK 17, Android SDK (platform 35, build-tools 35), Go 1.23+, Python 3.10+ (for tests).

```bash
# 1. Android SDK path
echo "sdk.dir=/path/to/android-sdk" > local.properties

# 2. Release signing (optional). Templates are in config-templates/; the signing/ directory is
#    not in the repository and not in the handoff package:
#    - your own key: copy config-templates/keystore.properties.example to signing/keystore.properties
#    - official builds (the key was rotated): copy config-templates/rotation.properties.example to
#      signing/rotation.properties; after assembleRelease builds an unsigned APK, run
#      python tools/sign_release.py <apk>
#    With neither, the release APK is unsigned and you can use the debug APK. See docs/HANDOFF.md §4
#
# 3. Unit tests (mcp-core + Robolectric) + APKs
./gradlew :mcp-core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
#    Output: app/build/outputs/apk/{debug,release}/violet-mcp-<version>-<type>.apk

# 4. Relay
cd relay && go test ./... \
  && GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o ../dist/violet-mcp-relay-linux-amd64 . \
  && GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o ../dist/violet-mcp-relay-linux-arm64 .
cd ..

# 5. PC end-to-end test (Go relay ⇄ JVM harness ⇄ Python MCP SDK, no phone needed)
python -m venv .venv && .venv/bin/pip install "mcp>=1.8,<2"
PYTHON=.venv/bin/python bash tests/run_pc_e2e.sh

# 6. Static APK check (permissions, removed components, dangerous APIs)
python tests/verify_apk.py --apk app/build/outputs/apk/debug/violet-mcp-<version>-debug.apk \
  --build-tools /path/to/android-sdk/build-tools/35.0.0
```

Official release packages are made with `tools/package_release.py`, which also checks for leaked secrets.

Windows tip: if your user folder name contains non-ASCII characters, Gradle's test workers may fail to start (`GradleWorkerMain` not found). Set `GRADLE_USER_HOME` to an ASCII-only path, e.g. `set GRADLE_USER_HOME=D:\gradle-home`.

## Credits and license

This project is based on [Cazriel/mcp-server](https://github.com/Cazriel/mcp-server) (GPL-3.0).

Main changes since 2026-10:

- Removed the write tools and their dependencies (Shizuku, QuickJS, DocumentFile, Gson) and the high-privilege permission declarations; the tools became a read-only allowlist.
- Rewrote the HTTP transport and authentication.
- Added tools that sense the phone's state (usage time, alarm, music, headphones, network and place, steps, calendar, phone events).
- Replaced the bore tunnel with a TLS relay, with support for several relays.
- Changed the foreground service type to specialUse.
- Added privacy switches and minimal output, Keystore-encrypted credentials, listener security limits, relay heartbeat / timeouts / offline wait / per-stage timing, three-state service status, diagnostics and security pages, and in-app updates.
- Renamed to Violet MCP in 1.3.0.

See [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md) and the git history for details.

Also uses: [Model Context Protocol](https://modelcontextprotocol.io/), [OkHttp](https://square.github.io/okhttp/) (Apache-2.0), [gorilla/websocket](https://github.com/gorilla/websocket) (BSD-2-Clause), [Jetpack Compose](https://developer.android.com/jetpack/compose), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines).

This project is released under the **GNU General Public License v3.0**; see [LICENSE](LICENSE) for the full text. When you distribute the APK, you must also provide the corresponding source code.
