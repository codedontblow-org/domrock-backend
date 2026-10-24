package br.com.camplana.dto.campanha;

import br.com.camplana.campanha.AcaoCampanha;
import br.com.camplana.campanha.Vigencia;
import br.com.camplana.entity.StatusCampanha;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/** Objeto Campanha do contrato 5.2. */
public record CampanhaResponse(
        long id,
        String nome,
        StatusCampanha status,
        Vigencia vigencia,
        @JsonProperty("em_simulacao") boolean emSimulacao,
        List<AcaoCampanha> acoes,
        UsuarioResumo autor,
        @JsonProperty("chat_id") String chatId,
        PeriodoCampanha periodo,
        @JsonProperty("versao_atual") Integer versaoAtual,
        @JsonProperty("ultima_simulacao") UltimaSimulacaoResponse ultimaSimulacao,
        List<VersaoResumoResponse> versoes,
        List<LogStatusResponse> log,
        @JsonProperty("criada_em") Instant criadaEm,
        @JsonProperty("atualizada_em") Instant atualizadaEm
) {}
