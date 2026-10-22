package br.com.camplana.campanha;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Registro de simulações em memória, no lugar da tabela {@code simulacao} do A6-66, que ainda não
 * existe. Uma simulação pertence à campanha da sua conversa, como o contrato 4.1 manda.
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

    private final Map<Long, SimulacaoGravada> simulacoes = new LinkedHashMap<>();
    private final Map<String, Long> campanhaDaConversa = new HashMap<>();

    public void limpar() {
        simulacoes.clear();
        campanhaDaConversa.clear();
    }

    /** Simulação concluída da Black Friday; ids maiores terminam depois. */
    public void concluida(long id, int usuarioId, String chatId) {
        simulacoes.put(id, new SimulacaoGravada(id, usuarioId, chatId, "CONCLUIDA",
                REGRA_BLACK_FRIDAY, RESULTADO_BLACK_FRIDAY, PRIMEIRA_CONCLUSAO.plusSeconds(id)));
    }

    public void rodando(long id, int usuarioId, String chatId) {
        simulacoes.put(id, new SimulacaoGravada(id, usuarioId, chatId, "RODANDO",
                REGRA_BLACK_FRIDAY, null, null));
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
