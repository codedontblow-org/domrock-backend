package br.com.camplana.importacao;

import br.com.camplana.dto.importacao.PendenciaImportacao;
import br.com.camplana.entity.Importacao;
import br.com.camplana.entity.Usuario;
import br.com.camplana.repository.ImportacaoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class ImportacaoPersistenceService {

    private final ImportacaoRepository importacaoRepository;
    private final ObjectMapper objectMapper;

    public ImportacaoPersistenceService(ImportacaoRepository importacaoRepository) {
        this.importacaoRepository = importacaoRepository;
        this.objectMapper = new ObjectMapper();
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
        job.setCriadaEm(LocalDateTime.now());

        return importacaoRepository.save(job);
    }

    @Transactional
    public void atualizarProgresso(Long jobId, int linhasImportadas, int totalLinhas, Map<String, Integer> totais) {
        Importacao job = importacaoRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setLinhasImportadas(linhasImportadas);
            job.setTotalLinhas(totalLinhas);
            job.setTotais(toJson(totais));
            importacaoRepository.save(job);
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
            job.setConcluidaEm(LocalDateTime.now());
            importacaoRepository.save(job);
        }
    }

    @Transactional
    public void finalizarJobComErro(Long jobId, String mensagemErro) {
        Importacao job = importacaoRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setStatus("FALHA");
            job.setPendencias(toJson(List.of(new PendenciaImportacao("SISTEMA", 0, "GERAL", mensagemErro, PendenciaImportacao.IMPEDITIVO))));
            job.setConcluidaEm(LocalDateTime.now());
            importacaoRepository.save(job);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
