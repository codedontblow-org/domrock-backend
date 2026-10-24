package br.com.camplana.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Na subida do backend, marca como ERRO as simulações que ficaram PENDENTE ou RODANDO no processo
 * anterior, evitando que fiquem travadas indefinidamente (A6-66).
 */
@Component
public class EncerradorSimulacoesInterrompidas {

    private static final Logger log = LoggerFactory.getLogger(EncerradorSimulacoesInterrompidas.class);

    private final SimulacaoPersistenceService persistenceService;

    public EncerradorSimulacoesInterrompidas(SimulacaoPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void encerrarAoSubir() {
        int encerradas = persistenceService.encerrarSimulacoesInterrompidas();
        if (encerradas > 0) {
            log.warn("{} simulacao(oes) interrompida(s) pelo reinicio marcada(s) como ERRO", encerradas);
        }
    }
}
