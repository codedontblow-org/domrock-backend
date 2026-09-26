package br.com.camplana.client;

import br.com.camplana.exception.LanaRespostaException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(components = LanaClient.class, properties = "lana.url=http://lana.test")
class LanaClientTests {

    @Autowired
    private LanaClient lanaClient;

    @Autowired
    private MockRestServiceServer servidorLana;

    @Test
    void invocarAgenteRepassaOCorpoEDevolveOJsonDaLana() {
        servidorLana.expect(requestTo("http://lana.test/agent/invoke"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chat_id").value("c1"))
                .andRespond(withSuccess("{\"response\":\"oi\",\"regra\":null}", MediaType.APPLICATION_JSON));

        JsonNode resposta = lanaClient.invocarAgente(Map.of("chat_id", "c1", "message", "oi"));

        assertThat(resposta.path("response").asString()).isEqualTo("oi");
    }

    @Test
    void simularPropagaStatusECorpoDoErroDaLana() {
        String erro = "{\"etapa\":\"validacao\",\"mensagem\":\"Regra incompleta\"}";
        servidorLana.expect(requestTo("http://lana.test/simulacao"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).body(erro).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> lanaClient.simular(Map.of("regra", Map.of())))
                .isInstanceOfSatisfying(LanaRespostaException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(422);
                    assertThat(e.getCorpo()).isEqualTo(erro);
                });
    }
}
