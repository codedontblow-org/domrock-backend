package br.com.camplana.campanha;

import br.com.camplana.entity.CampanhaStatusLog;
import br.com.camplana.entity.Simulacao;

import java.time.Instant;
import java.util.List;

/**
 * O que pesa na aprovação de uma versão (contrato 5.6).
 *
 * @param aprovadasDepois aprovações de outras campanhas do período depois da submissão, da mais antiga
 *                        para a mais nova; vazia se a versão já foi decidida
 * @param resimulacao     última re-simulação concluída da versão (5.9), ou {@code null}
 */
public record PendenciasDaVersao(List<CampanhaStatusLog> aprovadasDepois, Simulacao resimulacao) {

    /**
     * {@code true} quando a última dessas aprovações é mais nova que a re-simulação: a % vigente mudou
     * depois do número que está na tela.
     */
    public boolean resimulacaoNecessaria() {
        if (aprovadasDepois.isEmpty()) {
            return false;
        }
        Instant ultimaAprovacao = aprovadasDepois.get(aprovadasDepois.size() - 1).getEm();
        return resimulacao == null || !resimulacao.getCriadaEm().isAfter(ultimaAprovacao);
    }
}
