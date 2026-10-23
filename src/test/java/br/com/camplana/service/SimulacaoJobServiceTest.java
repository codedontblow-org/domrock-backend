package br.com.camplana.service;

import br.com.camplana.client.LanaSimulacaoClient;
import br.com.camplana.entity.EtapaSimulacao;
import br.com.camplana.entity.Simulacao;
import br.com.camplana.entity.StatusSimulacao;
import br.com.camplana.exception.LanaIndisponivelException;
import br.com.camplana.exception.LanaRespostaException;
import br.com.camplana.repository.SimulacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SimulacaoJobServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private SimulacaoPersistenceService persistenceService;

    @Mock
    private LanaSimulacaoClient lanaSimulacaoClient;

    private SimulacaoJobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new SimulacaoJobService(persistenceService, lanaSimulacaoClient, JSON);
    }

    @Test
    void processarSimulacaoAvancaPelasEtapasEConcluiComMetadadosDaLana() {
        JsonNode respostaLana = JSON.readTree("""
                {
                  "totais": {"baseline": 480263.24, "simulado": 503999.34, "diferenca": 23736.10},
                  "conflitos": [],
                  "trilha": [{"etapa": "gerando código", "ok": true}],
                  "codigo_gerado": "resultado = calcular(vendas)",
                  "modelo": "gemini-2.5-pro",
                  "tentativas": 1,
                  "duracao_ms": 1420
                }
                """);
        given(lanaSimulacaoClient.simular(any())).willReturn(respostaLana);

        jobService.processarSimulacaoAsync(42L, Map.of("chat_id", "c1", "regra", Map.of()));

        InOrder ordem = inOrder(persistenceService, lanaSimulacaoClient);
        ordem.verify(persistenceService).atualizarEtapa(42L, EtapaSimulacao.CARREGANDO_BASES);
        ordem.verify(persistenceService).atualizarEtapa(42L, EtapaSimulacao.GERANDO_CODIGO);
        ordem.verify(persistenceService).atualizarEtapa(42L, EtapaSimulacao.EXECUTANDO);
        ordem.verify(lanaSimulacaoClient).simular(any());
        ordem.verify(persistenceService).atualizarEtapa(42L, EtapaSimulacao.CONFERINDO);
        ordem.verify(persistenceService).concluirSimulacao(
                eq(42L),
                eq(JSON.writeValueAsString(respostaLana)),
                eq("[{\"etapa\":\"gerando código\",\"ok\":true}]"),
                eq("resultado = calcular(vendas)"),
                eq("gemini-2.5-pro"),
                eq(1),
                eq(1420L)
        );
    }

    @Test
    void processarSimulacaoGravaErroLegivelQuandoLanaRetorna422() {
        given(lanaSimulacaoClient.simular(any())).willThrow(
                new LanaRespostaException(422, "{\"etapa\":\"validacao\",\"mensagem\":\"Regra incompleta: falta período\"}"));

        jobService.processarSimulacaoAsync(43L, Map.of("chat_id", "c1", "regra", Map.of()));

        verify(persistenceService).falharSimulacao(43L, "Regra incompleta: falta período");
    }

    @Test
    void processarSimulacaoGravaErroQuandoLanaIndisponivel() {
        given(lanaSimulacaoClient.simular(any())).willThrow(
                new LanaIndisponivelException("Lana indisponível em /simulacao: Connection refused"));

        jobService.processarSimulacaoAsync(44L, Map.of("chat_id", "c1", "regra", Map.of()));

        verify(persistenceService).falharSimulacao(44L, "Lana indisponível em /simulacao: Connection refused");
    }

    @Test
    void processarSimulacaoEmTimeoutMarcaStatusErroComConcluidaEmPreenchido() {
        SimulacaoRepository repo = mock(SimulacaoRepository.class);
        Instant agora = Instant.parse("2026-10-21T13:05:00Z");
        SimulacaoPersistenceService realPersistence = new SimulacaoPersistenceService(
                repo, Clock.fixed(agora, ZoneOffset.UTC));
        SimulacaoJobService serviceComPersistenceReal = new SimulacaoJobService(
                realPersistence, lanaSimulacaoClient, JSON);

        Simulacao simulacao = new Simulacao();
        simulacao.setId(45L);
        simulacao.setStatus(StatusSimulacao.PENDENTE);
        given(repo.findById(45L)).willReturn(Optional.of(simulacao));
        given(lanaSimulacaoClient.simular(any())).willThrow(
                new LanaIndisponivelException("Timeout de 180s excedido"));

        serviceComPersistenceReal.processarSimulacaoAsync(45L, Map.of("chat_id", "c1", "regra", Map.of()));

        assertThat(simulacao.getStatus()).isEqualTo(StatusSimulacao.ERRO);
        assertThat(simulacao.getEtapa()).isNull();
        assertThat(simulacao.getErro()).isEqualTo("Timeout de 180s excedido");
        assertThat(simulacao.getConcluidaEm()).isEqualTo(agora);
    }
}
