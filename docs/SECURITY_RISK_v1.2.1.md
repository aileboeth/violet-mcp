# Security risk report (1.2.1-eve)

**English** | [简体中文](SECURITY_RISK_v1.2.1.zh-CN.md)

Date: 2026-10-10. This round only dealt with the remaining problems and added no features.

## 1. Did the signing key leak with the release packages?

**Conclusion: no leak was found.**

This round wrote `tests/check_release_artifacts.py` and did a content-level check of all historical artifacts:

- Scope: the APKs, source packages and relay programs of 1.1.0 and 1.2.0, plus this round's 1.2.1: 16 artifacts, 579 files (including nested packages such as jars).
- What was checked:
  1. forbidden file names;
  2. PEM private key blocks;
  3. JKS / PKCS#12 keystore structures;
  4. SHA-256 comparison with the real files in the local `signing/`;
  5. literal matching of the real local keystore password;
  6. `storePassword=` / `keyPassword=` with a literal value.
- Result: 0 problems.
- Self-test of the script: putting the real `.jks` (including a renamed copy) and `keystore.properties` into a test package, both were detected.

The git index contains no key files either.

| Hardening | Notes |
|---|---|
| `.gitignore` | Excludes `*.jks`, `*.keystore`, `*.p12`, `*.pfx`, `keystore.properties`, `device.secret`, `selfsigned.key` and `.env` across the whole repository |
| `tools/package_release.py` | The only packaging entry:<br>• source takes only files tracked by git, then filters by a blocklist<br>• after packaging it runs the leak check automatically and deletes all artifacts if it fails<br>• refuses to overwrite an existing version directory |
| Handling instructions | [SIGNING_KEY_INCIDENT.md](SIGNING_KEY_INCIDENT.md): when the key must be changed, and how APK v3 key rotation keeps installed users able to upgrade. Rehearsed with a throwaway test key: `rotate` → `sign --lineage --rotation-min-sdk-version 28` → `verify` shows the new certificate as the v3 signer |

## 2. Credential storage (the real problems fixed this round)

1.2.0's `SecretStore` had these problems:

| # | Behaviour in 1.2.0 | Risk | 1.2.1 |
|---|---|---|---|
| P1 | When ciphertext exists but Keystore is unavailable, **returns the plain-text copy directly** | Silently authenticates remotely with plain text | Returns "cannot decrypt" and refuses authentication |
| P2 | When decryption fails, **returns the leftover plain-text copy** | May accept an old credential that has already been replaced | Accepts only ciphertext; when decryption fails it refuses authentication and never falls back to the plain-text copy |
| P3 | When old plain text cannot be migrated, **keeps using plain text** | Silently authenticates remotely with plain text | Keeps the plain text so no configuration is lost, but it cannot be used for authentication until the user explicitly agrees to compatibility mode |
| P4 | When the encrypted write fails or there is no Keystore, **silently writes plain text** | Silent downgrade | Refuses to write and reports an error; plain text is written only after the user agrees to compatibility mode |

### Compatibility mode

- Can only be turned on on the "Security" page: read the risk explanation first, then tick "I understand the risk that credentials will be stored unencrypted" before confirming.
- Once on, the home screen and Security page keep showing a red warning.
- Turning compatibility mode off deletes all plain-text credentials and turns off the relay that uses a plain-text device key.

### Can a leftover plain-text copy still be accepted?

As long as ciphertext exists, the plain-text copy is never used:

- ciphertext decrypts successfully: the leftover plain-text copy is deleted;
- ciphertext fails to decrypt: authentication is refused, with no fallback to the plain-text copy;
- changing the token: the new ciphertext is written and the plain text deleted in the same commit, and the old token stops working immediately.

The relay only caches the token hash the phone reported. After the phone changes its token it reconnects immediately; in the second or two before the reconnection completes, the relay may still let the old token through, but the phone checks again and refuses it.

**Tests**: 16 cases were added or rewritten in Robolectric, covering migration, recovery after interruption, the two must-not-fall-back cases, granting and withdrawing compatibility-mode consent, token rotation, and verifying at the endpoint level that neither the old token nor a leftover token can authenticate.

**Mutation check**: reverting the P2 fix to the 1.2.0 code makes 2 cases fail, showing these cases catch the problem.

## 3. What stays the same

All of the following were tested again this round:

- the strict read-only allowlist (14 tools);
- only 5 tools on by default;
- the WSS relay and certificate pinning;
- MCP SDK 1.30.0 compatibility.

## 4. Remaining risks

| Risk | Level | Notes / mitigation |
|---|---|---|
| Behaviour on real phones not verified | High (uncertainty) | All of these were only tested in simulated environments: Keystore migration, screen-off and boot recovery on vendor systems such as OriginOS, network switching, accuracy of foreground app detection. The checklist is in sections G and H of DEVICE_CHECKLIST.md |
| Devices with a faulty Keystore | Medium | Since 1.2.1 such devices cannot use remote authentication by default, and the user must turn on compatibility mode deliberately. This is an intentional security trade-off at the cost of usability |
| The signing key is stored on the development computer | Medium | The password is stored in plain text in `signing/keystore.properties`. An encrypted offline backup is recommended, with regular checks per SIGNING_KEY_INCIDENT.md §5 |
| Android 8.0 / 8.1 cannot benefit from key rotation | Low | Only matters if the key really leaks; handling in SIGNING_KEY_INCIDENT.md §4.5 |
| The relay can see the token after TLS termination | Low | The relay is deployed on the user's own VPS and listens only locally |
| The local endpoint has no TLS | Low | Listens only on loopback and still needs the 256-bit token |
| The old Python SDK (1.8.0) hangs when the relay is unreachable | Low | A client-side problem; SDK 1.30 with an outer timeout is recommended. 1.8.0 was not re-run this round |
