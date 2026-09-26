package br.com.camplana.service;

import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

// Imports exatos gerados a partir do seu .proto:
import br.com.camplana.IAServiceGrpc;
import br.com.camplana.PromptRequest;
import br.com.camplana.PromptResponse;

@Service
public class IaGrpcClientService {

    @GrpcClient("iaService")
    private IAServiceGrpc.IAServiceBlockingStub iaStub;

    public String enviarMensagem(int usuarioId, String prompt, String contexto) {
        PromptRequest request = PromptRequest.newBuilder()
                .setUsuarioId(usuarioId)
                .setPrompt(prompt)
                .setContexto(contexto != null ? contexto : "")
                .build();

        PromptResponse response = this.iaStub.gerarResposta(request);
        return response.getResposta();
    }
}