# Guia de Importação e Estrutura das Entidades

## 1. Visão geral

Esta funcionalidade implementa a estrutura de persistência do banco e o fluxo de importação das bases de dados em arquivos `.xlsx`.

A importação é feita através da rota:

```http
POST /api/importacao
```

O fluxo geral é:

```text
Arquivo XLSX
    |
    v
ImportacaoController
    |
    v
ImportacaoService
    |
    +----------------------+
    |                      |
    v                      v
DetectorTipoArquivo    LeitorXlsx
                           |
                           v
                    Dados normalizados
                           |
              +------------+------------+
              |            |            |
              v            v            v
          Importador   Importador   Importador
             RH         Vendas       Comissao
              |            |            |
              +------------+------------+
                           |
                           v
                     Repositories
                           |
                           v
                       PostgreSQL
```

---

# 2. Estrutura de pacotes

A funcionalidade está organizada principalmente nos seguintes pacotes:

```text
br.com.camplana
|
+-- Controller
|   +-- ImportacaoController
|
+-- Entity
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
+-- Repository
|   +-- CargoRepository
|   +-- FuncionarioRepository
|   +-- FuncionarioCargoRepository
|   +-- FuncionarioLojaRepository
|   +-- LojaRepository
|   +-- MarcaRepository
|   +-- MarcaCargoRepository
|   +-- VendaRepository
|
+-- Import
|   +-- CompetenciaExtractor
|   +-- DetectorTipoArquivo
|   +-- LeitorXlsx
|   +-- NormalizadorData
|   +-- TipoArquivo
|
+-- Service
    +-- ImportacaoService
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

# 7. Classes do pacote Import

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
MultipartFile
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

## ImportacaoService

É o orquestrador principal.

Recebe uma lista de arquivos:

```java
List<MultipartFile> arquivos
```

Para cada arquivo:

1. verifica se é `.xlsx`;
2. identifica o tipo;
3. lê o arquivo usando `LeitorXlsx`;
4. chama o importador correspondente;
5. registra o resultado.

O resultado contém:

```java
String nomeArquivo;
String tipoDetectado;
boolean sucesso;
int linhasProcessadas;
String erro;
```

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

A matrícula precisa existir previamente no RH.

Por isso, a ordem recomendada é:

```text
RH
 ↓
VENDAS
```

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

```http
POST /api/importacao
```

O endpoint recebe:

```java
@RequestParam("arquivos")
List<MultipartFile> arquivos
```

e retorna:

```java
List<ResultadoImportacaoArquivo>
```

O conteúdo é enviado como:

```text
multipart/form-data
```

---

# 10. Como importar usando o Postman

## 10.1 Pré-requisitos

Antes de utilizar o Postman:

1. PostgreSQL deve estar disponível.
2. O banco deve estar configurado.
3. O backend deve estar executando.
4. A aplicação deve estar disponível na porta `8080`.

Para iniciar:

```bash
./mvnw spring-boot:run
```

ou através da configuração de execução da IDE.

---

## 10.2 Criar a requisição

No Postman:

```text
New Request
```

Método:

```text
POST
```

URL:

```text
http://localhost:8080/api/importacao
```

---

## 10.3 Configurar o Body

Acesse:

```text
Body
```

Selecione:

```text
form-data
```

Adicione uma linha:

| Key | Type | Value |
|---|---|---|
| `arquivos` | `File` | arquivo `.xlsx` |

É importante alterar o tipo de `Text` para:

```text
File
```

Depois clique na área de valor e selecione o arquivo.

Não é necessário configurar manualmente o header `Content-Type`. O Postman monta o `multipart/form-data` com o boundary necessário.

---

# 11. Importando RH

Primeiro envie o arquivo de RH.

Exemplo:

```text
BASE_RH_SET25.xlsx
```

Requisição:

```http
POST http://localhost:8080/api/importacao
```

Body:

```text
form-data

arquivos | File | BASE_RH_SET25.xlsx
```

Clique:

```text
Send
```

Resposta esperada:

```json
[
  {
    "nomeArquivo": "BASE_RH_SET25.xlsx",
    "tipoDetectado": "RH",
    "sucesso": true,
    "linhasProcessadas": 467,
    "erro": null
  }
]
```

O número de linhas depende do arquivo.

---

# 12. Importando Vendas

Depois que o RH for importado com sucesso, envie:

```text
BASE_VENDAS_SET25.xlsx
```

Body:

```text
form-data

arquivos | File | BASE_VENDAS_SET25.xlsx
```

O sistema procura cada matrícula no cadastro de funcionários.

Exemplo:

```text
Venda
MATRIC-56
   ↓
