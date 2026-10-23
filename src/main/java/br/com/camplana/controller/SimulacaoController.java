package br.com.camplana.controller;

import br.com.camplana.dto.simulacao.SimulacaoCriadaResponse;
import br.com.camplana.dto.simulacao.SimulacaoStatusResponse;
import br.com.camplana.entity.Usuario;
import br.com.camplana.service.SimulacaoManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/** US4/US5 e A6-66: cria o job assíncrono de simulação e consulta seu status e resultado. */
@RestController
@RequestMapping("/api/simulacoes")
@RequiredArgsConstructor
public class SimulacaoController {

    private final SimulacaoManagerService managerService;

    @PostMapping
    public ResponseEntity<SimulacaoCriadaResponse> simular(@RequestBody JsonNode body,
                                                           @AuthenticationPrincipal Usuario usuario) {
        SimulacaoCriadaResponse criada = managerService.iniciarSimulacao(body, usuario);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(criada);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SimulacaoStatusResponse> buscarPorId(@PathVariable Long id,
                                                               @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(managerService.buscarPorId(id, usuario));
    }
}
