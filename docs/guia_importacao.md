# Guia de Importação e Estrutura das Entidades

## 1. Visão geral

Esta funcionalidade implementa a estrutura de persistência do banco e a importação das bases do mês (RH, vendas e % de comissão) em arquivos `.xlsx`.

A importação é um **job assíncrono** (A6-78): o Supervisor envia as planilhas do mês, recebe o id do job na hora (`202`) e acompanha o status. As planilhas são validadas antes de gravar; com pendência IMPEDITIVA, nada é gravado.

Rotas (só o perfil Supervisor, com `Authorization: Bearer <token>`):

```http
POST /api/importacao        # envia as planilhas e cria o job
GET  /api/importacao/{id}   # status, totais e relatório de pendências
GET  /api/importacao        # histórico de envios, do mais recente para o mais antigo
```

O fluxo geral é:

```text
Planilhas .xlsx (RH + Vendas [+ Comissão])
    |
    v
ImportacaoController ------------------------> 202 { id, status: PROCESSANDO }
    |
    v
ImportacaoManagerService (.xlsx, nomes, um job por vez; cria o job)
    |
    v  (@Async, em segundo plano)
ImportacaoJobService
    |
    +--> DetectorTipoArquivo + LeitorXlsx
    |
    +--> ValidadorImportacaoService --> pendências IMPEDITIVO / AVISO
    |
    +--> com IMPEDITIVO: job PENDENTE, nada gravado
    |
    +--> sem IMPEDITIVO: ImportacaoPersistenceService.executarCargaAtomica
              |   (uma transação: tudo ou nada)
              +--> ImportadorRhService
              +--> ImportadorVendasService
              +--> ImportadorComissaoService
                        |
                        v
                   PostgreSQL --> job CONCLUIDO (ou FALHA)
```

---

# 2. Estrutura de pacotes

A funcionalidade está organizada principalmente nos seguintes pacotes:

```text
br.com.camplana
|
+-- controller
|   +-- ImportacaoController
|
+-- dto/importacao
|   +-- JobCriadoResponse
|   +-- JobStatusResponse
|   +-- HistoricoImportacaoResponse
|   +-- PendenciaImportacao
|
+-- entity
|   +-- Importacao            (job de importação)
|   +-- Cargo
|   +-- Funcionario
|   +-- FuncionarioCargo
|   +-- FuncionarioCargoId
|   +-- FuncionarioLoja
|   +-- FuncionarioLojaId
|   +-- Loja
|   +-- Marca
|   +-- MarcaCargo
|   +-- MarcaCargoId
|   +-- Venda
|
+-- repository
|   +-- ImportacaoRepository
|   +-- CargoRepository
|   +-- FuncionarioRepository
|   +-- FuncionarioCargoRepository
|   +-- FuncionarioLojaRepository
|   +-- LojaRepository
|   +-- MarcaRepository
|   +-- MarcaCargoRepository
|   +-- VendaRepository
|
+-- importacao
|   +-- ImportacaoManagerService
|   +-- ImportacaoJobService
|   +-- ImportacaoPersistenceService
|   +-- ValidadorImportacaoService
|   +-- EncerradorImportacoesInterrompidas
|   +-- CompetenciaExtractor
|   +-- DetectorTipoArquivo
|   +-- LeitorXlsx
|   +-- NormalizadorData
|   +-- TipoArquivo
|
+-- service
    +-- ImportadorRhService
    +-- ImportadorVendasService
    +-- ImportadorComissaoService
```

---

# 3. Entidades

As classes do pacote `Entity` representam as tabelas do PostgreSQL através do JPA.

As classes utilizam `@Entity` e `@Table` para fazer o mapeamento entre Java e banco.

## 3.1 Cargo

Representa os cargos dos funcionários.

Tabela:

```text
cargo
```

Campos principais:

| Java | Banco | Descrição |
|---|---|---|
| `id` | `id` | Identificador interno |
| `codCargo` | `cod_cargo` | Código do cargo |
| `descrCargo` | `descr_cargo` | Descrição do cargo |

`cod_cargo` é único e não pode ser nulo.

O `CargoRepository` permite procurar um cargo pelo código:

```java
Optional<Cargo> findByCodCargo(String codCargo);
```

---

## 3.2 Funcionario

