package br.com.camplana.dto.campanha;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.Instant;

/** Ficha e resultado saem como a Lana gerou (ADR-002). */
public record UltimaSimulacaoResponse(
        long id,
        @JsonRawValue String regra,
        @JsonRawValue String resultado,
        @JsonProperty("concluida_em") Instant concluidaEm
) {}
