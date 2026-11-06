<div align="center">

# Violet MCP(Android용 읽기 전용 휴대폰 상태 서비스)

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | **한국어** | [Español](README.es.md) | [Français](README.fr.md) | [Deutsch](README.de.md) | [Русский](README.ru.md) | [Português](README.pt-BR.md)

[![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?logo=android&logoColor=white)](#)
[![MCP](https://img.shields.io/badge/MCP-Streamable_HTTP-blue)](https://modelcontextprotocol.io/)

</div>

Violet MCP는 직접 운영하는 AI 동반자 **[Violet](https://github.com/aileboeth-web/violet-companion)**을 위한 Android용 읽기 전용 [MCP](https://modelcontextprotocol.io/) 서버입니다. 휴대폰에 설치하면 당신이 허락한 범위 안에서 Violet이 휴대폰 상태를 알 수 있습니다. 예를 들어:

- 배터리, 화면이 켜져 있는지, 쓰고 있는 앱, 오늘 얼마나 썼는지
- 다음 알람, 재생 중인 노래, 헤드폰을 꼈는지
- 집에 있는지 밖에 있는지, 오늘 걸음 수, 오늘과 내일의 일정

이 정보로 그녀는 당신이 지금 무엇을 하는지 더 잘 이해할 수 있습니다. 예를 들어 아침에 처음 휴대폰 잠금을 풀 때 "좋은 아침"이라고 인사할 수 있습니다. **휴대폰의 어떤 것도 바꿀 수 없습니다.**

[Cazriel/mcp-server](https://github.com/Cazriel/mcp-server)를 바탕으로 다음과 같이 바꿨습니다:

- **유지**: Streamable HTTP, Bearer 인증, 백그라운드 실행과 부팅 시 자동 시작.
- **삭제**: 기기 상태를 바꿀 수 있는 모든 기능.
- **추가**: 휴대폰 상태를 감지하는 읽기 전용 도구.
- **교체**: 원래의 평문 터널을 "휴대폰이 직접 당신의 VPS로 연결하는 TLS 릴레이"로 바꿨습니다.

현재 버전: 앱 **Violet MCP 1.4.2**(패키지 이름 `com.mcp.server.violet`), 릴레이 **violet-mcp-relay 1.2.0**.

앱 화면은 현재 중국어입니다.

## 특징

- **읽기 전용**:
  - 도구 21개는 모두 허용 목록에 하드코딩되어 있습니다.
  - 명령 실행, Shizuku, JavaScript, 파일 읽기/쓰기, 앱 제어, 클립보드, 알림 읽기, 임의의 HTTP 요청은 소스 코드에서 삭제되어 `tools/list`에도 나오지 않고 호출할 수도 없습니다.
- **도구마다 개별 스위치**:
  - 기본으로 켜진 것은 민감하지 않은 7개뿐입니다: 배터리, 화면 상태, 포그라운드 앱, 화면 정보, 상태 확인, 다음 알람, 헤드폰 / 블루투스.
  - 사용 시간, 재생 중인 음악, 네트워크와 장소, 걸음 수, 캘린더, 설치된 앱, 기기 식별자 등은 휴대폰의 "도구와 권한" 페이지에서 직접 켜야 합니다. 도구마다 "켜면 그녀가 무엇을 알 수 있는지"가 적혀 있고, 시스템 권한이 필요한 도구에는 "권한 허용" 버튼이 있습니다.
  - MCP 요청으로는 이 스위치를 바꿀 수 없습니다.
- **추측하지 않음**: 화면이 꺼져 있거나 잠겨 있거나 확실하지 않을 때는 어떤 앱을 쓰는지 추측하지 않고 이유를 알려 줍니다.
- **장소는 당신이 붙인 이름으로만**: Wi-Fi에 "집", "회사" 같은 이름을 붙일 수 있습니다. Wi-Fi 이름(SSID)은 휴대폰 안에서만 비교하고 밖으로 보내지 않습니다.
- **휴대폰에서 일어난 일**:
  - 잠금 해제(화면이 얼마나 꺼져 있었는지), 충전, 헤드폰 연결/해제, 이름 붙인 장소 도착/출발을 기록합니다.
  - Violet이 1분마다 가져가므로 릴레이는 바꿀 필요가 없습니다.
- **VPN이나 공인 IP 없이 원격 접속**:
  - 휴대폰이 `wss://`로 VPS의 릴레이에 직접 연결합니다.
  - 기기 키 + MCP 토큰 이중 인증과 인증서 고정(인증서를 바꾸기 쉽도록 새 지문과 옛 지문을 함께 저장할 수 있습니다).
  - 연결이 끊기면 자동으로 다시 연결하고, 네트워크가 바뀌면 즉시 다시 연결합니다.
  - 1.4.2부터 릴레이 서버를 최대 3개까지 동시에 연결할 수 있고, 각각 스위치가 있습니다.
- **자격 증명 보안**: 토큰과 기기 키는 Android Keystore로 암호화해 저장하며, 교체·폐기·전체 삭제가 가능합니다.
- **백그라운드 실행**:
  - `specialUse` 포그라운드 서비스를 사용합니다.
  - 서비스 상태는 실행 중, 제한됨, 중지됨 세 가지이며, 직접 중지한 뒤에는 자동으로 다시 켜지지 않습니다.
- **앱 내 업데이트**:
  - 설정 → 앱 업데이트에서 "자동 업데이트"(기본값), "알림만", "수동 확인" 중에서 고릅니다.
  - 이 저장소의 GitHub Releases에서만 내려받고, `SHA256SUMS.txt`, 패키지 이름, 버전 번호, 서명 인증서를 확인해 하나라도 맞지 않으면 설치하지 않습니다.
  - 처음에는 시스템 설정에서 이 앱의 "출처를 알 수 없는 앱 설치"를 허용해야 합니다.
- **루트도, 접근성 권한도 필요 없습니다.**

> 실제 휴대폰에서 매일 쓰고 있습니다. 제조사마다 백그라운드 실행 제한이 다르므로, 처음 설치한 뒤에는 [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.md)(영어)를 보며 배터리 제한 없음, 부팅 시 자동 시작, 화면이 꺼진 뒤에도 연결 유지를 한번 확인하는 것을 권합니다.

## 빠른 시작

**1. 서버에 릴레이 설치**

- 한 줄 명령으로 설치한 Violet(권장): Violet 관리 화면의 "도구와 휴대폰" 페이지에서 "릴레이 설치"를 누릅니다. 설치가 끝나면 휴대폰에 입력할 릴레이 주소, 기기 키, 인증서 지문이 복사 버튼과 함께 표시됩니다.
- 그 밖의 경우: [docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.md)를 따라 직접 배포합니다.

**2. 휴대폰에 설치하고 설정**

1. [Releases](https://github.com/aileboeth-web/violet-mcp/releases/latest)에서 최신 `violet-mcp-<버전>-release.apk`를 설치합니다(앱 이름 "Violet MCP"). 이후 버전은 앱 안에서 바로 업데이트할 수 있습니다.
2. 앱을 열고 → 설정 → 권한: "사용 정보 접근"을 허용하고, "배터리 제한 없음"도 켜는 것을 권합니다. 그런 다음 "도구와 권한" 페이지에서 Violet에게 공개할 도구를 고릅니다.
3. "원격 접속"에 1단계에서 얻은 릴레이 주소, 기기 키, 인증서 지문을 입력하고 연결을 켭니다.
4. 홈 화면으로 돌아가 **시작**을 누릅니다.

**3. Violet에 연결**

휴대폰의 "보안" 페이지에서 접근 토큰을 복사해 Violet 관리 화면의 "도구와 휴대폰" 페이지에 붙여 넣으면 연결됩니다. 릴레이를 직접 배포했다면 [docs/VIOLET_INTEGRATION.md](docs/VIOLET_INTEGRATION.md)를 따라 MCP 서버를 추가합니다.

**로컬 디버깅**(릴레이를 거치지 않음):

```bash
adb forward tcp:1145 tcp:1145
python tests/interop/test_mcp_interop.py --url http://127.0.0.1:1145/mcp --token <토큰> --device
```

## 문서

문서는 영어판과 간체 중국어판이 있습니다.

| 문서 | 내용 |
|---|---|
| [docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.md) | VPS 릴레이 직접 배포, 휴대폰 설정, 인증서 교체(그대로 복사해 쓸 수 있는 명령) |
| [docs/VIOLET_INTEGRATION.md](docs/VIOLET_INTEGRATION.md) | Violet 연결, 오류 코드, 지연 원인 찾기 |
| [docs/TOOLS.md](docs/TOOLS.md) | 도구 목록, 기본 스위치, 반환 필드, 필요한 Android 권한 |
| [docs/UPGRADE.md](docs/UPGRADE.md) | 업그레이드와 롤백(예전 Eve 읽기 전용 MCP에서 Violet MCP로 옮기는 방법 포함) |
| [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.md) | 실기기 점검표 |
| [docs/HANDOFF.md](docs/HANDOFF.md) | 소스 인계, 깨끗한 환경에서 빌드, 서명 템플릿(**작업 디렉터리를 그대로 압축하지 마세요**) |
| [docs/TEST_REPORT.md](docs/TEST_REPORT.md) | 버전별 자동 테스트 결과 |
| [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md), [docs/REVIEW_v1.2.md](docs/REVIEW_v1.2.md), [docs/SECURITY_AUDIT_v1.2.md](docs/SECURITY_AUDIT_v1.2.md), [docs/SECURITY_RISK_v1.2.1.md](docs/SECURITY_RISK_v1.2.1.md), [docs/SIGNING_KEY_INCIDENT.md](docs/SIGNING_KEY_INCIDENT.md) | 지난 보안 검토와 서명 키 교체 기록(이름을 바꾸기 전에 작성되어 당시의 버전 이름과 파일 이름을 씁니다) |

## 아키텍처

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

| 디렉터리 | 내용 |
|---|---|
| `mcp-core/` | 순수 Kotlin/JVM: MCP 프로토콜, HTTP 서버, 인증, 읽기 전용 허용 목록, 릴레이 클라이언트, 사용 시간 계산. 앱과 PC 쪽 테스트가 함께 사용 |
| `app/` | Android: 도구 구현, 포그라운드 서비스, 부팅 시 자동 시작, UI |
| `relay/` | Go 릴레이 `violet-mcp-relay`와 systemd 설정 |
| `tests/` | Python MCP SDK 상호 운용 / 보안 테스트, PC 엔드투엔드 스크립트, APK 정적 검사 |

## 엔드포인트

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| `POST` | `/mcp` | Bearer | Streamable HTTP(무상태, `application/json`으로 응답, 알림에는 202) |
| `GET` | `/status` | Bearer | 실행 상태(비밀 정보 없음) |
| `GET` | `/health` | 없음 | `{"ok":true}`만 반환 |
| `GET` / `DELETE` | `/mcp` | Bearer | 405(서버 푸시 스트림도 세션도 없음) |

엔드포인트의 보안 제한:

- 토큰은 끌 수 없습니다. 256비트 난수이며 상수 시간으로 비교합니다.
- `Origin` 헤더가 있는 요청은 모두 403입니다.
- 요청 본문 최대 1 MiB, JSON 중첩 깊이 최대 64, 배치 요청 최대 32개.
- 로컬 엔드포인트는 루프백 주소에만 바인딩할 수 있습니다.
- `/mcp` 응답에는 `Server-Timing`이 붙어 단계별 소요 시간을 볼 수 있습니다.

## 소스에서 빌드

필요한 것: JDK 17, Android SDK(platform 35, build-tools 35), Go 1.23+, Python 3.10+(테스트용).

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

공식 릴리스 패키지는 `tools/package_release.py`로 만들며, 비밀 정보 유출 검사도 함께 합니다.

Windows 팁: 사용자 폴더 이름에 ASCII가 아닌 문자가 있으면 Gradle 테스트 워커가 시작되지 않을 수 있습니다(`GradleWorkerMain`을 찾을 수 없음). `GRADLE_USER_HOME`을 ASCII로만 된 경로로 지정하세요. 예: `set GRADLE_USER_HOME=D:\gradle-home`.

## 감사의 말과 라이선스

이 프로젝트는 [Cazriel/mcp-server](https://github.com/Cazriel/mcp-server)(GPL-3.0)를 바탕으로 합니다.

2026-10 이후의 주요 변경:

- 쓰기 도구와 관련 의존성(Shizuku, QuickJS, DocumentFile, Gson), 고권한 선언을 삭제하고 도구를 읽기 전용 허용 목록으로 만들었습니다.
- HTTP 전송과 인증을 새로 썼습니다.
- 휴대폰 상태를 감지하는 도구를 추가했습니다(사용 시간, 알람, 음악, 헤드폰, 네트워크와 장소, 걸음 수, 캘린더, 휴대폰에서 일어난 일).
- bore 터널을 TLS 릴레이로 바꾸고 여러 릴레이를 지원합니다.
- 포그라운드 서비스 유형을 specialUse로 바꿨습니다.
- 개인정보 스위치와 최소 출력, Keystore로 암호화한 자격 증명, 수신 보안 제한, 릴레이 하트비트 / 타임아웃 / 오프라인 대기 / 단계별 계측, 세 가지 서비스 상태, 진단 페이지와 보안 페이지, 앱 내 업데이트를 추가했습니다.
- 1.3.0에서 이름을 Violet MCP로 바꿨습니다.

자세한 내용은 [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md)와 git 기록을 참고하세요.

함께 사용하는 것: [Model Context Protocol](https://modelcontextprotocol.io/), [OkHttp](https://square.github.io/okhttp/)(Apache-2.0), [gorilla/websocket](https://github.com/gorilla/websocket)(BSD-2-Clause), [Jetpack Compose](https://developer.android.com/jetpack/compose), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines).

이 프로젝트는 **GNU General Public License v3.0**으로 배포되며, 전문은 [LICENSE](LICENSE)에 있습니다. APK를 배포할 때는 해당 소스 코드도 함께 제공해야 합니다.
