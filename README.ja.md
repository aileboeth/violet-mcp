<div align="center">

# Violet MCP（Android 向け読み取り専用スマホ状態サービス）

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | **日本語** | [한국어](README.ko.md) | [Español](README.es.md) | [Français](README.fr.md) | [Deutsch](README.de.md) | [Русский](README.ru.md) | [Português](README.pt-BR.md)

[![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?logo=android&logoColor=white)](#)
[![MCP](https://img.shields.io/badge/MCP-Streamable_HTTP-blue)](https://modelcontextprotocol.io/)

</div>

Violet MCP は、セルフホスト型の AI コンパニオン **[Violet](https://github.com/aileboeth/violet-companion)** のための、Android 用の読み取り専用 [MCP](https://modelcontextprotocol.io/) サーバーです。スマホに入れると、あなたが許可した範囲で、Violet がスマホの状態を知ることができます。たとえば：

- バッテリー、画面がついているか、使っているアプリ、今日どれくらい使ったか
- 次のアラーム、再生中の曲、ヘッドホンをつけているか
- 家にいるか外出中か、今日の歩数、今日と明日の予定

これで彼女は、あなたがいま何をしているかをよりよく理解できます。たとえば朝はじめてスマホのロックを解除したときに「おはよう」と言えます。**スマホ上の何かを変更することはできません。**

[Cazriel/mcp-server](https://github.com/Cazriel/mcp-server) をもとに、次のように変更しています：

- **残したもの**：Streamable HTTP、Bearer 認証、バックグラウンド動作と起動時の自動開始。
- **削除したもの**：デバイスの状態を変更できる機能すべて。
- **追加したもの**：スマホの状態を感知する読み取り専用ツール。
- **置き換えたもの**：元の平文トンネルを、スマホから自分の VPS に接続する TLS リレーに置き換えました。

現在のバージョン：アプリ **Violet MCP 1.4.3**（パッケージ名 `com.mcp.server.violet`）、リレー **violet-mcp-relay 1.2.0**。

アプリの画面は現在中国語です。

## 特長

- **読み取り専用**：
  - 21 個のツールはすべて許可リストにハードコードされています。
  - コマンド実行、Shizuku、JavaScript、ファイルの読み書き、アプリ操作、クリップボード、通知の読み取り、任意の HTTP リクエストはソースコードから削除済みで、`tools/list` にも出てこず、呼び出すこともできません。
- **ツールごとのスイッチ**：
  - デフォルトでオンなのは機微でない 7 個だけです：バッテリー、画面の状態、フォアグラウンドアプリ、画面情報、ヘルスチェック、次のアラーム、ヘッドホン / Bluetooth。
  - 使用時間、再生中の曲、ネットワークと場所、歩数、カレンダー、インストール済みアプリ、デバイス識別子などは、スマホの「ツールと権限」ページで自分でオンにする必要があります。各ツールには「オンにすると彼女が何を知れるか」が書かれており、システム権限が必要なものには「許可する」ボタンがあります。
  - MCP リクエストからこれらのスイッチは変更できません。
- **推測しない**：画面がオフ、ロック中、または判断できないときは、使っているアプリを推測せず、その理由を返します。
- **場所はあなたが付けた名前だけ**：Wi-Fi に「家」「会社」などの名前を付けられます。Wi-Fi 名（SSID）はスマホ内で照合するだけで、外には送りません。
- **スマホ上の出来事**：
  - ロック解除（画面がどれくらいオフだったか）、充電、ヘッドホンの抜き差し、名前を付けた場所への到着・出発を記録します。
  - Violet が 1 分ごとに取りに来るので、リレーの変更は不要です。
- **VPN もグローバル IP も不要なリモートアクセス**：
  - スマホから `wss://` で VPS 上のリレーに接続します。
  - デバイスキー + MCP トークンの二重認証と証明書のピン留め（証明書の切り替えがしやすいよう、新旧のフィンガープリントを同時に保存できます）。
  - 切断後は自動で再接続し、ネットワークが切り替わるとすぐ再接続します。
  - 1.4.2 から、最大 3 台のリレーサーバーに同時接続でき、それぞれにスイッチがあります。
- **認証情報の安全性**：トークンとデバイスキーは Android Keystore で暗号化して保存し、ローテーション、取り消し、全消去ができます。
- **バックグラウンド動作**：
  - `specialUse` のフォアグラウンドサービスを使います。
  - サービスの状態は「実行中」「制限中」「停止」の 3 つ。手動で止めた後に勝手に再起動されることはありません。
- **アプリ内アップデート**：
  - 設定 → アプリのアップデート で「自動更新」（デフォルト）、「通知のみ」、「手動で確認」を選べます。
  - このリポジトリの GitHub Releases からのみダウンロードし、`SHA256SUMS.txt`、パッケージ名、バージョン番号、署名証明書を照合して、どれか一つでも合わなければインストールしません。
  - 初回は、システム設定でこのアプリに「不明なアプリのインストール」を許可する必要があります。
- **root もユーザー補助の権限も不要。**

> 実機で日常的に使っています。バックグラウンド動作の制限はメーカーごとに違うので、初回インストール後は [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.md)（英語）で一通り確認することをおすすめします：バッテリー無制限、起動時の自動開始、画面オフ後も接続しているか。

## クイックスタート

**1. サーバーにリレーをインストール**

- 1 行コマンドでインストールした Violet の場合（推奨）：Violet 管理画面の「ツールとスマホ」ページで「リレーをインストール」をクリックします。完了すると、スマホに入力するリレーアドレス、デバイスキー、証明書フィンガープリントがコピーボタン付きで表示されます。
- それ以外の場合：[docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.md) に沿って手動でデプロイします。

**2. スマホにインストールして設定**

1. [Releases](https://github.com/aileboeth/violet-mcp/releases/latest) から最新の `violet-mcp-<バージョン>-release.apk` をインストールします（アプリ名は「Violet MCP」）。以降のバージョンはアプリ内で直接更新できます。
2. アプリを開き → 設定 → 権限：「使用状況へのアクセス」を許可し、「バッテリー無制限」もおすすめです。その後「ツールと権限」ページで Violet に公開するツールを選びます。
3. 「リモートアクセス」で手順 1 のリレーアドレス、デバイスキー、証明書フィンガープリントを入力し、接続をオンにします。
4. ホーム画面に戻って **開始** をタップします。

**3. Violet に接続**

スマホの「セキュリティ」ページでアクセストークンをコピーし、Violet 管理画面の「ツールとスマホ」ページに貼り付ければ接続完了です。リレーを手動でデプロイした場合は、[docs/VIOLET_INTEGRATION.md](docs/VIOLET_INTEGRATION.md) に沿って MCP サーバーを追加します。

**ローカルでのデバッグ**（リレーを経由しない）：

```bash
adb forward tcp:1145 tcp:1145
python tests/interop/test_mcp_interop.py --url http://127.0.0.1:1145/mcp --token <トークン> --device
```

## ドキュメント

ドキュメントは英語版と簡体字中国語版があります。

| ドキュメント | 内容 |
|---|---|
| [docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.md) | VPS リレーの手動デプロイ、スマホの設定、証明書の切り替え（コピペで使えるコマンド） |
| [docs/VIOLET_INTEGRATION.md](docs/VIOLET_INTEGRATION.md) | Violet への接続、エラーコード、遅延の切り分け |
| [docs/TOOLS.md](docs/TOOLS.md) | ツール一覧、デフォルトのスイッチ、返すフィールド、必要な Android 権限 |
| [docs/UPGRADE.md](docs/UPGRADE.md) | アップグレードとロールバック（旧 Eve 読み取り専用 MCP から Violet MCP への移行を含む） |
| [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.md) | 実機チェックリスト |
| [docs/HANDOFF.md](docs/HANDOFF.md) | ソースの受け渡し、クリーンな環境でのビルド、署名テンプレート（**作業ディレクトリをそのまま zip にしないこと**） |
| [docs/TEST_REPORT.md](docs/TEST_REPORT.md) | バージョンごとの自動テスト結果 |
| [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md)、[docs/REVIEW_v1.2.md](docs/REVIEW_v1.2.md)、[docs/SECURITY_AUDIT_v1.2.md](docs/SECURITY_AUDIT_v1.2.md)、[docs/SECURITY_RISK_v1.2.1.md](docs/SECURITY_RISK_v1.2.1.md)、[docs/SIGNING_KEY_INCIDENT.md](docs/SIGNING_KEY_INCIDENT.md) | 過去のセキュリティレビューと署名鍵ローテーションの記録（改名前に書かれたため、当時のバージョン名とファイル名を使っています） |

## アーキテクチャ

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

| ディレクトリ | 内容 |
|---|---|
| `mcp-core/` | 純粋な Kotlin/JVM：MCP プロトコル、HTTP サーバー、認証、読み取り専用の許可リスト、リレークライアント、使用時間の計算。アプリと PC 側のテストで共用 |
| `app/` | Android：ツールの実装、フォアグラウンドサービス、起動時の自動開始、UI |
| `relay/` | Go 製リレー `violet-mcp-relay` と systemd の設定 |
| `tests/` | Python MCP SDK による相互運用 / セキュリティテスト、PC でのエンドツーエンドスクリプト、APK の静的チェック |

## エンドポイント

| メソッド | パス | 認証 | 説明 |
|---|---|---|---|
| `POST` | `/mcp` | Bearer | Streamable HTTP（ステートレス、`application/json` で応答。通知には 202） |
| `GET` | `/status` | Bearer | 動作状態（秘密情報は含まない） |
| `GET` | `/health` | なし | `{"ok":true}` だけを返す |
| `GET` / `DELETE` | `/mcp` | Bearer | 405（サーバープッシュのストリームもセッションもなし） |

エンドポイントのセキュリティ上の制限：

- トークンは無効にできません。256 ビットの乱数で、定数時間で比較します。
- `Origin` ヘッダー付きのリクエストはすべて 403 です。
- リクエスト本文は最大 1 MiB、JSON のネストは最大 64 段、バッチリクエストは最大 32 件。
- ローカルエンドポイントはループバックアドレスにしかバインドできません。
- `/mcp` の応答には `Server-Timing` が付き、各段階の所要時間が分かります。

## ソースからのビルド

必要なもの：JDK 17、Android SDK（platform 35、build-tools 35）、Go 1.23+、Python 3.10+（テスト用）。

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

正式なリリースパッケージは `tools/package_release.py` で作成し、秘密情報の漏えいチェックも行います。

Windows での注意：ユーザーフォルダー名に ASCII 以外の文字が含まれていると、Gradle のテストワーカーが起動しないことがあります（`GradleWorkerMain` が見つからない）。`GRADLE_USER_HOME` を ASCII だけのパスに設定してください。例：`set GRADLE_USER_HOME=D:\gradle-home`。

## 謝辞とライセンス

本プロジェクトは [Cazriel/mcp-server](https://github.com/Cazriel/mcp-server)（GPL-3.0）をもとにしています。

2026-10 以降の主な変更：

- 書き込み系ツールとその依存（Shizuku、QuickJS、DocumentFile、Gson）、高権限の宣言を削除し、ツールを読み取り専用の許可リストにしました。
- HTTP トランスポートと認証を書き直しました。
- スマホの状態を感知するツールを追加しました（使用時間、アラーム、音楽、ヘッドホン、ネットワークと場所、歩数、カレンダー、スマホ上の出来事）。
- bore トンネルを TLS リレーに置き換え、複数のリレーに対応しました。
- フォアグラウンドサービスの種類を specialUse に変更しました。
- プライバシースイッチと最小限の出力、Keystore で暗号化した認証情報、待ち受けのセキュリティ制限、リレーのハートビート / タイムアウト / オフライン待機 / 段階別の計時、3 状態のサービス状態、診断ページとセキュリティページ、アプリ内アップデートを追加しました。
- 1.3.0 で Violet MCP に改名しました。

詳しくは [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md) と git の履歴を参照してください。

使用しているもの：[Model Context Protocol](https://modelcontextprotocol.io/)、[OkHttp](https://square.github.io/okhttp/)（Apache-2.0）、[gorilla/websocket](https://github.com/gorilla/websocket)（BSD-2-Clause）、[Jetpack Compose](https://developer.android.com/jetpack/compose)、[kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines)。

本プロジェクトは **GNU General Public License v3.0** で公開しています。全文は [LICENSE](LICENSE) を参照してください。APK を配布する場合は、対応するソースコードも一緒に提供する必要があります。
