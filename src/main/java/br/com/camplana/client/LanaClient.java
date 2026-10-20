package br.com.camplana.client;

import br.com.camplana.exception.LanaIndisponivelException;
import br.com.camplana.exception.LanaRespostaException;
import br.com.camplana.grpc.InvokeResponse;
import br.com.camplana.service.IaGrpcClientService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Chat com a Lana (agente de IA) por gRPC (A6-53). O Spring é a porta única do front e devolve
 * o mesmo contrato do antigo POST /agent/invoke: chat_id, response, events e regra.
 * A simulação continua em HTTP: {@link LanaSimulacaoClient}.
 * Ex.: lanaClient.invocarAgente(Map.of("chat_id", "c1", "message", "oi"))
 */
@Component
@RequiredArgsConstructor
public class LanaClient {

    private final IaGrpcClientService iaGrpcClientService;
    private final ObjectMapper objectMapper;

    public JsonNode invocarAgente(Object corpo) {
        JsonNode pedido = objectMapper.valueToTree(corpo);
        String chatId = pedido.hasNonNull("chat_id") ? pedido.get("chat_id").asString() : "default";
        String mensagem = pedido.hasNonNull("message") ? pedido.get("message").asString() : "";
        InvokeResponse resposta = chamar(mensagem, chatId, regraEmJson(pedido));
        return montarResposta(chatId, resposta);
    }

    // A ficha editada no painel vai junto, para o agente partir dela; sem ficha, "".
    private String regraEmJson(JsonNode pedido) {
        JsonNode regra = pedido.path("regra");
        return regra.isObject() ? objectMapper.writeValueAsString(regra) : "";
    }

    private InvokeResponse chamar(String mensagem, String chatId, String regraJson) {
        try {
            return iaGrpcClientService.chamarAgenteLana(mensagem, chatId, regraJson);
        } catch (StatusRuntimeException e) {
            throw traduzirFalha(e.getStatus());
        }
    }

    // Os mesmos status do antigo HTTP: cota da IA = 503 e demora = 504, com etapa e mensagem
    // para o front mostrar; qualquer outra falha = Lana indisponível (502).
    private RuntimeException traduzirFalha(Status status) {
        String detalhe = status.getDescription() == null ? "" : status.getDescription();
        if (status.getCode() == Status.Code.RESOURCE_EXHAUSTED) {
            return new LanaRespostaException(503, falhaEmJson("cota_llm", detalhe));
        }
        if (status.getCode() == Status.Code.DEADLINE_EXCEEDED) {
            return new LanaRespostaException(504, falhaEmJson("tempo", detalhe));
        }
        return new LanaIndisponivelException("Lana falhou no chat (gRPC " + status.getCode() + "): " + detalhe);
    }

    private String falhaEmJson(String etapa, String mensagem) {
        ObjectNode falha = objectMapper.createObjectNode();
        falha.put("etapa", etapa);
        falha.put("mensagem", mensagem);
        return objectMapper.writeValueAsString(falha);
    }

    private JsonNode montarResposta(String chatId, InvokeResponse resposta) {
        ObjectNode corpo = objectMapper.createObjectNode();
        corpo.put("chat_id", chatId);
        corpo.put("response", resposta.getResposta());
        corpo.set("events", lerJson(resposta.getEventsJson(), objectMapper.createArrayNode()));
        corpo.set("regra", lerJson(resposta.getRegraJson(), objectMapper.getNodeFactory().nullNode()));
        return corpo;
    }

    // Eventos e ficha viajam como texto JSON no .proto; vazio = não vieram (guardrail ou turno sem ficha nova).
    private JsonNode lerJson(String json, JsonNode seVazio) {
        return json.isEmpty() ? seVazio : objectMapper.readTree(json);
    }
}
