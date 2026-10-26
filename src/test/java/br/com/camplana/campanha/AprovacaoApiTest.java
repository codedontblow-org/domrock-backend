package br.com.camplana.campanha;

import br.com.camplana.entity.StatusCampanha;
import br.com.camplana.entity.Usuario;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A6-68 pela API: ações da versão, re-simulação pendente e a 5ª verificação do decidir (contrato 5.6,
 * 5.8 e 5.9). "Agora" é fixo; a submissão que precisa ser mais antiga é recuada no banco.
 */
class AprovacaoApiTest extends CampanhaApiBase {

    private static final Instant UMA_HORA_ANTES = CampanhaTestConfig.AGORA.minusSeconds(3600);
    private static final String RESULTADO_RESIMULADO = "{\"totais\": {\"diferenca\": 30000.00}}";

    @Test
    void supervisorQueNaoEOAutorRecebeAsAcoesDaVersao() throws Exception {
        long id = submeterBlackFriday(gabriela);

        enviar(get("/api/campanhas/" + id + "/versoes/1"), sergio)
                .andExpect(jsonPath("$.acoes", contains("APROVAR", "REPROVAR", "RESIMULAR")))
                .andExpect(jsonPath("$.resimulacao_necessaria").value(false))
                .andExpect(jsonPath("$.aprovadas_depois", empty()))
                .andExpect(jsonPath("$.resimulacao").doesNotExist());
        enviar(get("/api/campanhas/" + id + "/versoes/1"), gabriela)
                .andExpect(jsonPath("$.acoes", empty()));
    }

    @Test
    void outraCampanhaDoPeriodoAprovadaDepoisPedeResimulacao() throws Exception {
        long pendente = submeterHaUmaHora(gabriela);
        long outra = submeterBlackFriday(gabriela);
        decidir(outra, StatusCampanha.APROVADA, silvia, null);

        enviar(get("/api/campanhas/" + pendente + "/versoes/1"), sergio)
                .andExpect(jsonPath("$.resimulacao_necessaria").value(true))
                .andExpect(jsonPath("$.acoes", contains("REPROVAR", "RESIMULAR")))
                .andExpect(jsonPath("$.aprovadas_depois[0].campanha_id").value(outra))
                .andExpect(jsonPath("$.aprovadas_depois[0].aprovador").value("Sílvia"))
                .andExpect(jsonPath("$.aprovadas_depois[0].aprovada_em").value("2026-10-21T13:05:00Z"));
        enviar(get("/api/campanhas/fila"), sergio)
                .andExpect(jsonPath("$[0].id").value(pendente))
                .andExpect(jsonPath("$[0].resimulacao_necessaria").value(true));
    }

