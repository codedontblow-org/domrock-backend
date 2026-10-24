package br.com.camplana.campanha;

import br.com.camplana.dto.campanha.PeriodoCampanha;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.NullNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Os poucos campos que a campanha lê da ficha e do resultado da Lana. O resto do JSON passa
 * intacto (ADR-002), então todo acoplamento ao formato da Lana fica aqui.
 *
 * <pre>campos.custoAdicional(simulacao.resultadoJson()); // 23736.10 na Black Friday</pre>
 */
@Component
public class CamposDaSimulacao {

    // As opções de marca do catálogo da Lana (contrato 2.1): "ALL" vale as seis.
    private static final String TODAS_AS_MARCAS = "ALL";
    private static final Set<String> MARCAS = Set.of("10", "20", "30", "40", "50", "60");

    private final ObjectMapper objectMapper;

    public CamposDaSimulacao(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** {@code parametros[key=periodo].value} da ficha (contrato 2.1). */
    public PeriodoCampanha periodo(String regraJson) {
        return lerPeriodo(objectMapper.readTree(regraJson)).orElseThrow(() -> new IllegalArgumentException(
                "Esperado parametros[key=periodo].value com data_inicio e data_fim na regra; recebido " + regraJson));
    }

    /**
     * Período, marcas e cargos da ficha (contrato 2.1). Vazio quando falta algum ou o período não fecha:
     * a ficha segue assim para a Lana, que devolve o 422 com os erros.
     *
     * <pre>campos.escopo(ficha); // Optional[24 a 30/11, marcas [10, 30], cargos [100, 200, 300]]</pre>
     */
    public Optional<EscopoCampanha> escopo(JsonNode regra) {
        Optional<PeriodoCampanha> periodo = lerPeriodo(regra).filter(p -> !p.dataInicio().isAfter(p.dataFim()));
        Set<String> marcas = codigos(parametro(regra, "marcas_alvo").path("value"));
        Set<String> cargos = codigos(parametro(regra, "cargos_alvo").path("value"));
        if (periodo.isEmpty() || marcas.isEmpty() || cargos.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new EscopoCampanha(periodo.get(), comTodasAsMarcas(marcas), cargos));
    }

    /** {@code parametros[key=pct_acrescimo].value} como veio; {@code null} se faltar, e a Lana recusa. */
    public JsonNode pctAcrescimo(JsonNode regra) {
        JsonNode valor = parametro(regra, "pct_acrescimo").path("value");
        return valor.isMissingNode() ? NullNode.getInstance() : valor;
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

    private Optional<PeriodoCampanha> lerPeriodo(JsonNode regra) {
        JsonNode valor = parametro(regra, "periodo").path("value");
        if (!valor.hasNonNull("data_inicio") || !valor.hasNonNull("data_fim")) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PeriodoCampanha(LocalDate.parse(valor.get("data_inicio").asString()),
                    LocalDate.parse(valor.get("data_fim").asString())));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Set<String> codigos(JsonNode valor) {
        Set<String> codigos = new TreeSet<>();
        for (JsonNode item : valor) {
            if ((item.isString() || item.isIntegralNumber()) && !item.asString().isBlank()) {
                codigos.add(item.asString());
            }
        }
        return codigos;
    }

    private static Set<String> comTodasAsMarcas(Set<String> marcas) {
        return marcas.contains(TODAS_AS_MARCAS) ? new TreeSet<>(MARCAS) : marcas;
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
