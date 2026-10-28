package br.com.camplana.dto.campanha;

import br.com.camplana.campanha.AcaoVersao;
import br.com.camplana.entity.StatusCampanha;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.Instant;
import java.util.List;

/**
 * A versão congelada (contrato 5.6), com as {@code acoes} de quem está vendo, a re-simulação pendente
 * e as aprovações do período depois da submissão (A6-68).
 */
public record VersaoCampanhaResponse(
        @JsonProperty("campanha_id") long campanhaId,
        int numero,
        StatusCampanha status,
        List<AcaoVersao> acoes,
        UsuarioResumo autor,
        @JsonProperty("submetida_em") Instant submetidaEm,
        @JsonProperty("simulacao_id") long simulacaoId,
        @JsonRawValue String regra,
        @JsonRawValue String resultado,
        @JsonProperty("ultima_mensagem_id") Long ultimaMensagemId,
        @JsonProperty("resimulacao_necessaria") boolean resimulacaoNecessaria,
        @JsonProperty("aprovadas_depois") List<AprovadaDepoisResponse> aprovadasDepois,
        ResimulacaoResponse resimulacao,
        DecisaoResponse decisao,
        @JsonProperty("intercorrencias") List<IntercorrenciaResponse> intercorrencias
) {}
