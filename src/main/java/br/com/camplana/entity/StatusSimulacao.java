package br.com.camplana.entity;

import java.util.List;

/** Status gravados da simulação (contrato 4.2). */
public enum StatusSimulacao {
    PENDENTE,
    RODANDO,
    CONCLUIDA,
    ERRO;

    /** Ainda sem resultado: o submeter (5.7) espera e a subida do backend encerra como ERRO. */
    public static final List<StatusSimulacao> EM_ANDAMENTO = List.of(PENDENTE, RODANDO);
}
