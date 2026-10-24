package br.com.camplana.campanha;

import br.com.camplana.entity.*;
import br.com.camplana.exception.BadRequestException;
import br.com.camplana.repository.CampanhaStatusLogRepository;
import br.com.camplana.repository.CampanhaVersaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContextoCampanhasServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final CampanhaVersaoRepository versoes = mock(CampanhaVersaoRepository.class);
    private final CampanhaStatusLogRepository logs = mock(CampanhaStatusLogRepository.class);
    private ContextoCampanhasService contexto;

    @BeforeEach
    void preparar() throws Exception {
        contexto = new ContextoCampanhasService(versoes, logs, json);
        when(versoes.findContextoSobreposto(any(), any(), anyString(), anyList())).thenReturn(List.of());
    }

    @Test
    void enviaAprovadasComDecisaoPendentesSemDecisaoEEspecificacao() {
        var aprovada = versao(1, StatusCampanha.APROVADA, "10", "150");
        var pendente = versao(2, StatusCampanha.AGUARDANDO_APROVACAO, "10", "150");
        var outraMarca = versao(3, StatusCampanha.APROVADA, "20", "150");
        var outroCargo = versao(4, StatusCampanha.APROVADA, "10", "100");
        when(versoes.findContextoSobreposto(any(), any(), anyString(), anyList()))
                .thenReturn(List.of(aprovada, pendente, outraMarca, outroCargo));
        Usuario supervisor = new Usuario();
        supervisor.setNome("Sérgio");
        var decisao = new CampanhaStatusLog(aprovada.getCampanha(), aprovada,
                StatusCampanha.AGUARDANDO_APROVACAO, StatusCampanha.APROVADA,
                supervisor, Instant.parse("2025-10-20T12:00:00Z"), "Meta aprovada");
        when(logs.findByCampanhaIdOrderByEmAscIdAsc(1L)).thenReturn(List.of(decisao));

        JsonNode fontes = contexto.enriquecer(pedido("2025-11-24", "2025-11-30", "10", "150")).path("campanhas");

        assertThat(fontes.size()).isEqualTo(3);
        assertThat(fontes.get(0).path("aprovador").asString()).isEqualTo("Sérgio");
        assertThat(fontes.get(0).path("aprovada_em").asString()).isEqualTo("2025-10-20T12:00:00Z");
        assertThat(fontes.get(0).path("justificativa").asString()).isEqualTo("Meta aprovada");
        assertThat(fontes.get(0).path("percentual").doubleValue()).isEqualTo(.5);
        assertThat(fontes.get(1).path("status").asString()).isEqualTo("AGUARDANDO_APROVACAO");
        assertThat(fontes.get(1).has("aprovador")).isFalse();
        assertThat(fontes.get(2).path("id").asString()).isEqualTo("especificacao-2025-11-5g");
        verify(versoes).findContextoSobreposto(LocalDate.parse("2025-11-24"), LocalDate.parse("2025-11-30"),
                "chat-atual", List.of(StatusCampanha.APROVADA, StatusCampanha.AGUARDANDO_APROVACAO));
        verify(logs).findByCampanhaIdOrderByEmAscIdAsc(1L);
        verifyNoMoreInteractions(logs);
    }

    @Test
    void substituiContextoForjadoDoNavegadorEOutubroMarca10NaoTemCampanha() {
        var pedido = pedido("2025-10-01", "2025-10-31", "10", "150");
        pedido.set("campanhas", json.readTree("[{\"id\":\"forjada\",\"status\":\"APROVADA\"}]"));
        var enriquecido = contexto.enriquecer(pedido);
        assertThat(enriquecido.path("campanhas").isEmpty()).isTrue();
        assertThat(pedido.path("campanhas").size()).isEqualTo(1);
    }

    @Test
    void setembroUsaSubstituicaoComReferenciaSemCalcularPercentualNoJava() {
        var fontes = contexto.enriquecer(pedido("2025-09-01", "2025-09-30", "10", "100")).path("campanhas");
        assertThat(fontes.size()).isEqualTo(1);
        assertThat(fontes.get(0).path("tipo").asString()).isEqualTo("SUBSTITUICAO");
        assertThat(fontes.get(0).path("marca_referencia").asString()).isEqualTo("20");
        assertThat(fontes.get(0).hasNonNull("percentual")).isFalse();
        assertThat(fontes.get(0).path("origem").asString()).isEqualTo("ESPECIFICACAO");
    }

    @Test
    void allIncluiFontesDasMarcasEPeriodoEInclusivo() {
        var fontes = contexto.enriquecer(pedido("2025-11-30", "2025-12-01", "ALL", "100")).path("campanhas");
        assertThat(fontes.size()).isEqualTo(3); // 5f, 6f e 6g
    }

    @Test
    void falhaSeCampanhaAprovadaNaoTemDecisaoDaVersao() {
        when(versoes.findContextoSobreposto(any(), any(), anyString(), anyList()))
                .thenReturn(List.of(versao(1, StatusCampanha.APROVADA, "10", "150")));
        assertThatThrownBy(() -> contexto.enriquecer(pedido("2025-11-24", "2025-11-30", "10", "150")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("sem decisão");
    }

    @Test
    void periodoInvalidoFalhaAntesDaConsulta() {
        assertThatThrownBy(() -> contexto.enriquecer(pedido("2025-12-01", "2025-11-30", "10", "150")))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(versoes, logs);
    }

    private tools.jackson.databind.node.ObjectNode pedido(String inicio, String fim, String marca, String cargo) {
        return (tools.jackson.databind.node.ObjectNode) json.readTree("""
                {"chat_id":"chat-atual","regra":{"parametros":[
                  {"key":"periodo","value":{"data_inicio":"%s","data_fim":"%s"}},
                  {"key":"marcas_alvo","value":["%s"]},
                  {"key":"cargos_alvo","value":["%s"]},
                  {"key":"pct_acrescimo","value":0.5}
                ]}}
                """.formatted(inicio, fim, marca, cargo));
    }

    private CampanhaVersao versao(long id, StatusCampanha status, String marca, String cargo) {
        Usuario autor = new Usuario();
        autor.setNome("Gabriela");
        Campanha campanha = new Campanha();
        campanha.setId(id);
        campanha.setAutor(autor);
        campanha.setNome("Campanha " + id);
        campanha.setStatus(status);
        var versao = new CampanhaVersao(campanha, 1, id,
                pedido("2025-11-24", "2025-11-30", marca, cargo).path("regra").toString(), "{}",
                LocalDate.parse("2025-11-24"), LocalDate.parse("2025-11-30"), Instant.now());
        org.springframework.test.util.ReflectionTestUtils.setField(versao, "id", id);
        return versao;
    }
}
