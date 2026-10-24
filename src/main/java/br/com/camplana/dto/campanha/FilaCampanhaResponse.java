package br.com.camplana.dto.campanha;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/** Item da fila do Supervisor (contrato 5.5). */
public record FilaCampanhaResponse(
        long id,
        String nome,
        int versao,
        UsuarioResumo autor,
        @JsonProperty("submetida_em") Instant submetidaEm,
        PeriodoCampanha periodo,
        @JsonProperty("custo_adicional") BigDecimal custoAdicional,
        int conflitos,
        @JsonProperty("resimulacao_necessaria") boolean resimulacaoNecessaria
) {}
