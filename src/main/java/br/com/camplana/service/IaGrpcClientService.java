package br.com.camplana.service;

import br.com.camplana.grpc.IAServiceGrpc;
import br.com.camplana.grpc.InvokeRequest;
import br.com.camplana.grpc.InvokeResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class IaGrpcClientService {

    private final ManagedChannel channel;
    private final IAServiceGrpc.IAServiceBlockingStub blockingStub;

    public IaGrpcClientService(
            @Value("${grpc.lana.host}") String host,
            @Value("${grpc.lana.port}") int port) {

        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext() // Necessário para ambiente de desenvolvimento local (sem TLS)
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .idleTimeout(5, TimeUnit.MINUTES)
                .enableRetry()
                .build();
        this.blockingStub = IAServiceGrpc.newBlockingStub(channel);
    }

    public String chamarAgenteLana(String prompt, String chatId) {
        InvokeRequest request = InvokeRequest.newBuilder()
                .setPrompt(prompt)
                .setChatId(chatId)
                .build();

        InvokeResponse response = this.blockingStub
                .withDeadlineAfter(2, TimeUnit.MINUTES)
                .withWaitForReady()
                .invokeAgent(request);
        return response.getResposta();
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}