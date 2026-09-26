# Backend - CamplanaAI

API desenvolvida em **Java 17 com Spring Boot** para o projeto **CamplanaAI**, responsável pelo gerenciamento das regras de negócio e pela integração com recursos de **IA Generativa**.

O projeto utiliza uma arquitetura em camadas (Layered Architecture), PostgreSQL como banco de dados relacional e **Dev Containers** para padronização do ambiente de desenvolvimento.

---

## Tecnologias Utilizadas

![Java](https://img.shields.io/badge/Java-17-141416?style=for-the-badge\&logo=openjdk\&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-141416?style=for-the-badge\&logo=springboot\&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-141416?style=for-the-badge\&logo=apachemaven\&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-141416?style=for-the-badge\&logo=postgresql\&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-141416?style=for-the-badge\&logo=docker\&logoColor=white)

---

## Arquitetura

**Tipo de Arquitetura:** Layered Architecture (Arquitetura em Camadas REST)

A aplicação é organizada em camadas responsáveis por diferentes partes do sistema:

* **Controller** — exposição dos endpoints REST e comunicação com o frontend.
* **Service** — implementação das regras de negócio e integração com serviços externos/IA.
* **Repository** — acesso e persistência dos dados utilizando Spring Data JPA.
* **Entity** — representação das entidades persistidas no PostgreSQL.
* **Config** — configurações globais da aplicação, como segurança e CORS.

---

## Estrutura do Projeto

```text
domrock-backend/
│
├── .devcontainer/         # Configuração do ambiente de desenvolvimento
├── .mvn/                  # Arquivos do Maven Wrapper
├── docs/                  # Documentação técnica do projeto
│   └── comandos.md        # Guia de comandos Maven e Docker
│
├── src/
│   ├── main/
│   │   ├── java/br/com/camplana/
│   │   │   ├── config/       # Beans globais e configurações
│   │   │   ├── Controller/   # Controllers REST
│   │   │   ├── Entity/       # Entidades JPA
│   │   │   ├── Repository/   # Interfaces Spring Data JPA
│   │   │   └── Service/      # Regras de negócio e integração com IA
│   │   │
│   │   └── resources/
│   │       └── application.properties
│   │
│   └── test/               # Testes unitários e de integração
│
├── .env.example            # Modelo das variáveis de ambiente
├── .gitignore              # Arquivos ignorados pelo Git
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

O projeto utiliza **PostgreSQL 17** como banco de dados relacional principal.

As informações de conexão são configuradas através de **variáveis de ambiente**, evitando que credenciais sejam armazenadas diretamente nos arquivos de configuração ou versionadas no repositório.

### Variáveis de ambiente

O projeto disponibiliza um arquivo `.env.example` contendo o modelo das variáveis necessárias:

```env
DB_URL=jdbc:postgresql://localhost:5432/camplana
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

### Configuração do ambiente

Após clonar o projeto, copie o arquivo `.env.example` para `.env`:

```bash
cp .env.example .env
```

No Windows, também é possível criar uma cópia manualmente:

```text
.env.example → .env
```

Depois, ajuste os valores de acordo com a configuração local do PostgreSQL.

> **Importante:** o arquivo `.env` contém informações de configuração que podem incluir credenciais e **não deve ser commitado no Git**. O arquivo `.env.example` deve ser utilizado apenas como modelo.

---

## Configuração do Spring Boot

A aplicação possui um `DotEnvInitializer` responsável por carregar as variáveis definidas no arquivo `.env` durante a inicialização da aplicação.

As propriedades do banco utilizam as variáveis de ambiente:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

Caso o arquivo `.env` não esteja disponível, a aplicação pode utilizar as variáveis de ambiente fornecidas diretamente pelo sistema.

---

## Executando o Projeto

### Pré-requisitos

Antes de executar o projeto, certifique-se de possuir:

* Java 17
* Docker
* Docker Compose
* PostgreSQL 17
* Git

### 1. Clone o repositório

```bash
git clone https://github.com/codedontblow-org/domrock-backend
cd domrock-backend
```

### 2. Configure o `.env`

Crie o arquivo `.env` a partir do exemplo:

```bash
cp .env.example .env
```

Configure as credenciais e a URL do PostgreSQL conforme seu ambiente.

### 3. Execute a aplicação

Utilizando o Maven Wrapper:

```bash
./mvnw spring-boot:run
```

Ou execute a aplicação diretamente pela IDE.

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

---

## Testes

Os testes do projeto estão localizados em:

```text
src/test/
```

O projeto utiliza testes unitários e testes de integração, com suporte a **Testcontainers** para execução de ambientes de teste isolados.

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

Para consultar os principais comandos utilizados no desenvolvimento:

[docs/comandos.md](docs/comandos.md)

---

## Licença

Este projeto está sob a licença especificada no arquivo [LICENSE](LICENSE).
