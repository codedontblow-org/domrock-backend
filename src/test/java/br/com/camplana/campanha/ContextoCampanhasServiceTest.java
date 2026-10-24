package br.com.camplana.campanha;

import br.com.camplana.entity.Campanha;
import br.com.camplana.entity.CampanhaStatusLog;
import br.com.camplana.entity.CampanhaVersao;
import br.com.camplana.entity.StatusCampanha;
import br.com.camplana.entity.Usuario;
import br.com.camplana.repository.CampanhaStatusLogRepository;
import br.com.camplana.repository.CampanhaVersaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContextoCampanhasServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final CampanhaVersaoRepository versoes = mock(CampanhaVersaoRepository.class);
    private final CampanhaStatusLogRepository logs = mock(CampanhaStatusLogRepository.class);
    private ContextoCampanhasService contexto;

    @BeforeEach
    void preparar() {
        contexto = new ContextoCampanhasService(versoes, logs, new CamposDaSimulacao(json), json);
        when(versoes.findContextoSobreposto(any(), any(), anyString(), anyList())).thenReturn(List.of());
    }

    @Test
    void aprovadaVaiNoFormatoDoContrato43ComADecisaoDaVersao() {
        var aprovada = versao(9, StatusCampanha.APROVADA, "[\"10\"]", "[\"150\"]");
        sobrepostas(aprovada);
        aprovadaPor(aprovada, "Sérgio", "2026-10-15T14:02:00Z", "Reforço da última semana de novembro");

        JsonNode campanhas = comoALanaRecebe(contexto.enriquecer(pedido("2025-11-24", "2025-11-30", "10", "150")));

        assertThat(campanhas.path("campanhas")).containsExactly(json.readTree("""
                { "campanha_id": 9, "versao": 1, "tipo": "ACRESCIMO", "nome": "Campanha 9", "status": "APROVADA",
                  "autor": "Gabriela", "aprovador": "Sérgio", "aprovada_em": "2026-10-15T14:02:00Z",
                  "justificativa": "Reforço da última semana de novembro",
                  "data_inicio": "2025-11-20", "data_fim": "2025-11-30",
                  "pct_acrescimo": 1.0, "marcas_alvo": ["10"], "cargos_alvo": ["150"] }
                """));
    }

    @Test
    void pendenteVaiSemDecisao() {
        sobrepostas(versao(11, StatusCampanha.AGUARDANDO_APROVACAO, "[\"10\"]", "[\"150\"]"));

        JsonNode pendente = contexto.enriquecer(pedido("2025-11-24", "2025-11-30", "10", "150")).path("campanhas").get(0);

        assertThat(pendente.path("status").asString()).isEqualTo("AGUARDANDO_APROVACAO");
        assertThat(List.of(pendente.get("aprovador"), pendente.get("aprovada_em"), pendente.get("justificativa")))
                .allMatch(JsonNode::isNull);
        verifyNoInteractions(logs);
    }

    @Test
    void ficaDeForaQuemNaoTemMarcaECargoEmComum() {
        sobrepostas(versao(1, StatusCampanha.AGUARDANDO_APROVACAO, "[\"20\"]", "[\"150\"]"),
                versao(2, StatusCampanha.AGUARDANDO_APROVACAO, "[\"10\"]", "[\"100\"]"));

        assertThat(contexto.enriquecer(pedido("2025-11-24", "2025-11-30", "10", "150")).path("campanhas")).isEmpty();
    }

    @Test
    void campanhaComTodasAsMarcasVaiComAsSeisMarcas() {
        sobrepostas(versao(1, StatusCampanha.AGUARDANDO_APROVACAO, "[\"ALL\"]", "[\"150\"]"));

        JsonNode item = contexto.enriquecer(pedido("2025-11-24", "2025-11-30", "20", "150")).path("campanhas").get(0);

        assertThat(item.path("marcas_alvo")).isEqualTo(json.readTree("[\"10\", \"20\", \"30\", \"40\", \"50\", \"60\"]"));
    }

    @Test
    void consultaOPeriodoDaFichaSemAPropriaConversa() {
        contexto.enriquecer(pedido("2025-11-24", "2025-11-30", "10", "150"));

        verify(versoes).findContextoSobreposto(LocalDate.parse("2025-11-24"), LocalDate.parse("2025-11-30"),
                "chat-atual", List.of(StatusCampanha.APROVADA, StatusCampanha.AGUARDANDO_APROVACAO));
    }

    @Test
    void naoMandaCampanhasDaEspecificacao() {
        // Out/25, marca 30, cargo 100 tem o item 4g; quem carrega a especificação é a Lana (contrato 4.3).
        assertThat(contexto.enriquecer(pedido("2025-10-01", "2025-10-31", "30", "100")).path("campanhas")).isEmpty();
    }

    @Test
    void descartaAsCampanhasMandadasPeloNavegador() {
        var pedido = pedido("2025-11-24", "2025-11-30", "10", "150");
        pedido.set("campanhas", json.readTree("[{\"campanha_id\": 99, \"status\": \"APROVADA\"}]"));

        assertThat(contexto.enriquecer(pedido).path("campanhas")).isEmpty();
        assertThat(pedido.path("campanhas")).hasSize(1);
    }

    @Test
    void fichaIncompletaSegueSemContextoParaALanaValidar() {
        var semCargos = pedido("2025-11-24", "2025-11-30", "10", "150");
        ((ArrayNode) semCargos.path("regra").path("parametros")).remove(2);

        assertThat(contexto.enriquecer(semCargos).path("campanhas")).isEmpty();
        verifyNoInteractions(versoes, logs);
    }

    @Test
    void periodoInvertidoSegueSemContextoParaALanaValidar() {
        assertThat(contexto.enriquecer(pedido("2025-12-01", "2025-11-30", "10", "150")).path("campanhas")).isEmpty();
        verifyNoInteractions(versoes, logs);
    }

    @Test
    void falhaSeCampanhaAprovadaNaoTemDecisaoDaVersao() {
        sobrepostas(versao(1, StatusCampanha.APROVADA, "[\"10\"]", "[\"150\"]"));

        assertThatThrownBy(() -> contexto.enriquecer(pedido("2025-11-24", "2025-11-30", "10", "150")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("sem decisão da versão 1");
    }

    /** Relido do texto, como chega na Lana: 9 é 9, seja Long ou Integer no Java. */
    private JsonNode comoALanaRecebe(JsonNode pedido) {
        return json.readTree(pedido.toString());
    }

    private void sobrepostas(CampanhaVersao... encontradas) {
        when(versoes.findContextoSobreposto(any(), any(), anyString(), anyList())).thenReturn(List.of(encontradas));
    }

    private void aprovadaPor(CampanhaVersao versao, String supervisor, String em, String justificativa) {
        Usuario usuario = new Usuario();
        usuario.setNome(supervisor);
        var decisao = new CampanhaStatusLog(versao.getCampanha(), versao, StatusCampanha.AGUARDANDO_APROVACAO,
                StatusCampanha.APROVADA, usuario, Instant.parse(em), justificativa);
        when(logs.findByCampanhaIdOrderByEmAscIdAsc(versao.getCampanha().getId())).thenReturn(List.of(decisao));
    }

    private ObjectNode pedido(String inicio, String fim, String marca, String cargo) {
        return (ObjectNode) json.readTree("""
                {"chat_id": "chat-atual", "regra": {"parametros": [
                  {"key": "periodo", "value": {"data_inicio": "%s", "data_fim": "%s"}},
                  {"key": "marcas_alvo", "value": ["%s"]},
                  {"key": "cargos_alvo", "value": ["%s"]},
                  {"key": "pct_acrescimo", "value": 0.5}
                ]}}
                """.formatted(inicio, fim, marca, cargo));
    }

    /** Versão 1 de 20 a 30/11/2025 com +1,0%, como a Campanha A do contrato. */
    private CampanhaVersao versao(long id, StatusCampanha status, String marcas, String cargos) {
        Usuario autor = new Usuario();
        autor.setNome("Gabriela");
        Campanha campanha = new Campanha();
        campanha.setId(id);
        campanha.setAutor(autor);
        campanha.setNome("Campanha " + id);
        campanha.setStatus(status);
        String regra = """
                {"parametros": [
                  {"key": "periodo", "value": {"data_inicio": "2025-11-20", "data_fim": "2025-11-30"}},
                  {"key": "pct_acrescimo", "value": 1.0},
                  {"key": "marcas_alvo", "value": %s},
                  {"key": "cargos_alvo", "value": %s}
                ]}""".formatted(marcas, cargos);
        var versao = new CampanhaVersao(campanha, 1, id, regra, "{}",
                LocalDate.parse("2025-11-20"), LocalDate.parse("2025-11-30"), Instant.now());
        ReflectionTestUtils.setField(versao, "id", id);
        return versao;
    }
}
