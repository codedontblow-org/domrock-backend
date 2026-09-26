package br.com.camplana.controller;

import br.com.camplana.service.IaGrpcClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat")
public class IAController {

    private final IaGrpcClientService iaGrpcClientService;

    public IAController(IaGrpcClientService iaGrpcClientService) {
        this.iaGrpcClientService = iaGrpcClientService;
    }

    @PostMapping
    public ResponseEntity<ChatResponseDto> chat(@RequestBody ChatRequestDto request) {
        String respostaIa = iaGrpcClientService.enviarMensagem(
                request.usuarioId(),
                request.prompt(),
                request.contexto());
        return ResponseEntity.ok(new ChatResponseDto(respostaIa));
    }

    public record ChatRequestDto(int usuarioId, String prompt, String contexto) {
    }

    public record ChatResponseDto(String resposta) {
    }
}