# Deploying the TLS relay and connecting Violet (relay 1.2.0 / phone app Violet MCP 1.3.0)

**English** | [简体中文](DEPLOY_RELAY.zh-CN.md)

```
Violet ──HTTP 127.0.0.1:8787/mcp──► violet-mcp-relay ◄══wss://VPS:8443/device (TLS, the phone connects out)══ phone
```

- The phone connects to the VPS itself, so it needs neither a public IP nor a VPN.
- Installing the relay on **the machine Violet runs on** is recommended: Violet talks to the local `127.0.0.1:8787`, so that leg never leaves the machine; only the phone's leg crosses the internet, and it is always TLS.
- By default the relay uses a self-signed certificate it generates itself, and the phone enters the certificate fingerprint (certificate pinning), so no domain is needed.
- If the MCP entry is configured on a non-loopback address without TLS, the relay **refuses to start**.

If you deployed the old version (`eve-mcp-relay` / phone app `com.mcp.server.eve`), see [UPGRADE.md](UPGRADE.md). For a fresh deployment, run the commands below in order; only step 1 needs one edit. The release files (`violet-mcp-relay-1.2.0-linux-*`, `violet-mcp-1.3.0-release.apk`) are on GitHub Releases, or are generated into `dist/v1.3.0/` by `tools/package_release.py`.

## 1. Upload the program from a Windows computer (PowerShell)

```powershell
$VPS = "root@your-vps-address"   # ← change only this, e.g. root@203.0.113.10
scp dist\v1.3.0\violet-mcp-relay-1.2.0-linux-amd64 dist\v1.3.0\violet-mcp-relay-1.2.0-linux-arm64 relay\deploy\violet-mcp-relay.service "${VPS}:/root/"   # run in the repository directory
ssh $VPS
```

## 2. Install on the VPS (root)

```bash
# Make sure the ports are free (no output means free)
ss -ltnp | grep -E ':8443 |:8787 '

mkdir -p /opt/violet-mcp-relay
ARCH=$( [ "$(uname -m)" = aarch64 ] && echo arm64 || echo amd64 )
install -m 0755 /root/violet-mcp-relay-1.2.0-linux-$ARCH /opt/violet-mcp-relay/violet-mcp-relay
install -m 0644 /root/violet-mcp-relay.service /etc/systemd/system/violet-mcp-relay.service
systemctl daemon-reload
systemctl enable --now violet-mcp-relay
journalctl -u violet-mcp-relay -n 20 --no-pager

# Check that the MCP entry listens only locally (should show 127.0.0.1:8787) and the device entry on 8443
ss -ltnp | grep -E ':8443 |:8787 '

# Open the phone's port (when using ufw; the cloud provider's security group must also allow TCP 8443)
command -v ufw >/dev/null && ufw status | grep -q "Status: active" && ufw allow 8443/tcp
```

## 3. Get the three values the phone needs

```bash
echo "Relay address:            wss://$(curl -4s https://ifconfig.me):8443/device"
echo "Device key:               $(cat /var/lib/violet-mcp-relay/device.secret)"
echo "Certificate fingerprint:  $(/opt/violet-mcp-relay/violet-mcp-relay -data-dir /var/lib/violet-mcp-relay -print-pin)"
```

The device key is stored only in `/var/lib/violet-mcp-relay/device.secret` (mode 0600) and never appears in any log.

## 4. On the phone

1. Install `dist/v1.3.0/violet-mcp-1.3.0-release.apk` and open the app.
2. Settings → Permissions → **Usage access** → allow. "Unrestricted battery" is optional but recommended.
3. Settings → Remote access: enter the three values from step 3 → **Save and apply** → turn on "Enable relay connection".
4. Settings → Background: turn on "Start on boot" if you want. Some brands also need auto-start and background running allowed in the phone manager app.
5. Tap **Start** on the home screen and check that "Remote access" shows "Connected".
6. Open "Tools & permissions" and choose the tools to share with Violet (by default only 5 non-sensitive ones are on).
7. Open "Security" and copy the access token; you need it in the next step.

## 5. Verify on the VPS

```bash
curl -s http://127.0.0.1:8787/health; echo      # deviceConnected should be true

TOKEN='paste the access token'                  # ← change only this
curl -s -D - http://127.0.0.1:8787/mcp -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_screen_state","arguments":{}}}' | grep -E 'Server-Timing|screenOn'

# Without a token it should return 401
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8787/mcp -H 'Content-Type: application/json' -d '{}'
```

## 6. Connect Violet

See [VIOLET_INTEGRATION.md](VIOLET_INTEGRATION.md). The URL is `http://127.0.0.1:8787/mcp` and the request header is `Authorization: Bearer <token>`.

### When Violet is not on the relay's machine

You cannot change `-client-listen` to a plain-text public address (the relay refuses to start). Options:

