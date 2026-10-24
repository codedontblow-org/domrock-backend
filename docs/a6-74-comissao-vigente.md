# A6-74 — Campanhas sobrepostas no pedido à Lana

O formato é o do contrato da sprint (`domrock-camplana/docs/contratos-sprint-2.md`):
o item de `campanhas` no `POST /simulacao` da Lana está na seção 4.3, e o que a Lana
devolve (`comissao_periodo`, `conflitos`, `trilha.campanhas`) nas seções 2.3 e 2.2.
A regra de sobreposição é a do estudo A6-73. Nenhum cálculo de % ou de comissão fica no Java.

## O que o backend faz

O job de simulação (`SimulacaoJobService`) passa o pedido por `ContextoCampanhasService`
antes de chamar a Lana:

- Lê período, marcas e cargos da ficha (`CamposDaSimulacao.escopo`, com `ALL` trocado
  pelas seis marcas). Se a ficha estiver incompleta, segue com `campanhas: []` e a Lana
  devolve o 422 com os erros, como antes.
- Busca a última versão das campanhas `APROVADA` ou `AGUARDANDO_APROVACAO` que cruzam o
  período, sem a da própria conversa (`CampanhaVersaoRepository.findContextoSobreposto`).
  Como `APROVADA` não sai mais desse status, a última versão é a aprovada.
- Fica só com as que têm ao menos uma marca × cargo em comum com a ficha.
- Autor vem da campanha; aprovador, data e justificativa vêm do log da mesma versão.
  Campanha aprovada sem esse registro interrompe a simulação, em vez de inventar a decisão.
- O que o navegador mandar em `campanhas` é descartado.

As campanhas da especificação (2h, 3g, 4g, 5f, 5g, 6f, 6g) **não** saem do backend:
a Lana carrega sozinha (contrato 4.3). No A6-77 elas passam a vir de uma tabela,
lida pela Lana, sem mudança aqui.

## Resultado e persistência

`SimulacaoPersistenceService` grava o resultado inteiro da Lana e a trilha.
`CampanhaService.submeter` copia o resultado para a versão imutável, então
`GET /api/campanhas/{id}/versoes/{numero}` mostra ao supervisor exatamente a % vigente,
o histórico e os conflitos vistos na submissão. A re-simulação na aprovação é do A6-68.

## Testes

- `ContextoCampanhasServiceTest`: o item do 4.3 com a decisão, a pendente sem decisão,
  marca × cargo em comum, `ALL`, a consulta sem a própria conversa, nada da especificação,
  ficha incompleta sem contexto e aprovada sem decisão.
- `CamposDaSimulacaoTest`: o escopo da ficha.
- `CampanhaApiTest` (PostgreSQL com Testcontainers): a consulta real e a versão congelada
  lida pelo supervisor.

`./mvnw test` roda tudo.
