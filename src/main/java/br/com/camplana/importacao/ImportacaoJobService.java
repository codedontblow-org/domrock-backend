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
    private final LeitorXlsx leitorXlsx;
    private final ValidadorImportacaoService validador;

    public ImportacaoJobService(
            ImportacaoPersistenceService persistenceService,
            LeitorXlsx leitorXlsx,
            ValidadorImportacaoService validador
    ) {
        this.persistenceService = persistenceService;
        this.leitorXlsx = leitorXlsx;
        this.validador = validador;
    }

    @Async
    public void processarImportacaoAsync(Long jobId, Map<String, byte[]> arquivosBytes) {
        log.info("Iniciando processamento assíncrono do job #{} na thread [{}]", jobId, Thread.currentThread().getName());
        List<PendenciaImportacao> pendencias = new ArrayList<>();
        String competencia = null;

        try {
            Map<TipoArquivo, byte[]> mapaArquivos = new EnumMap<>(TipoArquivo.class);
            Map<TipoArquivo, String> nomesPorTipo = new EnumMap<>(TipoArquivo.class);

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
                nomesPorTipo.put(tipoOpt.get(), nome);

                if (competencia == null) {
                    YearMonth ym = CompetenciaExtractor.doNomeArquivo(nome);
                    if (ym != null) {
                        competencia = ym.toString();
                    }
                }
            }

            List<Map<String, String>> linhasRh = mapaArquivos.containsKey(TipoArquivo.RH)
                    ? leitorXlsx.ler(mapaArquivos.get(TipoArquivo.RH)) : Collections.emptyList();

            List<Map<String, String>> linhasVendas = mapaArquivos.containsKey(TipoArquivo.VENDAS)
                    ? leitorXlsx.ler(mapaArquivos.get(TipoArquivo.VENDAS)) : Collections.emptyList();

            List<Map<String, String>> linhasComissao = mapaArquivos.containsKey(TipoArquivo.COMISSAO)
                    ? leitorXlsx.ler(mapaArquivos.get(TipoArquivo.COMISSAO)) : Collections.emptyList();

            ValidadorImportacaoService.ResultadoValidacao resValidacao = validador.validarLote(
                    nomesPorTipo.getOrDefault(TipoArquivo.RH, "RH"), linhasRh,
                    nomesPorTipo.getOrDefault(TipoArquivo.VENDAS, "VENDAS"), linhasVendas,
                    nomesPorTipo.getOrDefault(TipoArquivo.COMISSAO, "COMISSAO"), linhasComissao
            );
            pendencias.addAll(resValidacao.pendencias());

            boolean temImpeditivo = pendencias.stream()
                    .anyMatch(p -> PendenciaImportacao.IMPEDITIVO.equalsIgnoreCase(p.severidade()));

            int totalLinhas = linhasRh.size() + linhasVendas.size() + linhasComissao.size();

            // Bloqueio se houver erro impeditivo de validação
            if (temImpeditivo) {
                persistenceService.finalizarJobComPendencias(jobId, competencia, totalLinhas, pendencias);
                log.warn("Job #{} rejeitado com pendências impeditivas. Nenhuma alteração gravada.", jobId);
                return;
            }

            // Gravação atômica: ou tudo é commitado ou sofre rollback completo
            ImportacaoPersistenceService.ResultadoCarga resultado = persistenceService.executarCargaAtomica(
                    nomesPorTipo.get(TipoArquivo.RH), linhasRh,
                    nomesPorTipo.get(TipoArquivo.VENDAS), linhasVendas,
                    linhasComissao
            );

            persistenceService.finalizarJobComSucesso(
                    jobId,
                    competencia,
                    totalLinhas,
                    resultado.totalGravado(),
                    resultado.totaisPorTipo(),
                    pendencias
            );
            log.info("Job #{} concluído com sucesso. Competência: {}, Total gravado: {}", jobId, competencia, resultado.totalGravado());

        } catch (Exception ex) {
            log.error("Falha no processamento do job #{}. Erro: {}", jobId, ex.getMessage(), ex);
            persistenceService.finalizarJobComErro(jobId, ex.getMessage());
        }
    }
}