Representa os funcionários.

Tabela:

```text
funcionario
```

Campos:

| Java | Banco | Descrição |
|---|---|---|
| `id` | `id` | Identificador interno |
| `matricula` | `matricula` | Matrícula do funcionário |
| `dataAdmissao` | `data_admissao` | Data de admissão |
| `dataDemissao` | `data_demissao` | Data de demissão |

A matrícula é única.

O `FuncionarioRepository` possui:

```java
Optional<Funcionario> findByMatricula(String matricula);
```

Essa consulta é especialmente importante na importação de vendas.

A venda recebe uma matrícula e procura o funcionário correspondente antes de criar o registro.

---

## 3.3 Marca

Representa uma marca.

Tabela:

```text
marca
```

Campos:

| Java | Banco | Descrição |
|---|---|---|
| `id` | `id` | Identificador interno |
| `codMarca` | `cod_marca` | Código da marca |
| `descrMarca` | `descr_marca` | Descrição da marca |

`cod_marca` é único.

O `MarcaRepository` possui:

```java
Optional<Marca> findByCodMarca(String codMarca);
```

---

## 3.4 Loja

Representa uma loja vinculada a uma marca.

Tabela:

```text
loja
```

Campos:

| Java | Banco | Descrição |
|---|---|---|
| `id` | `id` | Identificador interno |
| `codLoja` | `cod_loja` | Código da loja |
| `descrLoja` | `descr_loja` | Descrição da loja |
| `marca` | `marca_id` | Marca à qual a loja pertence |

Relacionamento:

```text
Marca 1 ---- N Loja
```

A entidade `Loja` possui um relacionamento `@ManyToOne` com `Marca`.

O `LojaRepository` possui:

```java
Optional<Loja> findByCodLoja(String codLoja);
```

---

# 4. Entidades de relacionamento

Algumas relações possuem informações próprias, como a competência.

Por isso, em vez de utilizar apenas uma tabela intermediária simples, existem entidades específicas.

## 4.1 FuncionarioCargo

Relaciona:

```text
Funcionario
    +
Cargo
    +
Competência
```

Tabela:

```text
funcionario_cargo
```

A chave composta é:

```text
funcionario_id
cargo_id
date_ref
```

A classe:

```text
FuncionarioCargoId
```

representa essa chave composta.

Campos da chave:

```java
Integer funcionarioId;
Integer cargoId;
LocalDate dateRef;
```

A entidade `FuncionarioCargo` utiliza:

```java
@EmbeddedId
```

e os relacionamentos:

```java
@ManyToOne
@MapsId("funcionarioId")
Funcionario funcionario;
```

e:

```java
@ManyToOne
@MapsId("cargoId")
Cargo cargo;
```

Assim, uma linha da tabela representa o cargo que um funcionário possui em determinada competência.

---

## 4.2 FuncionarioLoja

Relaciona:

```text
Funcionario
    +
Loja
    +
Competência
```

Tabela:

```text
funcionario_loja
```

A chave composta é:

```text
funcionario_id
loja_id
date_ref
```

A classe:

```text
FuncionarioLojaId
```

representa essa chave.

Campos:

```java
Integer funcionarioId;
Integer lojaId;
LocalDate dateRef;
```

A entidade possui os relacionamentos com `Funcionario` e `Loja`.

---

## 4.3 MarcaCargo

Relaciona:

```text
Marca
    +
Cargo
    +
Competência
    +
Percentual de comissão
```

Tabela:

```text
marca_cargo
```

A chave composta é:

```text
marca_id
cargo_id
date_ref
```

A classe:

```text
MarcaCargoId
```

representa a chave composta.

Além da chave, `MarcaCargo` possui:

```java
BigDecimal pctComiss;
```

que é armazenado em:

```text
pct_comiss
```

A coluna possui precisão:

```text
NUMERIC(5,2)
```

---

# 5. Venda

Representa uma venda realizada por um funcionário.

Tabela:

```text
venda
```

Campos:

| Java | Banco | Descrição |
|---|---|---|
| `id` | `id` | Identificador interno |
| `funcionario` | `funcionario_id` | Funcionário relacionado |
| `dateRef` | `date_ref` | Data de referência |
| `vlrVenda` | `vlr_venda` | Valor da venda |

