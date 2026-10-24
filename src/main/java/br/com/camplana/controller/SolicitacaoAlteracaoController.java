package br.com.camplana.controller;

import br.com.camplana.dto.correcao.DecisaoRequest;
import br.com.camplana.dto.correcao.SolicitacaoDetalheResponse;
import br.com.camplana.dto.correcao.SolicitacaoRequest;
import br.com.camplana.entity.Usuario;
import br.com.camplana.service.SolicitacaoAlteracaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Contrato: docs/contratos-sprint-2.md, seção 6. Permissões por perfil ficam no SecurityConfig. */
@RestController
@RequestMapping("/api/correcoes")
@RequiredArgsConstructor
public class SolicitacaoAlteracaoController {

    private final SolicitacaoAlteracaoService service;

    @PostMapping
    public ResponseEntity<SolicitacaoDetalheResponse> criarSolicitacao(
            @Valid @RequestBody SolicitacaoRequest request,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarSolicitacao(request, usuarioLogado));
    }

    @GetMapping("/fila")
    public ResponseEntity<List<SolicitacaoDetalheResponse>> listarFila(
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        return ResponseEntity.ok(service.listarFila(usuarioLogado));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SolicitacaoDetalheResponse> buscarPorId(
            @PathVariable Integer id,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        return ResponseEntity.ok(service.buscarPorId(id, usuarioLogado));
    }

    @PostMapping("/{id}/decidir")
    public ResponseEntity<SolicitacaoDetalheResponse> decidirSolicitacao(
            @PathVariable Integer id,
            @RequestBody DecisaoRequest decisao,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        return ResponseEntity.ok(service.decidirSolicitacao(id, decisao, usuarioLogado));
    }
}
