# Upgrading and rolling back

**English** | [简体中文](UPGRADE.zh-CN.md)

## Violet MCP 1.4.3 → 1.4.4 (download source / GitHub mirrors; relay unchanged)

- Install `violet-mcp-1.4.4-release.apk` over the old one: token, relay configuration and tool switches are all kept, and nothing changes on Violet's side. If the phone cannot reach GitHub, download this one through the mirror links in the Chinese README ("国内网络：GitHub 加速地址").
- New: Settings → App updates → Download source. Default "automatic": GitHub first, then the GitHub mirror `https://p.jackyu.cn/`; or GitHub only, p.jackyu.cn only, or your own https mirror address. HTTPS only (the app still allows no cleartext connection at all).
- Checking for a new version now reads the release's `SHA256SUMS.txt` (`releases/latest/download/SHA256SUMS.txt`) instead of `api.github.com`: mirrors do not forward the GitHub API, and the API allows only 60 requests an hour. The release notes button still opens the release page on GitHub.
- The checks before installing are unchanged: SHA-256, package name, version number and signing certificate must all match, so a mirror cannot change the APK.
- Releases carry one more file, `SHA256SUMS.txt.sig` (signed by `tools/release_sign.py` with `signing/relay-sums-ed25519.key`): Violet 0.7.59 and later check it when the one-click relay install downloads through a mirror.
- Rolling back: `adb install -r -d` with 1.4.3, or uninstall and reinstall (data is wiped).

## Violet MCP 1.3.0 → 1.4.0 (in-app updates; relay unchanged)

- Install `violet-mcp-1.4.0-release.apk` over the old one (same package name and signature as 1.3.0): token, relay configuration and tool switches are all kept, and nothing changes on Violet's side.
- New: Settings → App updates. Default "automatic": checks GitHub Releases every 6 hours, and when there is a new version it downloads it, checks SHA-256 / package name / version / signing certificate, then hands it to the system to install (usually you tap "Update" once). Can be changed to "notify only" or "check manually".
- New permission `REQUEST_INSTALL_PACKAGES`: "Install unknown apps" must be turned on for this app in the system settings before updates can be installed; without it the MCP service still works, you just download and install updates yourself.
- The app accesses `api.github.com`, `github.com` and GitHub's download servers (no login, no data uploaded); in "check manually" mode only when you tap the button.
- Rolling back: uninstall and install 1.3.0 (data is wiped and has to be configured again), or use `adb install -r -d` (a downgrade install; whether data is kept depends on the system).

## 1.2.2-eve → Violet MCP 1.3.0 / relay 1.2.0 (rename; tools and security rules unchanged)

1.3.0 only changes names: app name "Violet MCP", package name `com.mcp.server.violet`, release file `violet-mcp-<version>-release.apk`; the relay is renamed `violet-mcp-relay` (1.2.0), and the environment variable becomes `VIOLET_RELAY_DEVICE_SECRET` (the old name `EVE_RELAY_DEVICE_SECRET` is still recognised). The 14 read-only tools, permissions, authentication, TLS and signing key are all unchanged.

**Phone**: the package name changed, so the system treats 1.3.0 as a different app, and it **cannot be installed over** the old "Eve read-only MCP" (`com.mcp.server.eve`).

1. Install `violet-mcp-1.3.0-release.apk` (it can live alongside the old app).
2. In the new app, turn on "Usage access" again, enter the relay address, device key and certificate fingerprint (the same as in the old app), and turn the relay on.
3. Copy the new access token on the "Security" page, and on Violet's Tools page change the `Authorization` of the `android-phone` server to the new token.
4. Once Violet can call it, turn off the relay in the old app, then uninstall the old app (when two apps connect to the same relay, the later one replaces the earlier one).

**Relay** (on the VPS, as root). To keep the old certificate and device key (so the phone does not need a new certificate fingerprint), copy the old data directory first:

```bash
systemctl disable --now eve-mcp-relay
cp -a /var/lib/private/eve-mcp-relay /var/lib/private/violet-mcp-relay   # DynamicUser data lives under /var/lib/private
mkdir -p /opt/violet-mcp-relay
ARCH=$( [ "$(uname -m)" = aarch64 ] && echo arm64 || echo amd64 )
install -m 0755 /root/violet-mcp-relay-1.2.0-linux-$ARCH /opt/violet-mcp-relay/violet-mcp-relay
install -m 0644 /root/violet-mcp-relay.service /etc/systemd/system/violet-mcp-relay.service
systemctl daemon-reload && systemctl enable --now violet-mcp-relay
journalctl -u violet-mcp-relay -n 20 --no-pager      # the certificate fingerprint should be the same as before
```

You can also skip copying the data directory: the new relay generates a new certificate and device key; get the three values as in step 3 of [DEPLOY_RELAY.md](DEPLOY_RELAY.md) and enter them on the phone again. Once everything works, the old `/opt/eve-mcp-relay` and `/etc/systemd/system/eve-mcp-relay.service` can be deleted.

These steps **have not yet been carried out on a real phone and a real VPS**.

