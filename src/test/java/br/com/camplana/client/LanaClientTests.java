package br.com.camplana.client;

import br.com.camplana.exception.LanaIndisponivelException;
import br.com.camplana.exception.LanaRespostaException;
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
 * Regressão (merge do backend #6): a ficha e os eventos não passavam pelo gRPC e o painel do
 * front nunca abria; estes testes cobram os dois sentidos.
 */
@SpringBootTest(
        classes = {
                LanaClient.class,
                IaGrpcClientService.class,
                LanaClientTests.JacksonTestConfig.class
        },
        properties = "grpc.lana.host=localhost")
class LanaClientTests {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private static final String REGRA_JSON = "{\"rule_id\":\"bf-1\",\"faltantes\":[]}";
    private static final String EVENTOS_JSON = "[{\"type\":\"tool_call\",\"tool\":\"registrar_parametros_regra\"}]";
    private static final String TRILHA_JSON = "{\"modelo\":\"gemini\",\"passos\":[{\"ordem\":1,\"ferramenta\":\"consultar_banco\"}]}";

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

    /** Implementação real (não é mock) do serviço da Lana, só para o teste; o prompt escolhe o cenário. */
    static class LanaFakeService extends IAServiceGrpc.IAServiceImplBase {
        @Override
        public void invokeAgent(InvokeRequest request, StreamObserver<InvokeResponse> observer) {
            ULTIMO_PEDIDO.set(request);

            switch (request.getPrompt()) {
                case "falhar" -> observer.onError(Status.INTERNAL.withDescription("erro simulado").asRuntimeException());
                case "cota" -> observer.onError(Status.RESOURCE_EXHAUSTED.withDescription("Aguarde 1 minuto").asRuntimeException());
                case "demorar" -> observer.onError(Status.DEADLINE_EXCEEDED.withDescription("A Lana demorou demais").asRuntimeException());
                case "registrar campanha" -> responder(observer, InvokeResponse.newBuilder()
                        .setResposta("Registrei a campanha.").setEventsJson(EVENTOS_JSON).setRegraJson(REGRA_JSON).build());
                case "consultar vendas" -> responder(observer, InvokeResponse.newBuilder()
                        .setResposta("A marca 30 vendeu R$ 333.666,01.").setTrilhaJson(TRILHA_JSON).build());
                default -> responder(observer, InvokeResponse.newBuilder()
                        .setResposta("Resposta da Lana para: " + request.getPrompt()).build());
            }
        }

        private static void responder(StreamObserver<InvokeResponse> observer, InvokeResponse resposta) {
            observer.onNext(resposta);
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
    void invocarAgenteDevolveAFichaEOsEventosDoTurno() {
        JsonNode resultado = lanaClient.invocarAgente(Map.of("message", "registrar campanha", "chat_id", "chat-1"));

        assertThat(resultado.get("response").asString()).isEqualTo("Registrei a campanha.");
        assertThat(resultado.get("regra").get("rule_id").asString()).isEqualTo("bf-1");
        assertThat(resultado.get("events")).hasSize(1);
        assertThat(resultado.get("events").get(0).get("tool").asString()).isEqualTo("registrar_parametros_regra");
    }

    @Test
    void invocarAgenteEnviaAFichaDoPainel() {
        lanaClient.invocarAgente(Map.of("message", "so a marca 30", "chat_id", "chat-1",
                "regra", Map.of("rule_id", "r1")));

        InvokeRequest enviado = ULTIMO_PEDIDO.get();
        assertThat(enviado.getPrompt()).isEqualTo("so a marca 30");
        assertThat(JSON.readTree(enviado.getRegraJson()).get("rule_id").asString()).isEqualTo("r1");
    }

    @Test
    void turnoSemFichaNovaDevolveRegraNulaEEventosVazios() {
        JsonNode resultado = lanaClient.invocarAgente(
                Map.of("message", "Quanto vendi em dezembro?", "chat_id", "chat-123"));

        assertThat(ULTIMO_PEDIDO.get().getRegraJson()).isEmpty();
        assertThat(resultado.get("chat_id").asString()).isEqualTo("chat-123");
        assertThat(resultado.get("response").asString())
                .isEqualTo("Resposta da Lana para: Quanto vendi em dezembro?");
        assertThat(resultado.get("events").isArray()).isTrue();
        assertThat(resultado.get("events")).isEmpty();
        assertThat(resultado.get("regra").isNull()).isTrue();
        assertThat(resultado.get("trilha").isNull()).isTrue();
    }

    @Test
    void invocarAgenteDevolveATrilhaDoTurno() {
        JsonNode resultado = lanaClient.invocarAgente(Map.of("message", "consultar vendas", "chat_id", "chat-1"));

        assertThat(resultado.get("trilha").get("passos").get(0).get("ferramenta").asString()).isEqualTo("consultar_banco");
    }

    @Test
    void invocarAgenteUsaChatIdPadraoQuandoAusente() {
        JsonNode resultado = lanaClient.invocarAgente(Map.of("message", "oi"));

        assertThat(ULTIMO_PEDIDO.get().getChatId()).isEqualTo("default");
        assertThat(resultado.get("chat_id").asString()).isEqualTo("default");
    }

    @Test
    void cotaDaIaVira503ComEtapaEMensagemParaOFront() {
        assertThatThrownBy(() -> lanaClient.invocarAgente(Map.of("message", "cota", "chat_id", "x")))
                .isInstanceOfSatisfying(LanaRespostaException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(503);
                    assertThat(JSON.readTree(e.getCorpo()).get("etapa").asString()).isEqualTo("cota_llm");
                    assertThat(JSON.readTree(e.getCorpo()).get("mensagem").asString()).isEqualTo("Aguarde 1 minuto");
                });
    }

    @Test
    void demoraDaLanaVira504() {
        assertThatThrownBy(() -> lanaClient.invocarAgente(Map.of("message", "demorar", "chat_id", "x")))
                .isInstanceOfSatisfying(LanaRespostaException.class, e -> assertThat(e.getStatus()).isEqualTo(504));
    }

    @Test
    void outraFalhaDoServidorGrpcViraLanaIndisponivel() {
        assertThatThrownBy(() -> lanaClient.invocarAgente(Map.of("message", "falhar", "chat_id", "x")))
                .isInstanceOf(LanaIndisponivelException.class)
                .hasMessageContaining("INTERNAL")
                .hasMessageContaining("erro simulado");
    }
}