    @Test
    void aprovarSemResimularDa409MasReprovarContinuaValendo() throws Exception {
        long pendente = submeterHaUmaHora(gabriela);
        long outra = submeterBlackFriday(gabriela);
        decidir(outra, StatusCampanha.APROVADA, silvia, null);

        enviar(post("/api/campanhas/" + pendente + "/decidir"), sergio, decisao(1, "APROVAR", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Simule de novo antes de aprovar"));
        enviar(post("/api/campanhas/" + pendente + "/decidir"), sergio, decisao(1, "REPROVAR", REPROVACAO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPROVADA"));
    }

    @Test
    void resimulacaoDepoisDaAprovacaoLiberaAprovarSobreONumeroNovo() throws Exception {
        long pendente = submeterHaUmaHora(gabriela);
        long outra = submeterBlackFriday(gabriela);
        decidir(outra, StatusCampanha.APROVADA, silvia, null);
        long resimulacao = resimulacaoConcluida(pendente, sergio, CampanhaTestConfig.AGORA.plusSeconds(60));

        enviar(get("/api/campanhas/" + pendente + "/versoes/1"), silvia)
                .andExpect(jsonPath("$.resimulacao_necessaria").value(false))
                .andExpect(jsonPath("$.acoes", contains("APROVAR", "REPROVAR", "RESIMULAR")))
                .andExpect(jsonPath("$.resimulacao.simulacao_id").value(resimulacao))
                .andExpect(jsonPath("$.resimulacao.por.nome").value("Sérgio"))
                .andExpect(jsonPath("$.resimulacao.resultado.totais.diferenca").value(30000.00))
                .andExpect(jsonPath("$.resultado.totais.diferenca").value(23736.10));
        enviar(post("/api/campanhas/" + pendente + "/decidir"), silvia, decisao(1, "APROVAR", null))
                .andExpect(status().isOk());
        String aprovadas = enviar(get("/api/campanhas/aprovadas"), gabriela).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(aprovadas, "$[?(@.id == " + pendente + ")].custo_adicional"))
                .containsExactly(30000.0);
    }

    @Test
    void resimularCriaUmJobDaVersaoQueOsSupervisoresAcompanham() throws Exception {
        long id = submeterBlackFriday(gabriela);

        String criada = enviar(post("/api/campanhas/" + id + "/versoes/1/resimular"), sergio)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andReturn().getResponse().getContentAsString();

        long simulacaoId = ((Number) JsonPath.read(criada, "$.id")).longValue();
        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT usuario_id, campanha_id, versao_id FROM simulacao WHERE id = ?", simulacaoId);
        assertThat(linha.get("usuario_id")).isEqualTo(sergio.getId());
        assertThat(linha.get("campanha_id")).isNull();
        assertThat(linha.get("versao_id")).isEqualTo(versaoUm(id));
        enviar(get("/api/simulacoes/" + simulacaoId), silvia).andExpect(status().isOk());
    }

    @Test
    void soOutroSupervisorResimulaVersaoAguardando() throws Exception {
        long daGabriela = submeterBlackFriday(gabriela);
        long doSergio = submeterBlackFriday(sergio);
        long decidida = submeterBlackFriday(gabriela);
        decidir(decidida, StatusCampanha.APROVADA, sergio, null);

        enviar(post("/api/campanhas/" + daGabriela + "/versoes/1/resimular"), gabriela)
                .andExpect(status().isForbidden());
        enviar(post("/api/campanhas/" + doSergio + "/versoes/1/resimular"), sergio)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Outra pessoa precisa aprovar esta campanha"));
        enviar(post("/api/campanhas/" + decidida + "/versoes/1/resimular"), silvia)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Esta versão já foi decidida por Sérgio em 21/10/2026"));
    }

    // ---------- Apoio ----------

    /** Submete e recua a submissão em uma hora, para uma aprovação "agora" ficar depois dela. */
    private long submeterHaUmaHora(Usuario autor) throws Exception {
        long id = submeterBlackFriday(autor);
        jdbc.update("UPDATE campanha_versao SET submetida_em = ? WHERE campanha_id = ?",
                Timestamp.from(UMA_HORA_ANTES), id);
        return id;
    }

    /** Re-simulação concluída da v1, como o job do 5.9 a deixaria gravada. */
    private long resimulacaoConcluida(long campanhaId, Usuario supervisor, Instant criadaEm) {
        long id = 9001;
        jdbc.update("""
                INSERT INTO simulacao (id, usuario_id, chat_id, versao_id, status, ficha, resultado, criada_em, concluida_em)
                VALUES (?, ?, ?, ?, 'CONCLUIDA', ?::jsonb, ?::jsonb, ?, ?)
                """, id, supervisor.getId(), chatDa(campanhaId), versaoUm(campanhaId),
                FakeRegistroSimulacoes.REGRA_BLACK_FRIDAY, RESULTADO_RESIMULADO,
                Timestamp.from(criadaEm), Timestamp.from(criadaEm.plusSeconds(60)));
        return id;
    }

    private long versaoUm(long campanhaId) {
        return versaoRepository.findByCampanhaIdOrderByNumeroAsc(campanhaId).get(0).getId();
    }
}
