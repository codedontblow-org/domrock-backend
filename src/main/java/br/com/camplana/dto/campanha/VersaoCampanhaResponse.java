package br.com.camplana.dto.campanha;

import br.com.camplana.entity.StatusCampanha;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.Instant;
import java.util.List;

/**
 * A versão congelada (contrato 5.6). {@code acoes}, {@code resimulacao_necessaria},
 * {@code aprovadas_depois} e {@code resimulacao} são do A6-68; até lá saem vazios.
 */
public record VersaoCampanhaResponse(
        @JsonProperty("campanha_id") long campanhaId,
        int numero,
        StatusCampanha status,
        List<String> acoes,
        UsuarioResumo autor,
        @JsonProperty("submetida_em") Instant submetidaEm,
        @JsonProperty("simulacao_id") long simulacaoId,
        @JsonRawValue String regra,
        @JsonRawValue String resultado,
        @JsonProperty("ultima_mensagem_id") Long ultimaMensagemId,
        @JsonProperty("resimulacao_necessaria") boolean resimulacaoNecessaria,
        @JsonProperty("aprovadas_depois") List<AprovadaDepoisResponse> aprovadasDepois,
        ResimulacaoResponse resimulacao,
        DecisaoResponse decisao
) {}
