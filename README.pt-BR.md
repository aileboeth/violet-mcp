<div align="center">

# Violet MCP (estado do celular somente leitura, para Android)

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Español](README.es.md) | [Français](README.fr.md) | [Deutsch](README.de.md) | [Русский](README.ru.md) | **Português**

[![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?logo=android&logoColor=white)](#)
[![MCP](https://img.shields.io/badge/MCP-Streamable_HTTP-blue)](https://modelcontextprotocol.io/)

</div>

O Violet MCP é um servidor [MCP](https://modelcontextprotocol.io/) somente leitura para Android, feito para a **[Violet](https://github.com/aileboeth-web/violet-companion)**, uma companheira de IA auto-hospedada. Depois de instalado no celular, a Violet pode saber o estado do aparelho dentro dos limites que você permitir, por exemplo:

- a bateria, se a tela está ligada, o app que você está usando e quanto tempo usou hoje;
- o próximo alarme, a música que está tocando, se há fones conectados;
- se você está em casa ou na rua, quantos passos deu hoje, sua agenda de hoje e de amanhã.

Assim ela entende melhor o que você está fazendo agora; por exemplo, pode dar bom-dia na primeira vez que você desbloqueia o celular de manhã. **Ele não pode mudar nada no celular.**

Baseado no [Cazriel/mcp-server](https://github.com/Cazriel/mcp-server), com estas mudanças:

- **Mantido**: Streamable HTTP, autenticação Bearer, execução em segundo plano e início ao ligar.
- **Removido**: tudo o que pode mudar o estado do aparelho.
- **Adicionado**: ferramentas somente leitura que percebem o estado do celular.
- **Substituído**: o túnel original em texto puro virou um relay TLS no seu próprio VPS, ao qual o celular se conecta.

Versão atual: app **Violet MCP 1.4.2** (pacote `com.mcp.server.violet`), relay **violet-mcp-relay 1.2.0**.

Por enquanto, a interface do app está em chinês.

## Recursos

- **Somente leitura**:
  - As 21 ferramentas estão fixas no código em uma lista de permissões.
  - Execução de comandos, Shizuku, JavaScript, leitura e escrita de arquivos, controle de apps, área de transferência, leitura de notificações e requisições HTTP arbitrárias foram removidos do código-fonte. Eles não aparecem em `tools/list` e não podem ser chamados.
- **Um interruptor por ferramenta**:
  - Por padrão, só 7 ferramentas não sensíveis ficam ligadas: bateria, estado da tela, app em primeiro plano, informações da tela, verificação de estado, próximo alarme, fones / Bluetooth.
  - Tempo de uso, música tocando, rede e local, passos, agenda, apps instalados, identificadores do aparelho e outras precisam ser ligadas por você na página Ferramentas e permissões do celular. Cada ferramenta diz o que ela poderá saber quando ligada, e as que precisam de uma permissão do sistema têm um botão Conceder.
  - Requisições MCP não podem mudar esses interruptores.
- **Sem chute**: com a tela desligada, o celular bloqueado ou a resposta incerta, ele não adivinha qual app você está usando e explica por quê.
- **Lugares só com os nomes que você dá**: você pode dar nome a uma rede Wi-Fi, como "casa" ou "trabalho". O nome da rede (SSID) só é comparado no celular e nunca é enviado.
- **Eventos do celular**:
  - Registra desbloqueios (e quanto tempo a tela ficou desligada), carregamento, conectar ou desconectar fones e chegar ou sair de um lugar com nome.
  - A Violet busca esses eventos uma vez por minuto; o relay não precisa de mudanças.
- **Acesso remoto sem VPN nem IP público**:
  - O celular se conecta via `wss://` ao relay no seu VPS.
  - Autenticação em dois fatores com chave do aparelho e token MCP, mais fixação de certificado (a impressão digital antiga e a nova podem ficar salvas juntas para facilitar a troca de certificado).
  - Reconecta sozinho depois de uma queda, e na hora quando a rede muda.
  - Desde a 1.4.2, pode se conectar a até 3 servidores de relay ao mesmo tempo, cada um com seu interruptor.
- **Credenciais seguras**: o token e a chave do aparelho são guardados criptografados com o Android Keystore e podem ser trocados, revogados ou apagados.
- **Execução em segundo plano**:
  - Usa um serviço em primeiro plano do tipo `specialUse`.
  - O serviço tem três estados: rodando, restrito e parado. Se você parar manualmente, ele não volta sozinho.
- **Atualizações pelo app**:
  - Configurações → Atualizações do app: escolha "automática" (padrão), "só avisar" ou "verificar manualmente".
  - Baixa apenas das GitHub Releases deste repositório e depois confere `SHA256SUMS.txt`, o nome do pacote, o número da versão e o certificado de assinatura; se algo não bater, nada é instalado.
  - Na primeira vez, é preciso permitir que este app "instale apps desconhecidos" nas configurações do sistema.
- **Sem root, sem permissão de acessibilidade.**

> Usado no dia a dia em celulares reais. Cada fabricante limita os apps em segundo plano de um jeito, então depois da primeira instalação vale passar por [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.md) (em inglês): bateria sem restrição, início ao ligar, conexão mantida com a tela desligada.

## Início rápido

**1. Instale o relay no seu servidor**

- Violet instalada com o comando de uma linha (recomendado): na página Ferramentas e celular do painel da Violet, clique em Instalar relay. Ao terminar, a página mostra o endereço do relay, a chave do aparelho e a impressão digital do certificado a preencher no celular, cada um com um botão de copiar.
- Caso contrário: implante manualmente seguindo [docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.md).

**2. Instale e configure o app no celular**

1. Instale o `violet-mcp-<versão>-release.apk` mais recente das [Releases](https://github.com/aileboeth-web/violet-mcp/releases/latest) (o app se chama "Violet MCP"). As próximas versões podem ser atualizadas pelo próprio app.
2. Abra o app → Configurações → Permissões: permita o "acesso aos dados de uso"; ligar "bateria sem restrição" também é recomendado. Depois escolha na página Ferramentas e permissões as ferramentas que vai compartilhar com a Violet.
3. Em Acesso remoto, preencha o endereço do relay, a chave do aparelho e a impressão digital do certificado do passo 1 e ligue a conexão.
4. Volte à tela inicial e toque em **Iniciar**.

**3. Conecte à Violet**

Copie o token de acesso na página Segurança do celular e cole na página Ferramentas e celular do painel da Violet. Pronto. Se você implantou o relay manualmente, adicione o servidor MCP como explicado em [docs/VIOLET_INTEGRATION.md](docs/VIOLET_INTEGRATION.md).

**Depuração local** (sem o relay):

```bash
adb forward tcp:1145 tcp:1145
python tests/interop/test_mcp_interop.py --url http://127.0.0.1:1145/mcp --token <token> --device
```

## Documentação

Os documentos estão em inglês e em chinês simplificado.

| Documento | Conteúdo |
|---|---|
| [docs/DEPLOY_RELAY.md](docs/DEPLOY_RELAY.md) | Implantar o relay no VPS manualmente, configurar o celular, trocar certificados (comandos para copiar e colar) |
| [docs/VIOLET_INTEGRATION.md](docs/VIOLET_INTEGRATION.md) | Conexão com a Violet, códigos de erro, localizar a latência |
| [docs/TOOLS.md](docs/TOOLS.md) | Lista de ferramentas, interruptores padrão, campos retornados, permissões do Android necessárias |
| [docs/UPGRADE.md](docs/UPGRADE.md) | Atualizar e voltar versão (incluindo a mudança do antigo Eve read-only MCP para o Violet MCP) |
| [docs/DEVICE_CHECKLIST.md](docs/DEVICE_CHECKLIST.md) | Checklist em celulares reais |
| [docs/HANDOFF.md](docs/HANDOFF.md) | Entrega do código-fonte, compilar em um ambiente limpo, modelos de assinatura (**não compacte simplesmente o diretório de trabalho**) |
| [docs/TEST_REPORT.md](docs/TEST_REPORT.md) | Resultados dos testes automatizados de cada versão |
| [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md), [docs/REVIEW_v1.2.md](docs/REVIEW_v1.2.md), [docs/SECURITY_AUDIT_v1.2.md](docs/SECURITY_AUDIT_v1.2.md), [docs/SECURITY_RISK_v1.2.1.md](docs/SECURITY_RISK_v1.2.1.md), [docs/SIGNING_KEY_INCIDENT.md](docs/SIGNING_KEY_INCIDENT.md) | Revisões de segurança anteriores e o registro da troca da chave de assinatura (escritos antes da mudança de nome, então usam os nomes de versão e de arquivo da época) |

## Arquitetura

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

| Diretório | Conteúdo |
|---|---|
| `mcp-core/` | Kotlin/JVM puro: protocolo MCP, servidor HTTP, autenticação, lista de permissões somente leitura, cliente do relay, cálculo do tempo de uso. Compartilhado pelo app e pelos testes no PC |
| `app/` | Android: implementação das ferramentas, serviço em primeiro plano, início ao ligar, interface |
| `relay/` | O relay em Go `violet-mcp-relay` e sua configuração do systemd |
| `tests/` | Testes de interoperabilidade / segurança com o SDK Python do MCP, scripts de ponta a ponta no PC, verificações estáticas do APK |

## Endpoints

| Método | Caminho | Autenticação | Observações |
|---|---|---|---|
| `POST` | `/mcp` | Bearer | Streamable HTTP (sem estado, responde com `application/json`; notificações recebem 202) |
| `GET` | `/status` | Bearer | Estado do serviço (sem segredos) |
| `GET` | `/health` | nenhuma | Retorna só `{"ok":true}` |
| `GET` / `DELETE` | `/mcp` | Bearer | 405 (sem fluxo de push do servidor e sem sessões) |

Limites de segurança dos endpoints:

- O token não pode ser desligado. É um valor aleatório de 256 bits comparado em tempo constante.
- Qualquer requisição com cabeçalho `Origin` recebe 403.
- Corpo da requisição até 1 MiB, aninhamento JSON até 64 níveis, requisições em lote até 32 itens.
- O endpoint local só pode se vincular a um endereço de loopback.
- As respostas de `/mcp` trazem `Server-Timing`, mostrando quanto tempo cada etapa levou.

## Compilar a partir do código-fonte

Requisitos: JDK 17, Android SDK (platform 35, build-tools 35), Go 1.23+, Python 3.10+ (para os testes).

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

Os pacotes oficiais são gerados com `tools/package_release.py`, que também verifica se nenhum segredo vazou.

Dica para Windows: se o nome da sua pasta de usuário tiver caracteres não ASCII, os processos de teste do Gradle podem não iniciar (`GradleWorkerMain` não encontrado). Defina `GRADLE_USER_HOME` para um caminho só com ASCII, por exemplo `set GRADLE_USER_HOME=D:\gradle-home`.

## Créditos e licença

Este projeto é baseado no [Cazriel/mcp-server](https://github.com/Cazriel/mcp-server) (GPL-3.0).

Principais mudanças desde 2026-10:

- Removidas as ferramentas de escrita e suas dependências (Shizuku, QuickJS, DocumentFile, Gson) e as declarações de permissões elevadas; as ferramentas viraram uma lista de permissões somente leitura.
- O transporte HTTP e a autenticação foram reescritos.
- Adicionadas ferramentas que percebem o estado do celular (tempo de uso, alarme, música, fones, rede e local, passos, agenda, eventos do celular).
- O túnel bore foi substituído por um relay TLS, com suporte a vários relays.
- O tipo do serviço em primeiro plano passou para specialUse.
- Adicionados interruptores de privacidade e saída mínima, credenciais criptografadas com o Keystore, limites de segurança na escuta, heartbeat / tempos limite / espera offline / medição por etapa no relay, estado do serviço com três estados, páginas de diagnóstico e de segurança, e atualizações pelo app.
- Renomeado para Violet MCP na 1.3.0.

Detalhes em [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md) e no histórico do git.

Também usa: [Model Context Protocol](https://modelcontextprotocol.io/), [OkHttp](https://square.github.io/okhttp/) (Apache-2.0), [gorilla/websocket](https://github.com/gorilla/websocket) (BSD-2-Clause), [Jetpack Compose](https://developer.android.com/jetpack/compose), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines).

Este projeto é distribuído sob a **GNU General Public License v3.0**; o texto completo está em [LICENSE](LICENSE). Ao distribuir o APK, você também precisa fornecer o código-fonte correspondente.
