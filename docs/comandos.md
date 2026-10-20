# Guia de Comandos Úteis

Este documento contém os principais comandos utilizados no desenvolvimento e manutenção do backend Java/Spring Boot.

## Execução da Aplicação

```bash
# Iniciar a aplicação em desenvolvimento (com Hot Reload via DevTools; o profile dev traz os padrões de banco, Lana e segredo JWT)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Iniciar em modo Debug (porta padrão 5005 para anexar a IDE)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

## Build e Empacotamento

```bash
# Limpar artefatos de compilações anteriores
./mvnw clean

# Compilar o projeto e gerar o executável (.jar) sem rodar testes
./mvnw package -DskipTests

# Executar build completo com testes
./mvnw clean package
```

## Testes

```bash
# Executar a suíte de testes unitários e de integração (Testcontainers)
./mvnw test

# Rodar uma classe de teste específica
./mvnw test -Dtest=NomeDaClasseTest
```

## Banco de Dados e Docker Local

```bash
# Subir o PostgreSQL do docker-compose.yml (banco camplana na porta 5432; caso não use Dev Containers)
docker compose up -d

# Mesma coisa com outra porta, se a 5432 estiver em uso (informe o endereço ao backend)
POSTGRES_HOST_PORT=5433 docker compose up -d
export DB_URL=jdbc:postgresql://localhost:5433/camplana

# Parar o banco (os dados ficam no volume; acrescente -v para apagá-los)
docker compose down
```