package br.com.camplana.service;

import org.springframework.stereotype.Service;
import net.devh.boot.grpc.client.inject.GrpcClient;

// Nomes atualizados de acordo com o arquivo .proto
import br.com.camplana.IAServiceGrpc;
import br.com.camplana.PromptRequest;
import br.com.camplana.PromptResponse;

@Service
public class IAClientService {

    @GrpcClient("iaService")
    private IAServiceGrpc.IAServiceBlockingStub iaServiceStub;

    public String callAI(String prompt) {
        PromptRequest request = PromptRequest.newBuilder()
                .setUsuarioId(1) // Pode passar o ID do usuário
                .setPrompt(prompt)
                .setContexto("")
                .build();

        // Método chamado no gRPC conforme o proto: GerarResposta
        PromptResponse response = this.iaServiceStub.gerarResposta(request);
        return response.getResposta();
    }
}