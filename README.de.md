<div align="center">

# Violet MCP (Handyzustand nur lesend, für Android)

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Español](README.es.md) | [Français](README.fr.md) | **Deutsch** | [Русский](README.ru.md) | [Português](README.pt-BR.md)

[![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?logo=android&logoColor=white)](#)
[![MCP](https://img.shields.io/badge/MCP-Streamable_HTTP-blue)](https://modelcontextprotocol.io/)

</div>

Violet MCP ist ein nur lesender [MCP](https://modelcontextprotocol.io/)-Server für Android, gedacht für **[Violet](https://github.com/aileboeth-web/violet-companion)**, eine selbst gehostete KI-Begleiterin. Sobald er auf deinem Handy ist, kann Violet innerhalb der Grenzen, die du erlaubst, den Zustand des Handys kennen, zum Beispiel:

- Akku, ob der Bildschirm an ist, welche App du nutzt und wie lange heute schon;
- den nächsten Wecker, den gerade laufenden Song, ob Kopfhörer verbunden sind;
- ob du zu Hause oder unterwegs bist, wie viele Schritte du heute gegangen bist, deinen Kalender für heute und morgen.

So versteht sie besser, was du gerade machst; zum Beispiel kann sie dir guten Morgen sagen, wenn du morgens zum ersten Mal dein Handy entsperrst. **Er kann auf dem Handy nichts verändern.**

Basiert auf [Cazriel/mcp-server](https://github.com/Cazriel/mcp-server), mit diesen Änderungen:

- **Behalten**: Streamable HTTP, Bearer-Authentifizierung, Betrieb im Hintergrund und Start beim Booten.
- **Entfernt**: alles, was den Zustand des Geräts verändern kann.
- **Hinzugefügt**: nur lesende Werkzeuge, die den Zustand des Handys erfassen.
- **Ersetzt**: Der ursprüngliche Klartext-Tunnel wurde durch ein TLS-Relay auf deinem eigenen VPS ersetzt, zu dem sich das Handy verbindet.

Aktuelle Version: App **Violet MCP 1.4.2** (Paket `com.mcp.server.violet`), Relay **violet-mcp-relay 1.2.0**.

Die Oberfläche der App ist derzeit auf Chinesisch.

## Funktionen

- **Nur lesend**:
  - Alle 21 Werkzeuge sind fest in einer Erlaubnisliste kodiert.
  - Befehlsausführung, Shizuku, JavaScript, Lesen und Schreiben von Dateien, App-Steuerung, Zwischenablage, Lesen von Benachrichtigungen und beliebige HTTP-Anfragen wurden aus dem Quellcode entfernt. Sie erscheinen nicht in `tools/list` und lassen sich nicht aufrufen.
- **Ein Schalter pro Werkzeug**:
  - Standardmäßig sind nur 7 unkritische Werkzeuge an: Akku, Bildschirmzustand, Vordergrund-App, Bildschirminfo, Statusprüfung, nächster Wecker, Kopfhörer / Bluetooth.
  - Nutzungszeit, laufende Musik, Netzwerk und Ort, Schritte, Kalender, installierte Apps, Gerätekennungen und andere musst du selbst auf der Seite Werkzeuge & Berechtigungen des Handys einschalten. Bei jedem Werkzeug steht, was sie damit erfahren kann, und für die, die eine Systemberechtigung brauchen, gibt es eine Schaltfläche Erteilen.
  - MCP-Anfragen können diese Schalter nicht ändern.
- **Kein Raten**: Wenn der Bildschirm aus ist, das Handy gesperrt ist oder es unklar ist, rät er nicht, welche App du nutzt, und sagt, warum.
- **Orte nur unter deinen Namen**: Du kannst einem WLAN einen Namen geben, etwa „Zuhause" oder „Büro". Der WLAN-Name (SSID) wird nur auf dem Handy verglichen und nie verschickt.
- **Ereignisse auf dem Handy**:
  - Er zeichnet Entsperrungen (und wie lange der Bildschirm aus war), Laden, Ein- und Ausstecken von Kopfhörern sowie Ankunft an einem benannten Ort oder Verlassen auf.
  - Violet holt sie einmal pro Minute ab; das Relay muss nicht geändert werden.
- **Fernzugriff ohne VPN oder öffentliche IP**:
  - Das Handy verbindet sich per `wss://` mit dem Relay auf deinem VPS.
  - Zwei-Faktor-Authentifizierung mit Geräteschlüssel und MCP-Token, dazu Certificate Pinning (alter und neuer Fingerabdruck können gleichzeitig gespeichert werden, damit ein Zertifikatswechsel einfach ist).
  - Verbindet sich nach einem Abbruch automatisch neu, bei einem Netzwechsel sofort.
  - Seit 1.4.2 kann es sich mit bis zu 3 Relay-Servern gleichzeitig verbinden, jeder mit eigenem Schalter.
- **Sichere Zugangsdaten**: Token und Geräteschlüssel werden mit dem Android Keystore verschlüsselt gespeichert und können erneuert, widerrufen oder gelöscht werden.
- **Betrieb im Hintergrund**:
  - Nutzt einen Vordergrunddienst vom Typ `specialUse`.
  - Der Dienst hat drei Zustände: läuft, eingeschränkt und gestoppt. Wenn du ihn von Hand stoppst, startet er nicht von selbst neu.
- **Updates in der App**:
  - Einstellungen → App-Updates: „automatisch" (Standard), „nur benachrichtigen" oder „manuell prüfen".
  - Lädt nur von den GitHub Releases dieses Repositorys herunter und prüft dann `SHA256SUMS.txt`, Paketname, Versionsnummer und Signaturzertifikat; stimmt eines davon nicht, wird nichts installiert.
  - Beim ersten Mal musst du dieser App in den Systemeinstellungen erlauben, „unbekannte Apps zu installieren".
- **Kein Root, keine Bedienungshilfen-Berechtigung.**

> Täglich auf echten Handys im Einsatz. Jeder Hersteller schränkt Hintergrund-Apps anders ein, daher lohnt es sich, nach der ersten Installation [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.md) (auf Englisch) durchzugehen: Akku ohne Einschränkung, Start beim Booten, Verbindung bleibt bei ausgeschaltetem Bildschirm bestehen.

## Schnellstart

**1. Das Relay auf deinem Server installieren**

- Violet wurde mit dem Einzeiler installiert (empfohlen): Klicke im Violet-Panel auf der Seite Werkzeuge & Handy auf Relay installieren. Danach zeigt die Seite Relay-Adresse, Geräteschlüssel und Zertifikat-Fingerabdruck, die auf dem Handy einzutragen sind, jeweils mit Kopier-Schaltfläche.
- Andernfalls: Richte es von Hand nach [docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.md) ein.

**2. Die App auf dem Handy installieren und einrichten**

1. Installiere die neueste `violet-mcp-<version>-release.apk` aus den [Releases](https://github.com/aileboeth-web/violet-mcp/releases/latest) (die App heißt „Violet MCP"). Spätere Versionen lassen sich direkt in der App aktualisieren.
2. Öffne die App → Einstellungen → Berechtigungen: erlaube den „Zugriff auf Nutzungsdaten"; „Akku ohne Einschränkung" einzuschalten wird ebenfalls empfohlen. Wähle dann auf der Seite Werkzeuge & Berechtigungen die Werkzeuge, die du mit Violet teilen willst.
3. Trage unter Fernzugriff Relay-Adresse, Geräteschlüssel und Zertifikat-Fingerabdruck aus Schritt 1 ein und schalte die Verbindung ein.
4. Geh zurück zum Startbildschirm und tippe auf **Starten**.

**3. Mit Violet verbinden**

Kopiere das Zugriffstoken auf der Seite Sicherheit des Handys und füge es auf der Seite Werkzeuge & Handy im Violet-Panel ein. Fertig. Wenn du das Relay von Hand eingerichtet hast, füge den MCP-Server wie in [docs/VIOLET_INTEGRATION.md](docs/VIOLET_INTEGRATION.md) beschrieben hinzu.

**Lokales Debugging** (ohne Relay):

```bash
adb forward tcp:1145 tcp:1145
python tests/interop/test_mcp_interop.py --url http://127.0.0.1:1145/mcp --token <token> --device
```

## Dokumentation

Die Dokumente gibt es auf Englisch und vereinfachtem Chinesisch.

| Dokument | Inhalt |
|---|---|
| [docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.md) | VPS-Relay von Hand einrichten, Handy einrichten, Zertifikate wechseln (Befehle zum Kopieren) |
| [docs/VIOLET_INTEGRATION.md](docs/VIOLET_INTEGRATION.md) | Anbindung an Violet, Fehlercodes, Latenz eingrenzen |
| [docs/TOOLS.md](docs/TOOLS.md) | Werkzeugliste, Standardschalter, zurückgegebene Felder, nötige Android-Berechtigungen |
| [docs/UPGRADE.md](docs/UPGRADE.md) | Aktualisieren und Zurückrollen (einschließlich des Wechsels vom alten Eve read-only MCP zu Violet MCP) |
| [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.md) | Checkliste für echte Handys |
| [docs/HANDOFF.md](docs/HANDOFF.md) | Übergabe des Quellcodes, Bauen in einer sauberen Umgebung, Signaturvorlagen (**das Arbeitsverzeichnis nicht einfach zippen**) |
| [docs/TEST_REPORT.md](docs/TEST_REPORT.md) | Ergebnisse der automatischen Tests jeder Version |
| [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md), [docs/REVIEW_v1.2.md](docs/REVIEW_v1.2.md), [docs/SECURITY_AUDIT_v1.2.md](docs/SECURITY_AUDIT_v1.2.md), [docs/SECURITY_RISK_v1.2.1.md](docs/SECURITY_RISK_v1.2.1.md), [docs/SIGNING_KEY_INCIDENT.md](docs/SIGNING_KEY_INCIDENT.md) | Frühere Sicherheitsprüfungen und das Protokoll zur Erneuerung des Signaturschlüssels (vor der Umbenennung geschrieben, daher mit den damaligen Versions- und Dateinamen) |

## Architektur

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

| Verzeichnis | Inhalt |
|---|---|
| `mcp-core/` | Reines Kotlin/JVM: MCP-Protokoll, HTTP-Server, Authentifizierung, nur lesende Erlaubnisliste, Relay-Client, Berechnung der Nutzungszeit. Gemeinsam genutzt von App und PC-Tests |
| `app/` | Android: Implementierung der Werkzeuge, Vordergrunddienst, Start beim Booten, Oberfläche |
| `relay/` | Das Go-Relay `violet-mcp-relay` und seine systemd-Konfiguration |
| `tests/` | Interop- / Sicherheitstests mit dem Python MCP SDK, PC-Ende-zu-Ende-Skripte, statische APK-Prüfungen |

## Endpunkte

| Methode | Pfad | Authentifizierung | Hinweise |
|---|---|---|---|
| `POST` | `/mcp` | Bearer | Streamable HTTP (zustandslos, antwortet mit `application/json`; Benachrichtigungen erhalten 202) |
| `GET` | `/status` | Bearer | Dienststatus (ohne Geheimnisse) |
| `GET` | `/health` | keine | Gibt nur `{"ok":true}` zurück |
| `GET` / `DELETE` | `/mcp` | Bearer | 405 (kein Server-Push-Stream und keine Sitzungen) |

Sicherheitsgrenzen der Endpunkte:

- Das Token lässt sich nicht abschalten. Es ist ein 256-Bit-Zufallswert und wird in konstanter Zeit verglichen.
- Jede Anfrage mit `Origin`-Header erhält 403.
- Anfragekörper bis 1 MiB, JSON-Verschachtelung bis 64 Ebenen, Batch-Anfragen bis 32 Einträge.
- Der lokale Endpunkt kann sich nur an eine Loopback-Adresse binden.
- Antworten von `/mcp` tragen `Server-Timing`, das die Dauer jeder Stufe zeigt.

## Aus dem Quellcode bauen

Voraussetzungen: JDK 17, Android SDK (platform 35, build-tools 35), Go 1.23+, Python 3.10+ (für die Tests).

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

Offizielle Release-Pakete entstehen mit `tools/package_release.py`, das auch auf durchgesickerte Geheimnisse prüft.

Tipp für Windows: Wenn der Name deines Benutzerordners Nicht-ASCII-Zeichen enthält, starten die Test-Worker von Gradle eventuell nicht (`GradleWorkerMain` nicht gefunden). Setze `GRADLE_USER_HOME` auf einen reinen ASCII-Pfad, z. B. `set GRADLE_USER_HOME=D:\gradle-home`.

## Danksagung und Lizenz

Dieses Projekt basiert auf [Cazriel/mcp-server](https://github.com/Cazriel/mcp-server) (GPL-3.0).

Wichtigste Änderungen seit 2026-10:

- Die schreibenden Werkzeuge und ihre Abhängigkeiten (Shizuku, QuickJS, DocumentFile, Gson) sowie die Deklarationen hoher Berechtigungen wurden entfernt; die Werkzeuge wurden zu einer nur lesenden Erlaubnisliste.
- HTTP-Transport und Authentifizierung wurden neu geschrieben.
- Werkzeuge hinzugefügt, die den Zustand des Handys erfassen (Nutzungszeit, Wecker, Musik, Kopfhörer, Netzwerk und Ort, Schritte, Kalender, Handy-Ereignisse).
- Der bore-Tunnel wurde durch ein TLS-Relay ersetzt, mit Unterstützung für mehrere Relays.
- Der Typ des Vordergrunddienstes wurde auf specialUse geändert.
- Hinzugefügt: Datenschutzschalter und minimale Ausgabe, mit dem Keystore verschlüsselte Zugangsdaten, Sicherheitsgrenzen für das Lauschen, Heartbeat / Zeitlimits / Offline-Wartezeit / Messung pro Stufe beim Relay, drei Dienstzustände, Diagnose- und Sicherheitsseiten sowie Updates in der App.
- In 1.3.0 in Violet MCP umbenannt.

Details in [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md) und im git-Verlauf.

Verwendet außerdem: [Model Context Protocol](https://modelcontextprotocol.io/), [OkHttp](https://square.github.io/okhttp/) (Apache-2.0), [gorilla/websocket](https://github.com/gorilla/websocket) (BSD-2-Clause), [Jetpack Compose](https://developer.android.com/jetpack/compose), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines).

Dieses Projekt steht unter der **GNU General Public License v3.0**; den vollständigen Text findest du in [LICENSE](LICENSE). Wer die APK weitergibt, muss auch den zugehörigen Quellcode bereitstellen.
