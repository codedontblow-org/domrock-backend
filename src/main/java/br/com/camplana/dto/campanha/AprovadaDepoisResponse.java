package br.com.camplana.dto.campanha;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** Item de {@code aprovadas_depois} na versão (contrato 5.6): outra campanha do período aprovada depois. */
public record AprovadaDepoisResponse(
        @JsonProperty("campanha_id") long campanhaId,
        String nome,
        String aprovador,
        @JsonProperty("aprovada_em") Instant aprovadaEm
) {}
