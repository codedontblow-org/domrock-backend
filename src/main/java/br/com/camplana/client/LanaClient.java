package br.com.camplana.client;

import br.com.camplana.service.IaGrpcClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Cliente gRPC da Lana (agente de IA). O Spring é a porta única do front;
 * as chamadas são delegadas para o microsserviço Python via gRPC.
 */
@Component
@RequiredArgsConstructor
public class LanaClient {

    private final IaGrpcClientService iaGrpcClientService;
    private final ObjectMapper objectMapper;

    public JsonNode invocarAgente(Object corpo) {
        try {
            // Converte o corpo recebido do controller para JsonNode para extrair os campos
            JsonNode node = objectMapper.valueToTree(corpo);

            String prompt = node.hasNonNull("message") ? node.get("message").asString() : "";
            String chatId = node.hasNonNull("chat_id") ? node.get("chat_id").asString() : "default";

            String respostaIa = iaGrpcClientService.chamarAgenteLana(prompt, chatId);

            // Mesmo contrato do endpoint HTTP antigo (AgentResponse) que o front espera
            ObjectNode resposta = objectMapper.createObjectNode();
            resposta.put("chat_id", chatId);
            resposta.put("response", respostaIa);
            resposta.putArray("events");   // vazio por enquanto (o proto ainda não carrega)
            resposta.putNull("regra");     // idem
            return resposta;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao invocar a Lana via gRPC: " + e.getMessage(), e);
        }
    }

    public JsonNode simular(Object corpo) {
        // Redireciona a simulação para o mesmo fluxo gRPC do agente
        return invocarAgente(corpo);
    }
}