<div align="center">

# Violet MCP（Android 唯讀手機狀態服務）

[English](README.md) | [简体中文](README.zh-CN.md) | **繁體中文** | [日本語](README.ja.md) | [한국어](README.ko.md) | [Español](README.es.md) | [Français](README.fr.md) | [Deutsch](README.de.md) | [Русский](README.ru.md) | [Português](README.pt-BR.md)

[![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?logo=android&logoColor=white)](#)
[![MCP](https://img.shields.io/badge/MCP-Streamable_HTTP-blue)](https://modelcontextprotocol.io/)

</div>

Violet MCP 是給 **[Violet](https://github.com/aileboeth-web/violet-companion)**（自架的 AI 陪伴）用的 Android 唯讀 [MCP](https://modelcontextprotocol.io/) 伺服器。裝在手機上以後，Violet 可以在你允許的範圍內知道手機的狀態，例如：

- 電量、螢幕亮著沒、正在用哪個 App、今天用了多久；
- 下一個鬧鐘、正在播的歌、有沒有戴耳機；
- 在家還是在外面、今天走了幾步、今明兩天的行程。

有了這些，她能更了解你此刻在做什麼，例如你早上第一次解鎖手機時，她可以跟你說早安。**它不能修改手機上的任何東西。**

修改自 [Cazriel/mcp-server](https://github.com/Cazriel/mcp-server)，改動如下：

- **保留**：Streamable HTTP、Bearer 驗證、背景執行和開機自動啟動。
- **刪除**：所有會修改裝置狀態的能力。
- **新增**：感知手機狀態的唯讀工具。
- **替換**：原本的明文通道改成「手機主動連到你自己 VPS 的 TLS 中繼」。

目前版本：App **Violet MCP 1.4.2**（套件名稱 `com.mcp.server.violet`），中繼 **violet-mcp-relay 1.2.0**。

App 介面目前是簡體中文。

## 特點

- **唯讀**：
  - 21 個工具全部寫死在白名單裡。
  - 指令執行、Shizuku、JavaScript、檔案讀寫、App 控制、剪貼簿、讀取通知、任意 HTTP 請求都已從原始碼刪除，不會出現在 `tools/list`，也無法呼叫。
- **每個工具個別開關**：
  - 預設只開 7 個不敏感的：電量、螢幕狀態、前景 App、螢幕資訊、健康檢查、下一個鬧鐘、耳機 / 藍牙。
  - 使用時間、正在播的歌、網路與地點、步數、行事曆、已安裝的 App、裝置識別資訊等，要你在手機的「工具與權限」頁自己開啟。每個工具都寫著「開啟後她能知道什麼」，需要系統權限的有「去授權」按鈕。
  - MCP 請求改不了這些開關。
- **不猜測**：螢幕關閉、鎖定或無法確定時，不猜你在用哪個 App，並說明原因。
- **地點只認你取的名字**：你可以替某個 Wi-Fi 取名，例如「家」「公司」。Wi-Fi 名稱（SSID）只在手機裡比對，不會送出去。
- **手機上的動靜**：
  - 能記下解鎖（螢幕關了多久）、充電、插拔耳機、抵達或離開你命名的地點。
  - Violet 每分鐘來取一次，中繼不需要修改。
- **遠端存取不需要 VPN 或公網 IP**：
  - 手機主動用 `wss://` 連到你 VPS 上的中繼。
  - 裝置金鑰 + MCP 權杖雙重驗證，憑證固定（可以同時保存新舊指紋，方便更換憑證）。
  - 斷線自動重連，切換網路立即重連。
  - 1.4.2 起最多可以同時連 3 個中繼伺服器，每個都有自己的開關。
- **憑證安全**：權杖和裝置金鑰用 Android Keystore 加密保存，可以輪換、撤銷、全部清除。
- **背景執行**：
  - 使用 `specialUse` 前景服務。
  - 服務狀態分為執行中、受限、已停止三種；你手動停止後不會被自動拉起。
- **App 內更新**：
  - 設定 → App 更新，可選「自動更新」（預設）、「只提醒」、「手動檢查」。
  - 只從本儲存庫的 GitHub Releases 下載，下載後核對 `SHA256SUMS.txt`、套件名稱、版本號和簽署憑證，任何一項不符都不安裝。
  - 第一次需要在系統設定裡允許本 App「安裝不明應用程式」。
- **不需要 Root，不需要無障礙權限。**

> 已經在實機上日常使用。不同廠商對背景執行的限制不一樣，第一次裝好後建議按 [docs/DEVICE_CHECKLIST.zh-CN.md](docs/DEVICE_CHECKLIST.zh-CN.md)（簡體中文）檢查一遍：電池不受限制、開機自動啟動、螢幕關閉後還連著。

## 快速開始

**1. 在伺服器上安裝中繼**

- 用一行指令安裝的 Violet（建議）：在 Violet 後台「工具與手機」頁點「一鍵安裝中繼」。裝好後，頁面會列出手機要填的中繼位址、裝置金鑰和憑證指紋，每一項都有複製按鈕。
- 其他情況：按 [docs/DEPLOY_RELAY.zh-CN.md](docs/DEPLOY_RELAY.zh-CN.md) 手動部署。

**2. 在手機上安裝和設定**

1. 安裝 [Releases](https://github.com/aileboeth-web/violet-mcp/releases/latest) 裡最新的 `violet-mcp-<版本>-release.apk`（App 名稱「Violet MCP」）。之後的版本可以在 App 裡直接更新。
2. 開啟 App → 設定 → 權限：允許「使用情形存取權」，也建議開啟「電池不受限制」。然後在「工具與權限」頁選好要開放給 Violet 的工具。
3. 在「遠端存取」裡填入第 1 步得到的中繼位址、裝置金鑰、憑證指紋，開啟連線。
4. 回到主頁點 **啟動**。

**3. 連上 Violet**

在手機的「安全」頁複製存取權杖，貼回 Violet 後台「工具與手機」頁，就連上了。手動部署中繼的，按 [docs/VIOLET_INTEGRATION.zh-CN.md](docs/VIOLET_INTEGRATION.zh-CN.md) 新增 MCP 伺服器。

**本機除錯**（不經過中繼）：

```bash
adb forward tcp:1145 tcp:1145
python tests/interop/test_mcp_interop.py --url http://127.0.0.1:1145/mcp --token <權杖> --device
```

## 文件

以下文件有英文版和簡體中文版，這裡連到簡體中文版。

| 文件 | 內容 |
|---|---|
| [docs/DEPLOY_RELAY.zh-CN.md](docs/DEPLOY_RELAY.zh-CN.md) | 手動部署 VPS 中繼、手機設定、更換憑證（可以直接複製的指令） |
| [docs/VIOLET_INTEGRATION.zh-CN.md](docs/VIOLET_INTEGRATION.zh-CN.md) | 接入 Violet、錯誤碼、延遲定位 |
| [docs/TOOLS.zh-CN.md](docs/TOOLS.zh-CN.md) | 工具清單、預設開關、回傳欄位、需要的 Android 權限 |
| [docs/UPGRADE.zh-CN.md](docs/UPGRADE.zh-CN.md) | 升級與回退（包括從舊的 Eve 唯讀 MCP 換到 Violet MCP） |
| [docs/DEVICE_CHECKLIST.zh-CN.md](docs/DEVICE_CHECKLIST.zh-CN.md) | 實機檢查清單 |
| [docs/HANDOFF.zh-CN.md](docs/HANDOFF.zh-CN.md) | 原始碼交接、在全新環境中建置、簽署範本（**不要直接壓縮工作目錄**） |
| [docs/TEST_REPORT.zh-CN.md](docs/TEST_REPORT.zh-CN.md) | 每個版本的自動化測試結果 |
| [docs/SECURITY_REVIEW.zh-CN.md](docs/SECURITY_REVIEW.zh-CN.md)、[docs/REVIEW_v1.2.zh-CN.md](docs/REVIEW_v1.2.zh-CN.md)、[docs/SECURITY_AUDIT_v1.2.zh-CN.md](docs/SECURITY_AUDIT_v1.2.zh-CN.md)、[docs/SECURITY_RISK_v1.2.1.zh-CN.md](docs/SECURITY_RISK_v1.2.1.zh-CN.md)、[docs/SIGNING_KEY_INCIDENT.zh-CN.md](docs/SIGNING_KEY_INCIDENT.zh-CN.md) | 歷次安全審查和簽署金鑰輪換紀錄（寫於改名前，用的是當時的版本名和檔名） |

## 架構

```
┌──────────────── Android 手機（Violet MCP，com.mcp.server.violet）────────┐
│ McpServerService（前景服務 specialUse，開機自動啟動，網路切換監聽）       │
│   ├─ LocalHttpServer  127.0.0.1:1145（僅限本機）                          │
│   └─ RelayClient ×1–3 ═══ wss:// 主動連出（裝置金鑰 + 憑證指紋）═══╗      │
│            ↓ 兩條路徑都進入同一個                                   ║      │
│   McpEndpoint（Bearer 驗證、拒絕帶 Origin 的請求）                  ║      │
│   McpProtocol（JSON-RPC，無狀態）→ ToolRegistry（唯讀白名單）       ║      │
│     → UsageTools / SystemTools / AppTools / LifeTools / MetaTools   ║      │
└─────────────────────────────────────────────────────────────────────╫──────┘
                                                                      ║
┌──────────────── VPS：violet-mcp-relay（Go）────────────────────────╨──────┐
│ 裝置入口（TLS WebSocket，連接埠在安裝時選定）                              │
│ 127.0.0.1:8787  MCP 入口 /mcp ◄── Violet（Python MCP SDK）                 │
│ 未驗證的請求在這裡直接回傳 401，不轉發；中繼本身沒有任何工具               │
└────────────────────────────────────────────────────────────────────────────┘
```

| 目錄 | 內容 |
|---|---|
| `mcp-core/` | 純 Kotlin/JVM：MCP 協定、HTTP 伺服器、驗證、唯讀白名單、中繼用戶端、使用時間計算。App 和電腦端測試共用 |
| `app/` | Android：工具實作、前景服務、開機自動啟動、介面 |
| `relay/` | Go 中繼 `violet-mcp-relay` 和 systemd 設定 |
| `tests/` | Python MCP SDK 互通 / 安全測試、電腦端到端腳本、APK 靜態檢查 |

## 端點

| 方法 | 路徑 | 驗證 | 說明 |
|---|---|---|---|
| `POST` | `/mcp` | Bearer | Streamable HTTP（無狀態，回應 `application/json`；通知回傳 202） |
| `GET` | `/status` | Bearer | 執行狀態（不含金鑰） |
| `GET` | `/health` | 無 | 只回傳 `{"ok":true}` |
| `GET` / `DELETE` | `/mcp` | Bearer | 405（不提供伺服器推送串流，也沒有工作階段） |

各端點的安全限制：

- 權杖無法關閉，是 256 bit 隨機值，以常數時間比較。
- 帶 `Origin` 標頭的請求一律回傳 403。
- 請求內容上限 1 MiB，JSON 巢狀深度上限 64，批次請求上限 32 筆。
- 本機端點只能綁定回送位址。
- `/mcp` 的回應帶有 `Server-Timing`，可以看各階段耗時。

## 從原始碼建置

環境：JDK 17、Android SDK（platform 35、build-tools 35）、Go 1.23+、Python 3.10+（測試用）。

```bash
# 1. Android SDK 路徑
echo "sdk.dir=/path/to/android-sdk" > local.properties

# 2. 發布簽署（選用）。範本在 config-templates/；簽署目錄 signing/ 不進儲存庫，也不進交接包：
#    - 用自己的金鑰：把 config-templates/keystore.properties.example 複製為 signing/keystore.properties
#    - 官方版本（金鑰已輪換）：把 config-templates/rotation.properties.example 複製為 signing/rotation.properties，
#      assembleRelease 產出未簽署的套件後執行 python tools/sign_release.py <apk>
#    兩者都沒有時 release 套件不簽署，可以先用 debug 套件。詳見 docs/HANDOFF.zh-CN.md §4

# 3. 單元測試（mcp-core + Robolectric 模擬環境）+ APK
./gradlew :mcp-core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
#    輸出 app/build/outputs/apk/{debug,release}/violet-mcp-<版本>-<類型>.apk

# 4. 中繼
cd relay && go test ./... \
  && GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o ../dist/violet-mcp-relay-linux-amd64 . \
  && GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o ../dist/violet-mcp-relay-linux-arm64 .
cd ..

# 5. 電腦端到端測試（Go 中繼 ⇄ JVM harness ⇄ Python MCP SDK，不需要手機）
python -m venv .venv && .venv/bin/pip install "mcp>=1.8,<2"
PYTHON=.venv/bin/python bash tests/run_pc_e2e.sh

# 6. APK 靜態檢查（權限、已刪除的元件、危險 API）
python tests/verify_apk.py --apk app/build/outputs/apk/debug/violet-mcp-<版本>-debug.apk \
  --build-tools /path/to/android-sdk/build-tools/35.0.0
```

正式發布套件用 `tools/package_release.py` 產生，它會做洩密檢查。

Windows 提示：如果使用者資料夾名稱含有非 ASCII 字元（例如中文），Gradle 的測試程序可能啟動失敗（找不到 `GradleWorkerMain`）。把 `GRADLE_USER_HOME` 設成純英文路徑即可，例如 `set GRADLE_USER_HOME=D:\gradle-home`。

## 致謝與授權條款

本專案修改自 [Cazriel/mcp-server](https://github.com/Cazriel/mcp-server)（GPL-3.0）。

2026-10 起的主要修改：

- 刪除寫入類工具和相關相依套件（Shizuku、QuickJS、DocumentFile、Gson）以及高權限宣告，工具改為唯讀白名單。
- 重寫 HTTP 傳輸與驗證。
- 新增感知手機狀態的工具（使用時間、鬧鐘、音樂、耳機、網路與地點、步數、行事曆、手機動靜）。
- 以 TLS 中繼取代 bore 通道，並支援多個中繼。
- 前景服務類型改為 specialUse。
- 新增隱私開關和最小化輸出、Keystore 加密憑證、監聽安全限制、中繼心跳 / 逾時 / 離線等待 / 分階段計時、三態服務狀態、診斷頁與安全頁、App 內更新。
- 1.3.0 改名為 Violet MCP。

詳見 [docs/SECURITY_REVIEW.zh-CN.md](docs/SECURITY_REVIEW.zh-CN.md) 和 git 歷史。

同時使用了：[Model Context Protocol](https://modelcontextprotocol.io/)、[OkHttp](https://square.github.io/okhttp/)（Apache-2.0）、[gorilla/websocket](https://github.com/gorilla/websocket)（BSD-2-Clause）、[Jetpack Compose](https://developer.android.com/jetpack/compose)、[kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines)。

本專案以 **GNU General Public License v3.0** 發布，全文見 [LICENSE](LICENSE)。散布 APK 時必須同時提供對應的原始碼。
