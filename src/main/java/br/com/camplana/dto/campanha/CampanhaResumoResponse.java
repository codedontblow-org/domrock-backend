package br.com.camplana.dto.campanha;

import br.com.camplana.campanha.Vigencia;
import br.com.camplana.entity.StatusCampanha;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/** Item de "minhas campanhas" (contrato 5.4). */
public record CampanhaResumoResponse(
        long id,
        String nome,
        StatusCampanha status,
        Vigencia vigencia,
        @JsonProperty("em_simulacao") boolean emSimulacao,
        @JsonProperty("versao_atual") Integer versaoAtual,
        PeriodoCampanha periodo,
        @JsonProperty("custo_adicional") BigDecimal custoAdicional,
        @JsonProperty("atualizada_em") Instant atualizadaEm
) {}
