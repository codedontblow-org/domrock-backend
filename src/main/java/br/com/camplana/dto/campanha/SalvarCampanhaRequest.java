package br.com.camplana.dto.campanha;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contrato 5.3. O nome é validado no serviço, junto com o do renomear. */
public record SalvarCampanhaRequest(
        String nome,
        @JsonProperty("chat_id") @NotBlank @Size(max = 64) String chatId,
        @JsonProperty("simulacao_id") @NotNull Long simulacaoId
) {}
