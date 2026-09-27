package br.com.camplana.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.JsonNode;

/**
 * Mensagem do chat. `chat_id` identifica a conversa (thread do agente); `regra` é opcional e
 * leva os parâmetros como estão no painel, com as edições do usuário, para o agente partir deles.
 */
public record ChatRequest(
        @JsonProperty("chat_id") @NotBlank String chatId,
        @NotBlank String message,
        JsonNode regra
) {
}
