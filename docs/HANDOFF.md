# Source handoff and building in a clean environment

**English** | [简体中文](HANDOFF.zh-CN.md)

## 1. Do not just zip up the working directory

A developer's working directory contains the following, and **none of it may be handed over**:

| Contents | Why it must not be handed over |
|---|---|
| `signing/`: signing private keys `*.jks`, the password file `rotation.properties`, rotation proofs `lineage-*.bin` | Anyone with the private key and password can sign a "legitimate update", install it over the app and read its data (see [SIGNING_KEY_INCIDENT.md](SIGNING_KEY_INCIDENT.md)) |
| `.git/` | The full history, which may contain things added by mistake and later deleted |
| `local.properties` | Local paths |
| `.gradle/`, `.kotlin/`, `*/build/`, `dist/` | Caches and build output: large, and may hold leftover intermediate files |

It happened once on 2026-10-10: the whole working directory was zipped into `mcp-server.zip` and handed over, including the signing private key and password; the key was rotated afterwards (record in SIGNING_KEY_INCIDENT.md §6).

## 2. Creating a handoff package (the only recommended way)

```bash
python tools/make_handoff.py --version 1.3.0
# Output: dist/handoff/violet-mcp-1.3.0-source-handoff.zip
#         dist/handoff/violet-mcp-1.3.0-source-handoff.scan.json (scan report)
```

The script does the following:

1. **Selects files**: takes only files tracked by git and new files not excluded by `.gitignore`, then filters again with two blocklists.
2. **Writes a manifest**: generates `HANDOFF_MANIFEST.txt` with the SHA-256 of every file.
3. **Scans**: runs a recursive security scan on **the generated ZIP itself**, covering every file in the ZIP and nested zip, jar, apk, tar and gz files:
   - forbidden file and directory names;
   - PEM private keys, JKS / PKCS#12 keystores;
   - password configuration lines;
   - common cloud service and API token formats;
   - byte-for-byte comparison with the real key files on the machine, and literal matching of the real passwords on the machine.

   If anything fails, the ZIP is deleted.

**Re-check** any handoff package by hand:

```bash
python -I -X utf8 tests/check_release_artifacts.py --handoff handoff-package.zip
```

## 3. Building in a clean environment

Needed:

- JDK 17
- Android SDK (platform 35, build-tools 35)
- Go 1.23+
- Python 3.10+ (only for the tests)

```bash
unzip violet-mcp-1.3.0-source-handoff.zip && cd violet-mcp-1.3.0
grep -v '^#' HANDOFF_MANIFEST.txt | sha256sum -c --quiet && echo manifest OK   # verify file integrity

cp config-templates/local.properties.example local.properties    # change to the local SDK path
./gradlew :mcp-core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
# Without a signing configuration the release APK is unsigned (DOES NOT VERIFY is expected)

cd relay && go vet ./... && go test ./... \
  && GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o violet-mcp-relay-linux-amd64 .
```

Windows tip: paths must not contain non-ASCII characters. If your user folder name does, set `GRADLE_USER_HOME` to an ASCII-only path.

When dependencies can only be downloaded through a proxy: Gradle does not read the `HTTP_PROXY` environment variable; use `JAVA_TOOL_OPTIONS="-Dhttps.proxyHost=… -Dhttps.proxyPort=… -Dhttp.proxyHost=… -Dhttp.proxyPort=…"` or settings such as `systemProp.https.proxyHost` in `~/.gradle/gradle.properties`.

**One-step verification** (unpacks into a fresh directory, uses fresh Gradle / Go caches, runs tests and builds, and checks the signing templates with a throwaway key):

```bash
bash tests/verify_handoff_build.sh handoff-package.zip <Android SDK path> <empty ASCII-only work directory>
```

## 4. Signing

The handoff package **contains no real keys**, only the templates in `config-templates/`.

| Situation | What to do |
|---|---|
| Building and installing yourself (a fork) | Copy `config-templates/keystore.properties.example` to `signing/keystore.properties` and fill in a key you generated yourself. APKs signed this way cannot be installed over the official version; uninstall it first |
| Releasing the official version (only the maintainer who holds the key) | Put the offline key back into `signing/`, copy `config-templates/rotation.properties.example` to `signing/rotation.properties` and fill in the password; `gradlew :app:assembleRelease` produces an unsigned package, then run `python tools/sign_release.py <apk>` to sign with v1 + v2 + lineage and verify automatically |

The two files may not exist at the same time, otherwise Gradle fails right away. This prevents signing with only the old key: phones already upgraded to the rotated version would refuse such an update.
