# A6-74 — Comissão vigente e conflitos

O job de simulação consulta `ContextoCampanhasService` antes de chamar `POST /simulacao`
na Lana. A requisição pública continua recebendo `chat_id` e `regra`; o backend monta
`campanhas` a partir das fontes confiáveis. Não existe cálculo de percentual ou de
comissão no Java.

## Seleção das fontes

- Última versão de cada campanha em `APROVADA` ou `AGUARDANDO_APROVACAO`.
- Datas inclusivas, com interseção de período, marcas e cargos da ficha.
- A própria conversa é excluída para não contar uma versão anterior como concorrente.
- Autor vem da campanha; aprovador, data e justificativa vêm do log da mesma versão.
  Uma campanha aprovada sem esse registro interrompe a simulação, em vez de inventar a decisão.
- `marcas_alvo: ALL` é expandido para os códigos do catálogo existente (10 a 60).
- Contexto informado pelo cliente é substituído pelo contexto montado no servidor.

A branch integrada tem as intercorrências de RH da V6, mas ainda não tem um catálogo
persistido das campanhas percentuais da especificação. Por isso,
`src/main/resources/campanhas/especificacao-2025.json` registra os itens 2h, 3g, 4g,
5f, 5g, 6f e 6g do `Especificacao.pdf`. São fontes identificadas por
`origem: ESPECIFICACAO`, sem autor, aprovador ou data de aprovação fictícios.
O item 3g referencia a marca 20; quem resolve sua taxa por cargo é a Lana.
Prêmios fixos e intercorrências de RH não são campanhas percentuais deste contrato.
Quando o importador de campanhas existir, deverá substituir esse catálogo mantendo
os IDs de origem, sem carregar as mesmas fontes duas vezes.

## Contrato backend → Lana

O campo opcional `campanhas` acompanha `regra` e `chat_id`:

```json
{
  "campanhas": [{
    "id": "campanha-12-v1",
    "nome": "Campanha A",
    "tipo": "ACRESCIMO",
    "status": "APROVADA",
    "data_inicio": "2025-11-20",
    "data_fim": "2025-11-30",
    "marcas_alvo": ["10"],
    "cargos_alvo": ["150"],
    "percentual": 1.0,
    "autor": "Gabriela",
    "aprovador": "Sérgio",
    "aprovada_em": "2025-10-20T12:00:00Z",
    "justificativa": "Meta aprovada",
    "origem": "CAMPANHA"
  }]
}
```

Percentuais são literais: `0.5` significa meio ponto percentual, não 50%.
`SUBSTITUICAO` usa `percentual` absoluto **ou** `marca_referencia`, nunca ambos.
A ficha do chat continua criando somente acréscimos nesta sprint.

## Resultado e persistência

A Lana acrescenta `comissoes_periodo`, `conflitos` e `trilha.campanhas` ao resultado.
Cada trecho tem `marca`, `cargo`, `inicio`, `fim`, `base`, `vigente`, `comCampanha`,
`historico` e `avisos`. O histórico distingue `ACRESCIMO` de `SUBSTITUICAO`, indica
`aplicada`, percentual resolvido e dados da decisão. `acrescimo` é nulo nas substituições.
Os conflitos trazem o intervalo de interseção, marca, cargos, tipo e status.
Pendentes aparecem apenas nos conflitos.

`SimulacaoPersistenceService` já grava o resultado JSON completo e a trilha.
`CampanhaService.submeter` já copia o resultado integral para a versão imutável.
Assim, `GET /api/campanhas/{id}/versoes/{numero}` entrega ao supervisor exatamente
os percentuais, fontes e conflitos observados na submissão, sem recalcular a versão.
Não é necessária migration, variável de ambiente nova nem alteração no frontend/IaC.

A re-simulação obrigatória antes da decisão pertence à A6-68; este PR fornece o
contexto para a simulação, mas não implementa a aprovação. As rotas de aprovação
não estão presentes na dependência A6-66 integrada nesta branch.

## Validação

- `bash mvnw -Dtest=ContextoCampanhasServiceTest,SimulacaoJobServiceTest test`
- Com Docker disponível: `bash mvnw -Dtest=CampanhaApiTest test`
- Suíte completa: `bash mvnw test`

Os testes de contexto verificam seleção, proveniência, pendentes, catálogo,
substituição de setembro e rejeição de contexto forjado. Os testes de API verificam
persistência e leitura pelo supervisor com PostgreSQL/Testcontainers.
