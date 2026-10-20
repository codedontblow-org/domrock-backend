# Arquitetura da comunicação com a Lana (gRPC)

Este documento descreve como o backend Spring Boot conversa com a **Lana**, o microsserviço de IA em Python, e por que escolhemos **gRPC** para isso.

---

## Visão geral

O Spring Boot é a **porta única do front-end**. O front chama `POST /api/chat` e o Spring repassa a mensagem à Lana por **gRPC** (porta `50051`).

Neste canal:

| | Java (Spring Boot) | Python (Lana) |
|---|---|---|
| Papel no gRPC | **Cliente** (inicia a chamada) | **Servidor** (escuta e responde) |
| Classe principal | `IaGrpcClientService` (stub bloqueante) | `IAServiceServicer` |
| Porta | usa `grpc.lana.port` (padrão `50051`) | escuta em `[::]:50051` |
| Keepalive e prazo | `keepAliveTime(30s)` e `withDeadlineAfter(2 min)` | `keepalive_time_ms` e timeout de 100 s |

Em relação ao front, o Spring é servidor HTTP. Em relação à Lana, ele é cliente gRPC.

---

## Por que gRPC

O gRPC é um framework de **chamada remota de procedimentos (RPC)** **open source** (licença Apache 2.0), criado no Google e hoje mantido pela CNCF, com suporte oficial a Java, Python e várias outras linguagens.

Os motivos da escolha:

- **Baixa latência e baixo overhead.** Usa HTTP/2 (conexão persistente e multiplexada) e serializa com Protocol Buffers, um formato binário mais compacto e rápido de processar que JSON. Não há custo de abrir uma conexão nova a cada mensagem.
- **Contrato forte.** O arquivo `.proto` define o serviço e as mensagens. Java e Python geram o código a partir dele, então os dois lados não divergem em nomes e tipos de campos.
- **Código gerado.** O stub do cliente (Java) e o servicer (Python) saem do mesmo contrato, sem montar requisições à mão.
- **Prazos e erros padronizados.** Deadline, cancelamento e status (`DEADLINE_EXCEEDED`, `UNAVAILABLE`, `RESOURCE_EXHAUSTED`) fazem parte do protocolo.
- **Evolução futura.** Suporta streaming, o que permite no futuro enviar a resposta da Lana em partes, conforme a LLM gera o texto.

> **Nota sobre desempenho:** o ganho do gRPC está na comunicação entre os serviços. O tempo de uma resposta da Lana (de segundos a cerca de 1 minuto) é dominado pela LLM e pelas consultas ao banco, não pelo transporte.

---

## Diagrama de arquitetura

```mermaid
flowchart LR
    subgraph Windows
        F["Front Vue<br/>+ Vite proxy"]
        subgraph Lana["Lana (Python / FastAPI)"]
            S["Servidor gRPC<br/>:50051<br/>IAServiceServicer"]
            G["Guardrail"]
            A["Agente LangChain"]
            S --> G
            G -->|"pergunta real"| A
        end
        DB[("Postgres")]
        LLM["LLM (OpenRouter)"]
        A --> LLM
        A --> DB
    end

    subgraph Devcontainer["Devcontainer (Linux)"]
        J["Spring Boot :8080<br/>LanaClient + IaGrpcClientService<br/>(gRPC CLIENTE)"]
    end

    F -->|"HTTP /api/chat"| J
    J -->|"gRPC InvokeAgent<br/>host.docker.internal:50051"| S
```

## Diagrama de sequência

```mermaid
sequenceDiagram
    autonumber
    participant F as Front (Vue)
    participant V as Vite proxy
    participant J as Spring Boot<br/>(gRPC CLIENTE)
    participant P as Lana Python<br/>(gRPC SERVIDOR :50051)
    participant L as LLM / Postgres

    F->>V: POST /backend/api/chat<br/>{chat_id, message}
    V->>J: POST /api/chat
    Note over J: LanaClient extrai message e chat_id<br/>IaGrpcClientService.chamarAgenteLana()
    J->>P: gRPC unário IAService.InvokeAgent<br/>InvokeRequest{prompt, chat_id}<br/>deadline 2 min, plaintext

    alt Guardrail (saudação ou ajuda)
        Note over P: classificar_guardrail() → resposta fixa<br/>custo zero de tokens
        P-->>J: InvokeResponse{resposta}
    else Pergunta real
        P->>L: lana_invoke() em thread<br/>LLM + consultas SQL
        L-->>P: resposta do agente
        P-->>J: InvokeResponse{resposta}
    else Erro ou timeout (100 s)
        P-->>J: status gRPC<br/>DEADLINE_EXCEEDED / RESOURCE_EXHAUSTED / INTERNAL
    end

    Note over J: LanaClient monta o JSON<br/>{chat_id, response, events: [], regra: null}
    J-->>V: 200 OK
    V-->>F: resposta exibida no chat
```

---

## Contrato (`.proto`)

O mesmo arquivo existe nos dois projetos (`src/main/proto/ia_service.proto` no Java e `app/proto/ia_service.proto` no Python).

```protobuf
syntax = "proto3";

package camplana;

option java_multiple_files = true;
option java_package = "br.com.camplana.grpc";
option java_outer_classname = "IaServiceProto";

service IAService {
  rpc InvokeAgent (InvokeRequest) returns (InvokeResponse);
}

message InvokeRequest {
  string prompt = 1;
  string chat_id = 2;
}

message InvokeResponse {
  string resposta = 1;
}
```

