# Como Executar

## Pré-requisitos
- **[Java 17](https://www.oracle.com/java/)** instalado e configurado nas variáveis de ambiente (JAVA_HOME).

### Para Opção 1: Dev Container (Recomendado)
- **[Docker Desktop](https://www.docker.com/)** instalado e aberto (Engine running).

- **[Visual Studio Code](https://code.visualstudio.com/)** com a extensão Dev Containers instalada.

### Para Opção 2: Execução Local (Sem Dev Container)
- **[Docker](https://www.docker.com/)** (para subir a imagem do PostgreSQL) ou uma instância local do PostgreSQL rodando.

- **IDE** IDE de sua preferência ([Visual Studio Code](https://code.visualstudio.com/) ou **[IntelliJ IDEA](https://www.jetbrains.com/pt-br/idea/)**).


## Passo a passo:
1. **Clone o repositório**:
```bash
git clone https://github.com/codedontblow-org/domrock-backend.git
cd domrock-backend

```
## Opção 1: Via Dev Container (Recomendado)

2. Abra a pasta domrock-backend no VS Code.

3. Pressione F1 (ou Ctrl+Shift+P), digite e selecione: Dev Containers: Reopen in Container.

4. Aguarde o build automático do ambiente isolado.

5. Abra o terminal integrado do VS Code (Ctrl + ') e execute (no Dev Container o banco é o serviço `db`):

```bash
DB_URL=jdbc:postgresql://db:5432/postgres ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Opção 2: Execução Local (Sem Dev Container)

2. Suba o banco PostgreSQL pelo `docker-compose.yml` do repositório (cria o banco `camplana` na porta 5432):
```bash
docker compose up -d
```

Se a porta 5432 já estiver em uso: `POSTGRES_HOST_PORT=5433 docker compose up -d` e `export DB_URL=jdbc:postgresql://localhost:5433/camplana`.

3. Execute a aplicação via Maven Wrapper, com o profile `dev` (traz os padrões de desenvolvimento; sem ele, é preciso definir `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `LANA_URL` e `LANA_DB_PASSWORD`):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Endpoints de Verificação
Após subir a aplicação por qualquer uma das opções, você poderá acessar:

> API Base: http://localhost:8080

Para conferir, faça login com um dos usuários da demonstração (senha `123456`; lista no README):

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"gabriela@camplana.com.br","senha":"123456"}'
```

A resposta `200` traz o `token`, que as outras rotas pedem no header `Authorization: Bearer <token>`.
