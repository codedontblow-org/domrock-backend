package br.com.camplana.service;

import br.com.camplana.client.LanaSimulacaoClient;
import br.com.camplana.entity.EtapaSimulacao;
import br.com.camplana.exception.LanaIndisponivelException;
import br.com.camplana.exception.LanaRespostaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Executa a simulação em background (A6-66): marca "Simulando", chama a Lana e grava o resultado
 * ("Concluída") ou o objeto de erro ("Erro") conforme o contrato 4.2.
 */
@Service
public class SimulacaoJobService {

    public static final String ERRO_LANA_INDISPONIVEL =
            "{\"etapa\":\"lana_indisponivel\",\"mensagem\":\"Lana indisponível em /simulacao\"}";
    public static final String ERRO_TEMPO =
            "{\"etapa\":\"tempo\",\"mensagem\":\"Tempo limite excedido na simulação.\"}";
    public static final String ERRO_INTERNO =
            "{\"etapa\":\"erro_interno\",\"mensagem\":\"Erro inesperado ao processar a simulação.\"}";

    private static final Logger log = LoggerFactory.getLogger(SimulacaoJobService.class);

    private final SimulacaoPersistenceService persistenceService;
    private final LanaSimulacaoClient lanaSimulacaoClient;
    private final ObjectMapper objectMapper;

    public SimulacaoJobService(SimulacaoPersistenceService persistenceService,
                               LanaSimulacaoClient lanaSimulacaoClient,
                               ObjectMapper objectMapper) {
        this.persistenceService = persistenceService;
        this.lanaSimulacaoClient = lanaSimulacaoClient;
        this.objectMapper = objectMapper;
    }

    @Async
    public void processarSimulacaoAsync(Long simulacaoId, Object payloadLana) {
        log.info("Iniciando simulação #{} na thread [{}]", simulacaoId, Thread.currentThread().getName());
        long inicioMs = System.currentTimeMillis();

        try {
            persistenceService.atualizarEtapa(simulacaoId, EtapaSimulacao.SIMULANDO);
            JsonNode resposta = exigirObjeto(lanaSimulacaoClient.simular(payloadLana));
            concluir(simulacaoId, resposta, inicioMs);
        } catch (LanaRespostaException ex) {
            String erroJson = normalizarErroDaLana(ex.getCorpo());
            log.warn("Simulação #{} falhou na Lana (HTTP {}): {}", simulacaoId, ex.getStatus(), erroJson);
            persistenceService.falharSimulacao(simulacaoId, erroJson);
        } catch (LanaIndisponivelException ex) {
            String erroJson = ehTimeoutDeLeitura(ex) ? ERRO_TEMPO : ERRO_LANA_INDISPONIVEL;
            log.error("Simulação #{} falhou na chamada à Lana: {}", simulacaoId, ex.getMessage());
            persistenceService.falharSimulacao(simulacaoId, erroJson);
        } catch (Exception ex) {
            log.error("Simulação #{} falhou com erro inesperado: {}", simulacaoId, ex.getMessage(), ex);
            persistenceService.falharSimulacao(simulacaoId, ERRO_INTERNO);
        }
    }

    private void concluir(Long simulacaoId, JsonNode resposta, long inicioMs) {
        Long duracaoMs = extrairLong(resposta, "duracao_ms", "duracao");
        if (duracaoMs == null) {
            duracaoMs = Math.max(0L, System.currentTimeMillis() - inicioMs);
        }
        persistenceService.concluirSimulacao(
                simulacaoId,
                extrairResultadoJson(resposta),
                extrairJsonOuNull(resposta, "trilha"),
                extrairCodigo(resposta),
                extrairModelo(resposta),
                extrairTentativas(resposta),
                duracaoMs
        );
        log.info("Simulação #{} concluída com sucesso em {} ms", simulacaoId, duracaoMs);
    }

    /**
     * Um 200 sem objeto JSON não é resultado: gravar CONCLUIDA com {} faria o submeter (5.7) usar
     * uma simulação vazia como a última concluída. Ex.: exigirObjeto(null) lança IllegalStateException.
     */
    private static JsonNode exigirObjeto(JsonNode resposta) {
        if (resposta != null && resposta.isObject()) {
            return resposta;
        }
        throw new IllegalStateException(
                "Lana respondeu 200 em /simulacao sem o objeto do resultado (2.3); recebido: " + resposta);
    }

    private String extrairResultadoJson(JsonNode resposta) {
        JsonNode noResultado = resposta.path("resultado");
        if (noResultado.isObject() && noResultado.has("totais")) {
            return objectMapper.writeValueAsString(noResultado);
        }
        return objectMapper.writeValueAsString(resposta);
    }

    private String extrairJsonOuNull(JsonNode resposta, String campo) {
        JsonNode node = resposta.path(campo);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return objectMapper.writeValueAsString(node);
    }

    private static String extrairCodigo(JsonNode resposta) {
        JsonNode codigo = resposta.path("codigo");
        if (codigo.isString()) {
            return codigo.asString();
        }
        JsonNode codigoGerado = resposta.path("codigo_gerado");
        if (codigoGerado.isString()) {
            return codigoGerado.asString();
        }
        return null;
    }

    private static String extrairModelo(JsonNode resposta) {
        JsonNode modeloTrilha = resposta.path("trilha").path("modelo");
        if (modeloTrilha.isString()) {
            return modeloTrilha.asString();
        }
        JsonNode modeloRaiz = resposta.path("modelo");
        if (modeloRaiz.isString()) {
            return modeloRaiz.asString();
        }
        return null;
    }

    private static Integer extrairTentativas(JsonNode resposta) {
        JsonNode tentativasTrilha = resposta.path("trilha").path("tentativas");
        if (tentativasTrilha.isNumber()) {
            return tentativasTrilha.intValue();
        }
        JsonNode tentativasRaiz = resposta.path("tentativas");
        if (tentativasRaiz.isNumber()) {
            return tentativasRaiz.intValue();
        }
        return null;
    }

    private static Long extrairLong(JsonNode resposta, String... campos) {
        for (String campo : campos) {
            JsonNode node = resposta.path(campo);
            if (!node.isMissingNode() && !node.isNull() && node.isNumber()) {
                return node.longValue();
            }
        }
        return null;
    }

    /**
     * Só o read timeout é "tempo": a Lana recebeu o pedido e não respondeu dentro do
     * spring.http.clients.read-timeout. Com factory=simple (HttpURLConnection) a mensagem é
     * "Read timed out"; "Connect timed out" é Lana fora do alcance e fica lana_indisponivel.
     */
    private static boolean ehTimeoutDeLeitura(Throwable erro) {
        for (Throwable atual = erro; atual != null; atual = atual.getCause()) {
            String msg = atual.getMessage();
            if (msg != null && msg.toLowerCase(Locale.ROOT).contains("read timed out")) {
                return true;
            }
        }
        return false;
    }

    String normalizarErroDaLana(String corpoErro) {
        if (corpoErro != null && !corpoErro.isBlank()) {
            try {
                JsonNode json = objectMapper.readTree(corpoErro);
                if (json.isObject()) {
                    return objectMapper.writeValueAsString(json);
                }
            } catch (Exception ignorado) {
                // cai no fallback abaixo
            }
        }
        Map<String, String> fallback = new LinkedHashMap<>();
        fallback.put("etapa", "validacao");
        fallback.put("mensagem", corpoErro != null && !corpoErro.isBlank()
                ? corpoErro.strip()
                : "A Lana recusou a simulação.");
        return objectMapper.writeValueAsString(fallback);
    }
}
