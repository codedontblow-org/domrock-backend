package br.com.camplana.campanha;

import br.com.camplana.dto.campanha.IntercorrenciaResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Implementação SQL nativa de {@link FonteIntercorrencias} sobre a tabela {@code evento_rh}.
 */
@Component
public class FonteIntercorrenciasJdbc implements FonteIntercorrencias {

    // Regras da consulta e justificativas:
    // 1. Escopo de funcionário:
    //    Verifica se existe ao menos um mesmo date_ref entre o 1º dia do mês de ini (date_trunc('month', ini))
    //    e a data fim em que o colaborador possui cargo com cod_cargo no escopo E está alocado em loja
    //    cuja marca tem cod_marca no escopo da campanha.
    // 2. Cargo do colaborador:
    //    O cargo selecionado é o cod_cargo no primeiro mês (menor date_ref) do período da campanha
    //    em que o colaborador esteve no escopo, obtido com ROW_NUMBER() ordenado por date_ref ASC.
    // 3. Cruzamento com o período da campanha [ini, fim]:
    //    - demissao: a demissão encerra o vínculo, então data_inicio deve ocorrer entre ini e fim;
    //    - licenca_maternidade sem data_fim (ou qualquer licença sem término): data_inicio <= fim;
    //    - demais eventos: sobrepõem o período com data_inicio <= fim AND data_fim >= ini;
    //    - data_inicio nula (ex.: cobertura de loja sem datas): descartada porque não é possível
    //      determinar o cruzamento temporal com a campanha (já alertada no relatório de pendências).
    // 4. Desduplicação:
    //    A especificação repete o mesmo evento em competências subsequentes alterando apenas a origem.
    //    Preservamos uma única linha por (matricula, tipo, data_inicio, data_fim), selecionando
    //    aquela com menor id (ROW_NUMBER() ordenado por id ASC).
    // 5. Ordenação:
    //    Ordenado primariamente por data_inicio e em seguida por matricula.
    private static final String SQL = """
            WITH escopo_funcionario AS (
                SELECT
                    f.matricula,
                    c.cod_cargo,
                    ROW_NUMBER() OVER (
                        PARTITION BY f.matricula
                        ORDER BY fc.date_ref ASC, c.cod_cargo ASC
                    ) AS rn
                FROM funcionario f
                JOIN funcionario_cargo fc ON fc.funcionario_id = f.id
                JOIN cargo c ON c.id = fc.cargo_id
                JOIN funcionario_loja fl ON fl.funcionario_id = f.id AND fl.date_ref = fc.date_ref
                JOIN loja l ON l.id = fl.loja_id
                JOIN marca m ON m.id = l.marca_id
                WHERE fc.date_ref >= date_trunc('month', CAST(:ini AS date))::date
                  AND fc.date_ref <= CAST(:fim AS date)
                  AND c.cod_cargo IN (:cargos)
                  AND m.cod_marca IN (:marcas)
            ),
            eventos_filtrados AS (
                SELECT
                    e.id,
                    e.tipo,
                    e.matricula,
                    e.data_inicio,
                    e.data_fim,
                    e.origem,
                    ROW_NUMBER() OVER (
                        PARTITION BY e.matricula, e.tipo, e.data_inicio, e.data_fim
                        ORDER BY e.id ASC
                    ) AS rn_evento
                FROM evento_rh e
                WHERE e.data_inicio IS NOT NULL
                  AND (
                      (e.tipo = 'demissao' AND e.data_inicio BETWEEN CAST(:ini AS date) AND CAST(:fim AS date))
                      OR (e.tipo <> 'demissao' AND e.data_fim IS NULL AND e.data_inicio <= CAST(:fim AS date))
                      OR (e.tipo <> 'demissao' AND e.data_fim IS NOT NULL AND e.data_inicio <= CAST(:fim AS date) AND e.data_fim >= CAST(:ini AS date))
                  )
            )
            SELECT
                ef.tipo,
                ef.matricula,
                efunc.cod_cargo AS cargo,
                ef.data_inicio,
                CASE WHEN ef.tipo = 'demissao' THEN NULL ELSE ef.data_fim END AS data_fim,
                ef.origem
            FROM eventos_filtrados ef
            JOIN escopo_funcionario efunc ON efunc.matricula = ef.matricula AND efunc.rn = 1
            WHERE ef.rn_evento = 1
            ORDER BY ef.data_inicio ASC, ef.matricula ASC, ef.id ASC
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public FonteIntercorrenciasJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<IntercorrenciaResponse> buscarPorEscopo(EscopoCampanha escopo) {
        if (escopo == null || escopo.periodo() == null
                || escopo.periodo().dataInicio() == null || escopo.periodo().dataFim() == null
                || escopo.marcas() == null || escopo.marcas().isEmpty()
                || escopo.cargos() == null || escopo.cargos().isEmpty()) {
            return List.of();
        }

        var params = new MapSqlParameterSource()
                .addValue("ini", escopo.periodo().dataInicio())
                .addValue("fim", escopo.periodo().dataFim())
                .addValue("cargos", escopo.cargos())
                .addValue("marcas", escopo.marcas());

        return jdbc.query(SQL, params, (rs, rowNum) -> new IntercorrenciaResponse(
                rs.getString("tipo"),
                rs.getString("matricula"),
                rs.getString("cargo"),
                rs.getObject("data_inicio", LocalDate.class),
                rs.getObject("data_fim", LocalDate.class),
                rs.getString("origem")
        ));
    }
}
