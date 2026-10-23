package br.com.camplana.dto.simulacao;

import br.com.camplana.entity.StatusSimulacao;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.Instant;

/** Estado atual da simulação consultada em GET /api/simulacoes/{id} (contrato 4.2). */
public record SimulacaoStatusResponse(
        Long id,
        StatusSimulacao status,
        String etapa,
        @JsonRawValue String ficha,
        @JsonRawValue String resultado,
        String erro,
        @JsonProperty("criada_em") Instant criadaEm,
        @JsonProperty("concluida_em") Instant concluidaEm
) {}
