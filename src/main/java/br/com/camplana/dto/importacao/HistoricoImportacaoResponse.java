package br.com.camplana.dto.importacao;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record HistoricoImportacaoResponse(
    Long id,
    String status,
    String competencia,
    String usuario,
    @JsonProperty("total_linhas") int totalLinhas,
    @JsonProperty("linhas_importadas") int linhasImportadas,
    @JsonProperty("criada_em") 
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    LocalDateTime criadaEm,
    @JsonProperty("concluida_em") 
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    LocalDateTime concluidaEm
) {}
