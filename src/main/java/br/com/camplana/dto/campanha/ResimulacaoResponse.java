package br.com.camplana.dto.campanha;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.Instant;

/** {@code resimulacao} da versão (contrato 5.6, rota 5.9). Preenchido pelo A6-68. */
public record ResimulacaoResponse(
        @JsonProperty("simulacao_id") long simulacaoId,
        UsuarioResumo por,
        Instant em,
        @JsonRawValue String resultado
) {}
