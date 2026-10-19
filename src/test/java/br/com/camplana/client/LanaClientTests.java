package br.com.camplana.client;

import br.com.camplana.grpc.IAServiceGrpc;
import br.com.camplana.grpc.InvokeRequest;
import br.com.camplana.grpc.InvokeResponse;
import br.com.camplana.service.IaGrpcClientService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste de integração do LanaClient com os beans reais (LanaClient + IaGrpcClientService)
 * contra um servidor gRPC real e local, sem mocks e sem depender da Lana em Python.
 */
@SpringBootTest(
        classes = {
                LanaClient.class,
                IaGrpcClientService.class,
                LanaClientTests.JacksonTestConfig.class
        },
        properties = "grpc.lana.host=localhost")
class LanaClientTests {

    /** Último pedido recebido pelo servidor, para verificar o que o Java enviou. */
    private static final AtomicReference<InvokeRequest> ULTIMO_PEDIDO = new AtomicReference<>();

    private static Server servidorGrpc;

    @Configuration
    static class JacksonTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }

    /** Implementação real (não é mock) do serviço da Lana, só para o teste. */
    static class LanaFakeService extends IAServiceGrpc.IAServiceImplBase {
        @Override
        public void invokeAgent(InvokeRequest request, StreamObserver<InvokeResponse> observer) {
            ULTIMO_PEDIDO.set(request);

            if ("falhar".equals(request.getPrompt())) {
                observer.onError(Status.INTERNAL.withDescription("erro simulado").asRuntimeException());
                return;
            }
            observer.onNext(InvokeResponse.newBuilder()
                    .setResposta("Resposta da Lana para: " + request.getPrompt())
                    .build());
            observer.onCompleted();
        }
    }

    @BeforeAll
    static void iniciarServidorGrpc() throws IOException {
        // Porta 0 = o SO escolhe uma porta livre (evita conflito no CI)
        servidorGrpc = ServerBuilder.forPort(0)
                .addService(new LanaFakeService())
                .build()
                .start();
    }

    @AfterAll
    static void pararServidorGrpc() throws InterruptedException {
        if (servidorGrpc != null) {
            servidorGrpc.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /** Aponta o canal do IaGrpcClientService para o servidor local do teste. */
    @DynamicPropertySource
    static void propriedadesGrpc(DynamicPropertyRegistry registry) {
        registry.add("grpc.lana.port", () -> servidorGrpc.getPort());
    }

    @Autowired
    private LanaClient lanaClient;

    @Test
    void invocarAgenteRepassaOCorpoEDevolveOJsonDaLana() {
        JsonNode resultado = lanaClient.invocarAgente(
                Map.of("message", "Quanto vendi em dezembro?", "chat_id", "chat-123"));

        // O que o Java enviou por gRPC
        InvokeRequest enviado = ULTIMO_PEDIDO.get();
        assertThat(enviado.getPrompt()).isEqualTo("Quanto vendi em dezembro?");
        assertThat(enviado.getChatId()).isEqualTo("chat-123");

        // O contrato devolvido ao front (AgentResponse)
        assertThat(resultado.get("chat_id").asString()).isEqualTo("chat-123");
        assertThat(resultado.get("response").asString())
                .isEqualTo("Resposta da Lana para: Quanto vendi em dezembro?");
        assertThat(resultado.get("events").isArray()).isTrue();
        assertThat(resultado.get("events")).isEmpty();
        assertThat(resultado.get("regra").isNull()).isTrue();
    }

    @Test
    void invocarAgenteUsaChatIdPadraoQuandoAusente() {
        JsonNode resultado = lanaClient.invocarAgente(Map.of("message", "oi"));

        assertThat(ULTIMO_PEDIDO.get().getChatId()).isEqualTo("default");
        assertThat(resultado.get("chat_id").asString()).isEqualTo("default");
    }

    @Test
    void simularReaproveitaOFluxoGrpcDoAgente() {
        JsonNode resultado = lanaClient.simular(Map.of("message", "simular", "chat_id", "chat-sim"));

        assertThat(resultado.get("response").asString()).isEqualTo("Resposta da Lana para: simular");
    }

    @Test
    void invocarAgenteEncapsulaFalhasDoServidorGrpc() {
        assertThatThrownBy(() -> lanaClient.invocarAgente(Map.of("message", "falhar", "chat_id", "x")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Erro ao invocar a Lana via gRPC")
                .hasMessageContaining("INTERNAL");
    }
}