Relacionamento:

```text
Funcionario 1 ---- N Venda
```

A venda possui um `@ManyToOne` com `Funcionario`.

O `VendaRepository` possui a consulta utilizada para verificar se uma competência já foi importada:

```java
boolean existsByDateRefBetween(LocalDate inicio, LocalDate fim);
```

---

# 6. Repositories

Os repositories ficam responsáveis pelo acesso às entidades no banco.

Todos utilizam Spring Data JPA e, quando necessário, adicionam métodos de consulta derivados do nome.

Estrutura:

```text
Repository
    |
    +-- JpaRepository
          |
          +-- operações CRUD
          +-- consultas específicas
```

## CargoRepository

```java
public interface CargoRepository
        extends JpaRepository<Cargo, Integer>
```

Consulta adicional:

```java
Optional<Cargo> findByCodCargo(String codCargo);
```

Utilizada para localizar cargos durante a importação.

---

## FuncionarioRepository

```java
public interface FuncionarioRepository
        extends JpaRepository<Funcionario, Integer>
```

Consulta:

```java
Optional<Funcionario> findByMatricula(String matricula);
```

Utilizada principalmente para:

- localizar funcionários durante RH;
- validar a matrícula de uma venda.

---

## LojaRepository

```java
public interface LojaRepository
        extends JpaRepository<Loja, Integer>
```

Consulta:

```java
Optional<Loja> findByCodLoja(String codLoja);
```

---

## MarcaRepository

```java
public interface MarcaRepository
        extends JpaRepository<Marca, Integer>
```

Consulta:

```java
Optional<Marca> findByCodMarca(String codMarca);
```

---

## FuncionarioCargoRepository

Utiliza chave composta:

```java
JpaRepository<FuncionarioCargo, FuncionarioCargoId>
```

A chave do repository é `FuncionarioCargoId`.

---

## FuncionarioLojaRepository

Utiliza:

```java
JpaRepository<FuncionarioLoja, FuncionarioLojaId>
```

Também possui uma consulta utilizada para verificar se uma competência de RH já foi importada:

```java
boolean existsByIdDateRef(LocalDate dateRef);
```

---

## MarcaCargoRepository

Utiliza:

```java
JpaRepository<MarcaCargo, MarcaCargoId>
```

A própria chave composta é utilizada para localizar a relação entre marca, cargo e competência.

---

## VendaRepository

Utiliza:

```java
JpaRepository<Venda, Integer>
```

Consulta adicional:

```java
boolean existsByDateRefBetween(
    LocalDate inicio,
    LocalDate fim
);
```

Ela verifica se já existem vendas dentro do intervalo da competência.

---

# 7. Classes do pacote importacao

## TipoArquivo

Enum utilizado para representar os tipos de base:

```java
RH
VENDAS
COMISSAO
```

---

## DetectorTipoArquivo

Responsável por identificar o tipo através do nome do arquivo.

Regras:

```text
RH
    -> RH

VENDAS
    -> VENDAS

COMMISS ou COMISS
    -> COMISSAO
```

Exemplos:

```text
BASE_RH_SET25.xlsx
BASE_VENDAS_SET25.xlsx
BASE_COMMISS_SET25.xlsx
```

---

## CompetenciaExtractor

Extrai a competência do nome do arquivo.

O padrão esperado é:

```text
MMMYY
```

Exemplos:

```text
SET25
OUT25
JAN26
```

Os meses aceitos são:

```text
JAN
FEV
MAR
ABR
MAI
JUN
JUL
AGO
SET
OUT
NOV
DEZ
```

Por exemplo:

```text
BASE_VENDAS_SET25.xlsx
```

resulta em:

```text
2025-09
```

---

## NormalizadorData

Responsável por transformar os valores de data recebidos pelo fluxo de importação em `LocalDate`.

A representação utilizada internamente é:

```text
yyyy-MM-dd
```

---

## LeitorXlsx

É responsável somente pela leitura da planilha.

Utiliza Apache POI.

O fluxo é:

```text
bytes da planilha (byte[])
    ↓
Workbook
    ↓
Sheet
    ↓
Cabeçalho
    ↓
Linhas
    ↓
Map<String, String>
```

O leitor:

