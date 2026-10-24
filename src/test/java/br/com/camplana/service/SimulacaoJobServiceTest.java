package br.com.camplana.service;

import br.com.camplana.client.LanaSimulacaoClient;
import br.com.camplana.campanha.ContextoCampanhasService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SimulacaoJobServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private SimulacaoPersistenceService persistenceService;

    @Mock
    private LanaSimulacaoClient lanaSimulacaoClient;

    @Mock
    private ContextoCampanhasService contextoCampanhas;

    private SimulacaoJobService jobService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(contextoCampanhas.enriquecer(any())).thenAnswer(i -> JSON.valueToTree(i.getArgument(0)));
        jobService = new SimulacaoJobService(persistenceService, lanaSimulacaoClient, JSON, contextoCampanhas);
    }

    @Test
    void processarSimulacaoRegistraSimulandoUmaUnicaVezEExtraiMetadadosDaTrilhaECodigo() {
        JsonNode respostaLana = JSON.readTree("""
                {
                  "totais": {"baseline": 480263.24, "simulado": 503999.34, "diferenca": 23736.10},
                  "comissoes_periodo": [{"marca":"10","cargo":"150","base":1,"vigente":3,"comCampanha":3.5}],
                  "conflitos": [{"id":"C","tipo":"ACRESCIMO","status":"AGUARDANDO_APROVACAO"}],
                  "codigo": "resultado = calcular(vendas)",
                  "trilha": {
                    "modelo": "gemini-3.5-flash-lite",
                    "tentativas": 1,
                    "premissas": ["Gerentes (150) excluídos"],
                    "passos": [
                      {"ordem": 1, "ferramenta": "carregar_bases", "situacao": "SUCESSO"}
                    ]
                  },
                  "duracao_ms": 1420
                }
                """);
        given(lanaSimulacaoClient.simular(any())).willReturn(respostaLana);

        jobService.processarSimulacaoAsync(42L, Map.of("chat_id", "c1", "regra", Map.of()));

        InOrder ordem = inOrder(persistenceService, lanaSimulacaoClient);
        ordem.verify(persistenceService).atualizarEtapa(42L, EtapaSimulacao.SIMULANDO);
        ordem.verify(lanaSimulacaoClient).simular(any());
        ordem.verify(persistenceService).concluirSimulacao(
                eq(42L),
                eq(JSON.writeValueAsString(respostaLana)),
                eq(JSON.writeValueAsString(respostaLana.get("trilha"))),
                eq("resultado = calcular(vendas)"),
                eq("gemini-3.5-flash-lite"),
                eq(1),
                eq(1420L)
        );
    }

    @Test
    void enviaOLoteEnriquecidoPeloBackend() {
        var recebido = Map.of("chat_id", "c1", "regra", Map.of());
        var enriquecido = (tools.jackson.databind.node.ObjectNode) JSON.readTree(
                "{\"chat_id\":\"c1\",\"regra\":{},\"campanhas\":[{\"id\":\"A\"}]}");
        given(contextoCampanhas.enriquecer(recebido)).willReturn(enriquecido);
        given(lanaSimulacaoClient.simular(enriquecido)).willReturn(JSON.readTree("{}"));

        jobService.processarSimulacaoAsync(46L, recebido);

        verify(lanaSimulacaoClient).simular(enriquecido);
    }

    @Test
    void contextoInvalidoFalhaSemChamarALana() {
        org.mockito.Mockito.doThrow(new br.com.camplana.exception.BadRequestException("Período inválido"))
                .when(contextoCampanhas).enriquecer(any());

        jobService.processarSimulacaoAsync(47L, Map.of("chat_id", "c1"));

        org.mockito.Mockito.verifyNoInteractions(lanaSimulacaoClient);
        verify(persistenceService).falharSimulacao(eq(47L), org.mockito.ArgumentMatchers.argThat(erro ->
                JSON.readTree(erro).path("etapa").asString().equals("validacao")));
    }

    @Test
    void processarSimulacaoPersisteObjetoDeErroDaLanaQuandoRetorna422() {
        String corpo422 = "{\"etapa\":\"validacao\",\"mensagem\":\"Regra incompleta ou inválida\",\"erros\":[\"periodo: obrigatório\"]}";
        given(lanaSimulacaoClient.simular(any())).willThrow(
                new LanaRespostaException(422, corpo422));

        jobService.processarSimulacaoAsync(43L, Map.of("chat_id", "c1", "regra", Map.of()));

        verify(persistenceService).falharSimulacao(43L, corpo422);
    }

    @Test
    void processarSimulacaoGravaErroLanaIndisponivelNoFormatoDoContrato() {
        given(lanaSimulacaoClient.simular(any())).willThrow(
                new LanaIndisponivelException("Lana indisponível em /simulacao: Connection refused"));

        jobService.processarSimulacaoAsync(44L, Map.of("chat_id", "c1", "regra", Map.of()));

        verify(persistenceService).falharSimulacao(
                44L,
                "{\"etapa\":\"lana_indisponivel\",\"mensagem\":\"Lana indisponível em /simulacao\"}"
        );
    }

    @Test
    void processarSimulacaoEmTimeoutMarcaStatusErroComEtapaErroEObjetoTempo() {
        SimulacaoRepository repo = mock(SimulacaoRepository.class);
        Instant agora = Instant.parse("2026-10-21T13:05:00Z");
        SimulacaoPersistenceService realPersistence = new SimulacaoPersistenceService(
                repo, Clock.fixed(agora, ZoneOffset.UTC));
        SimulacaoJobService serviceComPersistenceReal = new SimulacaoJobService(
                realPersistence, lanaSimulacaoClient, JSON, contextoCampanhas);

        Simulacao simulacao = new Simulacao();
        simulacao.setId(45L);
        simulacao.setStatus(StatusSimulacao.PENDENTE);
        simulacao.setEtapa(EtapaSimulacao.NA_FILA);
        given(repo.findById(45L)).willReturn(Optional.of(simulacao));
        given(lanaSimulacaoClient.simular(any())).willThrow(new LanaIndisponivelException(
                "Lana indisponível em /simulacao: I/O error on POST request for \"http://lana:8000/simulacao\": Read timed out"));

        serviceComPersistenceReal.processarSimulacaoAsync(45L, Map.of("chat_id", "c1", "regra", Map.of()));

        assertThat(simulacao.getStatus()).isEqualTo(StatusSimulacao.ERRO);
        assertThat(simulacao.getEtapa()).isEqualTo(EtapaSimulacao.ERRO);
        assertThat(simulacao.getErro()).isEqualTo("{\"etapa\":\"tempo\",\"mensagem\":\"Tempo limite excedido na simulação.\"}");
        assertThat(simulacao.getConcluidaEm()).isEqualTo(agora);
    }

    @Test
    void processarSimulacaoComConnectTimeoutGravaLanaIndisponivelEnaoTempo() {
        given(lanaSimulacaoClient.simular(any())).willThrow(new LanaIndisponivelException(
                "Lana indisponível em /simulacao: I/O error on POST request for \"http://lana:8000/simulacao\": Connect timed out"));

        jobService.processarSimulacaoAsync(46L, Map.of("chat_id", "c1", "regra", Map.of()));

        verify(persistenceService).falharSimulacao(46L, SimulacaoJobService.ERRO_LANA_INDISPONIVEL);
    }

    @Test
    void processarSimulacaoComRespostaVaziaDaLanaGravaErroEnaoConcluida() {
        given(lanaSimulacaoClient.simular(any())).willReturn(null);

        jobService.processarSimulacaoAsync(47L, Map.of("chat_id", "c1", "regra", Map.of()));

        verify(persistenceService).falharSimulacao(47L, SimulacaoJobService.ERRO_INTERNO);
        verify(persistenceService, never()).concluirSimulacao(any(), any(), any(), any(), any(), any(), any());
    }
}
