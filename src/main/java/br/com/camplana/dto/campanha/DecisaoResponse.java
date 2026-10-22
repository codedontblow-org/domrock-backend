package br.com.camplana.dto.campanha;

import br.com.camplana.entity.StatusCampanha;

import java.time.Instant;

/** @param decisao APROVADA ou REPROVADA (contrato 5.2 e 5.6) */
public record DecisaoResponse(StatusCampanha decisao, UsuarioResumo por, Instant em, String justificativa) {}
