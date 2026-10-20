package br.com.camplana.dto.importacao;

public record PendenciaImportacao(
        String origem,
        Integer linha,
        String campo,
        String motivo,
        String severidade
) {
    public static final String IMPEDITIVO = "IMPEDITIVO";
    public static final String AVISO = "AVISO";
}
