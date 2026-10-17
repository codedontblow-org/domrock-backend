package br.com.camplana.dto.importacao;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record JobStatusResponse(
    Long id,
    String status,
    String competencia,
    String usuario,
    @JsonProperty("total_linhas") int totalLinhas,
    @JsonProperty("linhas_importadas") int linhasImportadas,
    Map<String, Integer> totais,
    List<PendenciaImportacao> pendencias,
    @JsonProperty("criada_em") 
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    LocalDateTime criadaEm,
    @JsonProperty("concluida_em") 
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    LocalDateTime concluidaEm
) {}
