package br.com.camplana.campanha;

import br.com.camplana.TestcontainersConfiguration;
import br.com.camplana.entity.CampanhaVersao;
import br.com.camplana.entity.StatusCampanha;
import br.com.camplana.entity.Usuario;
import br.com.camplana.repository.CampanhaRepository;
import br.com.camplana.repository.CampanhaVersaoRepository;
import br.com.camplana.repository.UsuarioRepository;
import br.com.camplana.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Critérios de aceite do A6-64 pela API, com Postgres real (V7) e o registro de simulações falso. */
@Import({TestcontainersConfiguration.class, CampanhaTestConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CampanhaApiTest {

    private static final String REPROVACAO =
            "a meta de R$ 2 milhões está abaixo da venda real do período (R$ 2,4 milhões)";

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CampanhaRepository campanhaRepository;
    @Autowired private CampanhaVersaoRepository versaoRepository;
    @Autowired private FakeRegistroSimulacoes simulacoes;
    @Autowired private ContextoCampanhasService contextoCampanhas;

    private Usuario gabriela, sergio, silvia;
    private long proximaSimulacao;

    @BeforeEach
    void limparCampanhas() {
        jdbc.execute("DELETE FROM campanha_status_log");
        jdbc.execute("DELETE FROM campanha_versao");
        jdbc.execute("DELETE FROM campanha");
        simulacoes.limpar();
        proximaSimulacao = 345;
        gabriela = usuarioRepository.findById(1).orElseThrow();
        sergio = usuarioRepository.findById(2).orElseThrow();
        silvia = usuarioRepository.findById(3).orElseThrow();
    }

    // ---------- Salvar (5.3) ----------

    @Test
    void salvarBlackFridayFicaEmRascunhoComOCustoDaSimulacao() throws Exception {
        simulacoes.concluida(345, gabriela.getId(), "chat-bf");

        enviar(post("/api/campanhas"), gabriela, salvar("Black Friday 2025", "chat-bf", 345))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.versao_atual").doesNotExist())
                .andExpect(jsonPath("$.versoes", empty()))
                .andExpect(jsonPath("$.acoes", contains("RENOMEAR", "SUBMETER", "ARQUIVAR")))
                .andExpect(jsonPath("$.autor.nome").value("Gabriela"))
                .andExpect(jsonPath("$.periodo.data_inicio").value("2025-11-24"))
                .andExpect(jsonPath("$.ultima_simulacao.id").value(345))
                .andExpect(jsonPath("$.ultima_simulacao.resultado.totais.diferenca").value(23736.10))
                .andExpect(jsonPath("$.log", hasSize(1)))
                .andExpect(jsonPath("$.log[0].de").doesNotExist())
                .andExpect(jsonPath("$.log[0].para").value("RASCUNHO"))
                .andExpect(jsonPath("$.criada_em").value("2026-10-21T13:05:00Z"));

        enviar(get("/api/campanhas/minhas"), gabriela)
                .andExpect(jsonPath("$[0].nome").value("Black Friday 2025"))
                .andExpect(jsonPath("$[0].custo_adicional").value(23736.10));
    }

    @Test
    void salvarSemSimulacaoDa400() throws Exception {
        enviar(post("/api/campanhas"), gabriela, salvar("Black Friday 2025", "chat-vazio", 999))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Simule antes de salvar: a simulação 999 não existe"));
    }

    @Test
    void salvarComSimulacaoRodandoDa400() throws Exception {
        simulacoes.rodando(345, gabriela.getId(), "chat-bf");

        enviar(post("/api/campanhas"), gabriela, salvar("Black Friday 2025", "chat-bf", 345))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Simule antes de salvar: a simulação 345 está em RODANDO"));
    }

    @Test
    void salvarSimulacaoDeOutroUsuarioDa403() throws Exception {
        simulacoes.concluida(345, sergio.getId(), "chat-do-sergio");

        enviar(post("/api/campanhas"), gabriela, salvar("Black Friday 2025", "chat-do-sergio", 345))
                .andExpect(status().isForbidden());
    }

    @Test
    void salvarDuasVezesAMesmaConversaDa409() throws Exception {
        long id = salvarBlackFriday(gabriela);
        String chatId = chatDa(id);
        simulacoes.concluida(346, gabriela.getId(), chatId);

        // Mesmo caminho de dois cliques simultâneos: quem barra é o índice único uk_campanha_chat
        enviar(post("/api/campanhas"), gabriela, salvar("De novo", chatId, 346))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("A conversa " + chatId + " já tem campanha: uma conversa, uma campanha"));
    }

    // ---------- Ações do autor (5.7) ----------

    @Test
    void submeterViraAguardandoV1ERegistraNoLog() throws Exception {
        long id = salvarBlackFriday(gabriela);

        enviar(post("/api/campanhas/" + id + "/submeter"), gabriela)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_APROVACAO"))
                .andExpect(jsonPath("$.versao_atual").value(1))
                .andExpect(jsonPath("$.acoes", empty()))
                .andExpect(jsonPath("$.versoes[0].status").value("AGUARDANDO_APROVACAO"))
                .andExpect(jsonPath("$.log[1].de").value("RASCUNHO"))
                .andExpect(jsonPath("$.log[1].para").value("AGUARDANDO_APROVACAO"))
                .andExpect(jsonPath("$.log[1].versao").value(1))
                .andExpect(jsonPath("$.log[1].usuario.nome").value("Gabriela"))
                .andExpect(jsonPath("$.log[1].em").value("2026-10-21T13:05:00Z"));
    }

    @Test
    void v1ReprovadaContinuaConsultavelDepoisDaV2() throws Exception {
        long id = submeterBlackFriday(gabriela);
        decidir(id, StatusCampanha.REPROVADA, sergio, REPROVACAO);

        enviar(post("/api/campanhas/" + id + "/editar"), gabriela)
                .andExpect(jsonPath("$.status").value("RASCUNHO"));
        simulacoes.concluida(350, gabriela.getId(), chatDa(id));
        enviar(post("/api/campanhas/" + id + "/submeter"), gabriela)
                .andExpect(jsonPath("$.versao_atual").value(2));

        enviar(get("/api/campanhas/" + id + "/versoes/1"), silvia)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPROVADA"))
                .andExpect(jsonPath("$.simulacao_id").value(345))
                .andExpect(jsonPath("$.decisao.decisao").value("REPROVADA"))
                .andExpect(jsonPath("$.decisao.por.nome").value("Sérgio"))
                .andExpect(jsonPath("$.decisao.justificativa").value(REPROVACAO));
        enviar(get("/api/campanhas/" + id + "/versoes/2"), silvia)
                .andExpect(jsonPath("$.status").value("AGUARDANDO_APROVACAO"))
                .andExpect(jsonPath("$.simulacao_id").value(350));
    }

    @Test
    void submeterDeNovoSemSimularDa400() throws Exception {
        long id = submeterBlackFriday(gabriela);
        decidir(id, StatusCampanha.REPROVADA, sergio, REPROVACAO);
        enviar(post("/api/campanhas/" + id + "/editar"), gabriela);

        enviar(post("/api/campanhas/" + id + "/submeter"), gabriela)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Simule de novo antes de submeter: a v1 usou a simulação 345"));
    }

    @Test
    void aprovadaNaoVoltaParaRascunho() throws Exception {
        long id = submeterBlackFriday(gabriela);
        decidir(id, StatusCampanha.APROVADA, sergio, null);

        enviar(post("/api/campanhas/" + id + "/editar"), gabriela)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Transição inválida: campanha " + id + " está em APROVADA, pedido RASCUNHO"));
        enviar(get("/api/campanhas/" + id), gabriela)
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.vigencia").value("ENCERRADA"));
    }

    // ---------- Decidir (5.8) ----------

    @Test
    void supervisorAprovaAVersaoQueViu() throws Exception {
        long id = submeterBlackFriday(gabriela);

        enviar(post("/api/campanhas/" + id + "/decidir"), sergio, decisao(1, "APROVAR", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.versoes[0].status").value("APROVADA"))
                .andExpect(jsonPath("$.versoes[0].decisao.por.nome").value("Sérgio"))
                .andExpect(jsonPath("$.log[2].de").value("AGUARDANDO_APROVACAO"))
                .andExpect(jsonPath("$.log[2].para").value("APROVADA"))
                .andExpect(jsonPath("$.log[2].versao").value(1));
        enviar(get("/api/campanhas/fila"), silvia).andExpect(jsonPath("$", empty()));
    }

    @Test
    void aprovadasListaSoAsAprovadasDeTodosOsAutores() throws Exception {
        long aprovada = submeterBlackFriday(gabriela);
        decidir(aprovada, StatusCampanha.APROVADA, sergio, null);
        long reprovada = submeterBlackFriday(gabriela);
        decidir(reprovada, StatusCampanha.REPROVADA, sergio, REPROVACAO);
        submeterBlackFriday(gabriela);

        enviar(get("/api/campanhas/aprovadas"), silvia)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(aprovada))
                .andExpect(jsonPath("$[0].nome").value("Black Friday 2025"))
                .andExpect(jsonPath("$[0].autor.nome").value("Gabriela"))
                .andExpect(jsonPath("$[0].periodo.data_inicio").value("2025-11-24"))
                .andExpect(jsonPath("$[0].vigencia").value("ENCERRADA"))
                .andExpect(jsonPath("$[0].pct_acrescimo").value(1.0))
                .andExpect(jsonPath("$[0].marcas", contains("10", "30")))
                .andExpect(jsonPath("$[0].cargos", contains("100", "200", "300")))
                .andExpect(jsonPath("$[0].custo_adicional").value(23736.10));
        enviar(get("/api/campanhas/aprovadas"), gabriela)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void reprovarGuardaAJustificativa() throws Exception {
        long id = submeterBlackFriday(gabriela);

        enviar(post("/api/campanhas/" + id + "/decidir"), sergio, decisao(1, "REPROVAR", "  " + REPROVACAO + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPROVADA"));
        enviar(get("/api/campanhas/" + id + "/versoes/1"), gabriela)
                .andExpect(jsonPath("$.decisao.decisao").value("REPROVADA"))
                .andExpect(jsonPath("$.decisao.justificativa").value(REPROVACAO));
    }

    @Test
    void reprovarSemJustificativaDa400() throws Exception {
        long id = submeterBlackFriday(gabriela);

        enviar(post("/api/campanhas/" + id + "/decidir"), sergio, decisao(1, "REPROVAR", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Justificativa obrigatória para reprovar"));
    }

    @Test
    void supervisorNaoDecideAPropriaCampanha() throws Exception {
        long id = submeterBlackFriday(sergio);

        enviar(post("/api/campanhas/" + id + "/decidir"), sergio, decisao(1, "APROVAR", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Outra pessoa precisa aprovar esta campanha"));
    }

    @Test
    void gerenteNaoDecide() throws Exception {
        long id = submeterBlackFriday(sergio);

        enviar(post("/api/campanhas/" + id + "/decidir"), gabriela, decisao(1, "APROVAR", null))
                .andExpect(status().isForbidden());
    }

    @Test
    void segundaDecisaoDaMesmaVersaoDa409ComQuemDecidiu() throws Exception {
        long id = submeterBlackFriday(gabriela);
        decidir(id, StatusCampanha.APROVADA, sergio, null);

        enviar(post("/api/campanhas/" + id + "/decidir"), silvia, decisao(1, "REPROVAR", REPROVACAO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Esta versão já foi decidida por Sérgio em 21/10/2026"));
    }

    @Test
    void decidirVersaoInexistenteDa404() throws Exception {
        long id = submeterBlackFriday(gabriela);

        enviar(post("/api/campanhas/" + id + "/decidir"), sergio, decisao(7, "APROVAR", null))
                .andExpect(status().isNotFound());
    }

    @Test
    void submeterCampanhaDeOutroUsuarioDa403() throws Exception {
        long id = salvarBlackFriday(sergio);

        enviar(post("/api/campanhas/" + id + "/submeter"), gabriela)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Só quem criou a campanha " + id + " pode SUBMETER"));
    }

    @Test
    void submeterComSimulacaoRodandoDa409() throws Exception {
        long id = salvarBlackFriday(gabriela);
        simulacoes.rodando(346, gabriela.getId(), chatDa(id));

        enviar(post("/api/campanhas/" + id + "/submeter"), gabriela)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Aguarde a simulação 346 terminar"));
    }

    @Test
    void renomearEmRascunhoTrocaONome() throws Exception {
        long id = salvarBlackFriday(gabriela);

        enviar(put("/api/campanhas/" + id), gabriela, "{\"nome\": \"Black Friday 2025 - sem gerentes\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Black Friday 2025 - sem gerentes"));
    }

    @Test
    void renomearComNomeVazioDa400() throws Exception {
        long id = salvarBlackFriday(gabriela);

        enviar(put("/api/campanhas/" + id), gabriela, "{\"nome\": \"  \"}")
                .andExpect(status().isBadRequest());
    }

    // ---------- Listas (5.4 e 5.5) ----------

    @Test
    void arquivadaSaiDeMinhasSemFiltro() throws Exception {
        long arquivada = salvarBlackFriday(gabriela);
        enviar(post("/api/campanhas/" + arquivada + "/arquivar"), gabriela)
                .andExpect(jsonPath("$.status").value("ARQUIVADA"));
        long ativa = salvarBlackFriday(gabriela);

        enviar(get("/api/campanhas/minhas"), gabriela)
                .andExpect(jsonPath("$[*].id", contains((int) ativa)));
        enviar(get("/api/campanhas/minhas?status=ARQUIVADA"), gabriela)
                .andExpect(jsonPath("$[*].id", contains((int) arquivada)));
    }

    @Test
    void minhasComStatusInvalidoDa400() throws Exception {
        enviar(get("/api/campanhas/minhas?status=VIGENTE"), gabriela)
                .andExpect(status().isBadRequest());
    }

    @Test
    void filaMostraSoOQueOutrosSubmeteram() throws Exception {
        long daGabriela = submeterBlackFriday(gabriela);
        long doSergio = submeterBlackFriday(sergio);

        enviar(get("/api/campanhas/fila"), sergio)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains((int) daGabriela)))
                .andExpect(jsonPath("$[0].versao").value(1))
                .andExpect(jsonPath("$[0].custo_adicional").value(23736.10))
                .andExpect(jsonPath("$[0].conflitos").value(0));
        enviar(get("/api/campanhas/fila"), silvia)
                .andExpect(jsonPath("$[*].id", contains((int) daGabriela, (int) doSergio)));
    }

    @Test
    void gerenteNaoVeAFila() throws Exception {
        enviar(get("/api/campanhas/fila"), gabriela).andExpect(status().isForbidden());
    }

    // ---------- Abrir (5.6) ----------

    @Test
    void supervisorSoAbreDepoisDaSubmissao() throws Exception {
        long id = salvarBlackFriday(gabriela);
        enviar(get("/api/campanhas/" + id), sergio).andExpect(status().isForbidden());

        enviar(post("/api/campanhas/" + id + "/submeter"), gabriela);

        enviar(get("/api/campanhas/" + id), sergio)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoes", empty()));
    }

    @Test
    void campanhaOuVersaoInexistenteDa404() throws Exception {
        long id = salvarBlackFriday(gabriela);

        enviar(get("/api/campanhas/" + (id + 1000)), gabriela).andExpect(status().isNotFound());
        enviar(get("/api/campanhas/" + id + "/versoes/7"), gabriela).andExpect(status().isNotFound());
    }

    @Test
    void submissaoCongelaPercentuaisHistoricoEConflitosParaOSupervisor() throws Exception {
        String resultado = """
                {"totais":{"diferenca":15},
                 "comissao_periodo":[{"marca":"10","cargo":"150","pct_base":1,"pct_vigente":3,
                  "pct_com_campanha":3.5,"historico":[{"campanha_id":9,"versao":1,"tipo":"ACRESCIMO",
                  "aprovador":"Sérgio"}]}],
                 "conflitos":[{"campanha_id":11,"versao":1,"tipo":"ACRESCIMO","status":"AGUARDANDO_APROVACAO"}],
                 "trilha":{"campanhas":[{"campanha_id":9,"versao":1}]}}
                """;
        simulacoes.concluida(345, gabriela.getId(), "chat-vigencia", resultado);
        String resposta = enviar(post("/api/campanhas"), gabriela, salvar("Vigência", "chat-vigencia", 345))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(resposta, "$.id")).longValue();
        enviar(post("/api/campanhas/" + id + "/submeter"), gabriela).andExpect(status().isOk());
        simulacoes.concluida(346, gabriela.getId(), "chat-vigencia");

        enviar(get("/api/campanhas/" + id + "/versoes/1"), sergio)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado.comissao_periodo[0].pct_vigente").value(3))
                .andExpect(jsonPath("$.resultado.comissao_periodo[0].historico[0].aprovador").value("Sérgio"))
                .andExpect(jsonPath("$.resultado.conflitos[0].campanha_id").value(11))
                .andExpect(jsonPath("$.resultado.trilha.campanhas[0].campanha_id").value(9));
        enviar(get("/api/campanhas/fila"), sergio)
                .andExpect(jsonPath("$[0].conflitos").value(1));
    }

    @Test
    void contextoConsultaAprovadasEPendentesEExcluiPropriaConversaERascunhos() throws Exception {
        long aprovada = submeterBlackFriday(gabriela);
        decidir(aprovada, StatusCampanha.APROVADA, sergio, "Meta aprovada");
        long pendente = submeterBlackFriday(gabriela);
        long reprovada = submeterBlackFriday(gabriela);
        decidir(reprovada, StatusCampanha.REPROVADA, sergio, REPROVACAO);
        salvarBlackFriday(gabriela);
        var json = new ObjectMapper();
        var pedido = json.createObjectNode();
        pedido.put("chat_id", "nova-conversa");
        pedido.set("regra", json.readTree(FakeRegistroSimulacoes.REGRA_BLACK_FRIDAY));

        var fontes = contextoCampanhas.enriquecer(pedido).path("campanhas");
        assertThat(fontes).extracting(f -> f.path("campanha_id").asLong()).containsExactly(aprovada, pendente);
        assertThat(fontes.get(0).path("aprovador").asString()).isEqualTo("Sérgio");
        pedido.put("chat_id", chatDa(pendente));
        var semPropria = contextoCampanhas.enriquecer(pedido).path("campanhas");
        assertThat(semPropria).extracting(f -> f.path("campanha_id").asLong()).containsExactly(aprovada);
    }

    // ---------- Apoio ----------

    private long salvarBlackFriday(Usuario autor) throws Exception {
        String chatId = UUID.randomUUID().toString();
        long simulacaoId = proximaSimulacao++;
        simulacoes.concluida(simulacaoId, autor.getId(), chatId);
        String resposta = enviar(post("/api/campanhas"), autor, salvar("Black Friday 2025", chatId, simulacaoId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private long submeterBlackFriday(Usuario autor) throws Exception {
        long id = salvarBlackFriday(autor);
        enviar(post("/api/campanhas/" + id + "/submeter"), autor).andExpect(status().isOk());
        return id;
    }

    /** Decide a última versão pela rota (5.8). */
    private void decidir(long campanhaId, StatusCampanha decisao, Usuario supervisor, String justificativa)
            throws Exception {
        List<CampanhaVersao> versoes = versaoRepository.findByCampanhaIdOrderByNumeroAsc(campanhaId);
        int versao = versoes.get(versoes.size() - 1).getNumero();
        enviar(post("/api/campanhas/" + campanhaId + "/decidir"), supervisor,
                decisao(versao, decisao == StatusCampanha.APROVADA ? "APROVAR" : "REPROVAR", justificativa))
                .andExpect(status().isOk());
    }

    private String decisao(int versao, String decisao, String justificativa) throws Exception {
        return new ObjectMapper().writeValueAsString(
                java.util.Map.of("versao", versao, "decisao", decisao,
                        "justificativa", justificativa == null ? "" : justificativa));
    }

    private String chatDa(long campanhaId) {
        return campanhaRepository.findById(campanhaId).orElseThrow().getChatId();
    }

    private static String salvar(String nome, String chatId, long simulacaoId) {
        return "{\"nome\": \"%s\", \"chat_id\": \"%s\", \"simulacao_id\": %d}".formatted(nome, chatId, simulacaoId);
    }

    private ResultActions enviar(MockHttpServletRequestBuilder pedido, Usuario usuario) throws Exception {
        return mvc.perform(pedido.header("Authorization", "Bearer " + token(usuario)));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder pedido, Usuario usuario, String json) throws Exception {
        return enviar(pedido.contentType(MediaType.APPLICATION_JSON).content(json), usuario);
    }

    private String token(Usuario usuario) {
        return jwtService.gerarToken(usuario, jwtService.getExpirationDate());
    }
}
