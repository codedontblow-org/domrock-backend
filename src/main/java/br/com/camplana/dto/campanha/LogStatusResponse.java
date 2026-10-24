package br.com.camplana.dto.campanha;

import br.com.camplana.entity.StatusCampanha;

import java.time.Instant;

/** Item de {@code log} na Campanha (contrato 5.2). {@code versao} é o número, não o id. */
public record LogStatusResponse(
        StatusCampanha de,
        StatusCampanha para,
        Integer versao,
        UsuarioResumo usuario,
        Instant em,
        String justificativa
) {}
