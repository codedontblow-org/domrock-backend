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

/**
 * Executa a simulação em background (A6-66): avança pelas etapas, chama a Lana e grava o resultado
 * ou o erro legível.
 */
@Service
public class SimulacaoJobService {

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
            persistenceService.atualizarEtapa(simulacaoId, EtapaSimulacao.CARREGANDO_BASES);
            persistenceService.atualizarEtapa(simulacaoId, EtapaSimulacao.GERANDO_CODIGO);
            persistenceService.atualizarEtapa(simulacaoId, EtapaSimulacao.EXECUTANDO);

            JsonNode resposta = lanaSimulacaoClient.simular(payloadLana);

            persistenceService.atualizarEtapa(simulacaoId, EtapaSimulacao.CONFERINDO);

            String resultadoJson = extrairResultadoJson(resposta);
            String trilhaJson = extrairJsonOuNull(resposta, "trilha");
            String codigoGerado = extrairTexto(resposta, "codigo_gerado", "codigo");
            String modelo = extrairTexto(resposta, "modelo");
            Integer tentativas = extrairInteiro(resposta, "tentativas");
            Long duracaoMs = extrairLong(resposta, "duracao_ms", "duracao");
            if (duracaoMs == null) {
                duracaoMs = Math.max(0L, System.currentTimeMillis() - inicioMs);
            }

            persistenceService.concluirSimulacao(
                    simulacaoId,
                    resultadoJson,
                    trilhaJson,
                    codigoGerado,
                    modelo,
                    tentativas,
                    duracaoMs
            );
            log.info("Simulação #{} concluída com sucesso em {} ms", simulacaoId, duracaoMs);

        } catch (LanaRespostaException ex) {
            String mensagem = extrairMensagemLegivel(ex.getCorpo());
            log.warn("Simulação #{} falhou na Lana (HTTP {}): {}", simulacaoId, ex.getStatus(), mensagem);
            persistenceService.falharSimulacao(simulacaoId, mensagem);
        } catch (LanaIndisponivelException ex) {
            log.error("Simulação #{} falhou por indisponibilidade da Lana: {}", simulacaoId, ex.getMessage());
            persistenceService.falharSimulacao(simulacaoId, ex.getMessage());
        } catch (Exception ex) {
            String mensagem = ex.getMessage() != null && !ex.getMessage().isBlank()
                    ? ex.getMessage()
                    : "Erro inesperado ao processar a simulação.";
            log.error("Simulação #{} falhou com erro inesperado: {}", simulacaoId, mensagem, ex);
            persistenceService.falharSimulacao(simulacaoId, mensagem);
        }
    }

    private String extrairResultadoJson(JsonNode resposta) {
        if (resposta == null || resposta.isNull() || resposta.isMissingNode()) {
            return "{}";
        }
        JsonNode noResultado = resposta.path("resultado");
        if (noResultado.isObject() && noResultado.has("totais")) {
            return objectMapper.writeValueAsString(noResultado);
        }
        return objectMapper.writeValueAsString(resposta);
    }

    private String extrairJsonOuNull(JsonNode resposta, String campo) {
        if (resposta == null) {
            return null;
        }
        JsonNode node = resposta.path(campo);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return objectMapper.writeValueAsString(node);
    }

    private static String extrairTexto(JsonNode resposta, String... campos) {
        if (resposta == null) {
            return null;
        }
        for (String campo : campos) {
            JsonNode node = resposta.path(campo);
            if (!node.isMissingNode() && !node.isNull() && node.isString()) {
                return node.asString();
            }
        }
        return null;
    }

    private static Integer extrairInteiro(JsonNode resposta, String campo) {
        if (resposta == null) {
            return null;
        }
        JsonNode node = resposta.path(campo);
        if (!node.isMissingNode() && !node.isNull() && node.isNumber()) {
            return node.intValue();
        }
        return null;
    }

    private static Long extrairLong(JsonNode resposta, String... campos) {
        if (resposta == null) {
            return null;
        }
        for (String campo : campos) {
            JsonNode node = resposta.path(campo);
            if (!node.isMissingNode() && !node.isNull() && node.isNumber()) {
                return node.longValue();
            }
        }
        return null;
    }

    String extrairMensagemLegivel(String corpoErro) {
        if (corpoErro == null || corpoErro.isBlank()) {
            return "A Lana recusou a simulação sem detalhar o motivo.";
        }
        try {
            JsonNode json = objectMapper.readTree(corpoErro);
            for (String chave : new String[]{"mensagem", "message", "detail", "erro", "error"}) {
                JsonNode valor = json.path(chave);
                if (valor.isString() && !valor.asString().isBlank()) {
                    return valor.asString().strip();
                }
            }
        } catch (Exception ignorado) {
            // corpo não é JSON estruturado; usa texto limpo abaixo
        }
        return corpoErro.strip();
    }
}
