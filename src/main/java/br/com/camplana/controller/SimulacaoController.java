package br.com.camplana.controller;

import br.com.camplana.client.LanaClient;
import br.com.camplana.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** US4/US5: gera o código da regra confirmada, executa sobre os dados e devolve os totais. */
@RestController
@RequestMapping("/api/simulacoes")
@RequiredArgsConstructor
public class SimulacaoController {

    private final LanaClient lanaClient;

    @PostMapping
    public ResponseEntity<JsonNode> simular(@RequestBody JsonNode body) {
        if (!body.path("regra").isObject()) {
            throw new BadRequestException("Esperado {\"regra\": {...}} no corpo; recebido " + body);
        }
        return ResponseEntity.ok(lanaClient.simular(body));
    }
}
