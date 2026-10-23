package br.com.camplana.entity;

import java.util.List;

/** Etapas da simulação exibidas durante o processamento (contrato 4.2). */
public final class EtapaSimulacao {

    public static final String CARREGANDO_BASES = "carregando bases";
    public static final String GERANDO_CODIGO = "gerando código";
    public static final String EXECUTANDO = "executando";
    public static final String CONFERINDO = "conferindo";

    public static final List<String> TODAS = List.of(
            CARREGANDO_BASES,
            GERANDO_CODIGO,
            EXECUTANDO,
            CONFERINDO
    );

    private EtapaSimulacao() {
    }
}
