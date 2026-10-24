package br.com.camplana.client;

import br.com.camplana.exception.LanaIndisponivelException;
import br.com.camplana.exception.LanaRespostaException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import tools.jackson.databind.JsonNode;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Simulação por HTTP. Regressão (merge do backend #6): simular() tinha sido desviado para o chat
 * por gRPC e devolvia uma saudação da Lana em vez do resultado da simulação.
 */
@RestClientTest(components = LanaSimulacaoClient.class, properties = "lana.url=http://lana.test")
class LanaSimulacaoClientTests {

    @Autowired
    private LanaSimulacaoClient lanaSimulacaoClient;

    @Autowired
    private MockRestServiceServer servidorLana;

    @Test
    void simularPostaARegraEmSimulacaoEDevolveOResultado() {
        servidorLana.expect(requestTo("http://lana.test/simulacao"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.regra.rule_id").value("r1"))
                .andRespond(withSuccess("{\"totais\":{\"diferenca\":23736.17}}", MediaType.APPLICATION_JSON));

        JsonNode resultado = lanaSimulacaoClient.simular(Map.of("chat_id", "c1", "regra", Map.of("rule_id", "r1")));

        assertThat(resultado.path("totais").path("diferenca").asDouble()).isEqualTo(23736.17);
    }

    @Test
    void simularPropagaStatusECorpoDoErroDaLana() {
        String erro = "{\"etapa\":\"validacao\",\"mensagem\":\"Regra incompleta\"}";
        servidorLana.expect(requestTo("http://lana.test/simulacao"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).body(erro).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> lanaSimulacaoClient.simular(Map.of("regra", Map.of())))
                .isInstanceOfSatisfying(LanaRespostaException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(422);
                    assertThat(e.getCorpo()).isEqualTo(erro);
                });
    }

    @Test
    void simularSemLanaNoArViraIndisponivel() {
        servidorLana.expect(requestTo("http://lana.test/simulacao"))
                .andRespond(withException(new ConnectException("Connection refused")));

        assertThatThrownBy(() -> lanaSimulacaoClient.simular(Map.of("regra", Map.of())))
                .isInstanceOf(LanaIndisponivelException.class)
                .hasMessageContaining("/simulacao");
    }

    /** O job da A6-66 reconhece o timeout por este texto; se a mensagem mudar, o ERRO "tempo" some. */
    @Test
    void simularComReadTimeoutLevaOReadTimedOutNaMensagem() {
        servidorLana.expect(requestTo("http://lana.test/simulacao"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> lanaSimulacaoClient.simular(Map.of("regra", Map.of())))
                .isInstanceOf(LanaIndisponivelException.class)
                .hasMessageContaining("Read timed out");
    }
}
