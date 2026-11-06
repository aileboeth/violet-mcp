<div align="center">

# Violet MCP (состояние телефона только для чтения, для Android)

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Español](README.es.md) | [Français](README.fr.md) | [Deutsch](README.de.md) | **Русский** | [Português](README.pt-BR.md)

[![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?logo=android&logoColor=white)](#)
[![MCP](https://img.shields.io/badge/MCP-Streamable_HTTP-blue)](https://modelcontextprotocol.io/)

</div>

Violet MCP — это [MCP](https://modelcontextprotocol.io/)-сервер для Android, работающий только на чтение, для **[Violet](https://github.com/aileboeth-web/violet-companion)** — ИИ-компаньона, которого вы разворачиваете у себя. Когда он установлен на телефоне, Violet может знать состояние телефона в пределах, которые вы разрешили, например:

- заряд батареи, включён ли экран, каким приложением вы пользуетесь и сколько времени сегодня;
- следующий будильник, какая песня играет, подключены ли наушники;
- дома вы или нет, сколько шагов прошли сегодня, ваш календарь на сегодня и завтра.

Так она лучше понимает, чем вы сейчас заняты; например, может пожелать доброго утра, когда вы впервые за утро разблокируете телефон. **Изменить что-либо на телефоне он не может.**

Основан на [Cazriel/mcp-server](https://github.com/Cazriel/mcp-server) со следующими изменениями:

- **Оставлено**: Streamable HTTP, аутентификация Bearer, работа в фоне и запуск при загрузке.
- **Удалено**: всё, что может изменить состояние устройства.
- **Добавлено**: инструменты только для чтения, отслеживающие состояние телефона.
- **Заменено**: исходный туннель с открытым текстом заменён TLS-реле на вашем собственном VPS, к которому подключается телефон.

Текущая версия: приложение **Violet MCP 1.4.2** (пакет `com.mcp.server.violet`), реле **violet-mcp-relay 1.2.0**.

Интерфейс приложения пока на китайском языке.

## Возможности

- **Только чтение**:
  - Все 21 инструмент жёстко прописаны в списке разрешённых.
  - Выполнение команд, Shizuku, JavaScript, чтение и запись файлов, управление приложениями, буфер обмена, чтение уведомлений и произвольные HTTP-запросы удалены из исходного кода. Их нет в `tools/list`, и вызвать их нельзя.
- **Отдельный переключатель для каждого инструмента**:
  - По умолчанию включены только 7 нечувствительных: батарея, состояние экрана, приложение на переднем плане, сведения об экране, проверка состояния, следующий будильник, наушники / Bluetooth.
  - Время использования, текущая музыка, сеть и место, шаги, календарь, установленные приложения, идентификаторы устройства и прочее вы включаете сами на странице «Инструменты и разрешения» на телефоне. У каждого инструмента написано, что она сможет узнать после включения, а у тех, кому нужно системное разрешение, есть кнопка «Предоставить».
  - MCP-запросы не могут менять эти переключатели.
- **Без догадок**: если экран выключен, телефон заблокирован или ответ неясен, он не угадывает, каким приложением вы пользуетесь, и объясняет почему.
- **Места — только по вашим названиям**: можно дать имя сети Wi-Fi, например «дом» или «офис». Имя Wi-Fi (SSID) сравнивается только на телефоне и никуда не отправляется.
- **События на телефоне**:
  - Записываются разблокировки (и сколько времени экран был выключен), зарядка, подключение и отключение наушников, прибытие в названное место и уход из него.
  - Violet забирает их раз в минуту; менять реле не нужно.
- **Удалённый доступ без VPN и публичного IP**:
  - Телефон сам подключается по `wss://` к реле на вашем VPS.
  - Двухфакторная аутентификация ключом устройства и MCP-токеном, плюс закрепление сертификата (старый и новый отпечаток можно хранить одновременно, чтобы сертификат было легко сменить).
  - Автоматически переподключается после обрыва и сразу же — при смене сети.
  - С версии 1.4.2 можно подключаться одновременно к 3 серверам реле, у каждого свой переключатель.
- **Защита учётных данных**: токен и ключ устройства хранятся в зашифрованном виде через Android Keystore, их можно сменить, отозвать или стереть.
- **Работа в фоне**:
  - Используется служба переднего плана типа `specialUse`.
  - У службы три состояния: работает, ограничена и остановлена. Если вы остановили её вручную, сама она снова не запустится.
- **Обновления внутри приложения**:
  - «Настройки → Обновления приложения»: «автоматически» (по умолчанию), «только уведомлять» или «проверять вручную».
  - Скачивает только из GitHub Releases этого репозитория, затем проверяет `SHA256SUMS.txt`, имя пакета, номер версии и сертификат подписи; если хоть что-то не совпадает, ничего не устанавливается.
  - В первый раз нужно разрешить этому приложению «устанавливать неизвестные приложения» в настройках системы.
- **Не нужны ни root, ни разрешение специальных возможностей.**

> Ежедневно используется на реальных телефонах. Каждый производитель по-своему ограничивает фоновые приложения, поэтому после первой установки стоит пройтись по [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.md) (на английском): батарея без ограничений, запуск при загрузке, соединение сохраняется при выключенном экране.

## Быстрый старт

**1. Установите реле на сервер**

- Violet установлена однострочной командой (рекомендуется): на странице «Инструменты и телефон» в панели Violet нажмите «Установить реле». Когда установка закончится, страница покажет адрес реле, ключ устройства и отпечаток сертификата, которые нужно ввести на телефоне, — у каждого есть кнопка копирования.
- Иначе: разверните его вручную по [docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.md).

**2. Установите и настройте приложение на телефоне**

1. Установите последнюю `violet-mcp-<версия>-release.apk` из [Releases](https://github.com/aileboeth-web/violet-mcp/releases/latest) (приложение называется «Violet MCP»). Последующие версии можно обновлять прямо из приложения.
2. Откройте приложение → «Настройки → Разрешения»: разрешите «доступ к истории использования»; включить «батарею без ограничений» тоже рекомендуется. Затем на странице «Инструменты и разрешения» выберите инструменты, которые будут доступны Violet.
3. В разделе «Удалённый доступ» введите адрес реле, ключ устройства и отпечаток сертификата из шага 1 и включите соединение.
4. Вернитесь на главный экран и нажмите **Запустить**.

**3. Подключите к Violet**

Скопируйте токен доступа на странице «Безопасность» на телефоне и вставьте его на странице «Инструменты и телефон» в панели Violet. Готово. Если вы разворачивали реле вручную, добавьте MCP-сервер, как описано в [docs/VIOLET_INTEGRATION.md](docs/VIOLET_INTEGRATION.md).

**Локальная отладка** (без реле):

```bash
adb forward tcp:1145 tcp:1145
python tests/interop/test_mcp_interop.py --url http://127.0.0.1:1145/mcp --token <токен> --device
```

## Документация

Документы есть на английском и упрощённом китайском.

| Документ | Содержание |
|---|---|
| [docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.md) | Ручное развёртывание реле на VPS, настройка телефона, смена сертификата (команды для копирования) |
| [docs/VIOLET_INTEGRATION.md](docs/VIOLET_INTEGRATION.md) | Подключение к Violet, коды ошибок, поиск источника задержек |
| [docs/TOOLS.md](docs/TOOLS.md) | Список инструментов, переключатели по умолчанию, возвращаемые поля, нужные разрешения Android |
| [docs/UPGRADE.md](docs/UPGRADE.md) | Обновление и откат (включая переход со старого Eve read-only MCP на Violet MCP) |
| [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.md) | Чек-лист проверки на реальных телефонах |
| [docs/HANDOFF.md](docs/HANDOFF.md) | Передача исходного кода, сборка в чистом окружении, шаблоны подписи (**не архивируйте рабочий каталог целиком**) |
| [docs/TEST_REPORT.md](docs/TEST_REPORT.md) | Результаты автоматических тестов каждой версии |
| [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md), [docs/REVIEW_v1.2.md](docs/REVIEW_v1.2.md), [docs/SECURITY_AUDIT_v1.2.md](docs/SECURITY_AUDIT_v1.2.md), [docs/SECURITY_RISK_v1.2.1.md](docs/SECURITY_RISK_v1.2.1.md), [docs/SIGNING_KEY_INCIDENT.md](docs/SIGNING_KEY_INCIDENT.md) | Прошлые проверки безопасности и запись о смене ключа подписи (написаны до переименования, поэтому используют тогдашние названия версий и файлов) |

## Архитектура

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

| Каталог | Содержимое |
|---|---|
| `mcp-core/` | Чистый Kotlin/JVM: протокол MCP, HTTP-сервер, аутентификация, список разрешённых инструментов только для чтения, клиент реле, расчёт времени использования. Используется и приложением, и тестами на ПК |
| `app/` | Android: реализация инструментов, служба переднего плана, запуск при загрузке, интерфейс |
| `relay/` | Реле на Go `violet-mcp-relay` и его конфигурация systemd |
| `tests/` | Тесты совместимости / безопасности с Python MCP SDK, сквозные скрипты для ПК, статические проверки APK |

## Конечные точки

| Метод | Путь | Аутентификация | Примечания |
|---|---|---|---|
| `POST` | `/mcp` | Bearer | Streamable HTTP (без состояния, отвечает `application/json`; на уведомления — 202) |
| `GET` | `/status` | Bearer | Состояние службы (без секретов) |
| `GET` | `/health` | нет | Возвращает только `{"ok":true}` |
| `GET` / `DELETE` | `/mcp` | Bearer | 405 (нет потока push от сервера и нет сессий) |

Ограничения безопасности конечных точек:

- Токен нельзя отключить. Это случайное 256-битное значение, сравниваемое за постоянное время.
- Любой запрос с заголовком `Origin` получает 403.
- Тело запроса до 1 МиБ, вложенность JSON до 64 уровней, пакетные запросы до 32 элементов.
- Локальная конечная точка может привязываться только к loopback-адресу.
- Ответы `/mcp` содержат `Server-Timing` с длительностью каждого этапа.

## Сборка из исходников

Нужны: JDK 17, Android SDK (platform 35, build-tools 35), Go 1.23+, Python 3.10+ (для тестов).

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

Официальные пакеты собираются через `tools/package_release.py`, который заодно проверяет, не утекли ли секреты.

Совет для Windows: если в имени папки пользователя есть не-ASCII символы, тестовые процессы Gradle могут не запуститься (`GradleWorkerMain` не найден). Укажите в `GRADLE_USER_HOME` путь только из ASCII, например `set GRADLE_USER_HOME=D:\gradle-home`.

## Благодарности и лицензия

Проект основан на [Cazriel/mcp-server](https://github.com/Cazriel/mcp-server) (GPL-3.0).

Основные изменения с 2026-10:

- Удалены инструменты записи и их зависимости (Shizuku, QuickJS, DocumentFile, Gson), а также объявления высоких разрешений; инструменты превращены в список разрешённых только для чтения.
- Переписаны HTTP-транспорт и аутентификация.
- Добавлены инструменты, отслеживающие состояние телефона (время использования, будильник, музыка, наушники, сеть и место, шаги, календарь, события на телефоне).
- Туннель bore заменён TLS-реле с поддержкой нескольких реле.
- Тип службы переднего плана изменён на specialUse.
- Добавлены переключатели приватности и минимальный вывод, учётные данные, зашифрованные через Keystore, ограничения безопасности на прослушивание, heartbeat / тайм-ауты / ожидание при офлайне / замеры по этапам на реле, три состояния службы, страницы диагностики и безопасности, обновления внутри приложения.
- В версии 1.3.0 переименован в Violet MCP.

Подробнее — в [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md) и истории git.

Также используется: [Model Context Protocol](https://modelcontextprotocol.io/), [OkHttp](https://square.github.io/okhttp/) (Apache-2.0), [gorilla/websocket](https://github.com/gorilla/websocket) (BSD-2-Clause), [Jetpack Compose](https://developer.android.com/jetpack/compose), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines).

Проект распространяется по **GNU General Public License v3.0**; полный текст — в [LICENSE](LICENSE). При распространении APK необходимо также предоставлять соответствующий исходный код.