- utiliza a primeira planilha do arquivo;
- identifica os cabeçalhos da primeira linha;
- ignora cabeçalhos vazios;
- lê as linhas seguintes;
- ignora linhas completamente vazias;
- converte os valores para texto normalizado.

### Números

Valores numéricos são convertidos sem zeros decimais desnecessários.

Exemplo:

```text
20.0
```

vira:

```text
20
```

Isso é importante para códigos como:

```text
Cod_Marca
Cod_Loja
Cod_Cargo
```

### Datas

Células reconhecidas como datas são convertidas para:

```text
yyyy-MM-dd
```

### Fórmulas

Quando a célula é uma fórmula, o leitor utiliza o resultado armazenado da fórmula.

Isso evita que o processamento entre em recursão chamando `celulaComoTexto()` novamente.

---

# 8. Services de importação

## ImportacaoManagerService

Recebe a requisição do controller e, antes de criar o job:

1. recusa com `409` se já houver um job `PROCESSANDO`;
2. recusa com `400` arquivo sem nome, que não seja `.xlsx` ou enviado duas vezes;
3. lê os bytes dos arquivos e cria o job (`importacao`) com status `PROCESSANDO`;
4. dispara o processamento em segundo plano e devolve o id.

Também monta as respostas de `GET /api/importacao/{id}` e `GET /api/importacao`.

---

## ImportacaoJobService

Roda em segundo plano (`@Async`). Para cada job:

1. identifica o tipo de cada arquivo (`DetectorTipoArquivo`) e a competência pelo nome;
2. lê as planilhas (`LeitorXlsx`);
3. valida o lote (`ValidadorImportacaoService`);
4. com pendência IMPEDITIVA, encerra o job como `PENDENTE` sem gravar nada;
5. sem IMPEDITIVA, chama a carga atômica e encerra como `CONCLUIDO`, guardando os totais e os avisos;
6. se a carga lançar exceção (ex.: competência já importada), encerra como `FALHA` com a mensagem.

---

## ImportacaoPersistenceService

Grava o job e executa a carga. `executarCargaAtomica` chama os três importadores numa única transação: se um falhar, nada do envio fica no banco. O status do job é gravado em transações separadas, para que a falha da carga não apague o registro da falha.

---

## ValidadorImportacaoService

Valida o lote antes de qualquer gravação (regras na seção 16).

---

## EncerradorImportacoesInterrompidas

Na subida do backend, marca como `FALHA` os jobs que ficaram `PROCESSANDO` porque o processo anterior parou no meio da carga. Sem isso, a regra de um job por vez bloquearia toda importação nova com `409`.

---

## ImportadorRhService

Processa a base de RH.

Para cada linha, utiliza:

```text
Marca
Loja
Cargo
Funcionario
FuncionarioLoja
FuncionarioCargo
```

O processo simplificado é:

```text
Linha RH
   |
   +--> Marca
   |
   +--> Loja
   |
   +--> Cargo
   |
   +--> Funcionario
   |
   +--> FuncionarioLoja
   |
   +--> FuncionarioCargo
```

Quando uma marca, loja, cargo ou funcionário já existe, o service procura o registro existente e reutiliza a entidade.

A competência do nome do arquivo é comparada com a competência encontrada em `Data_Ref`.

Também existe uma validação para impedir a importação da mesma competência de RH novamente.

---

## ImportadorVendasService

Processa a base de vendas.

Campos utilizados:

```text
Matricula
Date_Ref
Vlr _Venda
```

Para cada linha:

```text
Matricula
    ↓
FuncionarioRepository
    ↓
Funcionario
    ↓
VendaRepository.save()
```

A venda só é gravada se a matrícula estiver no RH da mesma competência (vínculo em `funcionario_loja` no mês da venda). Caso contrário, a venda é descartada; o validador já lista esse caso como AVISO no relatório (seção 16).

Como RH e Vendas vão no mesmo envio, a carga grava o RH antes das vendas, na mesma transação.

A competência também é validada e a API verifica se já existem vendas para aquele mês.

---

## ImportadorComissaoService

Processa a base de comissão.

Campos:

```text
Cod_Marca
Descr_Marca
Cod_Cargo
Descri_Cargo
%_Comiss
```

O processo é:

```text
Marca
  +
Cargo
  +
Competência
  +
Percentual
  ↓
MarcaCargo
```

