package br.com.camplana.dto.campanha;

import br.com.camplana.entity.StatusCampanha;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** Item de {@code versoes} na Campanha (contrato 5.2). */
public record VersaoResumoResponse(
        int numero,
        StatusCampanha status,
        @JsonProperty("submetida_em") Instant submetidaEm,
        DecisaoResponse decisao
) {}
