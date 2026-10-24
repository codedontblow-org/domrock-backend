package br.com.camplana.entity;

import java.util.List;

/** Etapas da simulação exibidas ao usuário (contrato 4.2). */
public final class EtapaSimulacao {

    public static final String NA_FILA = "Na fila";
    public static final String SIMULANDO = "Simulando";
    public static final String CONCLUIDA = "Concluída";
    public static final String ERRO = "Erro";

    public static final List<String> TODAS = List.of(
            NA_FILA,
            SIMULANDO,
            CONCLUIDA,
            ERRO
    );

    private EtapaSimulacao() {
    }
}
