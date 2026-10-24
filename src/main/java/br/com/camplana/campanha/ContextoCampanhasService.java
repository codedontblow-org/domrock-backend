package br.com.camplana.campanha;

import br.com.camplana.dto.campanha.PeriodoCampanha;
import br.com.camplana.entity.Campanha;
import br.com.camplana.entity.CampanhaStatusLog;
import br.com.camplana.entity.CampanhaVersao;
import br.com.camplana.entity.StatusCampanha;
import br.com.camplana.repository.CampanhaStatusLogRepository;
import br.com.camplana.repository.CampanhaVersaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Monta {@code campanhas} do POST /simulacao da Lana (contrato 4.3): as versões aprovadas e as que aguardam
 * aprovação que cruzam o período e ao menos uma marca × cargo da ficha, sem a da própria conversa.
 * Nenhum cálculo de comissão aqui (A6-74); as campanhas da especificação a Lana carrega sozinha.
 *
 * <pre>contexto.enriquecer(Map.of("chat_id", "c1", "regra", ficha)).path("campanhas"); // [] sem sobreposição</pre>
 */
@Service
public class ContextoCampanhasService {

    private static final List<StatusCampanha> APROVADA_OU_PENDENTE =
            List.of(StatusCampanha.APROVADA, StatusCampanha.AGUARDANDO_APROVACAO);

    private final CampanhaVersaoRepository versoes;
    private final CampanhaStatusLogRepository logs;
    private final CamposDaSimulacao campos;
    private final ObjectMapper json;

    public ContextoCampanhasService(CampanhaVersaoRepository versoes, CampanhaStatusLogRepository logs,
                                    CamposDaSimulacao campos, ObjectMapper json) {
        this.versoes = versoes;
        this.logs = logs;
        this.campos = campos;
        this.json = json;
    }

    /** Cópia do pedido com {@code campanhas} montado aqui: o que o navegador mandou nesse campo é descartado. */
    @Transactional(readOnly = true)
    public ObjectNode enriquecer(Object payload) {
        JsonNode recebido = json.valueToTree(payload);
        if (!recebido.isObject()) {
            throw new IllegalArgumentException("Esperado objeto com chat_id e regra no pedido à Lana; recebido " + recebido);
        }
        ObjectNode pedido = (ObjectNode) recebido.deepCopy();
        pedido.set("campanhas", sobrepostas(pedido.path("chat_id").asString(), pedido.path("regra")));
        return pedido;
    }

    private ArrayNode sobrepostas(String chatId, JsonNode regra) {
        ArrayNode campanhas = json.createArrayNode();
        // Ficha incompleta segue sem contexto: quem valida e devolve os erros (422) é a Lana.
        Optional<EscopoCampanha> daFicha = campos.escopo(regra);
        if (daFicha.isEmpty()) {
            return campanhas;
        }
        PeriodoCampanha periodo = daFicha.get().periodo();
        for (CampanhaVersao versao : versoes.findContextoSobreposto(
                periodo.dataInicio(), periodo.dataFim(), chatId, APROVADA_OU_PENDENTE)) {
            JsonNode ficha = json.readTree(versao.getRegra());
            EscopoCampanha daVersao = escopoSalvo(versao, ficha);
            if (daFicha.get().temMarcaECargoEmComum(daVersao)) {
                campanhas.add(item(versao, ficha, daVersao));
            }
        }
        return campanhas;
    }

    private EscopoCampanha escopoSalvo(CampanhaVersao versao, JsonNode ficha) {
        return campos.escopo(ficha).orElseThrow(() -> new IllegalStateException(
                "Versão " + versao.getId() + " salva sem período, marcas ou cargos na ficha; recebido " + ficha));
    }

    /** O item do contrato 4.3. Nesta sprint a ficha do chat só cria acréscimo. */
    private ObjectNode item(CampanhaVersao versao, JsonNode ficha, EscopoCampanha escopo) {
        Campanha campanha = versao.getCampanha();
        ObjectNode item = json.createObjectNode();
        item.put("campanha_id", campanha.getId());
        item.put("versao", versao.getNumero());
        item.put("tipo", "ACRESCIMO");
        item.put("nome", campanha.getNome());
        item.put("status", campanha.getStatus().name());
        item.put("autor", campanha.getAutor().getNome());
        preencherDecisao(item, aprovacao(versao));
        item.put("data_inicio", versao.getDataInicio().toString());
        item.put("data_fim", versao.getDataFim().toString());
        item.set("pct_acrescimo", campos.pctAcrescimo(ficha));
        item.set("marcas_alvo", json.valueToTree(escopo.marcas()));
        item.set("cargos_alvo", json.valueToTree(escopo.cargos()));
        return item;
    }

    private static void preencherDecisao(ObjectNode item, Optional<CampanhaStatusLog> decisao) {
        item.put("aprovador", decisao.map(log -> log.getUsuario().getNome()).orElse(null));
        item.put("aprovada_em", decisao.map(log -> log.getEm().toString()).orElse(null));
        item.put("justificativa", decisao.map(CampanhaStatusLog::getJustificativa).orElse(null));
    }

    /** Aprovador, data e justificativa vêm do log da mesma versão; aprovada sem esse registro para a simulação. */
    private Optional<CampanhaStatusLog> aprovacao(CampanhaVersao versao) {
        Campanha campanha = versao.getCampanha();
        if (campanha.getStatus() != StatusCampanha.APROVADA) {
            return Optional.empty();
        }
        return Optional.of(logs.findByCampanhaIdOrderByEmAscIdAsc(campanha.getId()).stream()
                .filter(log -> log.getVersao() != null && Objects.equals(log.getVersao().getId(), versao.getId()))
                .filter(log -> log.getPara() == StatusCampanha.APROVADA)
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Campanha " + campanha.getId() + " aprovada sem decisão da versão " + versao.getId())));
    }
}