FuncionarioRepository.findByMatricula()
   ↓
Funcionario encontrado
   ↓
Venda salva
```

Se a matrícula não existir:

```text
Venda referencia matricula inexistente no RH
```

Nesse caso, o RH precisa ser importado antes.

---

# 13. Importando Comissão

Depois, envie:

```text
BASE_COMMISS_SET25.xlsx
```

Body:

```text
form-data

arquivos | File | BASE_COMMISS_SET25.xlsx
```

A base cria ou reutiliza:

```text
Marca
Cargo
MarcaCargo
```

---

# 14. Ordem recomendada

Para uma competência nova:

```text
                    COMPETÊNCIA
                         |
             +-----------+-----------+
             |                       |
             v                       v
            RH                  COMISSÃO
             |
             v
          VENDAS
```

A ordem mais importante é:

```text
RH → VENDAS
```

porque as vendas dependem dos funcionários cadastrados pelo RH.

---

# 15. Enviando vários arquivos

O endpoint aceita uma lista de arquivos.

No Postman, podem existir várias linhas com a mesma chave:

```text
arquivos | File | BASE_RH_SET25.xlsx
arquivos | File | BASE_VENDAS_SET25.xlsx
arquivos | File | BASE_COMMISS_SET25.xlsx
```

Entretanto, para testes e identificação de erros, é recomendado enviar um arquivo por vez.

Isso facilita identificar exatamente qual base apresentou problema.

---

# 16. Regras de validação

## Extensão

Somente arquivos:

```text
.xlsx
```

são aceitos.

---

## Tipo

O nome do arquivo precisa permitir identificar:

```text
RH
VENDAS
COMISS
```

---

## Competência

A competência presente no nome precisa ser compatível com a competência encontrada nos dados.

Exemplo:

```text
BASE_RH_SET25.xlsx
```

deve possuir dados de:

```text
SET/2025
```

Caso contrário, a importação é rejeitada.

---

## Dados obrigatórios

Quando uma coluna obrigatória estiver ausente ou vazia, é lançada uma exceção indicando a coluna.

Exemplo:

```text
Coluna obrigatoria ausente/vazia: Matricula
```

---

## Duplicidade de RH

A API verifica se já existe uma importação para a competência.

---

## Duplicidade de Vendas

A API verifica se já existem registros de venda no intervalo correspondente à competência.

---

## Matrícula de venda

A matrícula da venda precisa existir na tabela `funcionario`.

---

# 17. Resposta da API

O endpoint retorna uma lista de resultados.

Exemplo:

```json
[
  {
    "nomeArquivo": "BASE_RH_SET25.xlsx",
    "tipoDetectado": "RH",
    "sucesso": true,
    "linhasProcessadas": 467,
    "erro": null
  }
]
```

Em caso de erro:

```json
[
  {
    "nomeArquivo": "BASE_VENDAS_SET25.xlsx",
    "tipoDetectado": "VENDAS",
    "sucesso": false,
    "linhasProcessadas": 0,
    "erro": "Venda referencia matricula inexistente no RH: MATRIC-56 - importe o RH da competencia antes das Vendas."
  }
]
```

Importante:

```text
HTTP 200
```

não significa necessariamente que o arquivo foi importado com sucesso.

A aplicação retorna o resultado individual no campo:

```json
"sucesso"
```

Portanto, sempre verificar:

```json
"sucesso": true
```

e:

```json
"erro": null
```

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

---

# 20. Resumo das responsabilidades

| Componente | Responsabilidade |
|---|---|
| `ImportacaoController` | Receber os arquivos HTTP |
| `ImportacaoService` | Orquestrar a importação |
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

O fluxo completo de uma importação é:

```text
Postman
   |
   | multipart/form-data
   v
ImportacaoController
   |
   v
ImportacaoService
   |
   +--> valida .xlsx
   |
   +--> DetectorTipoArquivo
   |
   +--> LeitorXlsx
   |       |
   |       +--> lê cabeçalho
   |       +--> lê células
   |       +--> normaliza valores
   |       +--> ignora linhas vazias
   |
   +--> Importador correspondente
           |
           +--> valida competência
           +--> valida duplicidade
           +--> valida dependências
           +--> utiliza Repository
                    |
                    v
                PostgreSQL
```

Para uma nova competência, o procedimento operacional recomendado é:

```text
1. Subir PostgreSQL
2. Subir Spring Boot
3. Abrir Postman
4. POST /api/importacao
5. Enviar RH
6. Verificar sucesso
7. Enviar Vendas
8. Verificar sucesso
9. Enviar Comissão
10. Verificar sucesso
```
