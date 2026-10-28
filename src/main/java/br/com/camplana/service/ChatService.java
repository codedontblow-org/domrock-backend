package br.com.camplana.service;

import br.com.camplana.client.LanaClient;
import br.com.camplana.dto.ChatRequest;
import br.com.camplana.entity.Campanha;
import br.com.camplana.entity.MensagemChat;
import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.Usuario;
import br.com.camplana.exception.ForbiddenException;
import br.com.camplana.exception.NotFoundException;
import br.com.camplana.repository.CampanhaRepository;
import br.com.camplana.repository.CampanhaVersaoRepository;
import br.com.camplana.repository.MensagemChatRepository;
import br.com.camplana.repository.SimulacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Chat com a Lana e histórico de mensagens (contrato 3.1, 3.2 e 3.3).
 * A mensagem do usuário e a resposta da Lana são salvas juntas numa transação;
 * se a Lana falhar, nada é gravado.
 */
@Service
public class ChatService {

    private final LanaClient lanaClient;
    private final MensagemChatRepository mensagemChatRepository;
    private final SimulacaoRepository simulacaoRepository;
    private final CampanhaRepository campanhaRepository;
    private final CampanhaVersaoRepository campanhaVersaoRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final Clock relogio;

    public ChatService(LanaClient lanaClient,
                       MensagemChatRepository mensagemChatRepository,
                       SimulacaoRepository simulacaoRepository,
                       CampanhaRepository campanhaRepository,
                       CampanhaVersaoRepository campanhaVersaoRepository,
                       TransactionTemplate transactionTemplate,
                       ObjectMapper objectMapper,
                       Optional<Clock> relogio) {
        this.lanaClient = lanaClient;
        this.mensagemChatRepository = mensagemChatRepository;
        this.simulacaoRepository = simulacaoRepository;
        this.campanhaRepository = campanhaRepository;
        this.campanhaVersaoRepository = campanhaVersaoRepository;
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "TransactionTemplate é obrigatório");
        this.objectMapper = objectMapper;
        this.relogio = relogio.orElseGet(Clock::systemUTC);
    }

    public JsonNode conversar(ChatRequest pedido, Usuario usuario) {
        String chatId = pedido.chatId();
        validarProprietario(chatId, usuario);

        List<MensagemChat> anteriores = mensagemChatRepository.findByChatIdOrderByCriadaEmAscIdAsc(chatId);
        Long ultimoUsuarioMsgId = extrairUltimoUsuarioMsgId(anteriores);
        String historicoJson = montarHistoricoJson(anteriores);

        JsonNode respostaLana = lanaClient.invocarAgente(pedido, historicoJson);
        salvarTurnoEmTransacao(chatId, usuario, pedido.message(), respostaLana);

        List<MensagemChat> novasSistema = buscarMensagensSistema(chatId, ultimoUsuarioMsgId);
        anexarMensagensSistema(respostaLana, novasSistema);
        return respostaLana;
    }

    private Long extrairUltimoUsuarioMsgId(List<MensagemChat> mensagens) {
        return mensagens.stream()
                .filter(m -> "usuario".equals(m.getPapel()))
                .map(MensagemChat::getId)
                .reduce((primeiro, segundo) -> segundo)
                .orElse(null);
    }

    private List<MensagemChat> buscarMensagensSistema(String chatId, Long ultimoUsuarioMsgId) {
        Long corteId = ultimoUsuarioMsgId != null ? ultimoUsuarioMsgId : 0L;
        return mensagemChatRepository.findByChatIdAndPapelAndIdGreaterThanOrderByCriadaEmAscIdAsc(
                chatId, "sistema", corteId);
    }

    private void anexarMensagensSistema(JsonNode respostaLana, List<MensagemChat> novasSistema) {
        if (!(respostaLana instanceof ObjectNode obj)) {
            return;
        }
        ArrayNode sisArray = objectMapper.createArrayNode();
        for (MensagemChat s : novasSistema) {
            sisArray.add(converterMensagemParaJson(s));
        }
        obj.set("mensagens_sistema", sisArray);
        obj.set("solicitacao", NullNode.getInstance());
    }

    public JsonNode buscarMensagens(String chatId, Usuario usuario) {
        List<MensagemChat> mensagens = mensagemChatRepository.findByChatIdOrderByCriadaEmAscIdAsc(chatId);
        if (mensagens.isEmpty()) {
            throw new NotFoundException("Conversa " + chatId + " não encontrada");
        }
        validarAcessoMensagens(chatId, usuario);
        return montarRespostaMensagens(chatId, mensagens);
    }

    private void validarAcessoMensagens(String chatId, Usuario usuario) {
        Integer donoId = buscarDonoConversa(chatId).orElse(null);
        if (Objects.equals(donoId, usuario.getId())) {
            return;
        }
        if (ehSupervisorComVersaoSubmetida(chatId, usuario)) {
            return;
        }
        throw new ForbiddenException("Sem permissão para acessar a conversa " + chatId);
    }

    private boolean ehSupervisorComVersaoSubmetida(String chatId, Usuario usuario) {
        if (usuario.getPerfil() != Perfil.SUPERVISOR) {
            return false;
        }
        return campanhaRepository.findByChatId(chatId)
                .map(Campanha::getId)
                .filter(campanhaVersaoRepository::existsByCampanhaId)
                .isPresent();
    }

    private ObjectNode montarRespostaMensagens(String chatId, List<MensagemChat> mensagens) {
        ObjectNode resposta = objectMapper.createObjectNode();
        resposta.put("chat_id", chatId);
        campanhaRepository.findByChatId(chatId)
                .ifPresentOrElse(
                        c -> resposta.put("campanha_id", c.getId()),
                        () -> resposta.set("campanha_id", NullNode.getInstance()));

        ArrayNode arrayMsgs = objectMapper.createArrayNode();
        for (MensagemChat m : mensagens) {
            arrayMsgs.add(converterMensagemParaJson(m));
        }
        resposta.set("mensagens", arrayMsgs);
        return resposta;
    }

    private void salvarTurnoEmTransacao(String chatId, Usuario usuario, String prompt, JsonNode respostaLana) {
        transactionTemplate.executeWithoutResult(status -> {
            Instant agora = relogio.instant();
            MensagemChat msgUsuario = new MensagemChat(
                    chatId, usuario, "usuario", null, null,
                    prompt, null, null, agora
            );
            mensagemChatRepository.save(msgUsuario);

            String regraJson = extrairJsonComoString(respostaLana.path("regra"));
            String trilhaJson = extrairJsonComoString(respostaLana.path("trilha"));
            MensagemChat msgLana = new MensagemChat(
                    chatId, usuario, "lana", null, null,
                    respostaLana.path("response").asString(), regraJson, trilhaJson, agora
            );
            mensagemChatRepository.save(msgLana);
        });
    }

    private void validarProprietario(String chatId, Usuario usuario) {
        buscarDonoConversa(chatId).ifPresent(donoId -> {
            if (!Objects.equals(donoId, usuario.getId())) {
                throw new ForbiddenException("A conversa " + chatId + " pertence a outro usuário");
            }
        });
    }

    private Optional<Integer> buscarDonoConversa(String chatId) {
        Optional<Integer> donoCampanha = campanhaRepository.findByChatId(chatId)
                .map(c -> c.getAutor().getId());
        if (donoCampanha.isPresent()) {
            return donoCampanha;
        }
        Optional<Integer> donoSimulacao = simulacaoRepository.findFirstByChatIdOrderByIdAsc(chatId)
                .map(s -> s.getUsuario().getId());
        if (donoSimulacao.isPresent()) {
            return donoSimulacao;
        }
        return mensagemChatRepository.findFirstByChatIdOrderByCriadaEmAscIdAsc(chatId)
                .map(m -> m.getUsuario().getId());
    }

    private String montarHistoricoJson(List<MensagemChat> anteriores) {
        ArrayNode array = objectMapper.createArrayNode();
        for (MensagemChat msg : anteriores) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("papel", msg.getPapel());
            item.put("texto", msg.getTexto());
            if ("lana".equals(msg.getPapel())) {
                item.set("trilha", lerJsonOuNull(msg.getTrilha()));
                item.set("regra", lerJsonOuNull(msg.getFicha()));
            }
            array.add(item);
        }
        return objectMapper.writeValueAsString(array);
    }

    public JsonNode converterMensagemParaJson(MensagemChat msg) {
        ObjectNode no = objectMapper.createObjectNode();
        no.put("id", msg.getId());
        no.put("papel", msg.getPapel());
        if ("sistema".equals(msg.getPapel()) && msg.getTipo() != null) {
            no.put("tipo", msg.getTipo());
        }
        no.put("texto", msg.getTexto());
        if ("lana".equals(msg.getPapel())) {
            no.set("regra", lerJsonOuNull(msg.getFicha()));
            no.set("trilha", lerJsonOuNull(msg.getTrilha()));
        }
        if ("sistema".equals(msg.getPapel()) && "SIMULACAO".equals(msg.getTipo()) && msg.getSimulacaoId() != null) {
            no.put("simulacao_id", msg.getSimulacaoId());
        }
        no.put("criada_em", DateTimeFormatter.ISO_INSTANT.format(msg.getCriadaEm()));
        return no;
    }

    private JsonNode lerJsonOuNull(String json) {
        if (json == null || json.isBlank()) {
            return NullNode.getInstance();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return NullNode.getInstance();
        }
    }

    private String extrairJsonComoString(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.toString();
    }
}
