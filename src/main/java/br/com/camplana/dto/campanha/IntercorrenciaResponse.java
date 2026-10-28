package br.com.camplana.dto.campanha;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * Evento de RH que interfere no período e escopo de uma campanha.
 *
 * @param tipo afastamento, ferias, licenca_maternidade ou demissao
 * @param matricula identificador funcional do colaborador
 * @param cargo cod_cargo no primeiro mês do período da campanha em que ele está no escopo; pode ser null
 * @param dataInicio data de início do evento
 * @param dataFim data de término (null para demissão ou licença sem término)
 * @param origem fonte original do registro de evento
 */
public record IntercorrenciaResponse(
        String tipo,
        String matricula,
        String cargo,
        @JsonProperty("data_inicio") LocalDate dataInicio,
        @JsonProperty("data_fim") LocalDate dataFim,
        String origem
) {}
