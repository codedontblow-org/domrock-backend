package br.com.camplana.campanha;

import br.com.camplana.dto.campanha.CampanhaResponse;
import br.com.camplana.dto.campanha.DecidirCampanhaRequest;
import br.com.camplana.dto.campanha.PeriodoCampanha;
import br.com.camplana.dto.campanha.SalvarCampanhaRequest;
import br.com.camplana.entity.Campanha;
import br.com.camplana.entity.CampanhaVersao;
import br.com.camplana.entity.MensagemChat;
import br.com.camplana.entity.StatusCampanha;
import br.com.camplana.entity.Usuario;
import br.com.camplana.exception.BadRequestException;
import br.com.camplana.exception.ConflictException;
import br.com.camplana.exception.ForbiddenException;
import br.com.camplana.exception.NotFoundException;
import br.com.camplana.repository.CampanhaRepository;
import br.com.camplana.repository.CampanhaVersaoRepository;
import br.com.camplana.repository.MensagemChatRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Ações sobre a campanha (contrato 5.3 e 5.7). Cada uma confere a permissão em
 * {@link PermissoesCampanha}, muda o status em {@link TrocaDeStatusCampanha} e devolve a Campanha.
 *
 * <pre>campanhaService.submeter(12L, gabriela); // RASCUNHO -> AGUARDANDO_APROVACAO, nasce a v1</pre>
 */
@Service
public class CampanhaService {

    private static final int NOME_MAXIMO = 150;
    private static final String INDICE_UMA_POR_CONVERSA = "uk_campanha_chat"; // V7

    private final CampanhaRepository campanhaRepository;
    private final CampanhaVersaoRepository versaoRepository;
    private final MensagemChatRepository mensagemChatRepository;
    private final RegistroSimulacoes registroSimulacoes;
    private final TrocaDeStatusCampanha trocaDeStatus;
    private final CarregadorRetratoCampanha carregador;
    private final CampanhaRespostaMontador montador;
    private final CamposDaSimulacao campos;
    private final AprovacaoDaVersao aprovacao;
    private final Clock relogio;

    public CampanhaService(CampanhaRepository campanhaRepository, CampanhaVersaoRepository versaoRepository,
                           MensagemChatRepository mensagemChatRepository,
                           RegistroSimulacoes registroSimulacoes, TrocaDeStatusCampanha trocaDeStatus,
                           CarregadorRetratoCampanha carregador, CampanhaRespostaMontador montador,
                           CamposDaSimulacao campos, AprovacaoDaVersao aprovacao, Clock relogio) {
        this.campanhaRepository = campanhaRepository;
        this.versaoRepository = versaoRepository;
        this.mensagemChatRepository = mensagemChatRepository;
        this.registroSimulacoes = registroSimulacoes;
        this.trocaDeStatus = trocaDeStatus;
        this.carregador = carregador;
        this.montador = montador;
        this.campos = campos;
        this.aprovacao = aprovacao;
        this.relogio = relogio;
    }

    /** Salva a conversa simulada como campanha em RASCUNHO. A ficha e o resultado ficam na simulação. */
    @Transactional
    public CampanhaResponse salvar(SalvarCampanhaRequest pedido, Usuario autor) {
        simulacaoParaSalvar(pedido, autor);
        String nome = nomeValido(pedido.nome());
        Campanha campanha = inserir(nova(nome, pedido.chatId(), autor));
        trocaDeStatus.registrarCriacao(campanha, autor);
        registroSimulacoes.vincularConversa(pedido.chatId(), campanha.getId());
        return montador.campanha(carregador.carregar(campanha), autor);
    }

    @Transactional
    public CampanhaResponse renomear(long campanhaId, String nome, Usuario usuario) {
        Campanha campanha = paraAlterar(campanhaId);
        PermissoesCampanha.exigir(AcaoCampanha.RENOMEAR, carregador.carregar(campanha).situacao(), usuario.getId());
        campanha.setNome(nomeValido(nome));
        campanha.setAtualizadaEm(relogio.instant());
        return montador.campanha(carregador.carregar(campanha), usuario);
    }

