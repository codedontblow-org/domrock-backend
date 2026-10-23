package br.com.camplana.service;

import br.com.camplana.entity.Campanha;
import br.com.camplana.entity.Simulacao;
import br.com.camplana.entity.StatusSimulacao;
import br.com.camplana.entity.Usuario;
import br.com.camplana.repository.SimulacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Transações curtas e isoladas da simulação (A6-66): cada mudança de status ou etapa abre e fecha
 * sua própria transação para não segurar conexão do banco durante a chamada HTTP à Lana.
 */
@Service
public class SimulacaoPersistenceService {

    public static final String MOTIVO_INTERROMPIDA =
            "Simulação interrompida porque o backend reiniciou durante o processamento; simule novamente.";

    private static final List<StatusSimulacao> STATUS_EM_ANDAMENTO = List.of(
            StatusSimulacao.PENDENTE,
            StatusSimulacao.RODANDO
    );

    private final SimulacaoRepository simulacaoRepository;
    private final Clock relogio;

    public SimulacaoPersistenceService(SimulacaoRepository simulacaoRepository, Clock relogio) {
        this.simulacaoRepository = simulacaoRepository;
        this.relogio = relogio;
    }

    @Transactional
    public Simulacao criarSimulacaoInicial(Usuario usuario, String chatId, Campanha campanha, String fichaJson) {
        Simulacao simulacao = new Simulacao();
        simulacao.setUsuario(usuario);
        simulacao.setChatId(chatId);
        simulacao.setCampanha(campanha);
        simulacao.setStatus(StatusSimulacao.PENDENTE);
        simulacao.setFicha(fichaJson);
        simulacao.setCriadaEm(Instant.now(relogio));
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
            simulacao.setEtapa(null);
            simulacao.setResultado(resultadoJson);
            simulacao.setTrilha(trilhaJson);
            simulacao.setCodigoGerado(codigoGerado);
            simulacao.setModelo(modelo);
            simulacao.setTentativas(tentativas);
            simulacao.setDuracaoMs(duracaoMs);
            simulacao.setErro(null);
            simulacao.setConcluidaEm(Instant.now(relogio));
            simulacaoRepository.saveAndFlush(simulacao);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void falharSimulacao(Long id, String mensagemErro) {
        simulacaoRepository.findById(id).ifPresent(simulacao -> marcarErro(simulacao, mensagemErro));
    }

    @Transactional
    public int encerrarSimulacoesInterrompidas() {
        List<Simulacao> interrompidas = simulacaoRepository.findByStatusIn(STATUS_EM_ANDAMENTO);
        interrompidas.forEach(simulacao -> marcarErro(simulacao, MOTIVO_INTERROMPIDA));
        return interrompidas.size();
    }

    private void marcarErro(Simulacao simulacao, String mensagemErro) {
        simulacao.setStatus(StatusSimulacao.ERRO);
        simulacao.setEtapa(null);
        simulacao.setErro(mensagemErro);
        simulacao.setConcluidaEm(Instant.now(relogio));
        simulacaoRepository.saveAndFlush(simulacao);
    }
}