Below are the upgrade records of versions before 1.3.0 (file and package names are the names of that time).

## 1.2.1-eve → 1.2.2-eve (signing key rotation; phone and relay functions unchanged)

1.2.2 has no functional changes; it did only two things:

- rotated the signing key, because the old key leaked with a ZIP of the whole working directory (see SIGNING_KEY_INCIDENT.md §6);
- added safe source handoff packaging.

The relay is still 1.1.0 and needs no update.

**Upgrade (phone)**: install `dist/v1.2.2-eve/eve-readonly-mcp-1.2.2-eve-release.apk` over the old one. It can upgrade directly from 1.1.0, 1.2.0 or 1.2.1, keeping data (token, relay configuration and tool switches unchanged):

| Phone system | Can install over | Why |
|---|---|---|
| Android 9+ (API 28+) | Yes | The APK carries a rotation proof from the old certificate to the new one (v3 lineage), from which the system confirms it is the same developer |
| Android 8.0 / 8.1 | Yes | These systems only check the v2 signature, and v2 is still signed with the old key. This also means they **cannot benefit from the rotation's protection** |

These conclusions come from apksigner's verification results and Android's signing rules, and **have not been verified on a real phone yet** (see DEVICE_CHECKLIST.md A8–A10).

**After upgrading, as soon as possible**:

- install 1.2.2 on every phone; the rotation only takes effect on devices with 1.2.2 installed;
- delete every copy of `mcp-server.zip`;
- get APKs only from `dist/`.

**Rolling back to 1.2.1 or earlier**: once an Android 9+ phone has 1.2.2, 1.2.1, signed only with the old key, **cannot be installed over it**. This is the rotation's protection at work: the rotation proof does not grant the old key rollback capability. Besides, an APK with a lower version number cannot be installed over a higher one anyway. So rolling back requires uninstalling first; the local configuration is lost, the relay details have to be entered again, and Eve has to switch to the new token.

**Rolling back is not recommended**: 1.2.1 and earlier were signed only with the old key that has leaked.

**Changes to developer builds**:

- when `signing/rotation.properties` exists, `gradlew :app:assembleRelease` only produces an unsigned package, and `python tools/sign_release.py <apk>` must be run afterwards;
- releases use `tools/package_release.py` and source handoffs use `tools/make_handoff.py`; see HANDOFF.md.

---

## 1.2.0-eve → 1.2.1-eve (phone only, relay unchanged)

**Upgrade**: install `dist/v1.2.1-eve/eve-readonly-mcp-1.2.1-eve-release.apk` over the old one. Same signature as 1.1.0 and 1.2.0, data kept. Upgrading directly from 1.1.0 also works.

**Behaviour changes (security fixes)**:

| Situation | 1.2.0 | 1.2.1 |
|---|---|---|
| Keystore works (the vast majority of phones) | Stored encrypted | Unchanged: stored encrypted, token unchanged |
| Keystore unavailable, or an old plain-text token cannot be migrated | Silently switches to plain-text storage and keeps accepting authentication | **Remote authentication is disabled** and the home screen shows a red notice; the old token stays on the phone but is unusable for now. It comes back only after reading the risk explanation on the "Security" page and ticking consent to compatibility mode, after which a warning stays visible |
| The stored ciphertext cannot be decrypted | Falls back to a leftover plain-text copy if one exists | Refuses authentication and never uses any plain-text copy; change the token on the "Security" page (and update Eve's configuration) and enter the device key again in "Settings" |

**Check after upgrading**: open the app and see whether the home screen has a red "credentials" notice card. Without a notice, everything is the same as in 1.2.0.

**Rolling back to 1.2.0**: Android does not allow installing a lower version number over a higher one, so uninstall and then install `dist/v1.2.0-eve/eve-readonly-mcp-1.2.0-eve-release.apk`. Uninstalling wipes the local configuration, the relay details have to be entered again, and the token changes too (Eve's configuration has to be updated to match). Rolling back is not recommended unless 1.2.1 has a blocking problem, because 1.2.0 has the problems in the table above.

**Changes to releasing**: from now on all release packages are generated with `tools/package_release.py` and checked with `tests/check_release_artifacts.py`. How the signing key is handled is in [SIGNING_KEY_INCIDENT.md](SIGNING_KEY_INCIDENT.md).

---

# 1.1.0-eve → 1.2.0-eve, relay 1.0.0 → 1.1.0

## Compatibility

Mixing old and new versions was tested on a PC (see TEST_REPORT §1.3 mixed versions), so **either side can be upgraded first**:

| Phone | Relay | Result |
|---|---|---|
| 1.2.0 | 1.0.0 | Works; the relay sends no application-level heartbeat, so the phone's watchdog stays off and disconnects are detected by the phone-side WebSocket ping (default 60 seconds); Eve gets no Server-Timing |
| 1.1.0 | 1.1.0 | Works; the old phone answers the relay's heartbeat; none of the phone-side checks new in 1.2 (batch limit, jsonrpc version check, phone-side timing) |
| 1.2.0 | 1.1.0 | Recommended combination |

## Behaviour that changes after upgrading

| Change | Effect | What to do |
|---|---|---|
| Default privacy switches of tools | Tools **never changed by hand** in 1.1.0 follow the new defaults: only `battery`, `get_foreground_app`, `get_screen_state`, `screen_info` and `health` are on | To use usage time and other tools, turn them on on the "Tools & permissions" page |
| `get_foreground_app` return format | Changed from `status/isCurrent/packageName/...` to `state/app/package/observed_at/age_seconds`; non-current states no longer give an app | If Eve's prompts or parsing depend on the old fields, adjust them (the MCP tool description explains it, and models can usually read it directly) |
| "LAN access" removed | If LAN access was on in 1.1.0, after the upgrade the local endpoint listens only on 127.0.0.1 | Use the TLS relay instead |
| Encrypted token storage | Migrated automatically on first read; **the token value does not change** | Nothing to do |
| Start on boot | After the user tapped "Stop", neither boot nor an app update starts it automatically any more | To restore, tap "Start" in the app |
| Relay default timeouts | The timeout waiting for the phone's response went from 60 to 20 seconds; new offline wait of 5 seconds and heartbeat of 30 seconds | Adjust with `-request-timeout`, `-offline-wait`, `-ping-interval` |
| Relay plain-text listening on non-loopback | Used to print only a warning; now **refuses to start** | Change to 127.0.0.1, or add `-client-tls` |
| Relay device-side errors | Phone offline / timeout changed from HTTP 503/504 to HTTP 200 + a JSON-RPC error | Eve needs no change; the session is no longer interrupted by this |

## Upgrade steps

### 1. Relay (VPS)

First upload the new program from the Windows computer (change only `$VPS`):

```powershell
$VPS = "root@your-vps-address"
scp D:\Claude\mcp-server\dist\v1.2.0-eve\eve-mcp-relay-1.1.0-linux-amd64 D:\Claude\mcp-server\relay\deploy\eve-mcp-relay.service "${VPS}:/root/"
ssh $VPS
```

Then run on the VPS. The old program is kept as `eve-mcp-relay-1.0.0` for rolling back:

```bash
cp /opt/eve-mcp-relay/eve-mcp-relay /opt/eve-mcp-relay/eve-mcp-relay-1.0.0
install -m 0755 /root/eve-mcp-relay-1.1.0-linux-amd64 /opt/eve-mcp-relay/eve-mcp-relay
# The new systemd configuration is the same as the old (listening on 127.0.0.1:8787 and :8443); if you changed ExecStart, keep your own version
systemctl restart eve-mcp-relay
journalctl -u eve-mcp-relay -n 20 --no-pager     # should show "eve-mcp-relay 1.1.0" and a timers line
curl -s http://127.0.0.1:8787/health; echo       # deviceConnected is true once the phone reconnects
```

If it fails to start with `refusing to start`, `-client-listen` is set to a non-loopback address without TLS. Fix `/etc/systemd/system/eve-mcp-relay.service` as the message says, then run `systemctl daemon-reload && systemctl restart eve-mcp-relay`.

### 2. Phone

Install `eve-readonly-mcp-1.2.0-eve-release.apk` over the old one (same signature as 1.1.0, data kept). After installing:

1. Open the app and check that the home screen shows "Service running" and remote access shows "Connected".
2. Open the "Security" page and check that the token shows "stored encrypted with Android Keystore".
3. Open the "Tools & permissions" page and turn on sensitive tools as needed.

## Rolling back

### Relay back to 1.0.0

```bash
install -m 0755 /opt/eve-mcp-relay/eve-mcp-relay-1.0.0 /opt/eve-mcp-relay/eve-mcp-relay
systemctl restart eve-mcp-relay
```

device.secret and the self-signed certificate are both in `/var/lib/eve-mcp-relay` and work with both versions, so the phone configuration needs no change.

### Phone back to 1.1.0

Android does not allow installing an APK with a lower version number directly over a higher one, so **rolling back requires uninstalling first**, and the local configuration is lost:

1. Open 1.2.0, copy the access token on the "Security" page, and note the relay address, device key (`cat /var/lib/eve-mcp-relay/device.secret` on the VPS) and certificate fingerprint.
2. Uninstall 1.2.0 and install `dist/eve-readonly-mcp-1.1.0-eve-release.apk`.
3. Enter the relay configuration again in 1.1.0. The old version cannot import the original token and generates a new one, so **Project Eve's Authorization must be changed to the new token shown by 1.1.0**.
4. Known problems of 1.1.0: all tools on by default; token stored in plain text; after the user stops the service it can still be started by the app-update broadcast (see REVIEW_v1.2.md R4).

### Artifacts

- **The 1.1.0 stable files** are in the root of `dist/` and were not overwritten:
  - `eve-readonly-mcp-1.1.0-eve-release.apk`
  - `eve-mcp-relay-linux-amd64` (1.0.0)
  - source package
- **The 1.2.0 files** are all in `dist/v1.2.0-eve/`, with version numbers in their file names.