### Percentual

O XLSX armazena o percentual como fração.

Exemplo:

```text
0.025
```

representa:

```text
2,5%
```

Antes de salvar, o valor é multiplicado por `100`:

```text
0.025 × 100 = 2.50
```

O banco armazena:

```text
2.50
```

### GERENTE QUIOSQUE

Linhas cujo cargo seja:

```text
GERENTE QUIOSQUE
```

são ignoradas durante a importação.

---

# 9. Controller

`ImportacaoController` expõe:

| Rota | Recebe | Devolve |
|---|---|---|
| `POST /api/importacao` | `multipart/form-data`, campo `arquivos` (um ou mais `.xlsx`) | `202` com `{ "id", "status": "PROCESSANDO" }` |
| `GET /api/importacao/{id}` | id do job | status, competência, usuário, totais e pendências |
| `GET /api/importacao` | nada | histórico de jobs, sem as pendências |

A permissão (só Supervisor) fica na matriz do `SecurityConfig` (`/api/importacao/**`). Sem token: `401`; outro perfil: `403`.

---

# 10. Como importar usando o Postman

## 10.1 Pré-requisitos

1. PostgreSQL disponível (ex.: `docker compose up -d` na raiz do backend).
2. Backend executando com o profile `dev`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

ou pela IDE, com o profile `dev` ativo.

---

## 10.2 Fazer login como Supervisor

A importação é só do perfil Supervisor. Faça login com o Sérgio, usuário da demonstração:

- Método: `POST`
- URL: `http://localhost:8080/api/auth/login`
- Body → `raw` → `JSON`:

```json
{ "email": "sergio@camplana.com.br", "senha": "123456" }
```

Copie o `token` da resposta. Nas próximas requisições, na aba **Authorization**, escolha **Bearer Token** e cole o token.

---

## 10.3 Criar a requisição

Método `POST`, URL:

```text
http://localhost:8080/api/importacao
```

---

## 10.4 Configurar o Body

Acesse **Body** e selecione **form-data**. Para cada planilha, adicione uma linha com a chave `arquivos`, mude o tipo de `Text` para `File` e selecione o arquivo (as linhas da seção 11).

Não é necessário configurar manualmente o header `Content-Type`. O Postman monta o `multipart/form-data` com o boundary necessário.

---

# 11. Enviando as planilhas do mês

Envie as planilhas da competência **no mesmo envio**, uma linha `arquivos` para cada:

| Key | Type | Value |
|---|---|---|
| `arquivos` | `File` | `BASE RH_SET25.xlsx` |
| `arquivos` | `File` | `BASE_VENDAS_SET25.xlsx` |
| `arquivos` | `File` | `BASE_COMMISS_FINAL.xlsx` |

- RH e Vendas são obrigatórios: enviar só um deles termina o job como `PENDENTE` ("Planilha sem linhas de dados").
- A Comissão é opcional; quando vem, o validador confere se toda marca × cargo do RH tem %.
- O nome do arquivo identifica o tipo (`RH`, `VENDAS`, `COMISS`) e a competência (`SET25` = 2025-09).
- Na carga, o RH é gravado antes das vendas, na mesma transação.

Resposta imediata (`202`):

```json
{ "id": 3, "status": "PROCESSANDO" }
```

---

# 12. Acompanhando o job

Consulte `GET /api/importacao/{id}` (com o mesmo token) até o status sair de `PROCESSANDO`. Um mês do dataset leva cerca de 50 segundos.

| Status | Significado |
|---|---|
| `PROCESSANDO` | Validando ou gravando |
| `CONCLUIDO` | Gravado; os avisos, se houver, estão em `pendencias` |
| `PENDENTE` | Há pendência IMPEDITIVA: nada foi gravado. Corrija a planilha e envie de novo |
| `FALHA` | Erro na carga (ex.: competência já importada) ou backend reiniciado no meio do job; o motivo está em `erro` e em `pendencias` |

---

# 13. Histórico

`GET /api/importacao` lista os envios, do mais recente para o mais antigo, com status, competência, usuário, linhas lidas e linhas gravadas.

---

# 14. Uma importação por vez

