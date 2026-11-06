<div align="center">

# Violet MCP (état du téléphone en lecture seule, pour Android)

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Español](README.es.md) | **Français** | [Deutsch](README.de.md) | [Русский](README.ru.md) | [Português](README.pt-BR.md)

[![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?logo=android&logoColor=white)](#)
[![MCP](https://img.shields.io/badge/MCP-Streamable_HTTP-blue)](https://modelcontextprotocol.io/)

</div>

Violet MCP est un serveur [MCP](https://modelcontextprotocol.io/) Android en lecture seule, conçu pour **[Violet](https://github.com/aileboeth/violet-companion)**, une compagne IA auto-hébergée. Une fois installé sur votre téléphone, Violet peut connaître l'état du téléphone dans les limites que vous autorisez, par exemple :

- la batterie, si l'écran est allumé, l'app utilisée et combien de temps vous l'avez utilisée aujourd'hui ;
- la prochaine alarme, le morceau en cours, si un casque est branché ;
- si vous êtes chez vous ou dehors, combien de pas vous avez faits aujourd'hui, votre agenda d'aujourd'hui et de demain.

Elle comprend ainsi mieux ce que vous faites en ce moment ; par exemple, elle peut vous dire bonjour la première fois que vous déverrouillez votre téléphone le matin. **Il ne peut rien modifier sur le téléphone.**

Basé sur [Cazriel/mcp-server](https://github.com/Cazriel/mcp-server), avec ces changements :

- **Conservé** : Streamable HTTP, authentification Bearer, fonctionnement en arrière-plan et démarrage au boot.
- **Supprimé** : tout ce qui peut modifier l'état de l'appareil.
- **Ajouté** : des outils en lecture seule qui perçoivent l'état du téléphone.
- **Remplacé** : le tunnel d'origine en clair devient un relais TLS sur votre propre VPS, auquel le téléphone se connecte.

Version actuelle : app **Violet MCP 1.4.3** (paquet `com.mcp.server.violet`), relais **violet-mcp-relay 1.2.0**.

L'interface de l'app est pour l'instant en chinois.

## Fonctionnalités

- **Lecture seule** :
  - Les 21 outils sont codés en dur dans une liste autorisée.
  - L'exécution de commandes, Shizuku, JavaScript, la lecture et l'écriture de fichiers, le contrôle des apps, le presse-papiers, la lecture des notifications et les requêtes HTTP arbitraires ont été retirés du code source. Ils n'apparaissent pas dans `tools/list` et ne peuvent pas être appelés.
- **Un interrupteur par outil** :
  - Par défaut, seuls 7 outils non sensibles sont activés : batterie, état de l'écran, app au premier plan, infos d'écran, vérification d'état, prochaine alarme, casque / Bluetooth.
  - Le temps d'utilisation, la musique en cours, le réseau et le lieu, les pas, l'agenda, les apps installées, les identifiants de l'appareil et d'autres doivent être activés par vous sur la page Outils et autorisations du téléphone. Chaque outil indique ce qu'elle pourra savoir une fois activé, et ceux qui demandent une autorisation système ont un bouton Accorder.
  - Les requêtes MCP ne peuvent pas modifier ces interrupteurs.
- **Pas de devinettes** : écran éteint, téléphone verrouillé ou réponse incertaine, il ne devine pas quelle app vous utilisez et explique pourquoi.
- **Des lieux uniquement par les noms que vous donnez** : vous pouvez nommer un réseau Wi-Fi, par exemple « maison » ou « bureau ». Le nom du Wi-Fi (SSID) n'est comparé que sur le téléphone et n'est jamais envoyé.
- **Événements du téléphone** :
  - Il enregistre les déverrouillages (et la durée pendant laquelle l'écran était éteint), la charge, le branchement ou débranchement d'un casque, et l'arrivée dans un lieu nommé ou le départ.
  - Violet les récupère une fois par minute ; le relais n'a pas besoin d'être modifié.
- **Accès à distance sans VPN ni IP publique** :
  - Le téléphone se connecte en `wss://` au relais sur votre VPS.
  - Double authentification par clé d'appareil et jeton MCP, plus épinglage du certificat (l'ancienne et la nouvelle empreinte peuvent être enregistrées ensemble pour faciliter un changement de certificat).
  - Reconnexion automatique après une coupure, et immédiate lors d'un changement de réseau.
  - Depuis la 1.4.2, il peut se connecter à jusqu'à 3 serveurs relais à la fois, chacun avec son propre interrupteur.
- **Identifiants protégés** : le jeton et la clé d'appareil sont stockés chiffrés avec Android Keystore et peuvent être renouvelés, révoqués ou effacés.
- **Fonctionnement en arrière-plan** :
  - Utilise un service de premier plan `specialUse`.
  - Le service a trois états : en marche, restreint et arrêté. Une fois arrêté à la main, il ne redémarre pas tout seul.
- **Mises à jour dans l'app** :
  - Paramètres → Mises à jour de l'app : choisissez « automatique » (par défaut), « prévenir seulement » ou « vérifier manuellement ».
  - Télécharge uniquement depuis les GitHub Releases de ce dépôt, puis vérifie `SHA256SUMS.txt`, le nom du paquet, le numéro de version et le certificat de signature ; si l'un d'eux ne correspond pas, rien n'est installé.
  - La première fois, il faut autoriser cette app à « installer des apps inconnues » dans les paramètres du système.
- **Ni root, ni autorisation d'accessibilité.**

> Utilisé au quotidien sur de vrais téléphones. Chaque fabricant limite différemment les apps en arrière-plan, il vaut donc la peine, après la première installation, de parcourir [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.md) (en anglais) : batterie sans restriction, démarrage au boot, connexion maintenue écran éteint.

## Démarrage rapide

**1. Installer le relais sur votre serveur**

- Violet installé avec la commande d'une ligne (recommandé) : sur la page Outils et téléphone du panneau de Violet, cliquez sur Installer le relais. Une fois terminé, la page affiche l'adresse du relais, la clé d'appareil et l'empreinte du certificat à saisir sur le téléphone, chacune avec un bouton de copie.
- Sinon : déployez-le à la main en suivant [docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.md).

**2. Installer et configurer l'app sur le téléphone**

1. Installez le dernier `violet-mcp-<version>-release.apk` depuis les [Releases](https://github.com/aileboeth/violet-mcp/releases/latest) (l'app s'appelle « Violet MCP »). Les versions suivantes peuvent être mises à jour depuis l'app.
2. Ouvrez l'app → Paramètres → Autorisations : autorisez l'« accès aux données d'utilisation » ; activer « batterie sans restriction » est également recommandé. Choisissez ensuite sur la page Outils et autorisations les outils à partager avec Violet.
3. Sous Accès à distance, saisissez l'adresse du relais, la clé d'appareil et l'empreinte du certificat de l'étape 1, puis activez la connexion.
4. Revenez à l'écran d'accueil et touchez **Démarrer**.

**3. Le connecter à Violet**

Copiez le jeton d'accès sur la page Sécurité du téléphone et collez-le dans la page Outils et téléphone du panneau de Violet. C'est tout. Si vous avez déployé le relais à la main, ajoutez le serveur MCP comme expliqué dans [docs/VIOLET_INTEGRATION.md](docs/VIOLET_INTEGRATION.md).

**Débogage local** (sans le relais) :

```bash
adb forward tcp:1145 tcp:1145
python tests/interop/test_mcp_interop.py --url http://127.0.0.1:1145/mcp --token <jeton> --device
```

## Documentation

Les documents existent en anglais et en chinois simplifié.

| Document | Contenu |
|---|---|
| [docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.md) | Déployer le relais VPS à la main, configurer le téléphone, changer de certificat (commandes à copier-coller) |
| [docs/VIOLET_INTEGRATION.md](docs/VIOLET_INTEGRATION.md) | Connexion à Violet, codes d'erreur, localiser la latence |
| [docs/TOOLS.md](docs/TOOLS.md) | Liste des outils, interrupteurs par défaut, champs renvoyés, autorisations Android nécessaires |
| [docs/UPGRADE.md](docs/UPGRADE.md) | Mettre à jour et revenir en arrière (y compris passer de l'ancien Eve read-only MCP à Violet MCP) |
| [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.md) | Liste de vérification sur de vrais téléphones |
| [docs/HANDOFF.md](docs/HANDOFF.md) | Transmission du code source, compilation dans un environnement propre, modèles de signature (**ne zippez pas simplement le répertoire de travail**) |
| [docs/TEST_REPORT.md](docs/TEST_REPORT.md) | Résultats des tests automatisés de chaque version |
| [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md), [docs/REVIEW_v1.2.md](docs/REVIEW_v1.2.md), [docs/SECURITY_AUDIT_v1.2.md](docs/SECURITY_AUDIT_v1.2.md), [docs/SECURITY_RISK_v1.2.1.md](docs/SECURITY_RISK_v1.2.1.md), [docs/SIGNING_KEY_INCIDENT.md](docs/SIGNING_KEY_INCIDENT.md) | Revues de sécurité passées et registre de la rotation de la clé de signature (écrits avant le changement de nom, donc avec les noms de version et de fichier de l'époque) |

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

| Répertoire | Contenu |
|---|---|
| `mcp-core/` | Kotlin/JVM pur : protocole MCP, serveur HTTP, authentification, liste autorisée en lecture seule, client du relais, calcul du temps d'utilisation. Partagé par l'app et les tests sur PC |
| `app/` | Android : implémentation des outils, service de premier plan, démarrage au boot, interface |
| `relay/` | Le relais Go `violet-mcp-relay` et sa configuration systemd |
| `tests/` | Tests d'interopérabilité / de sécurité avec le SDK Python MCP, scripts de bout en bout sur PC, vérifications statiques de l'APK |

## Points d'accès

| Méthode | Chemin | Authentification | Remarques |
|---|---|---|---|
| `POST` | `/mcp` | Bearer | Streamable HTTP (sans état, répond en `application/json` ; les notifications reçoivent 202) |
| `GET` | `/status` | Bearer | État du service (sans secrets) |
| `GET` | `/health` | aucune | Renvoie seulement `{"ok":true}` |
| `GET` / `DELETE` | `/mcp` | Bearer | 405 (pas de flux poussé par le serveur, pas de sessions) |

Limites de sécurité des points d'accès :

- Le jeton ne peut pas être désactivé. C'est une valeur aléatoire de 256 bits comparée en temps constant.
- Toute requête avec un en-tête `Origin` reçoit 403.
- Corps de requête jusqu'à 1 Mio, imbrication JSON jusqu'à 64 niveaux, requêtes groupées jusqu'à 32 éléments.
- Le point d'accès local ne peut se lier qu'à une adresse de bouclage.
- Les réponses de `/mcp` portent `Server-Timing`, qui indique la durée de chaque étape.

## Compiler depuis les sources

Prérequis : JDK 17, Android SDK (platform 35, build-tools 35), Go 1.23+, Python 3.10+ (pour les tests).

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

Les paquets officiels sont produits par `tools/package_release.py`, qui vérifie aussi l'absence de fuite de secrets.

Astuce Windows : si le nom de votre dossier utilisateur contient des caractères non ASCII, les processus de test de Gradle peuvent ne pas démarrer (`GradleWorkerMain` introuvable). Définissez `GRADLE_USER_HOME` sur un chemin uniquement ASCII, par exemple `set GRADLE_USER_HOME=D:\gradle-home`.

## Remerciements et licence

Ce projet est basé sur [Cazriel/mcp-server](https://github.com/Cazriel/mcp-server) (GPL-3.0).

Principaux changements depuis 2026-10 :

- Suppression des outils d'écriture et de leurs dépendances (Shizuku, QuickJS, DocumentFile, Gson) ainsi que des déclarations d'autorisations élevées ; les outils sont devenus une liste autorisée en lecture seule.
- Réécriture du transport HTTP et de l'authentification.
- Ajout d'outils qui perçoivent l'état du téléphone (temps d'utilisation, alarme, musique, casque, réseau et lieu, pas, agenda, événements du téléphone).
- Remplacement du tunnel bore par un relais TLS, avec prise en charge de plusieurs relais.
- Passage du type de service de premier plan à specialUse.
- Ajout d'interrupteurs de confidentialité et d'une sortie minimale, d'identifiants chiffrés par Keystore, de limites de sécurité sur l'écoute, du battement de cœur / délais / attente hors ligne / mesure par étape du relais, d'un état de service à trois états, de pages de diagnostic et de sécurité, et des mises à jour dans l'app.
- Renommé Violet MCP en 1.3.0.

Détails dans [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md) et l'historique git.

Utilise aussi : [Model Context Protocol](https://modelcontextprotocol.io/), [OkHttp](https://square.github.io/okhttp/) (Apache-2.0), [gorilla/websocket](https://github.com/gorilla/websocket) (BSD-2-Clause), [Jetpack Compose](https://developer.android.com/jetpack/compose), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines).

Ce projet est publié sous la **GNU General Public License v3.0** ; le texte complet est dans [LICENSE](LICENSE). Si vous distribuez l'APK, vous devez aussi fournir le code source correspondant.