- **Java:** os stubs são gerados no build pelo `protobuf-maven-plugin` (`./mvnw clean compile`).
- **Python:** os stubs são gerados com `grpc_tools.protoc` e exigem `grpcio >= 1.84.0`.
- Ao alterar o `.proto`, copie-o para os dois projetos e regenere os stubs dos dois lados.

---

## Componentes

| Componente | Responsabilidade |
|---|---|
| `LanaClient` (Java) | Extrai `message` e `chat_id` do corpo REST, chama o serviço gRPC e monta o JSON de resposta para o front |
| `IaGrpcClientService` (Java) | Cria o `ManagedChannel` (plaintext, keepalive) e faz a chamada bloqueante com deadline de 2 min |
| `grpc_server.py` (Python) | Sobe o servidor `grpc.aio` na porta 50051, iniciado no `lifespan` do FastAPI |
| `ia_servicer.py` (Python) | Aplica o guardrail, chama `lana_invoke` em thread e converte erros em status gRPC |
| `guardrails.py` (Python) | Responde saudações e pedidos de ajuda sem chamar a LLM (regex + fuzzy matching) |

---

## Contrato de resposta ao front

O front espera o formato do `AgentResponse`. O `LanaClient` devolve:

```json
{
  "chat_id": "chat-123",
  "response": "texto da Lana",
  "events": [],
  "regra": null
}
```

---

## Configuração

```properties
grpc.lana.host=${GRPC_LANA_HOST:host.docker.internal}
grpc.lana.port=${GRPC_LANA_PORT:50051}
```

| Cenário | `GRPC_LANA_HOST` |
|---|---|
| Spring e Lana na mesma máquina, sem container | `localhost` |
| Spring no Dev Container e Lana no Windows | `host.docker.internal` |

No Python, o `.env` precisa de `DATABASE_URL` válida, no formato `postgresql://usuario:senha@localhost:5432/postgres` (não use o formato JDBC).

### Prazos e timeouts

Os prazos são escalonados para que o erro mais específico apareça primeiro:

| Camada | Valor | Observação |
|---|---|---|
| Lana (`asyncio.wait_for`) | 100 s | Falha primeiro, com mensagem clara |
| Spring (`withDeadlineAfter`) | 2 min | Prazo da chamada gRPC |
| Front (axios) | 3 min | Maior que os anteriores |

### Keepalive

Os dois lados configuram keepalive para evitar que conexões ociosas sejam derrubadas no trajeto Docker/Windows (`UNAVAILABLE: io exception`). O servidor Python precisa aceitar os pings do cliente, senão o erro `too_many_pings` fecha a conexão.

---

## Troubleshooting

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| Timeout logo no início | Host errado (`localhost` dentro do Dev Container) | Usar `host.docker.internal` |
| `NoSuchMethodError: Http2ClientStreamTransportState` | Versões gRPC diferentes no `pom.xml` | Usar o `grpc-bom` e dependências sem versão; rodar `./mvnw dependency:tree -Dincludes=io.grpc` |
| `UNAVAILABLE: io exception` | Conexão ociosa derrubada, firewall na porta 50051 ou Lana fora do ar | Conferir keepalive nos dois lados, firewall do Windows e se o uvicorn está de pé |
| `DEADLINE_EXCEEDED` | Agente preso (LLM, tool ou lock do chat) | Ver os logs do uvicorn; conferir `DATABASE_URL` e a cota da LLM |
| `RESOURCE_EXHAUSTED` | Limite de uso do provedor da LLM | Aguardar cerca de 1 minuto e repetir |
| `fe_sendauth: no password supplied` (log da Lana) | `DATABASE_URL` vazia no `.env` do Python | Preencher a variável e reiniciar o uvicorn |
| Retorna 200, mas o front não mostra texto | Campo da resposta diferente de `response` | Conferir o JSON na aba Network do navegador |
| Erro `Proto path element is not a directory` no build | Bug do `protobuf-maven-plugin` com certos JARs | Limpar `target/` e o cache `~/.m2` de `io/grpc` e `com/google/protobuf` |

---

## Limitações conhecidas

- O proto transporta só `resposta`. Os campos `events` e `regra` (painel de revisão de regras) ainda não passam por gRPC e vão vazios.
- `LanaClient.simular` reaproveita o fluxo do chat. A simulação real (`/api/simulacoes`) ainda não está implementada via gRPC.
- A chamada é bloqueante (unária). Respostas em streaming ficam como evolução.
- Mensagens respondidas pelo guardrail não entram no histórico do agente.
- O canal usa `plaintext` (sem TLS), adequado a desenvolvimento. Para produção, avaliar TLS entre os serviços.

## Próximos passos

- Estender o proto com `regra_json` (entrada) e `events_json` e `regra_json` (saída).
- Implementar a simulação por gRPC.
- Mapear `DEADLINE_EXCEEDED` para HTTP 504 com mensagem amigável.

---

## Como testar

1. Suba a Lana e confirme `📡 gRPC Server escutando em [::]:50051`.
2. Suba o Spring (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`).
3. Envie:

   ```http
   POST http://localhost:8080/api/chat
   { "chat_id": "teste-1", "message": "Olá Lana, o que você pode fazer?" }
   ```

4. Resultado esperado: `200` com `chat_id`, `response`, `events` e `regra`.
5. Teste automatizado do cliente (sem depender da Lana): `./mvnw test -Dtest=LanaClientTests`.
