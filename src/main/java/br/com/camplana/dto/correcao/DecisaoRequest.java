package br.com.camplana.dto.correcao;

import com.fasterxml.jackson.annotation.JsonAlias;

public record DecisaoRequest(
        String decisao,

        @JsonAlias("motivo")
        String justificativa,

        Boolean aprovado,

        String motivo
) {
    public DecisaoRequest(String decisao, String justificativa) {
        this(decisao, justificativa, null, null);
    }

    public DecisaoRequest(Boolean aprovado, String motivo) {
        this(
                aprovado != null ? (aprovado ? "APROVAR" : "REPROVAR") : null,
                motivo,
                aprovado,
                motivo
        );
    }

    public Boolean isAprovado() {
        if (decisao != null && !decisao.isBlank()) {
            String upper = decisao.trim().toUpperCase();
            if ("APROVAR".equals(upper) || "APROVADA".equals(upper) || "APROVADO".equals(upper)) {
                return true;
            }
            if ("REPROVAR".equals(upper) || "REPROVADA".equals(upper) || "REPROVADO".equals(upper)) {
                return false;
            }
            return null;
        }
        return aprovado;
    }

    public String justificativaFinal() {
        if (justificativa != null) {
            return justificativa;
        }
        return motivo;
    }
}
