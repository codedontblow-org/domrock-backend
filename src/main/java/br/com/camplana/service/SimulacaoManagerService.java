package br.com.camplana.service;

import br.com.camplana.dto.simulacao.SimulacaoCriadaResponse;
import br.com.camplana.dto.simulacao.SimulacaoStatusResponse;
import br.com.camplana.entity.Campanha;
import br.com.camplana.entity.Simulacao;
import br.com.camplana.entity.StatusCampanha;
import br.com.camplana.entity.Usuario;
import br.com.camplana.exception.BadRequestException;
import br.com.camplana.exception.ConflictException;
import br.com.camplana.exception.ForbiddenException;
import br.com.camplana.exception.NotFoundException;
import br.com.camplana.repository.CampanhaRepository;
import br.com.camplana.repository.SimulacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;

/**
 * Regras de entrada e consulta das simulações (contrato 4.1 e 4.2): valida conversa, autoria e
 * status da campanha antes de criar o job e disparar o processamento assíncrono.
 */
@Service
public class SimulacaoManagerService {

    private final SimulacaoPersistenceService persistenceService;
    private final SimulacaoJobService jobService;
    private final SimulacaoRepository simulacaoRepository;
    private final CampanhaRepository campanhaRepository;
    private final ObjectMapper objectMapper;

    public SimulacaoManagerService(SimulacaoPersistenceService persistenceService,
                                   SimulacaoJobService jobService,
                                   SimulacaoRepository simulacaoRepository,
                                   CampanhaRepository campanhaRepository,
                                   ObjectMapper objectMapper) {
        this.persistenceService = persistenceService;
        this.jobService = jobService;
        this.simulacaoRepository = simulacaoRepository;
        this.campanhaRepository = campanhaRepository;
        this.objectMapper = objectMapper;
    }

    public SimulacaoCriadaResponse iniciarSimulacao(JsonNode body, Usuario usuario) {
        String chatId = extrairChatIdValido(body);
        JsonNode fichaNode = extrairFichaValida(body);

        Campanha campanha = validarConversaECampanha(chatId, usuario);
        String fichaJson = objectMapper.writeValueAsString(fichaNode);

        Simulacao criada = persistenceService.criarSimulacaoInicial(usuario, chatId, campanha, fichaJson);

        Map<String, Object> payloadLana = Map.of(
                "chat_id", chatId,
                "regra", fichaNode
        );
        try {
            jobService.processarSimulacaoAsync(criada.getId(), payloadLana);
        } catch (Exception ex) {
            String erroSubmissao = ex.getMessage() != null && !ex.getMessage().isBlank()
                    ? ex.getMessage()
                    : "Falha ao enfileirar a execução assíncrona da simulação.";
            persistenceService.falharSimulacao(criada.getId(), erroSubmissao);
            throw ex;
        }

        return new SimulacaoCriadaResponse(criada.getId(), criada.getStatus());
    }

    @Transactional(readOnly = true)
    public SimulacaoStatusResponse buscarPorId(Long id, Usuario usuario) {
        Simulacao simulacao = simulacaoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Simulação " + id + " não encontrada"));

        if (!Objects.equals(simulacao.getUsuario().getId(), usuario.getId())) {
            throw new ForbiddenException("A simulação " + id + " pertence a outro usuário");
        }

        return new SimulacaoStatusResponse(
                simulacao.getId(),
                simulacao.getStatus(),
                simulacao.getEtapa(),
                simulacao.getFicha(),
                simulacao.getResultado(),
                simulacao.getErro(),
                simulacao.getCriadaEm(),
                simulacao.getConcluidaEm()
        );
    }

    private Campanha validarConversaECampanha(String chatId, Usuario usuario) {
        simulacaoRepository.findFirstByChatIdOrderByIdAsc(chatId).ifPresent(anterior -> {
            if (!Objects.equals(anterior.getUsuario().getId(), usuario.getId())) {
                throw new ForbiddenException("A conversa " + chatId + " pertence a outro usuário");
            }
        });

        Campanha campanha = campanhaRepository.findByChatId(chatId).orElse(null);
        if (campanha != null) {
            if (!Objects.equals(campanha.getAutor().getId(), usuario.getId())) {
                throw new ForbiddenException("A conversa " + chatId + " pertence a outro usuário");
            }
            if (campanha.getStatus() != StatusCampanha.RASCUNHO) {
                throw new ConflictException("Simular só é permitido com campanha em RASCUNHO: campanha "
                        + campanha.getId() + " está em " + campanha.getStatus());
            }
        }
        return campanha;
    }

    private static String extrairChatIdValido(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new BadRequestException("Corpo da simulação inválido");
        }
        JsonNode chatNode = body.path("chat_id");
        if (!chatNode.isString() || chatNode.asString().isBlank()) {
            throw new BadRequestException("Campo chat_id é obrigatório para simular");
        }
        String chatId = chatNode.asString().strip();
        if (chatId.length() > 64) {
            throw new BadRequestException("Campo chat_id deve ter no máximo 64 caracteres");
        }
        return chatId;
    }

    private static JsonNode extrairFichaValida(JsonNode body) {
        JsonNode regra = body.path("regra");
        if (regra.isObject() && !regra.isEmpty()) {
            return regra;
        }
        JsonNode ficha = body.path("ficha");
        if (ficha.isObject() && !ficha.isEmpty()) {
            return ficha;
        }
        throw new BadRequestException("Esperado objeto não vazio em \"regra\" ou \"ficha\" no corpo; recebido " + body);
    }
}
