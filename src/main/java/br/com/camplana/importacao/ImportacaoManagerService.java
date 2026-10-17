package br.com.camplana.importacao;

import br.com.camplana.dto.importacao.HistoricoImportacaoResponse;
import br.com.camplana.dto.importacao.JobCriadoResponse;
import br.com.camplana.dto.importacao.JobStatusResponse;
import br.com.camplana.dto.importacao.PendenciaImportacao;
import br.com.camplana.entity.Importacao;
import br.com.camplana.entity.Usuario;
import br.com.camplana.repository.ImportacaoRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;

@Service
public class ImportacaoManagerService {

    private static final Logger log = LoggerFactory.getLogger(ImportacaoManagerService.class);
    
    private final ImportacaoRepository importacaoRepository;
    private final ImportacaoPersistenceService persistenceService;
    private final ImportacaoJobService jobService;
    private final ObjectMapper objectMapper;

    public ImportacaoManagerService(
            ImportacaoRepository importacaoRepository,
            ImportacaoPersistenceService persistenceService,
            ImportacaoJobService jobService,
            ObjectMapper objectMapper
    ) {
        this.importacaoRepository = importacaoRepository;
        this.persistenceService = persistenceService;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    public JobCriadoResponse iniciarImportacao(List<MultipartFile> arquivos, boolean modoCargaInicial, Usuario usuario) {
        if (importacaoRepository.existsByStatus("PROCESSANDO")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma importação em andamento.");
        }

        if (arquivos == null || arquivos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nenhum arquivo enviado para importação.");
        }

        Map<String, byte[]> arquivosBytes = new HashMap<>();
        List<String> nomesArquivos = new ArrayList<>();
        Set<String> nomesVerificados = new HashSet<>();

        for (MultipartFile file : arquivos) {
            String nomeOriginal = file.getOriginalFilename();

            if (nomeOriginal == null || nomeOriginal.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo enviado sem nome válido.");
            }

            // Validação 1: Extensão obrigatória .xlsx
            if (!nomeOriginal.toLowerCase().endsWith(".xlsx")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Formato de arquivo inválido: " + nomeOriginal + ". Apenas planilhas .xlsx são permitidas.");
            }

            // Validação 2: Bloqueio de duplicados
            if (nomesVerificados.contains(nomeOriginal.toLowerCase())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Arquivo duplicado detectado no envio: " + nomeOriginal);
            }
            nomesVerificados.add(nomeOriginal.toLowerCase());

            try {
                arquivosBytes.put(nomeOriginal, file.getBytes());
                nomesArquivos.add(nomeOriginal);
            } catch (IOException e) {
                log.error("Erro de I/O ao ler bytes do arquivo {}", nomeOriginal, e);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Erro ao ler arquivo: " + nomeOriginal);
            }
        }

        Importacao job = persistenceService.criarJobInicial(nomesArquivos, usuario);
        jobService.processarImportacaoAsync(job.getId(), arquivosBytes, modoCargaInicial);

        return new JobCriadoResponse(job.getId(), job.getStatus());
    }

    @Transactional(readOnly = true)
    public JobStatusResponse buscarPorId(Long id) {
        Importacao job = importacaoRepository.findByIdWithUsuario(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job de importação não encontrado com ID: " + id));

        List<PendenciaImportacao> pendencias = parseJson(job.getPendencias(), new TypeReference<>() {});
        Map<String, Integer> totais = parseJson(job.getTotais(), new TypeReference<>() {});

        String nomeUsuario = job.getUsuario() != null ? job.getUsuario().getNome() : null;

        return new JobStatusResponse(
                job.getId(),
                job.getStatus(),
                job.getCompetencia(),
                nomeUsuario,
                job.getTotalLinhas(),
                job.getLinhasImportadas(),
                totais,
                pendencias,
                job.getCriadaEm(),
                job.getConcluidaEm()
        );
    }

    @Transactional(readOnly = true)
    public List<HistoricoImportacaoResponse> listarHistorico() {
        return importacaoRepository.findAllOrderByCriadaEmDesc().stream()
                .map(job -> new HistoricoImportacaoResponse(
                        job.getId(),
                        job.getStatus(),
                        job.getCompetencia(),
                        job.getUsuario() != null ? job.getUsuario().getNome() : null,
                        job.getTotalLinhas(),
                        job.getLinhasImportadas(),
                        job.getCriadaEm(),
                        job.getConcluidaEm()
                ))
                .toList();
    }

    private <T> T parseJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.warn("Falha ao desserializar JSON do job: {}", e.getMessage());
            return null;
        }
    }
}
