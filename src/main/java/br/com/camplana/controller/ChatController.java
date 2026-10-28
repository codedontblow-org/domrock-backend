package br.com.camplana.controller;

import br.com.camplana.dto.ChatRequest;
import br.com.camplana.entity.Usuario;
import br.com.camplana.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/** US1/US2: conversa com a Lana e histórico de mensagens. */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<JsonNode> conversar(@Valid @RequestBody ChatRequest body,
                                              @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(chatService.conversar(body, usuario));
    }

    @GetMapping("/{chat_id}/mensagens")
    public ResponseEntity<JsonNode> buscarMensagens(@PathVariable("chat_id") String chatId,
                                                    @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(chatService.buscarMensagens(chatId, usuario));
    }
}
