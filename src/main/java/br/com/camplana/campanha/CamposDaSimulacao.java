package br.com.camplana.campanha;

import br.com.camplana.dto.campanha.PeriodoCampanha;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.MissingNode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Os poucos campos que a campanha lê da ficha e do resultado da Lana. O resto do JSON passa
 * intacto (ADR-002), então todo acoplamento ao formato da Lana fica aqui.
 *
 * <pre>campos.custoAdicional(simulacao.resultadoJson()); // 23736.10 na Black Friday</pre>
 */
@Component
public class CamposDaSimulacao {

    private final ObjectMapper objectMapper;

    public CamposDaSimulacao(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** {@code parametros[key=periodo].value} da ficha (contrato 2.1). */
    public PeriodoCampanha periodo(String regraJson) {
        JsonNode valor = parametro(objectMapper.readTree(regraJson), "periodo").path("value");
        if (!valor.hasNonNull("data_inicio") || !valor.hasNonNull("data_fim")) {
            throw new IllegalArgumentException("Esperado parametros[key=periodo].value com data_inicio "
                    + "e data_fim na regra; recebido " + regraJson);
        }
        return new PeriodoCampanha(LocalDate.parse(valor.get("data_inicio").asString()),
                LocalDate.parse(valor.get("data_fim").asString()));
    }

    /** {@code totais.diferenca} do resultado (contrato 2.3 e 5.4). */
    public BigDecimal custoAdicional(String resultadoJson) {
        JsonNode diferenca = objectMapper.readTree(resultadoJson).path("totais").path("diferenca");
        if (!diferenca.isNumber()) {
            throw new IllegalArgumentException(
                    "Esperado totais.diferenca numérico no resultado; recebido " + resultadoJson);
        }
        return diferenca.decimalValue();
    }

    /** Itens de {@code conflitos} no resultado; 0 quando a Lana ainda não manda o campo (A6-74). */
    public int quantidadeConflitos(String resultadoJson) {
        return objectMapper.readTree(resultadoJson).path("conflitos").size();
    }

    private JsonNode parametro(JsonNode regra, String chave) {
        for (JsonNode parametro : regra.path("parametros")) {
            if (chave.equals(parametro.path("key").asString())) {
                return parametro;
            }
        }
        return MissingNode.getInstance();
    }
}
