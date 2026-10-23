package br.com.camplana.dto.simulacao;

import br.com.camplana.entity.StatusSimulacao;

/** Resposta de criação do job de simulação (HTTP 202 Accepted, contrato 4.1). */
public record SimulacaoCriadaResponse(
        Long id,
        StatusSimulacao status
) {}
