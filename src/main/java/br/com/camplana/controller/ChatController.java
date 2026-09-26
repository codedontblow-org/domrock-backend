package br.com.camplana.controller;

import br.com.camplana.client.LanaClient;
import br.com.camplana.dto.ChatRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** US1/US2: conversa com a Lana. A resposta traz `regra` quando o agente extraiu parâmetros. */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final LanaClient lanaClient;

    @PostMapping
    public ResponseEntity<JsonNode> conversar(@Valid @RequestBody ChatRequest body) {
        return ResponseEntity.ok(lanaClient.invocarAgente(body));
    }
}
