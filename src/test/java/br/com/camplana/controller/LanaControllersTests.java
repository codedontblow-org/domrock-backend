package br.com.camplana.controller;

import br.com.camplana.client.LanaClient;
import br.com.camplana.dto.ChatRequest;
import br.com.camplana.exception.LanaIndisponivelException;
import br.com.camplana.exception.LanaRespostaException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ChatController.class, SimulacaoController.class})
class LanaControllersTests {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LanaClient lanaClient;

    @Test
    void chatDevolveARespostaDaLana() throws Exception {
        given(lanaClient.invocarAgente(any())).willReturn(JSON.readTree("{\"response\":\"oi\"}"));

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"c1\",\"message\":\"oi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("oi"));
    }

    @Test
    void chatRepassaARegraDoPainelParaALana() throws Exception {
        given(lanaClient.invocarAgente(any())).willReturn(JSON.readTree("{\"response\":\"ok\"}"));

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"c1\",\"message\":\"so a marca 30\",\"regra\":{\"rule_id\":\"r1\"}}"))
                .andExpect(status().isOk());

        then(lanaClient).should().invocarAgente(argThat(corpo ->
                corpo instanceof ChatRequest pedido && "r1".equals(pedido.regra().path("rule_id").asString())));
    }

    @Test
    void chatRecusaMensagemVaziaSemChamarALana() throws Exception {
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"c1\",\"message\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(lanaClient);
    }

    @Test
    void simulacaoRecusaCorpoSemRegra() throws Exception {
        mockMvc.perform(post("/api/simulacoes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(lanaClient);
    }

    @Test
    void simulacaoRepassaOErro422DaLanaParaOFront() throws Exception {
        given(lanaClient.simular(any())).willThrow(
                new LanaRespostaException(422, "{\"etapa\":\"validacao\",\"mensagem\":\"Regra incompleta\"}"));

        mockMvc.perform(post("/api/simulacoes").contentType(MediaType.APPLICATION_JSON).content("{\"regra\":{}}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.etapa").value("validacao"));
    }

    @Test
    void simulacaoResponde502QuandoALanaEstaFora() throws Exception {
        given(lanaClient.simular(any())).willThrow(new LanaIndisponivelException("Lana indisponível"));

        mockMvc.perform(post("/api/simulacoes").contentType(MediaType.APPLICATION_JSON).content("{\"regra\":{}}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Lana indisponível"));
    }
}
