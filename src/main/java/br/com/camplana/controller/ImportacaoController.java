package br.com.camplana.controller;

import br.com.camplana.dto.importacao.HistoricoImportacaoResponse;
import br.com.camplana.dto.importacao.JobCriadoResponse;
import br.com.camplana.dto.importacao.JobStatusResponse;
import br.com.camplana.entity.Usuario;
import br.com.camplana.importacao.ImportacaoManagerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/importacao")
public class ImportacaoController {

    private final ImportacaoManagerService managerService;

    public ImportacaoController(ImportacaoManagerService managerService) {
        this.managerService = managerService;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<JobCriadoResponse> criarJob(
            @RequestParam("arquivos") List<MultipartFile> arquivos,
            @RequestParam(value = "modoCargaInicial", defaultValue = "false") boolean modoCargaInicial,
            @AuthenticationPrincipal Usuario usuario
    ) {
        JobCriadoResponse response = managerService.iniciarImportacao(arquivos, modoCargaInicial, usuario);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<JobStatusResponse> buscarJob(@PathVariable Long id) {
        return ResponseEntity.ok(managerService.buscarPorId(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<List<HistoricoImportacaoResponse>> listarHistorico() {
        return ResponseEntity.ok(managerService.listarHistorico());
    }
}
