package br.com.camplana.client;

import br.com.camplana.exception.LanaIndisponivelException;
import br.com.camplana.exception.LanaRespostaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.function.Supplier;

/**
 * Cliente HTTP da Lana (agente de IA em FastAPI). O Spring é a porta única do front;
 * o JSON do contrato passa intacto, sem ser duplicado em classes Java.
 * Ex.: lanaClient.invocarAgente(Map.of("chat_id", "c1", "message", "oi"))
 */
@Component
public class LanaClient {

    private final RestClient restClient;

    public LanaClient(RestClient.Builder builder, @Value("${lana.url}") String lanaUrl) {
        // Timeouts em application.properties (spring.http.clients.*).
        this.restClient = builder.baseUrl(lanaUrl).build();
    }

    public JsonNode invocarAgente(Object corpo) {
        return postar("/agent/invoke", corpo);
    }

    public JsonNode simular(Object corpo) {
        return postar("/simulacao", corpo);
    }

    private JsonNode postar(String caminho, Object corpo) {
        return tratarFalhas(caminho, () -> restClient.post()
                .uri(caminho)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(JsonNode.class));
    }

    private JsonNode tratarFalhas(String caminho, Supplier<JsonNode> chamada) {
        try {
            return chamada.get();
        } catch (RestClientResponseException e) {
            throw new LanaRespostaException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            throw new LanaIndisponivelException("Lana indisponível em " + caminho + ": " + e.getMessage());
        }
    }
}
