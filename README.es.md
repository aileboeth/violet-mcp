<div align="center">

# Violet MCP (estado del teléfono, solo lectura, para Android)

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | **Español** | [Français](README.fr.md) | [Deutsch](README.de.md) | [Русский](README.ru.md) | [Português](README.pt-BR.md)

[![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?logo=android&logoColor=white)](#)
[![MCP](https://img.shields.io/badge/MCP-Streamable_HTTP-blue)](https://modelcontextprotocol.io/)

</div>

Violet MCP es un servidor [MCP](https://modelcontextprotocol.io/) de solo lectura para Android, pensado para **[Violet](https://github.com/aileboeth-web/violet-companion)**, una compañera de IA autoalojada. Una vez instalado en tu móvil, Violet puede conocer el estado del teléfono dentro de los límites que permitas, por ejemplo:

- la batería, si la pantalla está encendida, la app que estás usando y cuánto la has usado hoy;
- la próxima alarma, la canción que suena, si llevas auriculares;
- si estás en casa o fuera, cuántos pasos has dado hoy, tu agenda de hoy y mañana.

Así entiende mejor qué estás haciendo en cada momento; por ejemplo, puede darte los buenos días la primera vez que desbloqueas el móvil por la mañana. **No puede cambiar nada en el teléfono.**

Basado en [Cazriel/mcp-server](https://github.com/Cazriel/mcp-server), con estos cambios:

- **Se mantiene**: Streamable HTTP, autenticación Bearer, ejecución en segundo plano e inicio al arrancar.
- **Se elimina**: todo lo que puede cambiar el estado del dispositivo.
- **Se añade**: herramientas de solo lectura que perciben el estado del teléfono.
- **Se sustituye**: el túnel original en texto plano pasa a ser un relé TLS en tu propio VPS al que se conecta el teléfono.

Versión actual: app **Violet MCP 1.4.2** (paquete `com.mcp.server.violet`), relé **violet-mcp-relay 1.2.0**.

Por ahora, la interfaz de la app está en chino.

## Características

- **Solo lectura**:
  - Las 21 herramientas están fijadas en el código en una lista permitida.
  - La ejecución de comandos, Shizuku, JavaScript, la lectura y escritura de archivos, el control de apps, el portapapeles, la lectura de notificaciones y las peticiones HTTP arbitrarias se han eliminado del código fuente. No aparecen en `tools/list` y no se pueden llamar.
- **Un interruptor por herramienta**:
  - Por defecto solo están activadas 7 herramientas no sensibles: batería, estado de la pantalla, app en primer plano, información de la pantalla, comprobación de estado, próxima alarma, auriculares / Bluetooth.
  - El tiempo de uso, lo que suena, red y lugar, pasos, calendario, apps instaladas, identificadores del dispositivo y otras las tienes que activar tú en la página Herramientas y permisos del móvil. Cada herramienta indica qué podrá saber ella al activarla, y las que necesitan un permiso del sistema tienen un botón Conceder.
  - Las peticiones MCP no pueden cambiar estos interruptores.
- **Sin suposiciones**: con la pantalla apagada, el teléfono bloqueado o cuando no está claro, no adivina qué app estás usando y explica por qué.
- **Lugares solo con los nombres que tú pones**: puedes poner nombre a una red Wi-Fi, como "casa" u "oficina". El nombre de la red (SSID) solo se compara en el teléfono y nunca se envía.
- **Sucesos del teléfono**:
  - Registra los desbloqueos (y cuánto tiempo estuvo apagada la pantalla), la carga, conectar o desconectar auriculares, y llegar a un lugar con nombre o salir de él.
  - Violet los recoge una vez por minuto; el relé no necesita cambios.
- **Acceso remoto sin VPN ni IP pública**:
  - El teléfono se conecta con `wss://` al relé de tu VPS.
  - Doble autenticación con clave de dispositivo y token MCP, y fijación de certificado (se pueden guardar a la vez la huella antigua y la nueva para cambiar de certificado sin problemas).
  - Se reconecta solo tras una desconexión, y de inmediato al cambiar de red.
  - Desde la 1.4.2 puede conectarse a la vez a hasta 3 servidores de relé, cada uno con su propio interruptor.
- **Credenciales seguras**: el token y la clave de dispositivo se guardan cifrados con Android Keystore y se pueden rotar, revocar o borrar.
- **Ejecución en segundo plano**:
  - Usa un servicio en primer plano de tipo `specialUse`.
  - El servicio tiene tres estados: en marcha, restringido y detenido. Si lo detienes a mano, no se vuelve a iniciar solo.
- **Actualizaciones desde la app**:
  - Ajustes → Actualizaciones de la app: elige "automática" (por defecto), "solo avisar" o "comprobar manualmente".
  - Solo descarga desde las GitHub Releases de este repositorio y luego comprueba `SHA256SUMS.txt`, el nombre del paquete, el número de versión y el certificado de firma; si algo no cuadra, no instala nada.
  - La primera vez tienes que permitir que esta app "instale apps desconocidas" en los ajustes del sistema.
- **Sin root ni permisos de accesibilidad.**

> Se usa a diario en teléfonos reales. Cada fabricante limita de forma distinta las apps en segundo plano, así que tras la primera instalación conviene repasar [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.md) (en inglés): batería sin restricciones, inicio al arrancar, conexión mantenida con la pantalla apagada.

## Inicio rápido

**1. Instala el relé en tu servidor**

- Violet instalado con el comando de una línea (recomendado): en la página Herramientas y móvil del panel de Violet, pulsa Instalar relé. Al terminar, la página muestra la dirección del relé, la clave de dispositivo y la huella del certificado que hay que introducir en el móvil, cada una con un botón para copiar.
- En otro caso: despliégalo a mano siguiendo [docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.md).

**2. Instala y configura la app en el móvil**

1. Instala el último `violet-mcp-<versión>-release.apk` de [Releases](https://github.com/aileboeth-web/violet-mcp/releases/latest) (la app se llama "Violet MCP"). Las versiones siguientes se pueden actualizar desde la propia app.
2. Abre la app → Ajustes → Permisos: permite el "acceso a datos de uso", y también se recomienda activar "batería sin restricciones". Después elige en la página Herramientas y permisos las herramientas que compartirás con Violet.
3. En Acceso remoto, introduce la dirección del relé, la clave de dispositivo y la huella del certificado del paso 1 y activa la conexión.
4. Vuelve a la pantalla principal y pulsa **Iniciar**.

**3. Conéctalo a Violet**

Copia el token de acceso en la página Seguridad del móvil y pégalo en la página Herramientas y móvil del panel de Violet. Listo. Si desplegaste el relé a mano, añade el servidor MCP como se explica en [docs/VIOLET_INTEGRATION.md](docs/VIOLET_INTEGRATION.md).

**Depuración local** (sin el relé):

```bash
adb forward tcp:1145 tcp:1145
python tests/interop/test_mcp_interop.py --url http://127.0.0.1:1145/mcp --token <token> --device
```

## Documentación

Los documentos están en inglés y en chino simplificado.

| Documento | Contenido |
|---|---|
| [docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.md) | Desplegar el relé del VPS a mano, configurar el móvil, cambiar certificados (comandos para copiar y pegar) |
| [docs/VIOLET_INTEGRATION.md](docs/VIOLET_INTEGRATION.md) | Conexión con Violet, códigos de error, localizar la latencia |
| [docs/TOOLS.md](docs/TOOLS.md) | Lista de herramientas, interruptores por defecto, campos devueltos, permisos de Android necesarios |
| [docs/UPGRADE.md](docs/UPGRADE.md) | Actualizar y volver atrás (incluido el paso del antiguo Eve read-only MCP a Violet MCP) |
| [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.md) | Lista de comprobación en teléfonos reales |
| [docs/HANDOFF.md](docs/HANDOFF.md) | Entrega del código fuente, compilar en un entorno limpio, plantillas de firma (**no comprimas sin más el directorio de trabajo**) |
| [docs/TEST_REPORT.md](docs/TEST_REPORT.md) | Resultados de las pruebas automáticas de cada versión |
| [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md), [docs/REVIEW_v1.2.md](docs/REVIEW_v1.2.md), [docs/SECURITY_AUDIT_v1.2.md](docs/SECURITY_AUDIT_v1.2.md), [docs/SECURITY_RISK_v1.2.1.md](docs/SECURITY_RISK_v1.2.1.md), [docs/SIGNING_KEY_INCIDENT.md](docs/SIGNING_KEY_INCIDENT.md) | Revisiones de seguridad anteriores y registro de la rotación de la clave de firma (escritos antes del cambio de nombre, así que usan los nombres de versión y archivo de entonces) |

## Arquitectura

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

| Directorio | Contenido |
|---|---|
| `mcp-core/` | Kotlin/JVM puro: protocolo MCP, servidor HTTP, autenticación, lista permitida de solo lectura, cliente del relé, cálculo del tiempo de uso. Compartido por la app y las pruebas en PC |
| `app/` | Android: implementación de las herramientas, servicio en primer plano, inicio al arrancar, interfaz |
| `relay/` | El relé en Go `violet-mcp-relay` y su configuración de systemd |
| `tests/` | Pruebas de interoperabilidad / seguridad con el SDK de Python para MCP, scripts de extremo a extremo en PC, comprobaciones estáticas del APK |

## Endpoints

| Método | Ruta | Autenticación | Notas |
|---|---|---|---|
| `POST` | `/mcp` | Bearer | Streamable HTTP (sin estado, responde con `application/json`; las notificaciones reciben 202) |
| `GET` | `/status` | Bearer | Estado del servicio (sin secretos) |
| `GET` | `/health` | ninguna | Solo devuelve `{"ok":true}` |
| `GET` / `DELETE` | `/mcp` | Bearer | 405 (sin flujo de envío desde el servidor y sin sesiones) |

Límites de seguridad de los endpoints:

- El token no se puede desactivar. Es un valor aleatorio de 256 bits comparado en tiempo constante.
- Cualquier petición con cabecera `Origin` recibe 403.
- Cuerpo de la petición hasta 1 MiB, anidamiento JSON hasta 64 niveles, peticiones por lotes hasta 32 elementos.
- El endpoint local solo puede enlazarse a una dirección de loopback.
- Las respuestas de `/mcp` llevan `Server-Timing`, que muestra cuánto tardó cada etapa.

## Compilar desde el código fuente

Requisitos: JDK 17, Android SDK (platform 35, build-tools 35), Go 1.23+, Python 3.10+ (para las pruebas).

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

Los paquetes oficiales se generan con `tools/package_release.py`, que además comprueba que no se filtren secretos.

Consejo para Windows: si el nombre de tu carpeta de usuario tiene caracteres no ASCII, los procesos de prueba de Gradle pueden no arrancar (`GradleWorkerMain` no encontrado). Pon `GRADLE_USER_HOME` en una ruta solo ASCII, por ejemplo `set GRADLE_USER_HOME=D:\gradle-home`.

## Créditos y licencia

Este proyecto se basa en [Cazriel/mcp-server](https://github.com/Cazriel/mcp-server) (GPL-3.0).

Cambios principales desde 2026-10:

- Se eliminaron las herramientas de escritura y sus dependencias (Shizuku, QuickJS, DocumentFile, Gson) y las declaraciones de permisos elevados; las herramientas pasaron a una lista permitida de solo lectura.
- Se reescribieron el transporte HTTP y la autenticación.
- Se añadieron herramientas que perciben el estado del teléfono (tiempo de uso, alarma, música, auriculares, red y lugar, pasos, calendario, sucesos del teléfono).
- El túnel bore se sustituyó por un relé TLS, con soporte para varios relés.
- El tipo de servicio en primer plano pasó a specialUse.
- Se añadieron interruptores de privacidad y salida mínima, credenciales cifradas con Keystore, límites de seguridad en la escucha, latido / tiempos de espera / espera sin conexión / medición por etapas del relé, estado del servicio en tres estados, páginas de diagnóstico y de seguridad, y actualizaciones desde la app.
- Renombrado a Violet MCP en la 1.3.0.

Más detalles en [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md) y en el historial de git.

También usa: [Model Context Protocol](https://modelcontextprotocol.io/), [OkHttp](https://square.github.io/okhttp/) (Apache-2.0), [gorilla/websocket](https://github.com/gorilla/websocket) (BSD-2-Clause), [Jetpack Compose](https://developer.android.com/jetpack/compose), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines).

Este proyecto se publica bajo la **GNU General Public License v3.0**; el texto completo está en [LICENSE](LICENSE). Si distribuyes el APK, debes proporcionar también el código fuente correspondiente.
