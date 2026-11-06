# Handling a signing key leak

**English** | [简体中文](SIGNING_KEY_INCIDENT.zh-CN.md)

Applies to the release signing key of the Eve read-only MCP: `signing/eve-release.jks` (PKCS#12, EC P-256, alias `eve-release`), with the password stored in `signing/keystore.properties`.

## 1. Status check (2026-10-10)

| Check | Result |
|---|---|
| Historical release packages: 1.1.0 source package and APK, 1.2.0 source package and APK, 5 relay programs | `tests/check_release_artifacts.py dist` checked 11 artifacts and 378 files (including nested packages such as jars) and **found no** signing key, PEM private key block or keystore structure, and no real password |
| git index | Does not contain `signing/`, `*.jks`, `*.keystore`, `keystore.properties` or `local.properties` |
| Self-test of the check script | Putting the real `.jks` and `keystore.properties` into a test zip, or renaming the `.jks` to `renamed.bin`, was always detected |

> **Update (later the same day)**: it was then found that a ZIP of the whole working directory had leaked the key and password; the key has been rotated, see section 6. Below is the conclusion of the check at the time, kept for the record.

**Conclusion (at the time)**: there was no evidence that the signing key had leaked, so it did not need to be changed immediately. This round did hardening:

- `.gitignore` excludes key files across the whole repository, not just the `signing/` directory;
- every release is packaged by `tools/package_release.py`: source takes only files tracked by git, filtered once more by a blocklist, and after packaging a leak check runs automatically and deletes all artifacts if it fails.

Packages already sent out can be re-checked at any time:

```bash
python -I -X utf8 tests/check_release_artifacts.py dist
```

## 2. What a leaked key means

Someone with the signing key (the `.jks` plus its password) can give any APK the same signature as this app, with the same package name `com.mcp.server.eve`. As soon as a user is tricked into installing such an "update":

- it **directly replaces** this app and inherits all of its data;
- it runs under the same UID, so it can use the Android Keystore to decrypt the access token and relay device key this app stored. With those two, it can impersonate your phone to the relay and access this server;
- it can inherit permissions this app was granted, such as usage access.

This app is not in any app store and can only be sideloaded, so an attacker has to get you to install their APK yourself. But once installed, all of the above happens.

## 3. When the key must be changed

| Situation | Action |
|---|---|
| Both the `.jks` and the password leaked. Including: the `signing/` directory, or `keystore.properties` together with the `.jks`, uploaded to cloud storage, a repository, a chat log or a public release package | **Must change**; follow section 4 immediately |
| The development computer was compromised, or someone else had access to it for a long time | **Must change** |
| Only the `.jks` leaked, not the password | The password is a 24-byte random value, so brute-forcing it is not realistic. Changing per section 4 is still recommended, at least soon |
| Only the password leaked, not the `.jks` | Changing the password with `keytool -storepasswd` / `-keypasswd` is enough; the key does not need to change |
| The certificate fingerprint (SHA-256) was published | Nothing to do. The certificate fingerprint is public information anyway |

## 4. Changing the key without breaking upgrades for installed users (APK Signature Scheme v3 key rotation)

Principle: the old key signs a "rotation proof" (lineage) declaring the new key as its successor.

- **Android 9 (API 28) and above**: devices accept updates signed with the new key that carry this proof, and app data is kept. After the new version is installed, **an APK signed only with the old key can no longer be installed over it** (unless the old key is granted the rollback capability; by default it is not).
- **Android 8.0 / 8.1 (API 26–27)**: these systems do not understand v3 signatures and only check v1/v2, which are still the old key. This means that on these devices **whoever holds the old key can still push "legitimate" updates**, and rotation cannot fix that. See the note at the end of this section.

`apksigner` is in `D:\android-sdk-public-build\build-tools\35.0.0\`.

> This section is the general procedure. When it was actually carried out on 2026-10-10, the lineage file was named `signing/lineage-v1-v2.bin`, the password was written to `signing/rotation.properties`, and the signing and verification of 4.3 were done automatically by `tools/sign_release.py`; see section 6.

### 4.1 Generate a new key, and move the old key out of the project directory

```bash
cd D:/Claude/mcp-server
NEWPW=$(python -c "import secrets;print(secrets.token_urlsafe(24))")
keytool -genkeypair -keystore signing/eve-release-2.jks -storetype PKCS12 -alias eve-release-2 \
  -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA -validity 10000 \
  -storepass "$NEWPW" -keypass "$NEWPW" -dname "CN=Eve Read-only MCP, O=Project Eve"
echo "New password (store it offline, then clear the screen): $NEWPW"
```

### 4.2 Sign the rotation proof with the old key

```bash
BT=D:/android-sdk-public-build/build-tools/35.0.0
$BT/apksigner.bat rotate --out signing/lineage-1-2.bin \
  --old-signer --ks signing/eve-release.jks --ks-key-alias eve-release \
  --new-signer --ks signing/eve-release-2.jks --ks-key-alias eve-release-2
```

`apksigner` asks for both passwords interactively.

### 4.3 Build an unsigned release package, then sign it with rotation

1. First rename `signing/keystore.properties` temporarily, so Gradle does not sign.
2. Then build:

```bash
JAVA_HOME=D:/JDK GRADLE_USER_HOME=D:/Claude/.gradle-ascii ./gradlew.bat :app:assembleRelease
APK=app/build/outputs/apk/release/eve-readonly-mcp-<new version>-release.apk
$BT/apksigner.bat sign \
  --ks signing/eve-release.jks --ks-key-alias eve-release \
  --next-signer --ks signing/eve-release-2.jks --ks-key-alias eve-release-2 \
  --lineage signing/lineage-1-2.bin --rotation-min-sdk-version 28 \
  "$APK"
$BT/apksigner.bat verify -v --print-certs "$APK"   # should show the new certificate as the v3 signer, with a lineage
```

Notes:

- `--rotation-min-sdk-version 28` makes every device on Android 9 and above use the new key. Without it, newer apksigner versions apply it only to Android 13 and above by default.
- Every later version must be signed with the same `--lineage`.

### 4.4 Release and wrap-up

1. Release this version as soon as possible and get it onto every phone. Rotation only takes effect on devices that have the new version installed.
2. On each phone, **change the token** on the "Security" page; on the VPS **change device.secret** (commands in DEPLOY_RELAY.md §9); then update the Project Eve configuration. If anyone ever installed a forged update on your phone, the old credentials should be considered leaked.
3. The old key must not be deleted: it is needed to generate the lineage and for any future rotation. Make an encrypted offline backup of it, then remove it from the development computer.
4. Update `signing/keystore.properties`, or switch to a manual signing flow, and state the current certificate's SHA-256 in the README.

### 4.5 Android 8.0 / 8.1 devices

v3 rotation cannot protect these devices. If the key really has leaked, the options are:

- **Recommended**: raise `minSdk` to 28 and ask users on these devices to uninstall and reinstall. Uninstalling clears the local configuration, so the relay details have to be entered again, and the token changes too.
- Or switch to a new `applicationId`, release it as a brand-new app, and uninstall the old one.

## 5. Everyday protection when nothing has leaked

- Keep `signing/` only on the development computer, with one encrypted offline backup (e.g. an encrypted USB drive). **If the key is lost, updates that install over the app can no longer be released**; only a new package name is possible.
- Never post the `signing/` directory, the `.jks` file or the password in chats, cloud storage or issues.
- Release only with `tools/package_release.py`. Check packages built by hand with `tests/check_release_artifacts.py` before releasing.
- Regularly run something like `Get-ChildItem -Recurse -Include *.jks,*.keystore,keystore.properties D:\` in Windows PowerShell to confirm the keys exist only where expected.

## 6. Incident record: 2026-10-10, a ZIP of the whole working directory leaked the signing key

| Item | Details |
|---|---|
| Discovery | The whole working directory was zipped directly into `mcp-server.zip` (about 176 MB, 3768 entries) and given to an outside party. Scanning it with `tests/check_release_artifacts.py --handoff` confirmed it contained `signing/eve-release.jks` and `signing/keystore.properties` (plain-text password), byte for byte identical to the local files. It also contained `.git/` (293 files), build directories such as `.gradle/`, `.kotlin/` and `app/build/`, `dist/` and `local.properties`. |
| Scope | 4437 files scanned (including nested archives); apart from the key and password above, **no** other credentials were found: no MCP access token, relay device key or cloud API key. The token on the phone and the device.secret on the VPS were never stored in the working directory. The git history contains no key files either. |
| Assessment | The private key and password leaked together, which meets the "must change" condition of section 3; the user also confirmed that the ZIP had left the machine. |
| Action | The v3 key rotation of section 4 was carried out:<br>• new key `signing/eve-release-2.jks` (alias `eve-release-2`)<br>• rotation proof `signing/lineage-v1-v2.bin`<br>• the old key's password file renamed to `keystore.properties.v1-retired`, no longer used for builds<br>• new signing configuration `signing/rotation.properties`<br>• when Gradle finds the rotation configuration it produces only an unsigned package, and it may not coexist with a single-key configuration<br>• since 1.2.2-eve signing and automatic verification are done by `tools/sign_release.py` |
| Certificates | Old certificate SHA-256 `52f602d3cf17deff7a9ebfdab12d18007b94211982fbf1c4b94ddf861160b499` (still used by Android 8.x)<br>New certificate SHA-256 `d5a5eeec329d58c4f4aa5ef70694eda249c642ffa9a5723d9a30f5f706af15ea` (used by Android 9+) |
| Prevention | Handoffs always use `tools/make_handoff.py` (see [HANDOFF.md](HANDOFF.md)) and releases always use `tools/package_release.py`; both scan the final ZIP. |

### Still to be done by the user

1. Delete `mcp-server.zip` locally and at every recipient. The leaked copies cannot be recalled, so the rotation is still necessary.
2. Install 1.2.2 over the old version on every phone **as soon as possible**. Rotation only takes effect on Android 9+ phones with 1.2.2 installed: until then, whoever holds the old key can still sign an APK that installs over it.
3. Do not install any "Eve read-only MCP" APK of unknown origin. Official APKs come only from `dist/` and can be checked with `apksigner verify --print-certs`: on Android 9+ it should show the new certificate.
4. Make an encrypted offline backup of the whole `signing/` directory: both keys, the lineage, `rotation.properties`. **If the new key is lost, updates that install over the app can no longer be released** (losing the lineage file is fine: when signing, point `--lineage` at any released APK of 1.2.2 or later; tested and it works). Every release still needs both keys: the old key for the v2 signature for Android 8.x, the new key for v3. Normally `signing/` can be put back on the development computer only for releases and removed afterwards. If `minSdk` is raised to 28, the old key will no longer be needed.
5. Android 8.x devices cannot be protected by the rotation; uninstalling the Eve read-only MCP there, or no longer using it, is recommended.
6. The access token and relay device key did **not** leak with the ZIP, so they do not need to be changed because of this incident. They do need changing if a same-named APK of unknown origin was ever installed on the phone.
