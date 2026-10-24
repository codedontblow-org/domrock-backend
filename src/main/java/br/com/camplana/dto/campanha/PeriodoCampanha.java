package br.com.camplana.dto.campanha;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public record PeriodoCampanha(
        @JsonProperty("data_inicio") LocalDate dataInicio,
        @JsonProperty("data_fim") LocalDate dataFim
) {}