Enquanto houver um job `PROCESSANDO`, um novo envio recebe `409` ("Já existe uma importação em andamento."). A trava é um índice único no banco (`uk_importacao_em_andamento`), então vale mesmo com dois envios ao mesmo tempo.

Se o backend reiniciar no meio de um job, o `EncerradorImportacoesInterrompidas` marca esse job como `FALHA` na subida e um novo envio volta a ser aceito. Como a carga é uma transação só, nada do envio interrompido fica no banco.

---

# 15. Reenvio

- Competência já importada: o job termina `FALHA` com "Competencia AAAA-MM ja foi importada"; os dados anteriores não mudam.
- Job `PENDENTE`: nada foi gravado, então basta corrigir a planilha e enviar de novo.
- Job `FALHA` por reinício: envie as mesmas planilhas de novo.

---

# 16. Regras de validação

Feitas pelo `ValidadorImportacaoService` antes de gravar. Cada pendência traz origem (o arquivo ou `Cruzamento`), linha, campo, motivo e severidade.

| Regra | Severidade |
|---|---|
| Arquivo que não é `.xlsx`, sem nome ou enviado duas vezes | recusado na hora (`400`), sem criar job |
| Tipo do arquivo não reconhecido pelo nome, ou dois arquivos do mesmo tipo | IMPEDITIVO |
| Planilha de RH ou de Vendas ausente ou sem linhas | IMPEDITIVO |
| Coluna obrigatória ausente | IMPEDITIVO |
| Data fora do formato `AAAA-MM-DD`, ou valor de venda ou % de comissão não numérico | IMPEDITIVO |
| Competência do nome diferente entre RH e Vendas | IMPEDITIVO |
| Funcionário do RH sem matrícula, loja ou cargo | IMPEDITIVO |
| Marca × cargo do RH sem % na planilha de comissão (quando enviada) | IMPEDITIVO |
| Venda de matrícula fora do RH da competência | AVISO: o mês é gravado sem essa venda; o aviso traz linha, data e valor |
| Funcionário do RH sem venda no mês | AVISO |

Colunas obrigatórias:

| Planilha | Colunas |
|---|---|
| RH | `Data_Ref`, `Cod_Marca`, `Descri_Marca`, `Cod_Loja`, `Descr_Loja`, `Matricula`, `Data_Admiss`, `Cod_Cargo`, `Descri_Cargo` |
| Vendas | `Date_Ref`, `Matricula`, `Vlr _Venda` |
| Comissão | `Cod_Marca`, `Descr_Marca`, `Cod_Cargo`, `Descri_Cargo`, `%_Comiss` |

Com qualquer IMPEDITIVO, nada é gravado (sem importação parcial).

Durante a carga, os importadores ainda conferem se o conteúdo bate com a competência do nome e se a competência já foi importada; nesses casos o job termina `FALHA`.

> Por que a venda fora do RH é AVISO: a base da Dom Rock tem 60 vendas assim em ago a dez/2025 (venda antes da admissão, funcionário fora do RH do mês, matrícula que não está em nenhum RH). Como IMPEDITIVO, só julho entraria. Decisão do PO em 19/10/2026.

---

# 17. Resposta da API

`POST /api/importacao` → `202`:

```json
{ "id": 1, "status": "PROCESSANDO" }
```

`GET /api/importacao/{id}` com o job terminado (jul/2025 do dataset real; pendências resumidas e datas ilustrativas):

```json
{
  "id": 1,
  "status": "CONCLUIDO",
  "competencia": "2025-07",
  "usuario": "Sérgio",
  "total_linhas": 5370,
  "linhas_importadas": 5364,
  "totais": { "RH": 322, "VENDAS": 5018, "COMISSAO": 24 },
  "pendencias": [
    {
      "origem": "BASE RH_JUL25.xlsx",
      "linha": 0,
      "campo": "Matricula",
      "motivo": "Funcionário com matrícula MATRIC-52 não registrou vendas no mês",
      "severidade": "AVISO"
    }
  ],
  "criada_em": "2026-10-19T11:20:01Z",
  "concluida_em": "2026-10-19T11:20:47Z"
}
```

Aviso de venda fora do RH (ago/2025):

```json
{
  "origem": "Cruzamento",
  "linha": 32,
  "campo": "Matricula",
  "motivo": "matrícula MATRIC-151 não encontrada no RH da competência; a venda de 2025-08-01 (R$ 79.5) não será gravada",
  "severidade": "AVISO"
}
```

