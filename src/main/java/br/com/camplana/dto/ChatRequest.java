package br.com.camplana.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** Mensagem do chat. `chat_id` identifica a conversa (thread do agente). */
public record ChatRequest(
        @JsonProperty("chat_id") @NotBlank String chatId,
        @NotBlank String message
) {
}
