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

/**
 * Simulação na Lana por HTTP (POST /simulacao). O gRPC do A6-51 cobre só o chat; a simulação
 * ficou em HTTP, e o JSON do contrato passa intacto, sem ser duplicado em classes Java.
 * Ex.: lanaSimulacaoClient.simular(Map.of("chat_id", "c1", "regra", regra))
 */
@Component
public class LanaSimulacaoClient {

    private static final String CAMINHO = "/simulacao";

    private final RestClient restClient;

    public LanaSimulacaoClient(RestClient.Builder builder, @Value("${lana.url}") String lanaUrl) {
        // Timeouts em application.properties (spring.http.clients.*).
        this.restClient = builder.baseUrl(lanaUrl).build();
    }

    public JsonNode simular(Object corpo) {
        try {
            return restClient.post()
                    .uri(CAMINHO)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            // Ex.: 422 com etapa e mensagem (regra incompleta, código gerado que falhou).
            throw new LanaRespostaException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            throw new LanaIndisponivelException("Lana indisponível em " + CAMINHO + ": " + e.getMessage());
        }
    }
}
