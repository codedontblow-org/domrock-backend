package br.com.camplana.campanha;

import java.util.Optional;

/**
 * O que a campanha precisa do registro de simulações (A6-66). Uma simulação é da campanha da sua
 * conversa: uma conversa, uma campanha (contrato 4.1).
 */
public interface RegistroSimulacoes {

    Optional<SimulacaoGravada> buscar(long simulacaoId);

    /** A que o submeter congela e a que aparece em {@code ultima_simulacao} (contrato 5.2). */
    Optional<SimulacaoGravada> ultimaConcluida(long campanhaId);

    /** Id de uma simulação PENDENTE ou RODANDO da campanha: o {@code em_simulacao} (contrato 5.1). */
    Optional<Long> emAndamento(long campanhaId);

    /** Ao salvar, as simulações da conversa passam a ser da campanha (contrato 5.3). */
    void vincularConversa(String chatId, long campanhaId);
}
