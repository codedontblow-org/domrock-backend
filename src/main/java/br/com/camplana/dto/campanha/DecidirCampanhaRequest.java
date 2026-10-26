package br.com.camplana.dto.campanha;

import jakarta.validation.constraints.NotNull;

/**
 * Contrato 5.8: {@code POST /api/campanhas/{id}/decidir}. {@code versao} é a que o Supervisor viu;
 * a justificativa é obrigatória ao reprovar e opcional ao aprovar.
 */
public record DecidirCampanhaRequest(
        @NotNull Integer versao,
        @NotNull Decisao decisao,
        String justificativa
) {

    public enum Decisao {
        APROVAR,
        REPROVAR
    }
}
