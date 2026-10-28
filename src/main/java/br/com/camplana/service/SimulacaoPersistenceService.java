package br.com.camplana.service;

import br.com.camplana.campanha.CamposDaSimulacao;
import br.com.camplana.entity.Campanha;
import br.com.camplana.entity.CampanhaVersao;
import br.com.camplana.entity.EtapaSimulacao;
import br.com.camplana.entity.MensagemChat;
import br.com.camplana.entity.Simulacao;
import br.com.camplana.entity.StatusSimulacao;
import br.com.camplana.entity.Usuario;
import br.com.camplana.repository.MensagemChatRepository;
import br.com.camplana.repository.SimulacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Transações curtas e isoladas da simulação (A6-66): cada mudança de status ou etapa abre e fecha
 * sua própria transação para não segurar conexão do banco durante a chamada HTTP à Lana.
 */
@Service
public class SimulacaoPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(SimulacaoPersistenceService.class);

    public static final String MOTIVO_INTERROMPIDA =
            "{\"etapa\":\"interrompida\",\"mensagem\":\"Simulação interrompida por reinício do servidor.\"}";

    private final SimulacaoRepository simulacaoRepository;
    private final MensagemChatRepository mensagemChatRepository;
    private final CamposDaSimulacao camposDaSimulacao;
    private final Clock relogio;

    public SimulacaoPersistenceService(SimulacaoRepository simulacaoRepository,
                                       MensagemChatRepository mensagemChatRepository,
                                       CamposDaSimulacao camposDaSimulacao,
                                       Clock relogio) {
        this.simulacaoRepository = simulacaoRepository;
        this.mensagemChatRepository = mensagemChatRepository;
        this.camposDaSimulacao = camposDaSimulacao;
        this.relogio = relogio;
    }

    @Transactional
    public Simulacao criarSimulacaoInicial(Usuario usuario, String chatId, Campanha campanha, String fichaJson) {
        Simulacao simulacao = new Simulacao();
        simulacao.setUsuario(usuario);
        simulacao.setChatId(chatId);
        simulacao.setCampanha(campanha);
        simulacao.setStatus(StatusSimulacao.PENDENTE);
        simulacao.setEtapa(EtapaSimulacao.NA_FILA);
        simulacao.setFicha(fichaJson);
        simulacao.setCriadaEm(Instant.now(relogio));
        return simulacaoRepository.saveAndFlush(simulacao);
    }

    /** Re-simulação do Supervisor (5.9): da versão, sem campanha, para não virar a última simulação do autor. */
    @Transactional
    public Simulacao criarResimulacao(Usuario supervisor, String chatId, CampanhaVersao versao) {
        Simulacao simulacao = criarSimulacaoInicial(supervisor, chatId, null, versao.getRegra());
        simulacao.setVersao(versao);
        return simulacaoRepository.saveAndFlush(simulacao);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void atualizarEtapa(Long id, String etapa) {
        simulacaoRepository.findById(id).ifPresent(simulacao -> {
            simulacao.setStatus(StatusSimulacao.RODANDO);
            simulacao.setEtapa(etapa);
            simulacaoRepository.saveAndFlush(simulacao);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void concluirSimulacao(Long id, String resultadoJson, String trilhaJson,
                                  String codigoGerado, String modelo, Integer tentativas, Long duracaoMs) {
        simulacaoRepository.findById(id).ifPresent(simulacao -> {
            simulacao.setStatus(StatusSimulacao.CONCLUIDA);
            simulacao.setEtapa(EtapaSimulacao.CONCLUIDA);
            simulacao.setResultado(resultadoJson);
            simulacao.setTrilha(trilhaJson);
            simulacao.setCodigoGerado(codigoGerado);
            simulacao.setModelo(modelo);
            simulacao.setTentativas(tentativas);
            simulacao.setDuracaoMs(duracaoMs);
            simulacao.setErro(null);
            Instant agora = Instant.now(relogio);
            simulacao.setConcluidaEm(agora);
            simulacaoRepository.saveAndFlush(simulacao);

            gravarMensagemSimulacaoConcluida(simulacao, resultadoJson, agora);
        });
    }

    private void gravarMensagemSimulacaoConcluida(Simulacao simulacao, String resultadoJson, Instant agora) {
        if (mensagemChatRepository == null || camposDaSimulacao == null || resultadoJson == null) {
            return;
        }
        try {
            String texto = formatarTextoSimulacaoConcluida(resultadoJson);
            MensagemChat msg = new MensagemChat(
                    simulacao.getChatId(), simulacao.getUsuario(), "sistema", "SIMULACAO",
                    simulacao.getId(), texto, null, null, agora
            );
            mensagemChatRepository.save(msg);
        } catch (Exception e) {
            // A simulação já está concluída; sem a mensagem de sistema ela continua válida.
            log.warn("Mensagem SIMULACAO não gravada para a simulação {}: {}", simulacao.getId(), e.getMessage());
        }
    }

    private String formatarTextoSimulacaoConcluida(String resultadoJson) {
        BigDecimal custo = camposDaSimulacao.custoAdicional(resultadoJson);
        DecimalFormat df = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.forLanguageTag("pt-BR")));
        return "Simulação concluída: custo adicional de R$ " + df.format(custo);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void falharSimulacao(Long id, String erroJson) {
        simulacaoRepository.findById(id).ifPresent(simulacao -> {
            marcarErro(simulacao, erroJson);
        });
    }

    @Transactional
    public int encerrarSimulacoesInterrompidas() {
        List<Simulacao> interrompidas = simulacaoRepository.findByStatusIn(StatusSimulacao.EM_ANDAMENTO);
        interrompidas.forEach(simulacao -> marcarErro(simulacao, MOTIVO_INTERROMPIDA));
        return interrompidas.size();
    }

    private void marcarErro(Simulacao simulacao, String erroJson) {
        simulacao.setStatus(StatusSimulacao.ERRO);
        simulacao.setEtapa(EtapaSimulacao.ERRO);
        simulacao.setErro(erroJson);
        simulacao.setConcluidaEm(Instant.now(relogio));
        simulacaoRepository.saveAndFlush(simulacao);
    }
}
