package br.com.camplana.service;

import br.com.camplana.dto.correcao.DecisaoRequest;
import br.com.camplana.dto.correcao.SolicitacaoDetalheResponse;
import br.com.camplana.dto.correcao.SolicitacaoDetalheResponse.DecisaoResumo;
import br.com.camplana.dto.correcao.SolicitacaoDetalheResponse.UsuarioResumo;
import br.com.camplana.dto.correcao.SolicitacaoRequest;
import br.com.camplana.entity.Cargo;
import br.com.camplana.entity.Funcionario;
import br.com.camplana.entity.FuncionarioCargo;
import br.com.camplana.entity.FuncionarioCargoId;
import br.com.camplana.entity.FuncionarioLoja;
import br.com.camplana.entity.FuncionarioLojaId;
import br.com.camplana.entity.Loja;
import br.com.camplana.entity.OrigemSolicitacao;
import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.SolicitacaoAlteracao;
import br.com.camplana.entity.StatusSolicitacao;
import br.com.camplana.entity.Usuario;
import br.com.camplana.exception.BadRequestException;
import br.com.camplana.exception.ConflictException;
import br.com.camplana.exception.NotFoundException;
import br.com.camplana.repository.CargoRepository;
import br.com.camplana.repository.FuncionarioCargoRepository;
import br.com.camplana.repository.FuncionarioLojaRepository;
import br.com.camplana.repository.FuncionarioRepository;
import br.com.camplana.repository.LojaRepository;
import br.com.camplana.repository.SolicitacaoAlteracaoRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Correção pontual da base (contrato: seção 6).
 *
 * <p>Regras que este serviço garante:
 * <ul>
 *   <li>Quatro Olhos: quem decide nunca é o autor, e a regra falha fechada.</li>
 *   <li>"antes" e "depois" são sempre códigos de negócio (cod_loja, cod_cargo) ou datas YYYY-MM-DD.
 *       Ids internos do banco nunca são aceitos.</li>
 *   <li>"depois" é validado na criação, para não existir solicitação impossível de aprovar.</li>
 *   <li>A decisão serializa por solicitação e por funcionário (lock pessimista, sempre nesta ordem),
 *       então duas decisões simultâneas não se atropelam.</li>
 *   <li>Aprovar = conferir o "antes" + aplicar na mesma transação. Divergência vira 409
 *       e a transação inteira volta.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SolicitacaoAlteracaoService {

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Só para o texto "decidida em dd/MM/yyyy". O timestamp continua em UTC. */
    private static final ZoneId FUSO_EXIBICAO = ZoneId.of("America/Sao_Paulo");
    private static final int CHAT_ID_MAX = 100;

    private static final String MSG_BASE_MUDOU = "A base mudou desde o pedido";
    private static final String MSG_SEM_PERMISSAO = "Sem permissão para esta ação";
    private static final String MSG_OUTRA_PESSOA = "Outra pessoa precisa aprovar esta correção";
    private static final String MSG_ANTES_NAO_CONFERE = "O valor 'antes' não confere com o estado atual da base (atual: ";

    private final SolicitacaoAlteracaoRepository solicitacaoRepository;
    private final FuncionarioRepository funcionarioRepository;
    private final FuncionarioLojaRepository funcionarioLojaRepository;
    private final FuncionarioCargoRepository funcionarioCargoRepository;
    private final LojaRepository lojaRepository;
    private final CargoRepository cargoRepository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    /** Valores já normalizados, prontos para a coluna JSONB. */
    private record Valores(String antesJson, String depoisJson) {
    }

    // ------------------------------------------------------------------ criação

    /**
     * Endpoint público (POST /api/correcoes). A origem é sempre USUARIO: LANA só entra pelo fluxo
     * do chat ({@link #criarSolicitacaoDaLana}), para ninguém forjar "aberta pela Lana".
     * Se vier chat_id junto com USUARIO, ele é descartado.
     */
    @Transactional
    public SolicitacaoDetalheResponse criarSolicitacao(SolicitacaoRequest dto, Usuario autor) {
        if (dto == null) {
            throw new BadRequestException("Corpo da requisição é obrigatório");
        }
        if (dto.origem() == OrigemSolicitacao.LANA) {
            throw new BadRequestException("A origem LANA só é aceita pelo fluxo do chat");
        }
        return criar(dto, autor, OrigemSolicitacao.USUARIO, null);
    }

    /**
     * Uso interno do chat (A6-81). O autor é o usuário logado da conversa, nunca vem do corpo.
     * Quem chama já deve ter confirmado que o chat_id pertence a esse usuário (regra do 3.1).
     */
    @Transactional
    public SolicitacaoDetalheResponse criarSolicitacaoDaLana(SolicitacaoRequest dto, Usuario usuarioDoChat) {
        if (dto == null) {
            throw new BadRequestException("Corpo da requisição é obrigatório");
        }
        String chatId = dto.chatId() == null ? "" : dto.chatId().trim();
        if (chatId.isEmpty()) {
            throw new BadRequestException("chat_id é obrigatório para solicitações abertas pela Lana");
        }
        if (chatId.length() > CHAT_ID_MAX) {
            throw new BadRequestException("chat_id excede " + CHAT_ID_MAX + " caracteres");
        }
        return criar(dto, usuarioDoChat, OrigemSolicitacao.LANA, chatId);
    }

    private SolicitacaoDetalheResponse criar(SolicitacaoRequest dto, Usuario autor,
                                             OrigemSolicitacao origem, String chatId) {
        if (autor == null || autor.getId() == null) {
            throw new AccessDeniedException(MSG_SEM_PERMISSAO);
        }
        if (dto.tipo() == null) {
            throw new BadRequestException("Tipo de alteração é obrigatório");
        }
        if (dto.matricula() == null || dto.matricula().isBlank()) {
            throw new BadRequestException("Matrícula é obrigatória");
        }

        LocalDate competencia = parseCompetencia(dto.competencia());

        Funcionario funcionario = funcionarioRepository.findByMatricula(dto.matricula().trim())
                .orElseThrow(() -> new BadRequestException("Matrícula não encontrada: " + dto.matricula().trim()));

        if (!funcionarioLojaRepository.existsByIdFuncionarioIdAndIdDateRef(funcionario.getId(), competencia)) {
            throw new BadRequestException(
                    "Funcionário não possui alocação na competência informada: " + YearMonth.from(competencia));
        }

        Valores valores = switch (dto.tipo()) {
            case TRANSFERENCIA_LOJA -> validarTransferenciaLoja(funcionario, competencia, dto);
            case TROCA_CARGO -> validarTrocaCargo(funcionario, competencia, dto);
            case DATA_ADMISSAO -> validarDataAdmissao(funcionario, dto);
            case DATA_DEMISSAO -> validarDataDemissao(funcionario, dto);
        };

        SolicitacaoAlteracao solicitacao = SolicitacaoAlteracao.builder()
                .tipo(dto.tipo())
                .matricula(funcionario.getMatricula())
                .competencia(competencia)
                .valorAntes(valores.antesJson())
                .valorDepois(valores.depoisJson())
                .motivo(dto.motivo())
                .autor(autor)
                .origem(origem)
                .chatId(chatId)
                .status(StatusSolicitacao.PENDENTE)
                .criadaEm(agoraUtc())
                .build();

        return toDetalheResponse(solicitacaoRepository.save(solicitacao));
    }

    private Valores validarTransferenciaLoja(Funcionario funcionario, LocalDate competencia, SolicitacaoRequest dto) {
        String codAntes = textoObrigatorio(dto.antes(), "antes");
        String codDepois = textoObrigatorio(dto.depois(), "depois");

        FuncionarioLoja atual = funcionarioLojaRepository
                .findAlocacaoPorCodLoja(funcionario.getId(), competencia, codAntes)
                .orElseThrow(() -> new BadRequestException(
                        MSG_ANTES_NAO_CONFERE + lojasAtuais(funcionario, competencia) + ")"));

        if (codAntes.equals(codDepois)) {
            throw new BadRequestException("A loja de destino deve ser diferente da atual");
        }
        Loja destino = lojaRepository.findByCodLoja(codDepois)
                .orElseThrow(() -> new BadRequestException("Loja de destino não encontrada: " + codDepois));
        if (funcionarioLojaRepository.existsByIdFuncionarioIdAndIdLojaIdAndIdDateRef(
                funcionario.getId(), destino.getId(), competencia)) {
            throw new BadRequestException(
                    "Funcionário já está alocado na loja " + codDepois + " nesta competência");
        }
        return new Valores(paraJson(atual.getLoja().getCodLoja()), paraJson(destino.getCodLoja()));
    }

    private Valores validarTrocaCargo(Funcionario funcionario, LocalDate competencia, SolicitacaoRequest dto) {
        String codAntes = textoObrigatorio(dto.antes(), "antes");
        String codDepois = textoObrigatorio(dto.depois(), "depois");

        FuncionarioCargo atual = funcionarioCargoRepository
                .findAlocacaoPorCodCargo(funcionario.getId(), competencia, codAntes)
                .orElseThrow(() -> new BadRequestException(
                        MSG_ANTES_NAO_CONFERE + cargosAtuais(funcionario, competencia) + ")"));

        if (codAntes.equals(codDepois)) {
            throw new BadRequestException("O cargo de destino deve ser diferente do atual");
        }
        Cargo destino = cargoRepository.findByCodCargo(codDepois)
                .orElseThrow(() -> new BadRequestException("Cargo de destino não encontrado: " + codDepois));
        if (funcionarioCargoRepository.existsByIdFuncionarioIdAndIdCargoIdAndIdDateRef(
                funcionario.getId(), destino.getId(), competencia)) {
            throw new BadRequestException(
                    "Funcionário já possui o cargo " + codDepois + " nesta competência");
        }
        return new Valores(paraJson(atual.getCargo().getCodCargo()), paraJson(destino.getCodCargo()));
    }

    private Valores validarDataAdmissao(Funcionario funcionario, SolicitacaoRequest dto) {
        LocalDate antes = dataObrigatoria(dto.antes(), "antes");
        LocalDate depois = dataObrigatoria(dto.depois(), "depois");

        if (!antes.equals(funcionario.getDataAdmissao())) {
            throw new BadRequestException(MSG_ANTES_NAO_CONFERE + funcionario.getDataAdmissao() + ")");
        }
        if (antes.equals(depois)) {
            throw new BadRequestException("A nova data de admissão deve ser diferente da atual");
        }
        LocalDate demissao = funcionario.getDataDemissao();
        if (demissao != null && depois.isAfter(demissao)) {
            throw new BadRequestException(
                    "A data de admissão não pode ser posterior à data de demissão (" + demissao + ")");
        }
        return new Valores(paraJson(antes.toString()), paraJson(depois.toString()));
    }

    private Valores validarDataDemissao(Funcionario funcionario, SolicitacaoRequest dto) {
        LocalDate antes = dataOpcional(dto.antes(), "antes"); // null = não havia demissão (contrato 6)
        LocalDate depois = dataObrigatoria(dto.depois(), "depois");

        if (!datasIguais(funcionario.getDataDemissao(), antes)) {
            String atual = funcionario.getDataDemissao() == null ? "sem demissão" : funcionario.getDataDemissao().toString();
            throw new BadRequestException(MSG_ANTES_NAO_CONFERE + atual + ")");
        }
        if (depois.equals(antes)) {
            throw new BadRequestException("A nova data de demissão deve ser diferente da atual");
        }
        if (depois.isBefore(funcionario.getDataAdmissao())) {
            throw new BadRequestException(
                    "A data de demissão não pode ser anterior à data de admissão (" + funcionario.getDataAdmissao() + ")");
        }
        return new Valores(paraJson(antes == null ? null : antes.toString()), paraJson(depois.toString()));
    }

    // ------------------------------------------------------------------ decisão

    @Transactional
    public SolicitacaoDetalheResponse decidirSolicitacao(Integer id, DecisaoRequest decisao, Usuario decisor) {
        if (decisor == null || decisor.getId() == null) {
            throw new AccessDeniedException(MSG_SEM_PERMISSAO);
        }

        // Lock 1: a solicitação. Uma segunda decisão simultânea espera aqui e depois cai no 409 "já decidida".
        SolicitacaoAlteracao solicitacao = solicitacaoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Solicitação não encontrada com ID: " + id));

        // Quatro Olhos: falha fechada. Sem autor identificável, ninguém aprova.
        Usuario autor = solicitacao.getAutor();
        if (autor == null || autor.getId() == null || autor.getId().equals(decisor.getId())) {
            throw new AccessDeniedException(MSG_OUTRA_PESSOA);
        }

        if (solicitacao.getStatus() != StatusSolicitacao.PENDENTE) {
            throw new ConflictException(montarMensagemJaDecidida(solicitacao));
        }

        Boolean aprovado = decisao == null ? null : decisao.isAprovado();
        if (aprovado == null) {
            throw new BadRequestException("Decisão (APROVAR ou REPROVAR) é obrigatória");
        }

        String justificativa = decisao.justificativaFinal();
        justificativa = (justificativa == null || justificativa.isBlank()) ? null : justificativa.trim();
        if (!aprovado && justificativa == null) {
            throw new BadRequestException("Justificativa obrigatória para reprovar");
        }

        if (aprovado) {
            aplicarAlteracao(solicitacao); // 409 aborta a transação inteira
            solicitacao.setStatus(StatusSolicitacao.APROVADA);
        } else {
            solicitacao.setStatus(StatusSolicitacao.REPROVADA);
        }
        solicitacao.setDecisor(decisor);
        solicitacao.setDecididaEm(agoraUtc());
        solicitacao.setJustificativa(justificativa);

        return toDetalheResponse(solicitacaoRepository.save(solicitacao));
    }

    /**
     * Confere o "antes" contra a base e aplica o "depois". Roda sob o lock da solicitação e agora
     * também sob o lock do funcionário, então nenhuma outra decisão altera esse funcionário
     * entre a conferência e a escrita.
     */
    private void aplicarAlteracao(SolicitacaoAlteracao solicitacao) {
        Funcionario funcionario = funcionarioRepository.findByMatricula(solicitacao.getMatricula())
                .orElseThrow(() -> new ConflictException(MSG_BASE_MUDOU));

        // Lock 2: o funcionário. refresh() relê a linha já travada, então o drift check vê o valor atual.
        entityManager.refresh(funcionario, LockModeType.PESSIMISTIC_WRITE);

        String antes = lerValor(solicitacao.getValorAntes());
        String depois = lerValor(solicitacao.getValorDepois());
        LocalDate competencia = solicitacao.getCompetencia();

        switch (solicitacao.getTipo()) {
            case TRANSFERENCIA_LOJA -> aplicarTransferenciaLoja(funcionario, competencia, antes, depois);
            case TROCA_CARGO -> aplicarTrocaCargo(funcionario, competencia, antes, depois);
            case DATA_ADMISSAO -> aplicarDataAdmissao(funcionario, antes, depois);
            case DATA_DEMISSAO -> aplicarDataDemissao(funcionario, antes, depois);
        }
    }

    private void aplicarTransferenciaLoja(Funcionario funcionario, LocalDate competencia, String codAntes, String codDepois) {
        FuncionarioLoja atual = funcionarioLojaRepository
                .findAlocacaoPorCodLoja(funcionario.getId(), competencia, codAntes)
                .orElseThrow(() -> new ConflictException(MSG_BASE_MUDOU));
        Loja destino = lojaRepository.findByCodLoja(codDepois)
                .orElseThrow(() -> new ConflictException(MSG_BASE_MUDOU));
        if (funcionarioLojaRepository.existsByIdFuncionarioIdAndIdLojaIdAndIdDateRef(
                funcionario.getId(), destino.getId(), competencia)) {
            throw new ConflictException(MSG_BASE_MUDOU);
        }

        // A loja faz parte da PK: não dá para dar UPDATE, então remove a tupla antiga e cria a nova.
        funcionarioLojaRepository.delete(atual);
        funcionarioLojaRepository.flush();
        funcionarioLojaRepository.save(new FuncionarioLoja(
                new FuncionarioLojaId(funcionario.getId(), destino.getId(), competencia), funcionario, destino));
    }

    private void aplicarTrocaCargo(Funcionario funcionario, LocalDate competencia, String codAntes, String codDepois) {
        FuncionarioCargo atual = funcionarioCargoRepository
                .findAlocacaoPorCodCargo(funcionario.getId(), competencia, codAntes)
                .orElseThrow(() -> new ConflictException(MSG_BASE_MUDOU));
        Cargo destino = cargoRepository.findByCodCargo(codDepois)
                .orElseThrow(() -> new ConflictException(MSG_BASE_MUDOU));
        if (funcionarioCargoRepository.existsByIdFuncionarioIdAndIdCargoIdAndIdDateRef(
                funcionario.getId(), destino.getId(), competencia)) {
            throw new ConflictException(MSG_BASE_MUDOU);
        }

        funcionarioCargoRepository.delete(atual);
        funcionarioCargoRepository.flush();
        funcionarioCargoRepository.save(new FuncionarioCargo(
                new FuncionarioCargoId(funcionario.getId(), destino.getId(), competencia), funcionario, destino));
    }

    private void aplicarDataAdmissao(Funcionario funcionario, String antes, String depois) {
        LocalDate dataAntes = LocalDate.parse(antes);
        LocalDate dataDepois = LocalDate.parse(depois);
        if (!dataAntes.equals(funcionario.getDataAdmissao())) {
            throw new ConflictException(MSG_BASE_MUDOU);
        }
        LocalDate demissao = funcionario.getDataDemissao();
        if (demissao != null && dataDepois.isAfter(demissao)) {
            throw new ConflictException(MSG_BASE_MUDOU);
        }
        funcionario.setDataAdmissao(dataDepois);
        funcionarioRepository.save(funcionario);
    }

    private void aplicarDataDemissao(Funcionario funcionario, String antes, String depois) {
        LocalDate dataAntes = antes == null ? null : LocalDate.parse(antes);
        LocalDate dataDepois = LocalDate.parse(depois);
        if (!datasIguais(funcionario.getDataDemissao(), dataAntes)) {
            throw new ConflictException(MSG_BASE_MUDOU);
        }
        if (dataDepois.isBefore(funcionario.getDataAdmissao())) {
            throw new ConflictException(MSG_BASE_MUDOU);
        }
        funcionario.setDataDemissao(dataDepois);
        funcionarioRepository.save(funcionario);
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public List<SolicitacaoDetalheResponse> listarFila(Usuario supervisorLogado) {
        if (supervisorLogado == null || supervisorLogado.getId() == null) {
            throw new AccessDeniedException(MSG_SEM_PERMISSAO);
        }
        return solicitacaoRepository
                .findByStatusAndAutorIdNotOrderByCriadaEmAsc(StatusSolicitacao.PENDENTE, supervisorLogado.getId())
                .stream()
                .map(this::toDetalheResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SolicitacaoDetalheResponse buscarPorId(Integer id, Usuario usuarioLogado) {
        if (usuarioLogado == null || usuarioLogado.getId() == null) {
            throw new AccessDeniedException(MSG_SEM_PERMISSAO);
        }
        SolicitacaoAlteracao solicitacao = solicitacaoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Solicitação não encontrada com ID: " + id));

        if (usuarioLogado.getPerfil() != Perfil.SUPERVISOR) {
            Usuario autor = solicitacao.getAutor();
            if (autor == null || !usuarioLogado.getId().equals(autor.getId())) {
                throw new AccessDeniedException(MSG_SEM_PERMISSAO);
            }
        }
        return toDetalheResponse(solicitacao);
    }

    // ------------------------------------------------------------------ apoio

    private static LocalDateTime agoraUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private String lojasAtuais(Funcionario funcionario, LocalDate competencia) {
        return funcionarioLojaRepository.findAlocacoesDaCompetencia(funcionario.getId(), competencia).stream()
                .map(fl -> fl.getLoja().getCodLoja())
                .collect(Collectors.joining(", "));
    }

    private String cargosAtuais(Funcionario funcionario, LocalDate competencia) {
        String atuais = funcionarioCargoRepository.findAlocacoesDaCompetencia(funcionario.getId(), competencia).stream()
                .map(fc -> fc.getCargo().getCodCargo())
                .collect(Collectors.joining(", "));
        return atuais.isEmpty() ? "sem cargo na competência" : atuais;
    }

    private String montarMensagemJaDecidida(SolicitacaoAlteracao solicitacao) {
        if (solicitacao.getDecisor() != null && solicitacao.getDecididaEm() != null) {
            String dia = solicitacao.getDecididaEm()
                    .atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(FUSO_EXIBICAO)
                    .format(DATA_BR);
            return "Esta correção já foi decidida por " + solicitacao.getDecisor().getNome() + " em " + dia;
        }
        return "Esta correção já foi decidida";
    }

    /** Competência é YYYY-MM (contrato) e vira o dia 1 do mês, que é o date_ref da base. */
    private LocalDate parseCompetencia(String competencia) {
        if (competencia == null || competencia.isBlank()) {
            throw new BadRequestException("Competência é obrigatória");
        }
        try {
            return YearMonth.parse(competencia.trim()).atDay(1);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Formato de competência inválido: " + competencia + ". Use YYYY-MM.");
        }
    }

    /** Aceita só texto. Null, ausente ou vazio viram null. Número, objeto e array são recusados. */
    private String textoOpcional(JsonNode node, String campo) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new BadRequestException("O valor '" + campo + "' deve ser um texto");
        }
        String texto = node.asText().trim();
        return texto.isEmpty() ? null : texto;
    }

    private String textoObrigatorio(JsonNode node, String campo) {
        String texto = textoOpcional(node, campo);
        if (texto == null) {
            throw new BadRequestException("O valor '" + campo + "' é obrigatório");
        }
        return texto;
    }

    private LocalDate dataOpcional(JsonNode node, String campo) {
        String texto = textoOpcional(node, campo);
        if (texto == null) {
            return null;
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Data inválida em '" + campo + "': " + texto + ". Use YYYY-MM-DD.");
        }
    }

    private LocalDate dataObrigatoria(JsonNode node, String campo) {
        LocalDate data = dataOpcional(node, campo);
        if (data == null) {
            throw new BadRequestException("O valor '" + campo + "' é obrigatório");
        }
        return data;
    }

    private boolean datasIguais(LocalDate a, LocalDate b) {
        return a == null ? b == null : a.equals(b);
    }

    /** Grava sempre um escalar JSON: "33", "2025-10-01" ou null. Nunca objeto. */
    private String paraJson(String valor) {
        if (valor == null) {
            return "null";
        }
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JacksonException e) {
            throw new IllegalStateException("Não foi possível serializar o valor da solicitação", e);
        }
    }

    private String lerValor(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return (node == null || node.isNull() || node.isMissingNode()) ? null : node.asText();
        } catch (JacksonException e) {
            throw new IllegalStateException("Valor inválido persistido na solicitação", e);
        }
    }

    private SolicitacaoDetalheResponse toDetalheResponse(SolicitacaoAlteracao s) {
        UsuarioResumo autor = s.getAutor() != null
                ? new UsuarioResumo(s.getAutor().getId(), s.getAutor().getNome())
                : null;

        DecisaoResumo decisao = null;
        if (s.getStatus() != StatusSolicitacao.PENDENTE && s.getDecisor() != null) {
            decisao = new DecisaoResumo(
                    s.getStatus().name(),
                    new UsuarioResumo(s.getDecisor().getId(), s.getDecisor().getNome()),
                    s.getDecididaEm(),
                    s.getJustificativa());
        }

        String autorNome = s.getAutor() != null ? s.getAutor().getNome() : null;
        String descricaoOrigem = null;
        if (autorNome != null) {
            descricaoOrigem = s.getOrigem() == OrigemSolicitacao.LANA
                    ? "aberta pela Lana a pedido de " + autorNome
                    : "aberta por " + autorNome;
        }

        return new SolicitacaoDetalheResponse(
                s.getId(),
                s.getTipo(),
                s.getMatricula(),
                s.getCompetencia() != null ? YearMonth.from(s.getCompetencia()).toString() : null,
                lerValor(s.getValorAntes()),
                lerValor(s.getValorDepois()),
                s.getMotivo(),
                s.getOrigem(),
                s.getChatId(),
                s.getStatus(),
                autor,
                s.getCriadaEm(),
                decisao,
                descricaoOrigem);
    }
}
