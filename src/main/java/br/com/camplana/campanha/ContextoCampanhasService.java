package br.com.camplana.campanha;

import br.com.camplana.entity.CampanhaStatusLog;
import br.com.camplana.entity.CampanhaVersao;
import br.com.camplana.entity.StatusCampanha;
import br.com.camplana.exception.BadRequestException;
import br.com.camplana.repository.CampanhaStatusLogRepository;
import br.com.camplana.repository.CampanhaVersaoRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Contexto confiável para a Lana: seleciona fontes; nenhum cálculo de comissão é feito aqui. */
@Service
public class ContextoCampanhasService {
    private static final List<StatusCampanha> STATUS = List.of(
            StatusCampanha.APROVADA, StatusCampanha.AGUARDANDO_APROVACAO);
    private static final Set<String> MARCAS = Set.of("10", "20", "30", "40", "50", "60");
    private final CampanhaVersaoRepository versoes;
    private final CampanhaStatusLogRepository logs;
    private final ObjectMapper json;
    private final JsonNode especificacao;

    public ContextoCampanhasService(CampanhaVersaoRepository versoes, CampanhaStatusLogRepository logs,
                                    ObjectMapper json) throws IOException {
        this.versoes = versoes;
        this.logs = logs;
        this.json = json;
        try (var entrada = new ClassPathResource("campanhas/especificacao-2025.json").getInputStream()) {
            this.especificacao = json.readTree(entrada);
        }
    }

    @Transactional(readOnly = true)
    public ObjectNode enriquecer(Object payload) {
        JsonNode recebido = json.valueToTree(payload);
        if (!recebido.isObject()) {
            throw new BadRequestException("Corpo da simulação inválido");
        }
        ObjectNode pedido = (ObjectNode) recebido.deepCopy();
        JsonNode regra = pedido.path("regra");
        var periodo = periodo(regra);
        Set<String> marcas = codigos(parametro(regra, "marcas_alvo"), true);
        Set<String> cargos = codigos(parametro(regra, "cargos_alvo"), false);
        ArrayNode campanhas = json.createArrayNode();
        String chatId = pedido.path("chat_id").asString();
        for (CampanhaVersao versao : versoes.findContextoSobreposto(periodo.get(0), periodo.get(1), chatId, STATUS)) {
            JsonNode ficha = json.readTree(versao.getRegra());
            Set<String> marcasFonte = codigos(parametro(ficha, "marcas_alvo"), true);
            Set<String> cargosFonte = codigos(parametro(ficha, "cargos_alvo"), false);
            if (intersecta(marcas, marcasFonte) && intersecta(cargos, cargosFonte)) {
                campanhas.add(daVersao(versao, ficha, marcasFonte, cargosFonte));
            }
        }
        for (JsonNode fonte : especificacao) {
            LocalDate inicio = LocalDate.parse(fonte.path("data_inicio").asString());
            LocalDate fim = LocalDate.parse(fonte.path("data_fim").asString());
            if (!inicio.isAfter(periodo.get(1)) && !fim.isBefore(periodo.get(0))
                    && intersecta(marcas, codigos(fonte.path("marcas_alvo"), true))
                    && intersecta(cargos, codigos(fonte.path("cargos_alvo"), false))) {
                campanhas.add(fonte.deepCopy());
            }
        }
        // Nunca aceita campanhas/decisões fornecidas pelo navegador.
        pedido.set("campanhas", campanhas);
        return pedido;
    }

    private ObjectNode daVersao(CampanhaVersao versao, JsonNode ficha, Set<String> marcas, Set<String> cargos) {
        var campanha = versao.getCampanha();
        JsonNode percentual = parametro(ficha, "pct_acrescimo");
        if (!(percentual.isNumber() || percentual.isString())) {
            throw new BadRequestException("Campanha " + campanha.getId() + " sem pct_acrescimo numérico");
        }
        ObjectNode fonte = json.createObjectNode();
        fonte.put("id", "campanha-" + campanha.getId() + "-v" + versao.getNumero());
        fonte.put("nome", campanha.getNome());
        fonte.put("tipo", "ACRESCIMO");
        fonte.put("status", campanha.getStatus().name());
        fonte.put("data_inicio", versao.getDataInicio().toString());
        fonte.put("data_fim", versao.getDataFim().toString());
        fonte.set("marcas_alvo", json.valueToTree(marcas.stream().sorted().toList()));
        fonte.set("cargos_alvo", json.valueToTree(cargos.stream().sorted().toList()));
        fonte.set("percentual", percentual);
        fonte.put("autor", campanha.getAutor().getNome());
        fonte.put("origem", "CAMPANHA");
        if (campanha.getStatus() == StatusCampanha.APROVADA) {
            CampanhaStatusLog decisao = logs.findByCampanhaIdOrderByEmAscIdAsc(campanha.getId()).stream()
                    .filter(l -> l.getVersao() != null && Objects.equals(l.getVersao().getId(), versao.getId()))
                    .filter(l -> l.getPara() == StatusCampanha.APROVADA)
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "Campanha aprovada sem decisão da versão " + versao.getId()));
            fonte.put("aprovador", decisao.getUsuario().getNome());
            fonte.put("aprovada_em", decisao.getEm().toString());
            fonte.put("justificativa", decisao.getJustificativa());
        }
        return fonte;
    }

    private List<LocalDate> periodo(JsonNode regra) {
        JsonNode valor = parametro(regra, "periodo");
        try {
            LocalDate inicio = LocalDate.parse(valor.path("data_inicio").asString());
            LocalDate fim = LocalDate.parse(valor.path("data_fim").asString());
            if (inicio.isAfter(fim)) throw new IllegalArgumentException();
            return List.of(inicio, fim);
        } catch (RuntimeException e) {
            throw new BadRequestException("Período da campanha inválido: informe data_inicio e data_fim em ordem");
        }
    }

    private static JsonNode parametro(JsonNode regra, String chave) {
        for (JsonNode campo : regra.path("parametros")) {
            if (chave.equals(campo.path("key").asString())) return campo.path("value");
        }
        throw new BadRequestException("Parâmetro obrigatório ausente: " + chave);
    }

    private static Set<String> codigos(JsonNode valor, boolean marcas) {
        List<JsonNode> valores = new ArrayList<>();
        if (valor.isArray()) valor.forEach(valores::add);
        else valores.add(valor);
        Set<String> codigos = new LinkedHashSet<>();
        for (JsonNode item : valores) {
            if (!(item.isString() || item.isIntegralNumber()) || codigo(item).isBlank()) {
                throw new BadRequestException("Marcas e cargos devem conter códigos válidos");
            }
            codigos.add(codigo(item));
        }
        if (codigos.isEmpty()) throw new BadRequestException("Marcas e cargos são obrigatórios");
        return marcas && codigos.contains("ALL") ? MARCAS : codigos;
    }

    private static String codigo(JsonNode item) {
        return item.isString() ? item.asString() : item.bigIntegerValue().toString();
    }

    private static boolean intersecta(Set<String> a, Set<String> b) {
        return a.stream().anyMatch(b::contains);
    }
}
