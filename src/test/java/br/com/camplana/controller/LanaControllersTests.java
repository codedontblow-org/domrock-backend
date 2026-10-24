package br.com.camplana.controller;

import br.com.camplana.client.LanaClient;
import br.com.camplana.dto.ChatRequest;
import br.com.camplana.entity.*;
import br.com.camplana.handler.GlobalExceptionHandler;
import br.com.camplana.handler.SecurityErrorHandler;
import br.com.camplana.repository.CampanhaRepository;
import br.com.camplana.repository.CampanhaVersaoRepository;
import br.com.camplana.repository.SimulacaoRepository;
import br.com.camplana.repository.UsuarioRepository;
import br.com.camplana.security.JwtService;
import br.com.camplana.security.PasswordConfig;
import br.com.camplana.security.SecurityConfig;
import br.com.camplana.security.UsuarioDetailsService;
import br.com.camplana.service.SimulacaoJobService;
import br.com.camplana.service.SimulacaoManagerService;
import br.com.camplana.service.SimulacaoPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {ChatController.class, SimulacaoController.class},
        properties = "api.security.token.secret=segredo-de-teste-com-mais-de-32-caracteres"
)
@Import({SecurityConfig.class, SecurityErrorHandler.class, GlobalExceptionHandler.class, JwtService.class,
        UsuarioDetailsService.class, PasswordConfig.class, SimulacaoManagerService.class})
class LanaControllersTests {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private LanaClient lanaClient;

    @MockitoBean
    private SimulacaoPersistenceService persistenceService;

    @MockitoBean
    private SimulacaoJobService jobService;

    @MockitoBean
    private SimulacaoRepository simulacaoRepository;

    @MockitoBean
    private CampanhaRepository campanhaRepository;

    @MockitoBean
    private CampanhaVersaoRepository campanhaVersaoRepository;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    private Usuario sergio;
    private Usuario gabriela;
    private String autorizacao;
    private String autorizacaoGabriela;

    @BeforeEach
    void autenticarUsuarios() {
        sergio = new Usuario();
        sergio.setId(2);
        sergio.setNome("Sérgio");
        sergio.setEmail("sergio@camplana.com.br");
        sergio.setPerfil(Perfil.SUPERVISOR);
        sergio.setAtivo(true);

        gabriela = new Usuario();
        gabriela.setId(1);
        gabriela.setNome("Gabriela");
        gabriela.setEmail("gabriela@camplana.com.br");
        gabriela.setPerfil(Perfil.GERENTE);
        gabriela.setAtivo(true);

        given(usuarioRepository.findByEmailIgnoreCase(sergio.getEmail())).willReturn(Optional.of(sergio));
        given(usuarioRepository.findByEmailIgnoreCase(gabriela.getEmail())).willReturn(Optional.of(gabriela));
        autorizacao = "Bearer " + jwtService.gerarToken(sergio, jwtService.getExpirationDate());
        autorizacaoGabriela = "Bearer " + jwtService.gerarToken(gabriela, jwtService.getExpirationDate());
    }

