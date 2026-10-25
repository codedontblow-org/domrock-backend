package br.com.camplana.dto.campanha;

import br.com.camplana.campanha.Vigencia;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

/**
 * Item de {@code GET /api/campanhas/aprovadas}: uma campanha aprovada, lida da versão aprovada.
 * {@code marcas} já vem com "ALL" trocado pelas seis marcas.
 */
public record CampanhaAprovadaResponse(
        long id,
        String nome,
        int versao,
        UsuarioResumo autor,
        PeriodoCampanha periodo,
        Vigencia vigencia,
        @JsonProperty("pct_acrescimo") JsonNode pctAcrescimo,
        List<String> marcas,
        List<String> cargos,
        @JsonProperty("custo_adicional") BigDecimal custoAdicional
) {}