- **Recommended**: install the relay on the machine Violet runs on.
- Give the relay a real certificate (domain + Let's Encrypt), so the MCP entry uses TLS too:
  1. Edit `ExecStart` in the service file and append `-client-listen :8444 -client-tls -tls-cert /etc/violet-mcp-relay/fullchain.pem -tls-key /etc/violet-mcp-relay/privkey.pem`.
  2. Copy the certificate files to `/etc/violet-mcp-relay/`. The service runs as a DynamicUser and cannot read the root-only private key under `/etc/letsencrypt/live`.
  3. Violet uses `https://your-domain:8444/mcp`.
  4. With a real certificate, the phone's "certificate fingerprint" can be left empty; after the certificate is renewed, the relay loads the new one within a minute.

## 7. Options

| Option | Default | Notes |
|---|---|---|
| `-device-listen` | `:8443` | Phone entry (always TLS) |
| `-client-listen` | `127.0.0.1:8787` | Violet entry; a non-loopback address requires `-client-tls`, otherwise the relay refuses to start |
| `-request-timeout` | `20s` | Longest wait for the phone's response (1s–120s). The default is below the Python SDK's 30-second HTTP timeout, so Violet gets a clear reason |
| `-offline-wait` | `5s` | How long a request waits at most while the phone is briefly offline (e.g. switching networks) (0–30s) |
| `-ping-interval` | `30s` | Interval of the relay's heartbeat to the phone (10s–10m). Shorter detects disconnects faster, but uses more phone battery with the screen off |
| `-read-timeout` | 3 × heartbeat interval | Disconnect when no data at all arrives from the phone for longer than this |
| `-log-level` | `info` | `debug` / `info` / `warn`; no level ever logs tokens or request content |
| `-print-pin` / `-pin-of FILE` | — | Print the public key fingerprint of the current certificate / a given certificate file |

To change options: edit the `ExecStart` line of `/etc/systemd/system/violet-mcp-relay.service`, then run `systemctl daemon-reload && systemctl restart violet-mcp-relay`.

## 8. Changing the certificate (no interruption, no blind trust in the new certificate)

The phone can store up to 3 certificate fingerprints at once. Change the certificate in this order:

```bash
# 1) Generate a new certificate on the VPS (example: a new self-signed one); don't replace the one in use yet
cd /var/lib/violet-mcp-relay
openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes -days 3650 \
  -subj "/CN=violet-mcp-relay" -keyout new.key -out new.crt
/opt/violet-mcp-relay/violet-mcp-relay -pin-of new.crt          # note the new fingerprint
```

2. On the phone: Settings → Certificate fingerprint, paste the new fingerprint **on a new line** below the old one → Save and apply. Now both the old and new certificate can connect.
3. Switch the certificate on the VPS:

```bash
cd /var/lib/violet-mcp-relay
mv selfsigned.crt old.crt && mv selfsigned.key old.key
mv new.crt selfsigned.crt && mv new.key selfsigned.key
chown --reference=old.key selfsigned.crt selfsigned.key   # the service runs as a DynamicUser, so the new files need the same owner
chmod 600 selfsigned.key
systemctl restart violet-mcp-relay
journalctl -u violet-mcp-relay -n 5 --no-pager | grep "certificate pin"   # should show the new fingerprint
```

4. Once the phone has reconnected, delete the old fingerprint line in the settings.

If the phone says "certificate fingerprint mismatch" and you have **not** changed the certificate, do not update the fingerprint: this may mean the connection is being intercepted.
The Security page shows the fingerprint the server presented; check it on the VPS with `-print-pin` first.

## 9. Operations

```bash
journalctl -u violet-mcp-relay -f                 # one line per request: status code, total/rtt/phone/tool timings
curl -s http://127.0.0.1:8787/health; echo      # whether the device is online, the latest request, the latest disconnect reason
systemctl restart violet-mcp-relay                 # the phone reconnects automatically after a restart

# Change the device key (then enter the new key on the phone)
systemctl stop violet-mcp-relay && rm /var/lib/violet-mcp-relay/device.secret && systemctl start violet-mcp-relay
cat /var/lib/violet-mcp-relay/device.secret

# Uninstall
systemctl disable --now violet-mcp-relay && rm -rf /opt/violet-mcp-relay /etc/systemd/system/violet-mcp-relay.service /var/lib/private/violet-mcp-relay
```

## Security essentials

| Requirement | Implementation |
|---|---|
| Authentication required | Phone → relay with the device key (only SHA-256 compared, in constant time); Violet → relay → phone with the MCP Bearer token: the relay first blocks by the token hash the phone reported, and the phone checks again |
| No tools exposed without authentication | The relay has no tools or tool list at all; unauthenticated requests get 401 at the relay and are never forwarded to the phone |
| No insecure listening | Refuses to start on a non-loopback address without TLS; checks again against the actual address after binding |
| No tokens in logs | Only method, path, status code and per-stage timings are logged; the device key is written only to a 0600 file |
| Reconnecting after disconnects | The relay sends a heartbeat every 30 seconds and disconnects after 90 seconds without data; the phone reconnects with exponential backoff 1→60 seconds (±20% jitter); immediately when the network changes; retries pause without a network |
| Controllable timeouts | Request timeout, offline wait, heartbeat and read timeout are all configurable; after a request timeout the relay probes the connection and disconnects if there is no answer within 5 seconds |
| TLS | The phone accepts only `wss://`; self-signed certificates are pinned by fingerprint, real certificates are checked against the system CAs and hostname; unknown certificates are never accepted automatically |
