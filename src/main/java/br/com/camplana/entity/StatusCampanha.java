package br.com.camplana.entity;

/** Status gravados da campanha (contrato 5.1). Vigência e "em simulação" são calculados na leitura. */
public enum StatusCampanha {
    RASCUNHO,
    AGUARDANDO_APROVACAO,
    APROVADA,
    REPROVADA,
    ARQUIVADA
}
