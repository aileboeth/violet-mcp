# Test report

**English** | [简体中文](TEST_REPORT.zh-CN.md)

## Violet MCP 1.4.4 (relay 1.2.0 unchanged) — 2026-11-08

In-app updates can go through a GitHub mirror (user: phones in mainland China cannot reach GitHub): Settings → App updates → Download source (automatic / GitHub only / p.jackyu.cn only / a custom https address), `Sources.kt`. The version check now reads `releases/latest/download/SHA256SUMS.txt` (no GitHub API any more: mirrors do not forward it). Each source may only contact GitHub's hosts plus its own host; HTTPS only. The SHA-256 / package name / version / signing certificate checks before installing are unchanged. Releases carry one more file, `SHA256SUMS.txt.sig` (`tools/release_sign.py`), which Violet checks when it installs the relay through a mirror.

| Test | Result |
|---|---|
| mcp-core + Robolectric (`:mcp-core:test :app:testDebugUnitTest --offline`, **simulated**) | Pass, 135/135 (75 + 60); UpdateCheckerTest rewritten, 11 tests (MockWebServer plays both github.com and a mirror: redirects, falling back from source to source, each source's failure reason, only allowed hosts contacted); AppUpdaterTest +2 (download source setting, remembering which source answered) |
| `:app:assembleDebug` / `:app:assembleRelease` (R8) | Pass; artifacts `violet-mcp-1.4.4-{debug,release}.apk`, versionCode 11 |
| Static APK check (`tests/verify_apk.py`, debug package) | Pass, 0 failures (including `usesCleartextTraffic=false`) |
| Mirrors (2026-11-08, from a Linux machine on China Mobile) | `p.jackyu.cn` redirects `releases/latest/download/SHA256SUMS.txt` to the tagged address (a relative address on the same host); the 1.4.3 APK, relays and SHA256SUMS.txt downloaded through it have the same SHA-256 as on GitHub |
| `SHA256SUMS.txt.sig` | `tools/release_sign.py` signed 1.4.3's SHA256SUMS.txt with the real key; Violet's `violet_sign.check(MCP_RELAY_KEY, …)` accepts it; the Violet release key, or a file changed by one byte, is refused |
| Signing (`tools/sign_release.py`, with the user's consent) | Pass: Android 9+ signer `d5a5eeec…` (same as 1.4.3), the old certificate for Android 8.x, lineage old → new |
| Release package (`tools/package_release.py --version 1.4.4 --relay-version 1.2.0`) | Pass: `dist/v1.4.4/` (now with `SHA256SUMS.txt.sig`, which Violet's MCP_RELAY_KEY accepts), 0 problems in the release artifact and handoff package scans |
| Real device (updating in the app through a mirror) | Cannot be verified (no device) |

## Violet MCP 1.4.3 (relay 1.2.0 unchanged) — 2026-11-05

The GitHub account was renamed from aileboeth-web to aileboeth: in-app updates now read `aileboeth/violet-mcp` (`UpdateChecker.DEFAULT_REPO`), and the settings text names the new repository. The old name only redirects until someone else registers it. Nothing else changed.

| Test | Result |
|---|---|
| mcp-core + Robolectric (`:mcp-core:test :app:testDebugUnitTest --offline`, **simulated**) | Pass, 131/131 (73 + 58) |
| `:app:assembleDebug` / `:app:assembleRelease` (R8) | Pass; artifacts `violet-mcp-1.4.3-{debug,release}.apk`, versionCode 10 |
| Static APK check (`tests/verify_apk.py`, debug package) | Pass, 0 failures |
| Old repository URL (`api.github.com/repos/aileboeth-web/violet-mcp/releases/latest`) | 301 to `api.github.com/repositories/…`, which stays within the updater's allowed hosts, so 1.4.2 installs still find 1.4.3 |
| Signing (`tools/sign_release.py`, with the user's consent) | Pass: Android 9+ signer `d5a5eeec…` (same as 1.4.2), the old certificate for Android 8.x, lineage old → new |
| Release package (`tools/package_release.py --version 1.4.3 --relay-version 1.2.0`) | Pass: `dist/v1.4.3/`, 0 problems in the release artifact and handoff package scans |
| Real device (updating 1.4.2 → 1.4.3 in the app) | Cannot be verified (no device) |

## Violet MCP 1.4.2 (relay 1.2.0 unchanged) — 2026-11-03

Up to 3 relay servers connected at once (the user chose a limit of 3 and a "server card list" on the settings page): one `RelayClient` per enabled slot, all sharing the same
`McpEndpoint` and access token; slot 1 keeps the 1.4.x keys, so after upgrading the old configuration becomes "server 1", and the device key is not migrated; changing one server rebuilds only that
connection. New settings card `RelayServersCard.kt`; the home, diagnostics and security pages show one row per server; `health` does not reveal other servers' addresses. Tools,
authentication and the relay protocol are unchanged.

| Test | Result |
|---|---|
| mcp-core + Robolectric (`:mcp-core:test :app:testDebugUnitTest --offline`, **simulated**) | Pass, 131/131 (73 + 58; 5 new `MultiRelayTest` cases: the 1.4.x configuration becomes server 1, each of three servers has its own key, switches don't affect each other, deleting one leaves the others, revoke / clear cover all servers) |
| UI screenshots (Robolectric native rendering, **simulated**, temporary test not kept in the repository) | Checked: collapsed, expanded for editing, dark theme, two servers + "Add server" |
| `:app:assembleDebug` / `:app:assembleRelease` (R8) | Pass; artifacts `violet-mcp-1.4.2-{debug,release}.apk`, versionCode 9 |
| Static APK check (`tests/verify_apk.py`, debug package) | Pass, 0 failures |
| Signing (`tools/sign_release.py`, the user said "go ahead") | Pass: Android 9+ signer `d5a5eeec…` (same as 1.4.1), the old certificate for Android 8.x, lineage old → new |
| Release package (`tools/package_release.py --version 1.4.2 --relay-version 1.2.0`) | Pass: `dist/v1.4.2/`, 0 problems in the release artifact and handoff package scans |
| Real device (several relays online at once, each reconnecting on its own after losing / switching networks, power use) | Cannot be verified (no device) |

## Violet MCP 1.4.1 (relay 1.2.0 unchanged) — 2026-11-03

Adds 7 read-only "life state" tools (`next_alarm`, `now_playing`, `audio_output`, `network_state`, `step_count`,
`calendar_events`, `phone_events`); the "Tools & permissions" page shows plain names + plain-language explanations (`ToolKit.PLAIN`), and "Grant" brings up the system request
or opens the system settings depending on the permission type. New permissions `ACTIVITY_RECOGNITION`, `READ_CALENDAR`, `ACCESS_FINE_LOCATION` /
`ACCESS_COARSE_LOCATION` (optional), and a new `MediaListenerService` that only the system can bind (notification access, handles no notifications).
Authentication and relay logic are unchanged; "phone events" are fetched by Violet once a minute, not pushed through the relay.

| Test | Result |
|---|---|
| mcp-core + Robolectric (`:mcp-core:test :app:testDebugUnitTest --offline`, **simulated**) | Pass, 126/126 (6 new `LifeToolsTest` cases: alarm / headphones on by default with results, refusal without guessing when the system permission is missing, the Wi-Fi name never leaves the phone, steps carry over midnight and reboots, events fetched by sequence number, every tool has a plain-language explanation; `HardeningTest`'s default-on set adds `next_alarm` and `audio_output`) |
| `:app:assembleDebug` / `:app:assembleRelease` (R8) | Pass; artifacts `violet-mcp-1.4.1-{debug,release}.apk`, versionCode 8 |
| Static APK check (`tests/verify_apk.py`, debug package) | Pass, 0 failures; the permission allowlist adds the 4 permissions above |
| Signing (`tools/sign_release.py`, the user said "finish everything first") | Pass: Android 9+ signer `d5a5eeec…` (same as 1.4.0, so it installs over it / updates in-app), the old certificate for Android 8.x, lineage old → new |
| Release package (`tools/package_release.py --version 1.4.1 --relay-version 1.2.0`) | Pass: `dist/v1.4.1/`, 0 problems in the release artifact and handoff package scans |
| Real device (steps, now playing, calendar, Wi-Fi naming, event records, each permission dialog) | Cannot be verified (no device); Wi-Fi recognition only works on Android 12+ |

## Violet MCP 1.4.0 (relay 1.2.0 unchanged) — 2026-10-27

Adds in-app updates (`com.mcp.server.update.UpdateChecker` in `mcp-core`, the app's `AppUpdater` / `UpdateInstallReceiver`, "App updates" on the settings page, a notice on the home screen), and the new permission `REQUEST_INSTALL_PACKAGES`. Tools, authentication and relay logic are unchanged.

| Test | Result |
|---|---|
| mcp-core unit tests (`:mcp-core:test --offline`) | Pass, 73/73 (9 new `UpdateCheckerTest` cases: finding the package and checksum file, deleting on SHA-256 mismatch, refusing missing assets / non-version tags, connecting only to allowlisted hosts and checking before connecting, refusing redirects to other hosts, GitHub accessed only over https by default, both checksum file formats, version comparison) |
| Robolectric (`:app:testDebugUnitTest`, **simulated**) | Pass, 47/47 (9 new `AppUpdaterTest` cases: automatic by default / manual mode never checks by itself, 6-hour interval, notify-only does not download, a download that is not a package signed by this app is deleted and not installed, automatic mode downloads by itself, failure reasons saved, results still shown after a restart, signing certificate chain rule, install failure shown) |
| `:app:assembleDebug` / `:app:assembleRelease` (R8) | Pass; artifacts `violet-mcp-1.4.0-{debug,release}.apk` |
| Static APK check (`tests/verify_apk.py`, debug package) | Pass, 0 failures. The permission allowlist adds `REQUEST_INSTALL_PACKAGES`; writing / deleting files is allowed only in `com/mcp/server/update/` and `AppUpdater`; new checks "PackageInstaller appears only in app updates" and "the update result receiver is not exported"; the tools and protocol core still may not write files |
| Signing (`tools/sign_release.py`, with the user's consent) | Pass: Android 9+ signer `d5a5eeec…` (same as 1.3.0, so it installs over it), the old certificate for Android 8.x, lineage old → new |
| Release package (`tools/package_release.py --version 1.4.0 --relay-version 1.2.0`) | Pass: `dist/v1.4.0/`, 0 problems in the release artifact and handoff package scans; `aapt2 badging`: versionCode 7, 1.4.0, with `REQUEST_INSTALL_PACKAGES`; SHA256SUMS.txt in the `<sha256> *<file name>` format the app updater needs |
| Real device (installing, system confirmation, service recovery after updating) | Cannot be verified (no device); see DEVICE_CHECKLIST section U |

## Violet MCP 1.3.0 (relay 1.2.0) — 2026-10-27

Only renames (app name, package name `com.mcp.server.violet`, artifact names, relay name and environment variable); tools and security logic are unchanged.

| Test | Result |
|---|---|
| mcp-core unit tests (`:mcp-core:test --offline`) | Pass, 64/64 |
| Go relay `go vet` / `go test -count=1 ./...` | Pass, 20/20 (new `TestDeviceSecretEnvironmentNewAndOldName`: the old environment variable `EVE_RELAY_DEVICE_SECRET` still works, the new name takes precedence) |
| Static contract with Violet (the 14 tools agree on all three sides, all read-only) | Pass |
| Robolectric (`:app:testDebugUnitTest`, **simulated**) | Pass, 38/38 |
| `:app:assembleDebug` / `:app:assembleRelease` | Pass; `aapt2 dump badging`: `com.mcp.server.violet`, versionCode 6, 1.3.0, app name "Violet MCP" |
| Static APK check (`tests/verify_apk.py`, debug package) | Pass, 0 failures |
| Signing (`tools/sign_release.py`, with the user's consent) | Pass: Android 9+ signer `d5a5eeec…` (the same rotated key as 1.2.2), the old certificate for Android 8.x, lineage old → new |
| Release package (`tools/package_release.py --version 1.3.0 --relay-version 1.2.0`) | Pass: `dist/v1.3.0/` (APK, relay linux-amd64 / arm64, source handoff package, SHA256SUMS), 0 problems in the release artifact and handoff package scans |
| PC end-to-end | Not run |
| Real device | Cannot be verified (no device) |

## 1.2.2-eve (relay 1.1.0, unchanged) — 2026-10-10

This round's changes:

- signing key rotation (APK Signature Scheme v3 lineage);
- new safe source handoff packaging (`tools/make_handoff.py`) and handoff package scanning (`check_release_artifacts.py --handoff`);
- new signing script `tools/sign_release.py` and signing configuration templates;
- **no functional changes** to the app or relay; only the version number changed to 1.2.2-eve / versionCode 5.

Every result in the table below was **actually run** on the final code this round.

| Test | Command | Result |
|---|---|---|
| `mcp-core` unit tests | `gradlew :mcp-core:test --rerun-tasks` | **64/64 pass** |
| Android unit tests (Robolectric, **simulated environment**) | `gradlew :app:testDebugUnitTest --rerun-tasks` | **38/38 pass** |
| Go relay | `go vet ./... && go test -count=1 ./...` | **19/19 pass** |
| Python MCP SDK 1.30.0 end-to-end | `tests/run_pc_e2e.sh` | Through the relay **41/41**, direct **41/41**, resilience **11/11**; insecure listening configurations refuse to start; no token or device key in relay.log / harness.log |
| Static APK check | `tests/verify_apk.py` | Debug package **45/45 pass**; the release package fails only "check that the subject is not obfuscated", as expected (same as 1.2.1) |
| Release APK signature | `apksigner verify -v --min-sdk-version 26` | Pass (v2 + v3). Android 9+ sees the new certificate `d5a5eeec…15ea` as signer, Android 8.x the old certificate `52f602d3…b499`; the embedded lineage is old → new. `aapt2` shows versionCode 5 / versionName 1.2.2-eve |
| Gradle signing guard | Both `rotation.properties` and `keystore.properties` in `signing/` | The build fails right away with the reason, preventing signing with only the old key |
| Recovery after losing the lineage file | `apksigner sign --lineage <a released 1.2.2 APK>` | The signed APK carries the full 2-level lineage |
| **Building in a clean environment** (handoff package) | `tests/verify_handoff_build.sh <handoff package> <SDK> D:/Claude/.handoff-fresh` | **All pass**, details below |
| Handoff package scan (**the ZIP itself**) | `make_handoff.build()` → `check_release_artifacts.py --handoff` | 0 problems, details below |
| Release directory scan | `check_release_artifacts.py dist/v1.2.2-eve` | 0 problems |
| Re-check of historical artifacts | `check_release_artifacts.py dist`; `sha256sum -c` in each directory | 0 problems; checksums of the 1.1.0 (dist root), 1.2.0 and 1.2.1 artifacts all match, **nothing overwritten** |
| Scanner self-test | Plant into a temporary zip a real key (renamed and put into a jar inside the zip), the real password (put into a tar.gz inside the zip), an `sk-ant-` style key, `.git/`, `build/` | All detected; a clean zip passes |
| Overwrite protection | Run `package_release.py` again on the existing `dist/v1.2.2-eve`; call `make_handoff.build()` again on an existing handoff package | Both refuse, original files unchanged |

Details of **building in a clean environment**:

- The handoff package is unpacked into an empty directory, with `GRADLE_USER_HOME`, `GOMODCACHE` and `GOCACHE` all new empty directories, so all dependencies are downloaded again. Downloads go through the local proxy, supplied via `JAVA_TOOL_OPTIONS`, which is not part of the source.
- Results:
  - all 104 files in `HANDOFF_MANIFEST.txt` pass verification;
  - the package has no `signing/`, `.git/`, `local.properties`, caches or build directories;
  - 64 core tests and 38 Android unit tests, 0 failures;
  - debug and release builds succeed; without a signing configuration the release package is unsigned, as expected;
  - relay `go vet` / `go test` pass, and the Linux amd64 build succeeds;
  - after filling in a throwaway test key per `config-templates/keystore.properties.example`, the release package signs successfully.
- This verification ran on Windows (Git Bash); it has **not** actually been run on Linux / macOS.

Re-check in handoff mode of **the full working directory ZIP the user provided earlier** (`mcp-server.zip`):

- 4437 files (including nested ones), 3631 problems in total;
- key-related among them: `signing/eve-release.jks` (byte for byte identical to the local one, PKCS#12), `signing/keystore.properties` (with the real local password), `local.properties`;
- the rest are files under `.git/`, `.gradle/`, `.kotlin/`, `build/` and `dist/`;
- no other tokens or API keys were found.

**Not verified** (no phone connected, and no emulator can run):

- installing 1.2.2 over 1.2.1 on a real phone;
- refusing 1.2.1 once 1.2.2 is installed;
- Android 8.x behaviour.

See DEVICE_CHECKLIST.md A8–A10. The other real-device items are the same as for 1.2.1 and still not verified.

---

## 1.2.1-eve (relay 1.1.0, unchanged this round) — 2026-10-10

This round's changes: credential storage now fails closed, and compatibility mode needs the user's explicit consent; a leak check for release packages and a packaging script were added.
Every result in the table below was **re-run** on the final code this round; items not re-run are listed separately.

| Test | Command | Result |
|---|---|---|
| `mcp-core` unit tests | `gradlew clean :mcp-core:test --rerun-tasks` | **64/64 pass** |
| Android unit tests (Robolectric, **simulated environment**) | `gradlew :app:testDebugUnitTest --rerun-tasks` | **38/38 pass** (including 16 SecretStoreTest cases, some new and some rewritten this round) |
| Mutation check | Put back 1.2.0's "use the plain-text copy when decryption fails" | 2 cases fail, so it is detected; restored afterwards |
| Go relay | `go vet ./... && go test -count=1 ./...` | **19/19 pass** |
| Python MCP SDK **1.30.0** end-to-end | `tests/run_pc_e2e.sh` | All pass, details below |
| Static APK check | `tests/verify_apk.py` (debug package) | **45/45 pass**; the release package fails only "check that the subject is obfuscated", as expected |
| Release package leak check | `tests/check_release_artifacts.py dist` | 16 artifacts, 579 files, **0 problems**; the self-test with a planted real key detects it |
| Packaging script | `tools/package_release.py --version 1.2.1-eve` | Generates the artifacts, `sha256sum -c` all OK; refuses to overwrite when run again; deletes the artifacts automatically when the leak check fails (actually triggered before a false positive was fixed) |
| Signing key rotation rehearsal (throwaway test key) | `apksigner rotate` → `sign --lineage --rotation-min-sdk-version 28` → `verify` | Verifies with the v3 scheme, signer is the new certificate |

End-to-end test details:

| Step | Result |
|---|---|
| Relay configured to listen in plain text on `0.0.0.0` | Refuses to start |
| Through the relay | 41/41 |
| Direct to the local endpoint | 41/41 |
| Resilience tests | 11/11 (all 20 concurrent calls succeed; p95 3.8 ms; -32011 after a 5.0 s timeout; reconnects 1.0 s after a relay restart; fails in 2.8 s when the relay is unreachable) |
| Log check | No token or device key |
| New phone ⇄ old relay 1.0.0 | 38/38 |
| Old phone 1.1.0 ⇄ new relay | 36/36 |

**Tests not run this round (cannot be counted as passing):**

- all real-device items, see [DEVICE_CHECKLIST.md](DEVICE_CHECKLIST.md). The new sections G (credentials and Keystore) and H (vendor systems such as OriginOS) have not been run either;
- Android Keystore encryption and decryption on a real phone, and the migration when upgrading 1.1.0 / 1.2.0 to 1.2.1. Robolectric has no AndroidKeyStore, so this round's tests used a fake encryptor;
- the end-to-end tests with Python MCP SDK 1.8.0 were not re-run this round (the previous round's results are in §1.3.1 below);
- running the obfuscated release APK on a real phone.

---

# 1.2.0-eve test record (previous round, kept as history)

Date: 2026-10-10. All numbers below come from actual runs of the 1.2.0 code.

**Results fall into three categories:**

| Category | Meaning | Conclusion |
|---|---|---|
| Automated (PC) | JVM unit tests, Go tests, Python MCP SDK end-to-end, static APK checks. The phone side uses a JVM harness running **the same** `mcp-core` code as the app, with simulated tool data | All pass |
| Simulated environment (Robolectric) | Simulates the Android framework on the JVM and runs the app's real tool, service and settings code; system events, screen state and AppOps are injected by the tests | All pass; **does not represent a real phone** |
| Real phone | Background survival, Doze, network switching, start on boot, Keystore and vendor system restrictions on an Android phone | **Not verified** (the development environment has no device and cannot run an accelerated emulator). Checklist in [DEVICE_CHECKLIST.md](DEVICE_CHECKLIST.md) |

## 1. Automated (PC)

### 1.1 `mcp-core` unit tests: all 64 pass (stable over 3 consecutive runs)

```
gradlew :mcp-core:test
```

| Test class | Cases | Covers |
|---|---|---|
| `ToolRegistryTest` | 5 | Registering any of 36 removed tool names fails; the allowlist and removed list do not overlap; tools that are off are invisible and unresolvable; read-only annotations |
| `McpProtocolTest` | 10 | Protocol version negotiation; notifications; id types; removed tools / variants / tools that are off all return -32602; argument and tool errors as isError results; batch requests |
| `HardeningTest` | 8 | The local endpoint refuses to bind `0.0.0.0`, LAN IPs, `::` and domain names; 200,000 levels of nested JSON are refused safely; batches over 32 items and jsonrpc≠2.0 are refused; 11 kinds of malformed request bodies never run a tool; Server-Timing; request statistics contain no credentials; privacy defaults (sensitive tools off by default, not listable, not callable); parsing and validation of multiple certificate fingerprints |
| `HttpServerTest` | 15 | Over a real socket: authentication, Origin, routing, 405/202, reading CJK request bodies by bytes, chunked, 413 (including a regression test that **really sends a body over 1 MiB** and still gets 413), an early 401 not being RST, Content-Type, protocol version header |
| `RelayClientTest` | 13 | Certificate pinning; forwarding and second authentication; Server-Timing passed back; ids of 40 concurrent requests match one to one; application-level ping/pong; the watchdog reconnects when the relay goes silent; a wrong fingerprint goes into long backoff and reports the server's fingerprint; a second fingerprint for changing certificates; an unpinned self-signed certificate is refused; device key refused; long backoff after being replaced (4000); reconnects after the relay disconnects; 10 consecutive immediate reconnects debounced into 1; no connection attempt without a network, immediate connection when the network returns |
| `UsageAggregatorTest` | 9 | Session pairing, merging switches within an app, range clipping, screen off and shutdown, foreground decision (screen off / locked take precedence over inference from events) |
| `SecretsTest` | 6 | Token length and randomness, Bearer parsing, redaction, fingerprint parsing, configuration validation |

### 1.2 Go relay tests: all 19 pass

```
cd relay && go vet ./... && go test ./...
```

Covers:

- **Authentication**: a wrong device key is refused; unauthenticated requests, wrong tokens and Origin are all blocked at the relay and **never reach the phone**.
- **Forwarding**: only allowlisted request and response headers are forwarded; `/health` returns minimal information; browsers cannot open the device WebSocket.
- **Listening security**: 0.0.0.0, `:8787`, LAN IPs, `[::]` and domain names all refuse to start without TLS; the second check against the actually bound address works too; timing parameters out of range refuse to start.
- **Offline and timeouts**: the offline wait catches a device that is reconnecting, and the wait is capped; device offline, timeout and mid-request disconnects all return JSON-RPC errors with the original id (notifications get 202, batches answer item by item, unparsable requests fall back to an HTTP status code, wrong tokens still get 401).
- **Heartbeat and probing**: the heartbeat announces its interval, and a device that does not answer pong is disconnected after the read timeout; after a request timeout the connection is probed and dropped if there is no answer within 5 seconds.
- **Concurrency and timing**: the results of 50 concurrent requests never mix; Server-Timing carries per-stage timings and blocks non-numeric values from being injected into the response header.
- **Logs**: every test asserts at the **debug log level** that the relay log contains no token and no device key.

### 1.3 Python MCP SDK end-to-end (`tests/run_pc_e2e.sh`): all pass

Chain: Go relay (self-signed TLS) ⇄ JVM harness ⇄ Python MCP SDK 1.30.0.

| Scenario | Result |
|---|---|
| Relay listening in plain text on `0.0.0.0` | Refuses to start (`refusing to start`) |
| Through the relay: protocol, authentication, allowlist, malformed requests (`test_mcp_interop.py`) | 41/41 |
| Direct to the phone's local endpoint | 41/41 |
| Resilience (`test_resilience.py`) | 11/11 |
| Log leak check (relay, harness) | No token or device key in either |
| Mixed versions A: current phone code ⇄ old relay 1.0.0 | 38/38 (checks new in 1.2 skipped) |
| Mixed versions B: old phone code 1.1.0 ⇄ current relay | 36/36 (checks new in 1.2 skipped) |

The 41 checks cover:

- **Authentication**: no token, a wrong token and the Basic scheme all return 401 without leaking tool names; Origin returns 403; unauthenticated access to `/tools`, `/info`, `/sse`, `/` and `/status` exposes nothing.
- **Protocol**: minimal `/health`; GET returns 405; notifications return 202; initialize returns JSON; protocol version negotiation.
- **Malformed requests**: bodies over 1 MiB return 413; 100,000 levels of nested JSON, invalid UTF-8, jsonrpc 1.0 and a batch of 33 are all refused; duplicate keys (`battery` followed by `shell`) cannot run shell; batch calls to write tools are all refused.
- **SDK behaviour**: the SDK cannot initialise without a token; the server declares only the tools capability; tools/list stays within the allowlist, all with read-only annotations; every listed tool is called one by one; a tool that is off (`storage_info`) can be neither listed nor called directly.
- **Write tools**: 36 removed tool names plus 10 name variants, 46 in total, none of which can be called.

Measured values of the 11 resilience checks:

| Item | Result |
|---|---|
| 20 concurrent tools/call in one session | 20/20, 147 ms |
| Single-call latency on Eve's side (local relay) | p50 3.1 ms, p95 3.8 ms |
| Server-Timing | relay 0.8 ms, rtt 0.8 ms, phone 0.3 ms, tool 0.0 ms |
| Tool exceeding the relay timeout (5 s) | `McpError -32011` after 5.0 s, with a clear reason |
| Further calls in the same session after the timeout | Succeed |
| Phone reconnecting by itself after a relay restart | 1.0 s |
| The same MCP session after a relay restart | Still usable (on a local network) |
| Relay completely unreachable | The client fails within 2.7 s, never hangs |
| Phone reconnecting after the relay recovers | 1.3 s |

### 1.3.1 The same end-to-end tests re-run with Python MCP SDK 1.8.0 (final code)

| Scenario | Result |
|---|---|
| Through the relay / direct | 41/41, 41/41 (negotiated protocol 2024-11-05) |
| Resilience | 10/11 |

**The only failure is "the client fails fast when the relay is completely unreachable": on a refused connection SDK 1.8.0 does not hand the error to the caller, and `initialize` hangs until the caller's own timeout (30 seconds in the test).**
This is the behaviour of the old SDK client and cannot be fixed on the server; SDK 1.30.0 fails within 2.7 seconds.

Recommendations:

- Project Eve should use SDK ≥ 1.30;
- or set an outer timeout with `asyncio.wait_for` around calls, see [VIOLET_INTEGRATION.md](VIOLET_INTEGRATION.md) (called EVE_INTEGRATION.md at the time).

### 1.4 Static APK check (`tests/verify_apk.py`)

Run on the debug package (not obfuscated, so classes can be found by name): all 45 checks pass, covering:

- permissions exactly match the manifest (9), and 10 high-privilege permissions were removed.
- No Shizuku provider, no accessibility service; `allowBackup=false`, `usesCleartextTraffic=false`, foreground service type `specialUse`.
- The APK has no Shizuku, QuickJS or DocumentFile, and none of the original project's Bridge, Bore, File, Script, Net or FloatWindow classes.
- No `Runtime.exec`, `ProcessBuilder`, file deletion or writing, ContentResolver writes, force-stopping apps or writing system settings anywhere in the app.
- **In the tool package and protocol core** there is none of the following: starting components or sending broadcasts, posting notifications, writing the clipboard, writing system settings, changing volume / Wi-Fi / Bluetooth, running processes, calling or texting, writing files.

On the release package (R8 obfuscated) only "the tool classes exist" fails. This is the check script's intended behaviour: it uses this item to confirm that the package being checked is not obfuscated. Permission, component and third-party library checks all pass.

## 2. Simulated environment (Robolectric 4.14.1, Android SDK 34): all 30 pass

```
gradlew :app:testDebugUnitTest
```

| Test class | Cases | Covers |
|---|---|---|
| `ToolsPolicyTest` | 6 | The tools the app actually registers equal the allowlist exactly; a fresh install lists only 5 tools, and calling a sensitive tool directly is refused; switches take effect immediately; writing a removed tool name into the switches has no effect; **after turning on all 14 tools and calling each, no Activity / Service was started, no broadcast sent, no notification posted, the brightness setting unchanged, and no file written**; `system_properties` no longer contains the serial number |
| `UsageToolsTest` | 9 | current returns only 5 fields with the app name; results update after switching apps; screen off and locked give no app; leaving the foreground returns unknown without guessing; detailed mode gives last_app only after it is turned on on the phone; **after usage access is revoked** the foreground app returns permission_denied and usage time returns PERMISSION_DENIED; usage time is computed from events with app names; screen state is real-time |
| `SecretStoreTest` | 8 | Migrates from plain text keeping the original value; keeps the plain text and reports an error when migration fails; never silently replaces anything when decryption is impossible; an interrupted migration is completed; explicitly enters compatibility mode without Keystore; **the token is unchanged after upgrading from 1.1.0**; clear all generates a new token and deletes the relay credentials; revoking remote credentials keeps the token |
| `ServiceLifecycleTest` | 7 | After starting, the local endpoint serves only on 127.0.0.1 (401 without a token); "Stop" on the notification is recorded as stopped by the user; after the user stops it, the system's sticky restart does not bring the service back; when it should be running, the sticky restart restores it; when killed it shows as "restricted", not "running"; boot recovery happens only when "start on boot is on and the user never stopped it by hand", and a faked QUICKBOOT broadcast is ignored; APPLY does not start the foreground service when the service is not running; a fresh install shows "stopped" |

**Mutation check**: two defects were introduced on purpose — making `installed_apps` on by default, and making "Stop" not record the user's intent — and the matching tests failed. So these tests really do catch problems.

## 3. Tests not run

The following need a real phone and are **not verified; they cannot be counted as passing**:

- accuracy of foreground app and usage time data on a real phone, app switching, locking, system behaviour after revoking permission
- background survival: swiping from recent tasks, stopping from the task manager, vendor power-saving policies, long screen-off periods, Doze
- start on boot (`BOOT_COMPLETED` really delivered, and whether a `specialUse` foreground service can start on Android 15)
- Wi-Fi ↔ mobile network switching, airplane mode, recovery after losing the network, recovery after a relay restart on the real internet
- Android Keystore encryption and decryption on a real phone, and the token migration when upgrading from 1.1.0
- running the R8-obfuscated release package on a real phone (this round's Robolectric and harness tests are all based on unobfuscated code)
- behaviour when two phones use the same credentials (the logic is verified in `RelayClientTest` and the Go tests)
- the actual integration in Project Eve, and the latency distribution on a real network

## 4. Known limitations

- The foreground app is inferred from UsageStatsManager events, and system events may be delayed by a few seconds; screens such as the notification shade and system dialogs produce no Activity events, in which case `unknown` is returned.
- Usage time does not count screen-off periods; `method=events` supports at most 10 days, so use `method=system` for longer ranges.
- Vendor systems (MIUI, ColorOS, HarmonyOS, OneUI and others) may restrict background running further. The UI shows "restricted / stopped", but staying online forever cannot be guaranteed.
- When the relay is completely unreachable, the Python SDK ends the current session; Eve has to re-establish the session on the next call. Device offline or timeouts do not end the session.
- Old Python SDKs (e.g. 1.8.0) hang when the relay is unreachable until the caller's timeout (see §1.3.1).
