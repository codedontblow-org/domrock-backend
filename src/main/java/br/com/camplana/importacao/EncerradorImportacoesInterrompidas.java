package br.com.camplana.importacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Na subida do backend, encerra os jobs de importacao que ficaram PROCESSANDO no processo anterior,
 * para que o indice de "uma importacao por vez" nao bloqueie as proximas com 409.
 * Ex.: o container reinicia no meio da carga de jul/25; ao subir, o job vira FALHA e o Supervisor reenvia.
 */
@Component
public class EncerradorImportacoesInterrompidas {

    private static final Logger log = LoggerFactory.getLogger(EncerradorImportacoesInterrompidas.class);

    private final ImportacaoPersistenceService persistenceService;

    public EncerradorImportacoesInterrompidas(ImportacaoPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void encerrarAoSubir() {
        int encerrados = persistenceService.encerrarJobsInterrompidos();
        if (encerrados > 0) {
            log.warn("{} importacao(oes) interrompida(s) pelo reinicio marcada(s) como FALHA", encerrados);
        }
    }
}