    /** Congela a última simulação concluída numa versão nova e manda para aprovação. */
    @Transactional
    public CampanhaResponse submeter(long campanhaId, Usuario autor) {
        Campanha campanha = paraAlterar(campanhaId);
        RetratoCampanha retrato = carregador.carregar(campanha);
        PermissoesCampanha.exigir(AcaoCampanha.SUBMETER, retrato.situacao(), autor.getId());

        int numero = retrato.versaoAtual().map(versao -> versao.getNumero() + 1).orElse(1);
        Instant agora = relogio.instant();
        String texto = "Enviado para análise — v" + numero;
        MensagemChat msg = new MensagemChat(
                campanha.getChatId(), autor, "sistema", "STATUS", null, texto, null, null, agora
        );
        msg = mensagemChatRepository.save(msg);

        CampanhaVersao versao = versaoRepository.save(congelar(retrato, msg.getId()));
        trocaDeStatus.trocar(campanha, StatusCampanha.AGUARDANDO_APROVACAO, autor, versao, null);
        return montador.campanha(carregador.carregar(campanha), autor);
    }

    /**
     * AGUARDANDO_APROVACAO -> APROVADA ou REPROVADA, na versão que o Supervisor viu (contrato 5.8).
     * O perfil SUPERVISOR já vem barrado no SecurityConfig; aqui, o resto, na ordem do contrato.
     */
    @Transactional
    public CampanhaResponse decidir(long campanhaId, DecidirCampanhaRequest pedido, Usuario supervisor) {
        Campanha campanha = paraAlterar(campanhaId);
        RetratoCampanha retrato = carregador.carregar(campanha);
        CampanhaVersao versao = retrato.versao(pedido.versao())
                .orElseThrow(() -> new NotFoundException("Versão " + pedido.versao() + " da campanha "
                        + campanhaId + " não encontrada"));
        boolean reprovar = pedido.decisao() == DecidirCampanhaRequest.Decisao.REPROVAR;
        SituacaoVersao situacao = aprovacao.situacao(retrato, versao, aprovacao.pendencias(retrato, versao));
        // Justificativa (só reprovar) e re-simulação (só aprovar) nunca se cruzam: a ordem do 5.8 se mantém.
        PermissoesVersao.exigir(reprovar ? AcaoVersao.REPROVAR : AcaoVersao.APROVAR, situacao, supervisor);

        String justificativa = extrairJustificativa(pedido, reprovar);
        StatusCampanha para = reprovar ? StatusCampanha.REPROVADA : StatusCampanha.APROVADA;
        gravarMensagemDecisao(campanha, supervisor, versao, reprovar, justificativa);

        trocaDeStatus.trocar(campanha, para, supervisor, versao, justificativa);
        return montador.campanha(carregador.carregar(campanha), supervisor);
    }

    private String extrairJustificativa(DecidirCampanhaRequest pedido, boolean reprovar) {
        String justificativa = pedido.justificativa() == null || pedido.justificativa().isBlank()
                ? null : pedido.justificativa().strip();
        if (reprovar && justificativa == null) {
            throw new BadRequestException("Justificativa obrigatória para reprovar");
        }
        return justificativa;
    }

    private void gravarMensagemDecisao(Campanha campanha, Usuario supervisor,
                                      CampanhaVersao versao, boolean reprovar, String justificativa) {
        String texto = reprovar
                ? "Reprovado por " + supervisor.getNome() + ": " + justificativa
                : "Aprovado por " + supervisor.getNome() + " — v" + versao.getNumero();
        MensagemChat msg = new MensagemChat(
                campanha.getChatId(), supervisor, "sistema", "STATUS", null, texto, null, null, relogio.instant()
        );
        mensagemChatRepository.save(msg);
    }

    /** REPROVADA -> RASCUNHO: a próxima submissão será a versão seguinte. */
    @Transactional
    public CampanhaResponse editar(long campanhaId, Usuario autor) {
        Campanha campanha = paraAlterar(campanhaId);
        RetratoCampanha retrato = carregador.carregar(campanha);
        PermissoesCampanha.exigir(AcaoCampanha.EDITAR, retrato.situacao(), autor.getId());

        int proximaVersao = retrato.versaoAtual().map(v -> v.getNumero() + 1).orElse(1);
        Instant agora = relogio.instant();
        String texto = "Voltou para rascunho — a próxima submissão será a v" + proximaVersao;
        MensagemChat msg = new MensagemChat(
                campanha.getChatId(), autor, "sistema", "STATUS", null, texto, null, null, agora
        );
        mensagemChatRepository.save(msg);

        trocaDeStatus.trocar(campanha, StatusCampanha.RASCUNHO, autor, null, null);
        return montador.campanha(carregador.carregar(campanha), autor);
    }

