package br.com.camplana.dto.correcao;

import br.com.camplana.entity.OrigemSolicitacao;
import br.com.camplana.entity.TipoAlteracao;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SolicitacaoRequest(
        @JsonProperty("chat_id")
        @JsonAlias({"chatId", "chat_id"})
        String chatId,

        @NotNull(message = "Tipo de alteração é obrigatório")
        TipoAlteracao tipo,

        @NotBlank(message = "Matrícula é obrigatória")
        String matricula,

        @NotBlank(message = "Competência é obrigatória")
        String competencia,

        @JsonAlias("valor_antes")
        JsonNode antes,

        @NotNull(message = "Valor 'depois' é obrigatório")
        @JsonAlias("valor_depois")
        JsonNode depois,

        String motivo,

        String autor,

        @NotNull(message = "Origem da solicitação é obrigatória")
        OrigemSolicitacao origem
) {
}
