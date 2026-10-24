package br.com.camplana.dto.correcao;

import br.com.camplana.entity.OrigemSolicitacao;
import br.com.camplana.entity.StatusSolicitacao;
import br.com.camplana.entity.TipoAlteracao;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record SolicitacaoDetalheResponse(
        Integer id,
        TipoAlteracao tipo,
        String matricula,
        String competencia,
        String antes,
        String depois,
        String motivo,
        OrigemSolicitacao origem,
        @JsonProperty("chat_id")
        String chatId,
        StatusSolicitacao status,
        UsuarioResumo autor,
        @JsonProperty("criada_em")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        LocalDateTime criadaEm,
        DecisaoResumo decisao,
        @JsonProperty("descricao_origem")
        String descricaoOrigem
) {
    public record UsuarioResumo(
            Integer id,
            String nome
    ) {}

    public record DecisaoResumo(
            String decisao,
            UsuarioResumo por,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            LocalDateTime em,
            String justificativa
    ) {}
}
