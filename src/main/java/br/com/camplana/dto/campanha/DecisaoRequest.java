package br.com.camplana.dto.campanha;

import br.com.camplana.entity.StatusCampanha;
import jakarta.validation.constraints.NotNull;

/**
 * Decisão de um supervisor sobre uma versão. Aceita só APROVADA ou REPROVADA;
 * a validação do valor e da justificativa fica no service (mensagens específicas).
 */
public record DecisaoRequest(
        @NotNull StatusCampanha decisao,
        String justificativa
) {
}