    @Transactional
    public CampanhaResponse arquivar(long campanhaId, Usuario autor) {
        Campanha campanha = paraAlterar(campanhaId);
        PermissoesCampanha.exigir(AcaoCampanha.ARQUIVAR, carregador.carregar(campanha).situacao(), autor.getId());

        Instant agora = relogio.instant();
        String texto = "Arquivada";
        MensagemChat msg = new MensagemChat(
                campanha.getChatId(), autor, "sistema", "STATUS", null, texto, null, null, agora
        );
        mensagemChatRepository.save(msg);

        trocaDeStatus.trocar(campanha, StatusCampanha.ARQUIVADA, autor, null, null);
        return montador.campanha(carregador.carregar(campanha), autor);
    }

    // Ordem do contrato 5.3: simulação não concluída (400), de outro usuário (403), de outro chat (400)
    private void simulacaoParaSalvar(SalvarCampanhaRequest pedido, Usuario autor) {
        long id = pedido.simulacaoId();
        SimulacaoGravada simulacao = registroSimulacoes.buscar(id)
                .orElseThrow(() -> new BadRequestException("Simule antes de salvar: a simulação " + id + " não existe"));
        if (!simulacao.concluida()) {
            throw new BadRequestException("Simule antes de salvar: a simulação " + id + " está em " + simulacao.status());
        }
        if (simulacao.usuarioId() != autor.getId()) {
            throw new ForbiddenException("A conversa " + simulacao.chatId() + " é de outro usuário");
        }
        if (!simulacao.chatId().equals(pedido.chatId())) {
            throw new BadRequestException("A simulação " + id + " é da conversa " + simulacao.chatId()
                    + ", não da conversa " + pedido.chatId());
        }
    }

    // PermissoesCampanha já garantiu que existe simulação concluída e que ela não virou versão
    private CampanhaVersao congelar(RetratoCampanha retrato, Long ultimaMensagemId) {
        SimulacaoGravada simulacao = retrato.ultimaSimulacao();
        PeriodoCampanha periodo = campos.periodo(simulacao.regraJson());
        int numero = retrato.versaoAtual().map(versao -> versao.getNumero() + 1).orElse(1);
        return new CampanhaVersao(retrato.campanha(), numero, simulacao.id(), simulacao.regraJson(),
                simulacao.resultadoJson(), periodo.dataInicio(), periodo.dataFim(), ultimaMensagemId, relogio.instant());
    }

    private Campanha nova(String nome, String chatId, Usuario autor) {
        Instant agora = relogio.instant();
        Campanha campanha = new Campanha();
        campanha.setNome(nome);
        campanha.setChatId(chatId);
        campanha.setAutor(autor);
        campanha.setStatus(StatusCampanha.RASCUNHO);
        campanha.setCriadaEm(agora);
        campanha.setAtualizadaEm(agora);
        return campanha;
    }

    // O índice único decide "uma conversa, uma campanha", e não um SELECT antes do INSERT: com dois
    // cliques em "Salvar" ao mesmo tempo, os dois SELECTs não achariam nada e o segundo INSERT daria 500.
    private Campanha inserir(Campanha nova) {
        try {
            return campanhaRepository.save(nova);
        } catch (DataIntegrityViolationException erro) {
            if (!String.valueOf(erro.getMostSpecificCause().getMessage()).contains(INDICE_UMA_POR_CONVERSA)) {
                throw erro;
            }
            throw new ConflictException("A conversa " + nova.getChatId() + " já tem campanha: uma conversa, uma campanha");
        }
    }

    private Campanha paraAlterar(long campanhaId) {
        return campanhaRepository.findParaAlterar(campanhaId)
                .orElseThrow(() -> new NotFoundException("Campanha " + campanhaId + " não encontrada"));
    }

    private static String nomeValido(String nome) {
        if (nome == null || nome.isBlank() || nome.strip().length() > NOME_MAXIMO) {
            throw new BadRequestException("Nome da campanha: esperado de 1 a " + NOME_MAXIMO
                    + " caracteres; recebido \"" + nome + "\"");
        }
        return nome.strip();
    }
}
