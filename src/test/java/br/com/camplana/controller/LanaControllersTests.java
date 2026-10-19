package br.com.camplana.controller;

import br.com.camplana.client.LanaClient;
import br.com.camplana.dto.ChatRequest;
import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.Usuario;
import br.com.camplana.exception.LanaIndisponivelException;
import br.com.camplana.exception.LanaRespostaException;
import br.com.camplana.repository.UsuarioRepository;
import br.com.camplana.security.JwtService;
import br.com.camplana.security.PasswordConfig;
import br.com.camplana.security.SecurityConfig;
import br.com.camplana.handler.SecurityErrorHandler;
import br.com.camplana.security.UsuarioDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {ChatController.class, SimulacaoController.class},
        properties = "api.security.token.secret=segredo-de-teste-com-mais-de-32-caracteres"
)

@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtService.class,
        UsuarioDetailsService.class, PasswordConfig.class})
class LanaControllersTests {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private LanaClient lanaClient;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    private String autorizacao;

    @BeforeEach
    void autenticarComoSupervisor() {
        var sergio = new Usuario();
        sergio.setId(2);
        sergio.setNome("Sérgio");
        sergio.setEmail("sergio@camplana.com.br");
        sergio.setPerfil(Perfil.SUPERVISOR);
        sergio.setAtivo(true);
        given(usuarioRepository.findByEmail(sergio.getEmail())).willReturn(Optional.of(sergio));
        autorizacao = "Bearer " + jwtService.gerarToken(sergio, jwtService.getExpirationDate());
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
    void simulacaoRecusaCorpoSemRegra() throws Exception {
        mockMvc.perform(post("/api/simulacoes").header("Authorization", autorizacao)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(lanaClient);
    }

    @Test
    void simulacaoRepassaOErro422DaLanaParaOFront() throws Exception {
        given(lanaClient.simular(any())).willThrow(
                new LanaRespostaException(422, "{\"etapa\":\"validacao\",\"mensagem\":\"Regra incompleta\"}"));

        mockMvc.perform(post("/api/simulacoes").header("Authorization", autorizacao)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"regra\":{}}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.etapa").value("validacao"));
    }

    @Test
    void simulacaoResponde502QuandoALanaEstaFora() throws Exception {
        given(lanaClient.simular(any())).willThrow(new LanaIndisponivelException("Lana indisponível"));

        mockMvc.perform(post("/api/simulacoes").header("Authorization", autorizacao)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"regra\":{}}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Lana indisponível"));
    }
}