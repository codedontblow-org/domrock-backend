package br.com.camplana.campanha;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Registro de simulações para os testes de campanha ({@link CampanhaApiTest}): permite fixar o ID
 * exato de cada simulação (ex.: 345, 350) satisfazendo a FK {@code fk_versao_simulacao} da V8.
 */
public class FakeRegistroSimulacoes implements RegistroSimulacoes {

    // Ficha e resultado da Black Friday dos critérios do card, só com os campos que a campanha lê.
    public static final String REGRA_BLACK_FRIDAY = """
            {"rule_id": "draft-3fa9c1d2", "tipo_regra": "BONUS_TEMPORARIO", "parametros": [
              {"key": "periodo", "value": {"data_inicio": "2025-11-24", "data_fim": "2025-11-30"}},
              {"key": "pct_acrescimo", "value": 1.0}
            ]}""";
    public static final String RESULTADO_BLACK_FRIDAY = """
            {"totais": {"baseline": 480263.24, "simulado": 503999.34, "diferenca": 23736.10},
             "conflitos": [], "codigo": "resultado = calcular(vendas)"}""";

    private static final Instant PRIMEIRA_CONCLUSAO = Instant.parse("2026-10-21T12:59:02Z");

    private final JdbcTemplate jdbc;
    private final Map<Long, SimulacaoGravada> simulacoes = new LinkedHashMap<>();
    private final Map<String, Long> campanhaDaConversa = new HashMap<>();

    public FakeRegistroSimulacoes() {
        this(null);
    }

    public FakeRegistroSimulacoes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void limpar() {
        simulacoes.clear();
        campanhaDaConversa.clear();
        if (jdbc != null) {
            jdbc.execute("DELETE FROM simulacao");
        }
    }

    /** Simulação concluída da Black Friday; ids maiores terminam depois. */
    public void concluida(long id, int usuarioId, String chatId) {
        Instant concluidaEm = PRIMEIRA_CONCLUSAO.plusSeconds(id);
        simulacoes.put(id, new SimulacaoGravada(id, usuarioId, chatId, "CONCLUIDA",
                REGRA_BLACK_FRIDAY, RESULTADO_BLACK_FRIDAY, concluidaEm));
        inserirNoBanco(id, usuarioId, chatId, "CONCLUIDA", RESULTADO_BLACK_FRIDAY, concluidaEm);
    }

    public void rodando(long id, int usuarioId, String chatId) {
        simulacoes.put(id, new SimulacaoGravada(id, usuarioId, chatId, "RODANDO",
                REGRA_BLACK_FRIDAY, null, null));
        inserirNoBanco(id, usuarioId, chatId, "RODANDO", null, null);
    }

    private void inserirNoBanco(long id, int usuarioId, String chatId, String status, String resultado, Instant concluidaEm) {
        if (jdbc == null) {
            return;
        }
        jdbc.update("""
                INSERT INTO simulacao (id, usuario_id, chat_id, status, ficha, resultado, criada_em, concluida_em)
                VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                """,
                id, usuarioId, chatId, status, REGRA_BLACK_FRIDAY, resultado,
                Timestamp.from(PRIMEIRA_CONCLUSAO), concluidaEm == null ? null : Timestamp.from(concluidaEm));
    }

    @Override
    public Optional<SimulacaoGravada> buscar(long simulacaoId) {
        return Optional.ofNullable(simulacoes.get(simulacaoId));
    }

    @Override
    public Optional<SimulacaoGravada> ultimaConcluida(long campanhaId) {
        return daCampanha(campanhaId)
                .filter(SimulacaoGravada::concluida)
                .max(Comparator.comparing(SimulacaoGravada::concluidaEm));
    }

    @Override
    public Optional<Long> emAndamento(long campanhaId) {
        return daCampanha(campanhaId)
                .filter(simulacao -> !simulacao.concluida() && !"ERRO".equals(simulacao.status()))
                .map(SimulacaoGravada::id)
                .findFirst();
    }

    @Override
    public void vincularConversa(String chatId, long campanhaId) {
        campanhaDaConversa.put(chatId, campanhaId);
    }

    private Stream<SimulacaoGravada> daCampanha(long campanhaId) {
        return simulacoes.values().stream()
                .filter(simulacao -> Objects.equals(campanhaDaConversa.get(simulacao.chatId()), campanhaId));
    }
}
