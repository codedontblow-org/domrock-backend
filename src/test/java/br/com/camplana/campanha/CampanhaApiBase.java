package br.com.camplana.campanha;

import br.com.camplana.TestcontainersConfiguration;
import br.com.camplana.entity.CampanhaVersao;
import br.com.camplana.entity.StatusCampanha;
import br.com.camplana.entity.Usuario;
import br.com.camplana.repository.CampanhaRepository;
import br.com.camplana.repository.CampanhaVersaoRepository;
import br.com.camplana.repository.MensagemChatRepository;
import br.com.camplana.repository.UsuarioRepository;
import br.com.camplana.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes da API de campanha: Postgres real (V7 a V9), registro de simulações falso, relógio
 * fixo em {@link CampanhaTestConfig#AGORA} e os usuários do seed (Gabriela gerente; Sérgio e Sílvia
 * supervisores).
 */
@Import({TestcontainersConfiguration.class, CampanhaTestConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class CampanhaApiBase {

    protected static final String REPROVACAO =
            "a meta de R$ 2 milhões está abaixo da venda real do período (R$ 2,4 milhões)";

    @Autowired protected MockMvc mvc;
    @Autowired protected JwtService jwtService;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UsuarioRepository usuarioRepository;
    @Autowired protected CampanhaRepository campanhaRepository;
    @Autowired protected CampanhaVersaoRepository versaoRepository;
    @Autowired protected MensagemChatRepository mensagemChatRepository;
    @Autowired protected FakeRegistroSimulacoes simulacoes;
    @Autowired protected ContextoCampanhasService contextoCampanhas;

    protected Usuario gabriela, sergio, silvia;
    private long proximaSimulacao;

    @BeforeEach
    void limparCampanhas() {
        limparResimulacoes();
        jdbc.execute("DELETE FROM evento_rh");
        jdbc.execute("DELETE FROM campanha_status_log");
        jdbc.execute("DELETE FROM campanha_versao");
        jdbc.execute("DELETE FROM campanha");
        jdbc.execute("DELETE FROM mensagem_chat");
        simulacoes.limpar();
        proximaSimulacao = 345;
        gabriela = usuarioRepository.findById(1).orElseThrow();
        sergio = usuarioRepository.findById(2).orElseThrow();
        silvia = usuarioRepository.findById(3).orElseThrow();
    }

    // A re-simulação (V9) aponta para a versão: sai antes dela, aqui e no fim, para não travar a limpeza
    // das outras classes que apagam campanha_versao.
    @AfterEach
    void limparResimulacoes() {
        jdbc.execute("DELETE FROM simulacao WHERE versao_id IS NOT NULL");
    }

    // ---------- Apoio ----------

    protected long salvarBlackFriday(Usuario autor) throws Exception {
        String chatId = UUID.randomUUID().toString();
        long simulacaoId = proximaSimulacao++;
        simulacoes.concluida(simulacaoId, autor.getId(), chatId);
        String resposta = enviar(post("/api/campanhas"), autor, salvar("Black Friday 2025", chatId, simulacaoId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    protected long submeterBlackFriday(Usuario autor) throws Exception {
        long id = salvarBlackFriday(autor);
        enviar(post("/api/campanhas/" + id + "/submeter"), autor).andExpect(status().isOk());
        return id;
    }

    /** Decide a última versão pela rota (5.8). */
    protected void decidir(long campanhaId, StatusCampanha decisao, Usuario supervisor, String justificativa)
            throws Exception {
        List<CampanhaVersao> versoes = versaoRepository.findByCampanhaIdOrderByNumeroAsc(campanhaId);
        int versao = versoes.get(versoes.size() - 1).getNumero();
        enviar(post("/api/campanhas/" + campanhaId + "/decidir"), supervisor,
                decisao(versao, decisao == StatusCampanha.APROVADA ? "APROVAR" : "REPROVAR", justificativa))
                .andExpect(status().isOk());
    }

    protected String decisao(int versao, String decisao, String justificativa) throws Exception {
        return new ObjectMapper().writeValueAsString(
                java.util.Map.of("versao", versao, "decisao", decisao,
                        "justificativa", justificativa == null ? "" : justificativa));
    }

    protected String chatDa(long campanhaId) {
        return campanhaRepository.findById(campanhaId).orElseThrow().getChatId();
    }

    protected static String salvar(String nome, String chatId, long simulacaoId) {
        return "{\"nome\": \"%s\", \"chat_id\": \"%s\", \"simulacao_id\": %d}".formatted(nome, chatId, simulacaoId);
    }

    protected String autorizacao(Usuario usuario) {
        return "Bearer " + token(usuario);
    }

    protected long criarCampanha(String chatId) throws Exception {
        long simulacaoId = proximaSimulacao++;
        simulacoes.concluida(simulacaoId, gabriela.getId(), chatId);
        String resposta = enviar(post("/api/campanhas"), gabriela, salvar("Campanha " + chatId, chatId, simulacaoId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    protected ResultActions enviar(MockHttpServletRequestBuilder pedido, Usuario usuario) throws Exception {
        return mvc.perform(pedido.header("Authorization", "Bearer " + token(usuario)));
    }

    protected ResultActions enviar(MockHttpServletRequestBuilder pedido, Usuario usuario, String json) throws Exception {
        return enviar(pedido.contentType(MediaType.APPLICATION_JSON).content(json), usuario);
    }

    private String token(Usuario usuario) {
        return jwtService.gerarToken(usuario, jwtService.getExpirationDate());
    }
}
