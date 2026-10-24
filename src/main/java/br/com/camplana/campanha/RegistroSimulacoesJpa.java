package br.com.camplana.campanha;

import br.com.camplana.entity.Simulacao;
import br.com.camplana.entity.StatusSimulacao;
import br.com.camplana.repository.SimulacaoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implementação JPA de {@link RegistroSimulacoes} sobre a tabela {@code simulacao} (A6-66).
 */
@Component
public class RegistroSimulacoesJpa implements RegistroSimulacoes {

    private final SimulacaoRepository simulacaoRepository;

    public RegistroSimulacoesJpa(SimulacaoRepository simulacaoRepository) {
        this.simulacaoRepository = simulacaoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SimulacaoGravada> buscar(long simulacaoId) {
        return simulacaoRepository.findById(simulacaoId)
                .map(RegistroSimulacoesJpa::paraGravada);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SimulacaoGravada> ultimaConcluida(long campanhaId) {
        return simulacaoRepository
                .findFirstByCampanhaIdAndStatusOrderByConcluidaEmDescIdDesc(campanhaId, StatusSimulacao.CONCLUIDA)
                .map(RegistroSimulacoesJpa::paraGravada);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> emAndamento(long campanhaId) {
        return simulacaoRepository
                .findFirstByCampanhaIdAndStatusInOrderByIdDesc(campanhaId, StatusSimulacao.EM_ANDAMENTO)
                .map(Simulacao::getId);
    }

    @Override
    @Transactional
    public void vincularConversa(String chatId, long campanhaId) {
        simulacaoRepository.vincularConversa(chatId, campanhaId);
    }

    private static SimulacaoGravada paraGravada(Simulacao simulacao) {
        return new SimulacaoGravada(
                simulacao.getId(),
                simulacao.getUsuario().getId(),
                simulacao.getChatId(),
                simulacao.getStatus().name(),
                simulacao.getFicha(),
                simulacao.getResultado(),
                simulacao.getConcluidaEm()
        );
    }
}