    @Test
    void chatDevolveARespostaDaLana() throws Exception {
        given(lanaClient.invocarAgente(any())).willReturn(JSON.readTree("{\"response\":\"oi\"}"));

        mockMvc.perform(post("/api/chat").header("Authorization", autorizacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"c1\",\"message\":\"oi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("oi"));
    }

    @Test
    void chatRepassaARegraDoPainelParaALana() throws Exception {
        given(lanaClient.invocarAgente(any())).willReturn(JSON.readTree("{\"response\":\"ok\"}"));

        mockMvc.perform(post("/api/chat").header("Authorization", autorizacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"c1\",\"message\":\"so a marca 30\",\"regra\":{\"rule_id\":\"r1\"}}"))
                .andExpect(status().isOk());

        then(lanaClient).should().invocarAgente(argThat(corpo ->
                corpo instanceof ChatRequest pedido && "r1".equals(pedido.regra().path("rule_id").asString())));
    }

    @Test
    void chatRecusaMensagemVaziaSemChamarALana() throws Exception {
        mockMvc.perform(post("/api/chat").header("Authorization", autorizacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"c1\",\"message\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(lanaClient);
    }

    @Test
    void simulacaoCriaJobAssincronoEDevolve202ComEtapaNaFila() throws Exception {
        Simulacao criada = new Simulacao();
        criada.setId(345L);
        criada.setUsuario(sergio);
        criada.setChatId("c1");
        criada.setStatus(StatusSimulacao.PENDENTE);
        criada.setEtapa(EtapaSimulacao.NA_FILA);

        given(simulacaoRepository.findFirstByChatIdOrderByIdAsc("c1")).willReturn(Optional.empty());
        given(campanhaRepository.findByChatId("c1")).willReturn(Optional.empty());
        given(persistenceService.criarSimulacaoInicial(any(), eq("c1"), eq(null), any())).willReturn(criada);

        mockMvc.perform(post("/api/simulacoes").header("Authorization", autorizacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"c1\",\"regra\":{\"rule_id\":\"r1\"}}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(345))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.etapa").value("Na fila"));

        then(jobService).should().processarSimulacaoAsync(eq(345L), any());
    }

    @Test
    void simulacaoRecusaCorpoSemChatIdOuSemRegra() throws Exception {
        mockMvc.perform(post("/api/simulacoes").header("Authorization", autorizacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"regra\":{\"rule_id\":\"r1\"}}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/simulacoes").header("Authorization", autorizacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"c1\",\"regra\":{}}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/simulacoes").header("Authorization", autorizacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"c1\",\"ficha\":{\"rule_id\":\"r1\"}}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(persistenceService, jobService);
    }

    @Test
    void simulacaoResponde403QuandoConversaPertenceAOutroUsuario() throws Exception {
        Simulacao deOutro = new Simulacao();
        deOutro.setId(10L);
        deOutro.setUsuario(gabriela);
        deOutro.setChatId("chat-gabriela");
        given(simulacaoRepository.findFirstByChatIdOrderByIdAsc("chat-gabriela")).willReturn(Optional.of(deOutro));

        mockMvc.perform(post("/api/simulacoes").header("Authorization", autorizacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"chat-gabriela\",\"regra\":{\"rule_id\":\"r1\"}}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(persistenceService, jobService);
    }

    @Test
    void simulacaoResponde409QuandoCampanhaDaConversaNaoEstaEmRascunho() throws Exception {
        Campanha campanha = new Campanha();
        campanha.setId(15L);
        campanha.setAutor(sergio);
        campanha.setChatId("chat-submetida");
        campanha.setStatus(StatusCampanha.AGUARDANDO_APROVACAO);

        given(simulacaoRepository.findFirstByChatIdOrderByIdAsc("chat-submetida")).willReturn(Optional.empty());
        given(campanhaRepository.findByChatId("chat-submetida")).willReturn(Optional.of(campanha));

        mockMvc.perform(post("/api/simulacoes").header("Authorization", autorizacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"chat-submetida\",\"regra\":{\"rule_id\":\"r1\"}}"))
                .andExpect(status().isConflict());

        verifyNoInteractions(persistenceService, jobService);
    }

    @Test
    void buscarSimulacaoPorIdRetorna200ComCamposDoContrato42() throws Exception {
        Campanha campanha = new Campanha();
        campanha.setId(12L);

        Simulacao simulacao = new Simulacao();
        simulacao.setId(345L);
        simulacao.setUsuario(sergio);
        simulacao.setChatId("c1f0a7e2");
        simulacao.setCampanha(campanha);
        simulacao.setStatus(StatusSimulacao.CONCLUIDA);
        simulacao.setEtapa(EtapaSimulacao.CONCLUIDA);
        simulacao.setFicha("{\"rule_id\":\"r1\"}");
        simulacao.setResultado("{\"totais\":{\"diferenca\":23736.10}}");
        simulacao.setDuracaoMs(1420L);
        simulacao.setCriadaEm(Instant.parse("2026-10-21T12:58:00Z"));
        simulacao.setConcluidaEm(Instant.parse("2026-10-21T12:59:00Z"));

        given(simulacaoRepository.findById(345L)).willReturn(Optional.of(simulacao));

        mockMvc.perform(get("/api/simulacoes/345").header("Authorization", autorizacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(345))
                .andExpect(jsonPath("$.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.etapa").value("Concluída"))
                .andExpect(jsonPath("$.chat_id").value("c1f0a7e2"))
                .andExpect(jsonPath("$.campanha_id").value(12))
                .andExpect(jsonPath("$.duracao_ms").value(1420))
                .andExpect(jsonPath("$.ficha.rule_id").value("r1"))
                .andExpect(jsonPath("$.resultado.totais.diferenca").value(23736.10))
                .andExpect(jsonPath("$.criada_em").value("2026-10-21T12:58:00Z"))
                .andExpect(jsonPath("$.concluida_em").value("2026-10-21T12:59:00Z"));
    }

    @Test
    void supervisorPodeConsultarSimulacaoDeOutroUsuarioSeCampanhaTiverVersaoSubmetida() throws Exception {
        Campanha campanha = new Campanha();
        campanha.setId(12L);
        campanha.setAutor(gabriela);

        Simulacao simulacao = new Simulacao();
        simulacao.setId(345L);
        simulacao.setUsuario(gabriela);
        simulacao.setChatId("chat-gabriela");
        simulacao.setCampanha(campanha);
        simulacao.setStatus(StatusSimulacao.CONCLUIDA);
        simulacao.setEtapa(EtapaSimulacao.CONCLUIDA);
        simulacao.setFicha("{\"rule_id\":\"r1\"}");

        given(simulacaoRepository.findById(345L)).willReturn(Optional.of(simulacao));
        given(campanhaVersaoRepository.existsByCampanhaId(12L)).willReturn(true);

        mockMvc.perform(get("/api/simulacoes/345").header("Authorization", autorizacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(345));

        given(campanhaVersaoRepository.existsByCampanhaId(12L)).willReturn(false);

        mockMvc.perform(get("/api/simulacoes/345").header("Authorization", autorizacao))
                .andExpect(status().isForbidden());
    }

    @Test
    void gerenteNaoConsultaSimulacaoDeOutroUsuarioMesmoComVersaoSubmetida() throws Exception {
        Campanha campanha = new Campanha();
        campanha.setId(12L);
        campanha.setAutor(sergio);

        Simulacao simulacao = new Simulacao();
        simulacao.setId(345L);
        simulacao.setUsuario(sergio);
        simulacao.setChatId("chat-sergio");
        simulacao.setCampanha(campanha);
        simulacao.setStatus(StatusSimulacao.CONCLUIDA);

        given(simulacaoRepository.findById(345L)).willReturn(Optional.of(simulacao));
        given(campanhaVersaoRepository.existsByCampanhaId(12L)).willReturn(true);

        mockMvc.perform(get("/api/simulacoes/345").header("Authorization", autorizacaoGabriela))
                .andExpect(status().isForbidden());
    }

    @Test
    void buscarSimulacaoInexistenteRetorna404() throws Exception {
        given(simulacaoRepository.findById(404L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/simulacoes/404").header("Authorization", autorizacao))
                .andExpect(status().isNotFound());
    }
}
