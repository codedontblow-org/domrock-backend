package br.com.camplana.importacao;

import br.com.camplana.dto.importacao.PendenciaImportacao;
import br.com.camplana.entity.Importacao;
import br.com.camplana.entity.Usuario;
import br.com.camplana.repository.ImportacaoRepository;
import br.com.camplana.service.ImportadorComissaoService;
import br.com.camplana.service.ImportadorRhService;
import br.com.camplana.service.ImportadorVendasService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImportacaoPersistenceService {

    private final ImportacaoRepository importacaoRepository;
    private final ObjectMapper objectMapper;
    private final ImportadorRhService importadorRh;
    private final ImportadorVendasService importadorVendas;
    private final ImportadorComissaoService importadorComissao;

    public ImportacaoPersistenceService(
            ImportacaoRepository importacaoRepository,
            ImportadorRhService importadorRh,
            ImportadorVendasService importadorVendas,
            ImportadorComissaoService importadorComissao,
            ObjectMapper objectMapper
    ) {
        this.importacaoRepository = importacaoRepository;
        this.importadorRh = importadorRh;
        this.objectMapper = objectMapper;
        this.importadorVendas = importadorVendas;
        this.importadorComissao = importadorComissao;
    }

    static final String MOTIVO_INTERROMPIDA =
            "Importação interrompida porque o backend reiniciou durante o processamento; envie as planilhas de novo.";

    public record ResultadoCarga(int totalGravado, Map<String, Integer> totaisPorTipo) {}

    @Transactional
    public ResultadoCarga executarCargaAtomica(
            String nomeRh, List<Map<String, String>> linhasRh,
            String nomeVendas, List<Map<String, String>> linhasVendas,
            List<Map<String, String>> linhasComissao
    ) {
        int gravadasRh = 0;
        if (linhasRh != null && !linhasRh.isEmpty()) {
            gravadasRh = importadorRh.importar(nomeRh, linhasRh);
        }

        int gravadasVendas = 0;
        if (linhasVendas != null && !linhasVendas.isEmpty()) {
            gravadasVendas = importadorVendas.importar(nomeVendas, linhasVendas);
        }

        int gravadasComissao = 0;
        if (linhasComissao != null && !linhasComissao.isEmpty()) {
            gravadasComissao = importadorComissao.importar(linhasComissao);
        }

        Map<String, Integer> totais = new HashMap<>();
        totais.put("RH", gravadasRh);
        totais.put("VENDAS", gravadasVendas);
        totais.put("COMISSAO", gravadasComissao);

        return new ResultadoCarga(gravadasRh + gravadasVendas + gravadasComissao, totais);
    }

    @Transactional
    public Importacao criarJobInicial(List<String> arquivos, Usuario usuario) {
        Importacao job = new Importacao();
        job.setStatus("PROCESSANDO");
        job.setUsuario(usuario);
        job.setArquivos(toJson(arquivos));
        job.setTotais(toJson(Collections.emptyMap()));
        job.setPendencias(toJson(Collections.emptyList()));
        job.setTotalLinhas(0);
        job.setLinhasImportadas(0);
        job.setCriadaEm(LocalDateTime.now(ZoneOffset.UTC));

        try {
            return importacaoRepository.saveAndFlush(job);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma importação em andamento.");
        }
    }

    @Transactional
    public void finalizarJobComSucesso(Long jobId, String competencia, int totalLinhas, int linhasImportadas,
                                       Map<String, Integer> totais, List<PendenciaImportacao> pendencias) {
        finalizarJob(jobId, "CONCLUIDO", competencia, totalLinhas, linhasImportadas, totais, pendencias);
    }

    @Transactional
    public void finalizarJobComPendencias(Long jobId, String competencia, int totalLinhas, List<PendenciaImportacao> pendencias) {
        finalizarJob(jobId, "PENDENTE", competencia, totalLinhas, 0, Collections.emptyMap(), pendencias);
    }

    @Transactional
    public void finalizarJob(Long jobId, String status, String competencia, int totalLinhas, int linhasImportadas,
                             Map<String, Integer> totais, List<PendenciaImportacao> pendencias) {
        Importacao job = importacaoRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setStatus(status);
            job.setCompetencia(competencia);
            job.setTotalLinhas(totalLinhas);
            job.setLinhasImportadas(linhasImportadas);
            job.setTotais(toJson(totais));
            job.setPendencias(toJson(pendencias));
            job.setConcluidaEm(LocalDateTime.now(ZoneOffset.UTC));
            importacaoRepository.save(job);
        }
    }

    @Transactional
    public void finalizarJobComErro(Long jobId, String mensagemErro) {
        importacaoRepository.findById(jobId).ifPresent(job -> marcarFalha(job, mensagemErro));
    }

    /**
     * Encerra como FALHA os jobs que ficaram PROCESSANDO porque o backend parou no meio da carga
     * (o @Async morre com o processo). Sem isso, o indice uk_importacao_em_andamento devolveria
     * 409 a toda importacao nova. Supoe uma unica instancia do backend, como no compose do projeto.
     * Ex.: chamado na subida por EncerradorImportacoesInterrompidas.
     */
    @Transactional
    public int encerrarJobsInterrompidos() {
        List<Importacao> interrompidos = importacaoRepository.findByStatus("PROCESSANDO");
        interrompidos.forEach(job -> marcarFalha(job, MOTIVO_INTERROMPIDA));
        return interrompidos.size();
    }

    private void marcarFalha(Importacao job, String mensagemErro) {
        job.setStatus("FALHA");
        job.setErro(mensagemErro);
        job.setPendencias(toJson(List.of(new PendenciaImportacao("SISTEMA", 0, "GERAL", mensagemErro, PendenciaImportacao.IMPEDITIVO))));
        job.setConcluidaEm(LocalDateTime.now(ZoneOffset.UTC));
        importacaoRepository.save(job);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JacksonException e) {
            return "[]";
        }
    }
}