`total_linhas` conta as linhas lidas nas três planilhas; `linhas_importadas`, as gravadas. A diferença são linhas que a carga ignora de propósito (ex.: vendas fora do RH e o cargo `GERENTE QUIOSQUE` da comissão).

---

# 18. Estrutura do banco

As principais relações são:

```text
                    +---------+
                    |  Marca  |
                    +----+----+
                         |
                         | 1:N
                         v
                    +---------+
                    |  Loja   |
                    +---------+

+--------------+          +-------------+
| Funcionario  |          |    Cargo    |
+------+-------+          +------+------+
       |                         |
       |                         |
       +-----------+-------------+
                   |
          +--------+--------+
          |                 |
          v                 v
+-------------------+ +-------------------+
| FuncionarioLoja   | | FuncionarioCargo  |
+-------------------+ +-------------------+

+---------+       +---------+
|  Marca  |       |  Cargo  |
+----+----+       +----+----+
     \                 /
      \               /
       v             v
        +-----------+
        | MarcaCargo|
        +-----------+

+--------------+
| Funcionario  |
+------+-------+
       |
       | 1:N
       v
+--------------+
|    Venda     |
+--------------+
```

A competência (`date_ref`) faz parte das entidades de relacionamento que precisam representar o estado da associação em determinado período.

Além das tabelas da base, a tabela `importacao` (migration `V5`) guarda cada job: status, competência, usuário, arquivos, totais, pendências (`jsonb`), erro e datas em UTC. Um índice único parcial garante no máximo um job `PROCESSANDO`.

---

# 19. Resumo das tabelas

| Tabela | Representa |
|---|---|
| `marca` | Marcas |
| `loja` | Lojas pertencentes às marcas |
| `cargo` | Cargos |
| `funcionario` | Funcionários |
| `funcionario_loja` | Funcionário em uma loja por competência |
| `funcionario_cargo` | Funcionário em um cargo por competência |
| `marca_cargo` | Comissão de um cargo em uma marca por competência |
| `venda` | Vendas realizadas por funcionários |
| `importacao` | Jobs de importação, com o relatório de pendências |

---

# 20. Resumo das responsabilidades

| Componente | Responsabilidade |
|---|---|
| `ImportacaoController` | Receber os arquivos e expor o status e o histórico dos jobs |
| `ImportacaoManagerService` | Checar os arquivos, impedir dois jobs ao mesmo tempo, criar o job e montar as respostas |
| `ImportacaoJobService` | Processar o job em segundo plano: ler, validar e decidir entre gravar ou parar |
| `ValidadorImportacaoService` | Gerar as pendências IMPEDITIVO e AVISO antes de gravar |
| `ImportacaoPersistenceService` | Gravar o job e executar a carga numa transação única |
| `EncerradorImportacoesInterrompidas` | Encerrar como `FALHA` os jobs interrompidos por reinício |
| `DetectorTipoArquivo` | Identificar o tipo da base |
| `CompetenciaExtractor` | Extrair competência do nome |
| `LeitorXlsx` | Ler e normalizar o XLSX |
| `NormalizadorData` | Normalizar datas |
| `ImportadorRhService` | Importar RH |
| `ImportadorVendasService` | Importar vendas |
| `ImportadorComissaoService` | Importar comissões |
| `Entity` | Mapear tabelas do banco para Java |
| `Repository` | Acessar e consultar o banco |
| PostgreSQL | Persistir os dados |

---

# 21. Fluxo completo

O fluxo completo de uma importação é o da seção 1. Para uma nova competência, o procedimento recomendado é:

```text
1. Subir PostgreSQL e o backend (profile dev)
2. Login como Supervisor (POST /api/auth/login)
3. POST /api/importacao com RH + Vendas (+ Comissão) do mês
4. GET /api/importacao/{id} até sair de PROCESSANDO
5. CONCLUIDO: conferir os avisos
   PENDENTE: corrigir a planilha e enviar de novo
   FALHA: ler o motivo em "erro"
```

Para carregar jul a dez/2025 de uma vez no ambiente completo, use o `scripts/importar-dataset.sh` do repositório `domrock-camplana`.
