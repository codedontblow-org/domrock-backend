# Backend - CamplanaAI

API desenvolvida em **Java 17 com Spring Boot 4** para o projeto **CamplanaAI**, responsável pelo gerenciamento das regras de negócio, pelo login com permissões por perfil e pela integração com a **Lana** (agente de IA Generativa).

O projeto utiliza uma arquitetura em camadas (Layered Architecture), PostgreSQL como banco de dados relacional e **Dev Containers** para padronização do ambiente de desenvolvimento.

---

## Tecnologias Utilizadas

![Java](https://img.shields.io/badge/Java-17-141416?style=for-the-badge\&logo=openjdk\&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4-141416?style=for-the-badge\&logo=springboot\&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-141416?style=for-the-badge\&logo=springsecurity\&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-141416?style=for-the-badge\&logo=apachemaven\&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-141416?style=for-the-badge\&logo=postgresql\&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-141416?style=for-the-badge\&logo=docker\&logoColor=white)

---

## Arquitetura

**Tipo de Arquitetura:** Layered Architecture (Arquitetura em Camadas REST)

A aplicação é organizada em camadas responsáveis por diferentes partes do sistema:

* **Controller** — exposição dos endpoints REST e comunicação com o frontend.
* **Service** — implementação das regras de negócio.
* **Repository** — acesso e persistência dos dados utilizando Spring Data JPA.
* **Entity** — representação das entidades persistidas no PostgreSQL.
* **Security** — login, token JWT e permissões por perfil (Spring Security).
* **Client** — chamadas HTTP para a Lana (chat e simulação).

---

## Estrutura do Projeto

```text
domrock-backend/
│
├── .devcontainer/         # Configuração do ambiente de desenvolvimento
├── .mvn/                  # Arquivos do Maven Wrapper
├── docs/                  # Documentação técnica do projeto
│   ├── comandos.md        # Guia de comandos Maven e Docker
│   ├── manual-instalacao.md
│   ├── guia_importacao.md # Importação das planilhas e entidades
│   └── domrock-dataset/   # Planilhas da Dom Rock (RH, vendas e % de comissão, jul-dez/2025)
│
├── src/
│   ├── main/
│   │   ├── java/br/com/camplana/
│   │   │   ├── client/       # Cliente HTTP da Lana
│   │   │   ├── controller/   # Controllers REST
│   │   │   ├── dto/          # Corpos de requisição e resposta (auth, usuario)
│   │   │   ├── entity/       # Entidades JPA
│   │   │   ├── exception/    # Exceções e formato único de erro (ErroResponse)
│   │   │   ├── handler/      # Tratamento de erros (GlobalExceptionHandler, 401/403)
│   │   │   ├── importacao/   # Leitura e validação das planilhas .xlsx
│   │   │   ├── mapper/       # Conversão entre entidades e DTOs
│   │   │   ├── repository/   # Interfaces Spring Data JPA
│   │   │   ├── security/     # JWT, filtro, matriz de perfis
│   │   │   └── service/      # Regras de negócio
│   │   │
│   │   └── resources/
│   │       ├── application.properties      # Valores de todos os ambientes, sem padrões
│   │       ├── application-dev.properties  # Padrões só para desenvolvimento local (profile dev)
│   │       └── db/migration/               # Migrations do Flyway (V1, V2, ...)
│   │
│   └── test/               # Testes unitários e de integração
│
├── .gitignore              # Arquivos ignorados pelo Git
├── docker-compose.yml      # PostgreSQL para rodar o backend localmente
├── Dockerfile              # Configuração da imagem Docker
├── mvnw                    # Maven Wrapper para Linux/macOS
├── mvnw.cmd                # Maven Wrapper para Windows
├── pom.xml                 # Dependências e configuração do Maven
├── CHANGELOG.md            # Histórico de alterações
├── LICENSE                 # Licença do projeto
└── README.md               # Documentação principal
```

---

## Banco de Dados

O projeto utiliza **PostgreSQL 16** como banco de dados relacional principal. As tabelas são criadas pelo **Flyway** ao subir a aplicação, a partir das migrations em `src/main/resources/db/migration/`.

A migration `V4` cria os usuários da demonstração (ver [Usuários da demonstração](#usuários-da-demonstração)).

---

## Configuração do Spring Boot

O backend **não lê arquivo `.env`**: as configurações vêm de variáveis de ambiente. Há dois modos.

| Arquivo | Quando vale | Padrões |
| --- | --- | --- |
| `application.properties` | Sempre (inclusive no Docker Compose do `domrock-camplana`) | **Nenhum**: faltou variável, a aplicação não sobe |
| `application-dev.properties` | Só com o profile `dev` ativo | Banco em `localhost:5432/camplana`, usuário e senha `postgres`, Lana em `localhost:8000` e um segredo JWT de desenvolvimento |

Variáveis obrigatórias fora do profile `dev`:

| Variável | Exemplo | Para quê |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/camplana` | Endereço do banco |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | Credenciais do banco |
| `JWT_SECRET` | saída de `openssl rand -base64 48` | Segredo que assina o token do login (mínimo 32 caracteres) |
| `LANA_URL` | `http://localhost:8000` | Endereço da Lana |
| `LANA_DB_PASSWORD` | `lana_leitura` | Senha do papel somente leitura da Lana (criado pela migration `V3`) |

No profile `dev`, qualquer uma dessas variáveis, se definida, substitui o padrão (ex.: `DB_URL` para usar outra porta).

> **Importante:** nunca ative o profile `dev` em produção. O segredo de desenvolvimento é público e o header `X-Usuario-Dev` (ver [Autenticação](#autenticação)) permitiria entrar como qualquer usuário.

---

## Executando o Projeto

Para subir o sistema completo (banco, backend, Lana e frontend) com um comando, use o repositório [domrock-camplana](https://github.com/codedontblow-org/domrock-camplana). As instruções abaixo rodam **só o backend**, para desenvolvimento.

### Pré-requisitos

Antes de executar o projeto, certifique-se de possuir:

* Java 17
* Docker com Docker Compose
* Git

### 1. Clone o repositório

```bash
git clone https://github.com/codedontblow-org/domrock-backend.git
cd domrock-backend
```

### 2. Suba o PostgreSQL

```bash
docker compose up -d
```

O `docker-compose.yml` deste repositório sobe só o PostgreSQL, com o banco `camplana` na porta `5432`.

> O container se chama `postgres-camplana`, o mesmo nome usado pelo compose do `domrock-camplana`: não suba os dois ao mesmo tempo.

Se a porta `5432` já estiver em uso, troque a porta e informe o novo endereço ao backend:

```bash
POSTGRES_HOST_PORT=5433 docker compose up -d
export DB_URL=jdbc:postgresql://localhost:5433/camplana
```

### 3. Execute a aplicação com o profile `dev`

Linux/macOS:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Windows:

```bash
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Pela IDE, ative o profile `dev` na configuração de execução (no IntelliJ: *Active profiles* = `dev`).

Sem o profile `dev`, a aplicação só sobe com todas as variáveis da tabela acima definidas.

### 4. Confira

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"gabriela@camplana.com.br","senha":"123456"}'
```

A resposta `200` traz o `token`, o `perfil` e a `validade`.

---

## Autenticação

Todas as rotas, exceto o login, exigem o header `Authorization: Bearer <token>`. O token vale 8 horas. Sem token, a resposta é **401**; com um perfil sem permissão, **403**.

| Rota | Gerente | Supervisor | Administrador |
| --- | --- | --- | --- |
| `POST /api/auth/login` | sim | sim | sim |
| `POST /api/chat` e `POST /api/simulacoes` | sim | sim | não |
| `/api/campanhas/**` (Sprint 2) | sim | sim | não |
| `POST /api/campanhas/{id}/decidir` (Sprint 2) | não | sim | não |
| `/api/importacao/**` (importação da base do mês; ver [docs/guia_importacao.md](docs/guia_importacao.md)) | não | sim | não |
| `/api/usuarios/**` (ver [Gestão de usuários](#gestão-de-usuários)) | não | não | sim |

Qualquer rota fora dessa tabela é negada.

No profile `dev`, o header `X-Usuario-Dev: <id do usuário>` substitui o token, para testar sem fazer login (ex.: `curl http://localhost:8080/api/usuarios -H "X-Usuario-Dev: 4"`). Fora do profile `dev`, esse header é ignorado.

### Usuários da demonstração

Criados pela migration `V4`. Senha de todos: `123456` (só para a demonstração).

| id | Nome | E-mail | Perfil |
| --- | --- | --- | --- |
| 1 | Gabriela | gabriela@camplana.com.br | Gerente de Vendas (`GERENTE`) |
| 2 | Sérgio | sergio@camplana.com.br | Supervisor de Vendas (`SUPERVISOR`) |
| 3 | Sílvia | silvia@camplana.com.br | Supervisor de Vendas (`SUPERVISOR`) |
| 4 | Ana | ana@camplana.com.br | Administrador (`ADMIN`) |

Usuário inativo (`ativo = false`) não entra, nem pelo login nem com um token emitido antes.

### Gestão de usuários

Só o Administrador. O usuário nunca é apagado: é desativado, para que campanhas e aprovações continuem apontando para o autor.

| Rota | O que faz | Erros |
| --- | --- | --- |
| `POST /api/usuarios` | Cria o usuário. Corpo: `nome`, `email`, `senha` (6 a 72 caracteres) e `perfil` (`GERENTE`, `SUPERVISOR` ou `ADMIN`). Responde `201` | `409` se o e-mail já existir, sem diferenciar maiúsculas |
| `GET /api/usuarios` | Lista paginada (parâmetros `page`, `size` e `sort` do Spring, ex.: `?page=0&size=20&sort=nome`) | |
| `GET /api/usuarios/{id}` | Busca um usuário | |
| `PUT /api/usuarios/{id}` | Edita `nome`, `email` e `perfil`; campo ausente não muda. A senha não é editada aqui | `409` se o e-mail for de outro usuário; `400` se o Administrador tirar o próprio perfil de Administrador |
| `DELETE /api/usuarios/{id}` | **Desativa** (`ativo = false`); a linha continua no banco. Responde `200` com o usuário | `400` se for a própria conta |
| `PATCH /api/usuarios/{id}/reativar` | Reativa (`ativo = true`) | |

A resposta nunca traz a senha: só `id`, `nome`, `email`, `perfil` e `ativo`.

- O e-mail é comparado sem diferenciar maiúsculas no cadastro, na edição e no login (`Ana@camplana.com.br` entra como `ana@camplana.com.br`).
- Editar o e-mail de um usuário invalida o token dele (o token guarda o e-mail): ele precisa entrar de novo.
- Com as duas travas do Administrador, sempre sobra pelo menos um Administrador ativo.

---

## Desenvolvimento com Dev Container

O projeto possui configuração de **Dev Container**, permitindo que o ambiente de desenvolvimento seja padronizado utilizando Docker.

Isso facilita a configuração do projeto entre diferentes máquinas, reduzindo diferenças entre ambientes de desenvolvimento.

Para utilizar:

1. Instale o Docker.
2. Instale o **Visual Studio Code**.
3. Instale a extensão **Dev Containers**.
4. Abra o projeto no VS Code.
5. Selecione **Reopen in Container** quando solicitado.

Dentro do Dev Container, o banco é o serviço `db` (banco `postgres`), e não `localhost:5432/camplana`. Rode informando o endereço:

```bash
DB_URL=jdbc:postgresql://db:5432/postgres ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## Testes

Os testes do projeto estão localizados em:

```text
src/test/
```

O projeto utiliza testes unitários e testes de integração, com suporte a **Testcontainers** para execução de ambientes de teste isolados (é preciso ter o Docker rodando).

Para executar os testes:

```bash
./mvnw test
```

No Windows:

```bash
mvnw.cmd test
```

---

## Documentação Complementar

* [docs/comandos.md](docs/comandos.md): principais comandos utilizados no desenvolvimento.
* [docs/manual-instalacao.md](docs/manual-instalacao.md): passo a passo de instalação.
* [docs/guia_importacao.md](docs/guia_importacao.md): importação das planilhas e estrutura das entidades.

---

## Licença

Este projeto está sob a licença especificada no arquivo [LICENSE](LICENSE).
