<div align="center">

# Violet MCP（Android 只读手机状态服务）

[English](README.md) | **简体中文** | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Español](README.es.md) | [Français](README.fr.md) | [Deutsch](README.de.md) | [Русский](README.ru.md) | [Português](README.pt-BR.md)

[![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?logo=android&logoColor=white)](#)
[![MCP](https://img.shields.io/badge/MCP-Streamable_HTTP-blue)](https://modelcontextprotocol.io/)

</div>

Violet MCP 是给 **[Violet](https://github.com/aileboeth/violet-companion)**（自己部署的 AI 陪伴）用的 Android 只读 [MCP](https://modelcontextprotocol.io/) 服务器。装在手机上以后，Violet 可以在你允许的范围内知道手机的状态，比如：

- 电量、屏幕亮没亮、正在用什么 App、今天用了多久；
- 下一个闹钟、正在放的歌、戴没戴耳机；
- 在家还是在外面、今天走了多少步、今明两天的日程。

有了这些，她能更懂你此刻在做什么，比如你早上第一次解锁手机时，她可以跟你说早安。**它不能修改手机上的任何东西。**

修改自 [Cazriel/mcp-server](https://github.com/Cazriel/mcp-server)，改动如下：

- **保留**：Streamable HTTP、Bearer 鉴权、后台运行和开机自启。
- **删除**：所有会修改设备状态的能力。
- **新增**：感知手机状态的只读工具。
- **替换**：原来的明文隧道改成“手机主动连接你自己 VPS 的 TLS 中继”。

当前版本：应用 **Violet MCP 1.4.4**（包名 `com.mcp.server.violet`），中继 **violet-mcp-relay 1.2.0**。

## 特点

- **只读**：
  - 21 个工具全部写死在白名单里。
  - 命令执行、Shizuku、JavaScript、文件读写、应用控制、剪贴板、读通知、任意 HTTP 请求都已经从源码里删掉，既不出现在 `tools/list`，也无法调用。
- **每个工具单独开关**：
  - 默认只开 7 个不敏感的：电量、屏幕状态、前台应用、屏幕信息、健康检查、下一个闹钟、耳机 / 蓝牙。
  - 使用时长、正在放的歌、网络和地点、步数、日程、已装应用、设备标识等，要你在手机的“工具与权限”页自己打开。每个工具都写着“打开后她能知道什么”，需要系统权限的有“去授权”按钮。
  - MCP 请求改不了这些开关。
- **不猜测**：熄屏、锁屏或者无法确定时，不猜你在用哪个 App，并说明原因。
- **地点只认你起的名字**：你可以给某个 Wi-Fi 起名，比如“家”“公司”。Wi-Fi 名称（SSID）只在手机里比对，不会发出去。
- **手机上的动静**：
  - 能记下解锁（熄屏了多久）、充电、插拔耳机、到达或离开你命名的地点。
  - Violet 每分钟来取一次，中继不需要改。
- **远程访问不需要 VPN 或公网 IP**：
  - 手机主动用 `wss://` 连接你 VPS 上的中继。
  - 设备密钥 + MCP 令牌双重认证，证书固定（可以同时存新旧指纹，方便换证书）。
  - 断线自动重连，切换网络立即重连。
  - 1.4.2 起最多可以同时连 3 个中继服务器，每个都有自己的开关。
- **凭据安全**：令牌和设备密钥用 Android Keystore 加密保存，可以轮换、撤销、全部清除。
- **后台运行**：
  - 用 `specialUse` 前台服务。
  - 服务状态分运行、受限、已停止三种；你手动停止后不会被自动拉起来。
- **应用内更新**：
  - 设置 → 应用更新，可选“自动更新”（默认）、“只提醒”、“手动检查”。
  - 从本仓库的 GitHub Releases 下载。1.4.4 起可以选“下载来源”：默认“自动”（先连 GitHub，连不上再试加速站 p.jackyu.cn），也可以只用 GitHub、只用 p.jackyu.cn，或者填自己的 https 加速地址。
  - 下载后核对 `SHA256SUMS.txt`、包名、版本号和签名证书，任何一项不对都不安装，所以加速站改不了安装包。
  - 第一次需要在系统设置里允许本应用“安装未知应用”。
- **不需要 Root，不需要无障碍权限。**

> 已经在真机上日常使用。不同厂商对后台运行的限制不一样，第一次装好后建议按 [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.zh-CN.md) 检查一遍：电池无限制、开机自启、熄屏后还连着。

## 快速开始

**1. 在服务器上装中继**

- 用一行命令装的 Violet（推荐）：在 Violet 后台“工具与手机”页点“一键安装中继”。装好后，页面上会列出手机要填的中继地址、设备密钥和证书指纹，每一项都有复制按钮。
- 其他情况：按 [docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.zh-CN.md) 手动部署。

**2. 在手机上安装和设置**

1. 安装 [Releases](https://github.com/aileboeth/violet-mcp/releases/latest) 里最新的 `violet-mcp-<版本>-release.apk`（应用名“Violet MCP”）。以后的版本可以在应用里直接更新。打不开 GitHub 时用 [GitHub 加速地址](#国内网络github-加速地址) 下载。
2. 打开应用 → 设置 → 权限：允许“使用情况访问权限”，“电池无限制”也建议打开。然后在“工具与权限”页选好要开放给 Violet 的工具。
3. 在“远程访问”里填上第 1 步得到的中继地址、设备密钥、证书指纹，打开连接。
4. 回到主页点 **启动**。

**3. 连上 Violet**

在手机的“安全”页复制访问令牌，粘贴回 Violet 后台“工具与手机”页，就连上了。手动部署中继的，按 [docs/VIOLET_INTEGRATION.md](docs/VIOLET_INTEGRATION.zh-CN.md) 添加 MCP 服务器。

### 国内网络：GitHub 加速地址

手机或服务器连不上 GitHub 时，在 GitHub 地址前面加上加速站的地址再下载：

| 加速站 | 说明 |
|---|---|
| `https://p.jackyu.cn/` | HTTPS，有时比较慢 |
| `http://8.217.135.233:9999/` | 明文 HTTP，通常更快。应用内更新不用它（本应用不允许任何明文连接），在手机浏览器里下载可以用 |

2026-11-08 在国内网络（中国移动）实测，两个都能用，下载的 APK 和中继程序与 GitHub 上的完全一致（SHA-256 相同）。加速站是别人提供的免费服务，不归本项目管，随时可能失效；能直接连上 GitHub 时请用官方地址。

- **下载或手动更新 App**（在手机浏览器里打开，下载后直接覆盖安装，令牌、中继配置和工具开关都会保留）：
  - https://p.jackyu.cn/https://github.com/aileboeth/violet-mcp/releases/download/v1.4.4/violet-mcp-1.4.4-release.apk
  - 备用：http://8.217.135.233:9999/https://github.com/aileboeth/violet-mcp/releases/download/v1.4.4/violet-mcp-1.4.4-release.apk
  - 以后有新版本时，把地址里的两处 `1.4.4` 换成新的版本号（最新版本号见本页开头的“当前版本”）
- **应用内更新**：1.4.4 起在“设置 → 应用更新 → 下载来源”里设置，默认“自动”，GitHub 连不上会自动改用 p.jackyu.cn。1.4.3 及更早的版本只能直连 GitHub，手机连不上时先按上面的链接手动装一次 1.4.4。
- **中继程序**（手动部署时用，见 [docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.zh-CN.md)）：在服务器上执行，按服务器架构选 `amd64` 或 `arm64`：

```bash
curl -fL -o /root/violet-mcp-relay-1.2.0-linux-amd64 https://p.jackyu.cn/https://github.com/aileboeth/violet-mcp/releases/download/v1.4.4/violet-mcp-relay-1.2.0-linux-amd64
curl -fL -o /root/SHA256SUMS.txt https://p.jackyu.cn/https://github.com/aileboeth/violet-mcp/releases/download/v1.4.4/SHA256SUMS.txt
cd /root && grep linux-amd64 SHA256SUMS.txt | sha256sum -c -
```

- **Violet 后台的“一键安装中继”**：Violet 0.7.59 起按 Violet 后台的“下载来源”下载，经加速站下载时还要核对本仓库的发布签名（`SHA256SUMS.txt.sig`，1.4.4 起每个版本都有），签名不对就不安装。更早的 Violet 可以先按上面下载，再按 DEPLOY_RELAY 手动部署。
- 卸载：App 在手机系统设置里卸载即可；中继在服务器上执行 `/opt/violet/violet mcp-relay uninstall`（Violet 装的）或按 DEPLOY_RELAY 删除（手动部署的），都不需要连 GitHub。

**本机调试**（不经过中继）：

```bash
adb forward tcp:1145 tcp:1145
python tests/interop/test_mcp_interop.py --url http://127.0.0.1:1145/mcp --token <令牌> --device
```

## 文档

| 文档 | 内容 |
|---|---|
| [docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.zh-CN.md) | 手动部署 VPS 中继、手机设置、换证书（可以直接复制的命令） |
| [docs/VIOLET_INTEGRATION.md](docs/VIOLET_INTEGRATION.zh-CN.md) | 接入 Violet、错误码、延迟定位 |
| [docs/TOOLS.md](docs/TOOLS.zh-CN.md) | 工具清单、默认开关、返回字段、需要的 Android 权限 |
| [docs/UPGRADE.md](docs/UPGRADE.zh-CN.md) | 升级与回退（包括从旧的 Eve 只读 MCP 换到 Violet MCP） |
| [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.zh-CN.md) | 真机检查清单 |
| [docs/HANDOFF.md](docs/HANDOFF.zh-CN.md) | 源码交接、在全新环境里构建、签名模板（**不要直接压缩工作目录**） |
| [docs/TEST_REPORT.md](docs/TEST_REPORT.zh-CN.md) | 每个版本的自动化测试结果 |
| [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.zh-CN.md)、[docs/REVIEW_v1.2.md](docs/REVIEW_v1.2.zh-CN.md)、[docs/SECURITY_AUDIT_v1.2.md](docs/SECURITY_AUDIT_v1.2.zh-CN.md)、[docs/SECURITY_RISK_v1.2.1.md](docs/SECURITY_RISK_v1.2.1.zh-CN.md)、[docs/SIGNING_KEY_INCIDENT.md](docs/SIGNING_KEY_INCIDENT.zh-CN.md) | 历次安全审查和签名密钥轮换记录（写于改名前，用的是当时的版本名和文件名） |

## 架构

```
┌──────────────── Android 手机（Violet MCP，com.mcp.server.violet）────────┐
│ McpServerService（前台服务 specialUse，开机自启，网络切换监听）           │
│   ├─ LocalHttpServer  127.0.0.1:1145（只限本机）                          │
│   └─ RelayClient ×1–3 ═══ wss:// 主动连出（设备密钥 + 证书指纹）═══╗      │
│            ↓ 两条路径都进入同一个                                   ║      │
│   McpEndpoint（Bearer 鉴权、拒绝带 Origin 的请求）                  ║      │
│   McpProtocol（JSON-RPC，无状态）→ ToolRegistry（只读白名单）       ║      │
│     → UsageTools / SystemTools / AppTools / LifeTools / MetaTools   ║      │
└─────────────────────────────────────────────────────────────────────╫──────┘
                                                                      ║
┌──────────────── VPS：violet-mcp-relay（Go）────────────────────────╨──────┐
│ 设备入口（TLS WebSocket，端口安装时选定）                                  │
│ 127.0.0.1:8787  MCP 入口 /mcp ◄── Violet（Python MCP SDK）                 │
│ 未认证的请求在这里直接返回 401，不转发；中继本身没有任何工具               │
└────────────────────────────────────────────────────────────────────────────┘
```

| 目录 | 内容 |
|---|---|
| `mcp-core/` | 纯 Kotlin/JVM：MCP 协议、HTTP 服务器、鉴权、只读白名单、中继客户端、使用时长计算。应用和电脑端测试共用 |
| `app/` | Android：工具实现、前台服务、开机自启、界面 |
| `relay/` | Go 中继 `violet-mcp-relay` 和 systemd 配置 |
| `tests/` | Python MCP SDK 互操作 / 安全测试、电脑端到端脚本、APK 静态检查 |

## 端点

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| `POST` | `/mcp` | Bearer | Streamable HTTP（无状态，响应 `application/json`；通知返回 202） |
| `GET` | `/status` | Bearer | 运行状态（不含密钥） |
| `GET` | `/health` | 无 | 只返回 `{"ok":true}` |
| `GET` / `DELETE` | `/mcp` | Bearer | 405（不提供服务器推送流，也没有会话） |

各端点的安全限制：

- 令牌不能关闭，是 256 bit 随机值，用常量时间比较。
- 带 `Origin` 头的请求一律返回 403。
- 请求体上限 1 MiB，JSON 嵌套深度上限 64，批量请求上限 32 条。
- 本地端点只能绑定回环地址。
- `/mcp` 的响应带 `Server-Timing`，可以看各阶段耗时。

## 从源码构建

环境：JDK 17、Android SDK（platform 35、build-tools 35）、Go 1.23+、Python 3.10+（测试用）。

```bash
# 1. Android SDK 路径
echo "sdk.dir=/path/to/android-sdk" > local.properties

# 2. 发布签名（可选）。模板在 config-templates/；签名目录 signing/ 不进仓库，也不进交接包：
#    - 用自己的密钥：复制 config-templates/keystore.properties.example 为 signing/keystore.properties
#    - 官方版本（密钥已轮换）：复制 config-templates/rotation.properties.example 为 signing/rotation.properties，
#      assembleRelease 产出未签名包后运行 python tools/sign_release.py <apk>
#    两个都没有时 release 包不签名，可以先用 debug 包。详见 docs/HANDOFF.md §4

# 3. 单元测试（mcp-core + Robolectric 模拟环境）+ APK
./gradlew :mcp-core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
#    输出 app/build/outputs/apk/{debug,release}/violet-mcp-<版本>-<类型>.apk

# 4. 中继
cd relay && go test ./... \
  && GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o ../dist/violet-mcp-relay-linux-amd64 . \
  && GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o ../dist/violet-mcp-relay-linux-arm64 .
cd ..

# 5. 电脑端到端测试（Go 中继 ⇄ JVM harness ⇄ Python MCP SDK，不需要手机）
python -m venv .venv && .venv/bin/pip install "mcp>=1.8,<2"
PYTHON=.venv/bin/python bash tests/run_pc_e2e.sh

# 6. APK 静态检查（权限、已删除的组件、危险 API）
python tests/verify_apk.py --apk app/build/outputs/apk/debug/violet-mcp-<版本>-debug.apk \
  --build-tools /path/to/android-sdk/build-tools/35.0.0
```

正式发布包用 `tools/package_release.py` 生成，它会做泄密检查。

Windows 提示：如果用户目录里有中文，Gradle 的测试进程可能启动失败（报 `GradleWorkerMain` 找不到）。把 `GRADLE_USER_HOME` 设成纯英文路径就行，例如 `set GRADLE_USER_HOME=D:\gradle-home`。

## 致谢与许可证

本项目修改自 [Cazriel/mcp-server](https://github.com/Cazriel/mcp-server)（GPL-3.0）。

2026-10 起的主要修改：

- 删除写入类工具和相关依赖（Shizuku、QuickJS、DocumentFile、Gson）以及高权限声明，工具改为只读白名单。
- 重写 HTTP 传输与鉴权。
- 新增感知手机状态的工具（使用时长、闹钟、音乐、耳机、网络和地点、步数、日程、手机动静）。
- 用 TLS 中继取代 bore 隧道，并支持多个中继。
- 前台服务类型改为 specialUse。
- 新增隐私开关和最小化输出、Keystore 加密凭据、监听安全限制、中继心跳 / 超时 / 离线等待 / 分阶段计时、三态服务状态、诊断页与安全页、应用内更新。
- 1.3.0 改名为 Violet MCP。

详见 [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.zh-CN.md) 和 git 历史。

同时使用了：[Model Context Protocol](https://modelcontextprotocol.io/)、[OkHttp](https://square.github.io/okhttp/)（Apache-2.0）、[gorilla/websocket](https://github.com/gorilla/websocket)（BSD-2-Clause）、[Jetpack Compose](https://developer.android.com/jetpack/compose)、[kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines)。

本项目按 **GNU General Public License v3.0** 发布，全文见 [LICENSE](LICENSE)。分发 APK 时必须同时提供对应的源码。
