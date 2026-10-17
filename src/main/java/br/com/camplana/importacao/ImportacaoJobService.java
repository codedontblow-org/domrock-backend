package br.com.camplana.importacao;

import br.com.camplana.dto.importacao.PendenciaImportacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.*;

@Service
public class ImportacaoJobService {

    private static final Logger log = LoggerFactory.getLogger(ImportacaoJobService.class);

    private final ImportacaoPersistenceService persistenceService;

    public ImportacaoJobService(ImportacaoPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Async
    public void processarImportacaoAsync(Long jobId, Map<String, byte[]> arquivosBytes, boolean modoCargaInicial) {
        log.info("Iniciando processamento assíncrono do job #{} na thread [{}]", jobId, Thread.currentThread().getName());        List<PendenciaImportacao> pendencias = new ArrayList<>();
        String competencia = null;

        try {
            Map<TipoArquivo, byte[]> mapaArquivos = new EnumMap<>(TipoArquivo.class);

            for (Map.Entry<String, byte[]> entry : arquivosBytes.entrySet()) {
                String nome = entry.getKey();
                byte[] bytes = entry.getValue();

                Optional<TipoArquivo> tipoOpt = DetectorTipoArquivo.detectar(nome);
                if (tipoOpt.isEmpty()) {
                    pendencias.add(new PendenciaImportacao(nome, 0, "arquivo", "Tipo de arquivo não reconhecido", PendenciaImportacao.IMPEDITIVO));
                    continue;
                }

                if (mapaArquivos.containsKey(tipoOpt.get())) {
                    pendencias.add(new PendenciaImportacao(
                        nome, 
                        0, 
                        "arquivo", 
                        "Mais de um arquivo detectado para a categoria: " + tipoOpt.get().name(), 
                        PendenciaImportacao.IMPEDITIVO
                    ));
                    continue;
                }

                mapaArquivos.put(tipoOpt.get(), bytes);

                if (competencia == null) {
                    YearMonth ym = CompetenciaExtractor.doNomeArquivo(nome);
                    if (ym != null) {
                        competencia = ym.toString();
                    }
                }
            }

            boolean temImpeditivo = pendencias.stream()
                    .anyMatch(p -> PendenciaImportacao.IMPEDITIVO.equalsIgnoreCase(p.severidade()));

            if (temImpeditivo && !modoCargaInicial) {
                persistenceService.finalizarJobComPendencias(jobId, competencia, 0, pendencias);
                return;
            }

            int totalLinhas = 0;
            int linhasImportadas = 0;
            Map<String, Integer> totais = new HashMap<>();

            for (Map.Entry<TipoArquivo, byte[]> entry : mapaArquivos.entrySet()) {
                totais.put(entry.getKey().name(), 0);
            }

            persistenceService.finalizarJobComSucesso(jobId, competencia, totalLinhas, linhasImportadas, totais, pendencias);
            log.info("Job #{} concluído com sucesso. Competência: {}", jobId, competencia);

        } catch (Exception ex) {
            log.error("Falha no processamento do job #{}", jobId, ex);
            persistenceService.finalizarJobComErro(jobId, ex.getMessage());
        }
    }
}
