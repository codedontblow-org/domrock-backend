package br.com.camplana.controller;

import br.com.camplana.service.ImportacaoService;
import br.com.camplana.service.ImportacaoService.ResultadoImportacaoArquivo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/importacao")
public class    ImportacaoController {

    private final ImportacaoService importacaoService;

    public ImportacaoController(ImportacaoService importacaoService) {
        this.importacaoService = importacaoService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<List<ResultadoImportacaoArquivo>> importar(
            @RequestParam("arquivos") List<MultipartFile> arquivos) {

        List<ResultadoImportacaoArquivo> resultado = importacaoService.importarLote(arquivos);
        return ResponseEntity.ok(resultado);
    }
}