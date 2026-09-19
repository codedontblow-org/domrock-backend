package br.com.camplana.importacao;

import java.util.Locale;
import java.util.Optional;

public class DetectorTipoArquivo {

    public static Optional<TipoArquivo> detectar(String nomeArquivo) {
        if (nomeArquivo == null) return Optional.empty();
        String nome = nomeArquivo.toUpperCase(Locale.ROOT);

        if (nome.contains("COMMISS") || nome.contains("COMISS")) {
            return Optional.of(TipoArquivo.COMISSAO);
        }
        if (nome.contains("VENDAS")) {
            return Optional.of(TipoArquivo.VENDAS);
        }
        if (nome.contains("RH")) {
            return Optional.of(TipoArquivo.RH);
        }
        return Optional.empty();
    }
}