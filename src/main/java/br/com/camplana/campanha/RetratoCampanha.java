package br.com.camplana.campanha;

import br.com.camplana.entity.Campanha;
import br.com.camplana.entity.CampanhaStatusLog;
import br.com.camplana.entity.CampanhaVersao;
import br.com.camplana.entity.StatusCampanha;

import java.util.List;
import java.util.Optional;

/**
 * Tudo o que se lê de uma campanha, carregado de uma vez: permissões e resposta usam os mesmos dados.
 *
 * @param versoes                da mais antiga para a mais nova
 * @param log                    em ordem de gravação
 * @param ultimaSimulacao        última simulação concluída, ou {@code null}
 * @param simulacaoEmAndamentoId simulação PENDENTE ou RODANDO, ou {@code null}
 */
public record RetratoCampanha(
        Campanha campanha,
        List<CampanhaVersao> versoes,
        List<CampanhaStatusLog> log,
        SimulacaoGravada ultimaSimulacao,
        Long simulacaoEmAndamentoId
) {

    public Optional<CampanhaVersao> versaoAtual() {
        return versoes.isEmpty() ? Optional.empty() : Optional.of(versoes.get(versoes.size() - 1));
    }

    public Optional<CampanhaVersao> versao(int numero) {
        return versoes.stream().filter(versao -> versao.getNumero() == numero).findFirst();
    }

    /**
     * Ficha e resultado que valem na tela (período e custo): fora de RASCUNHO, os da versão
     * congelada; em RASCUNHO ou sem versão, os da última simulação.
     */
    public Optional<String> regraExibida() {
        return versaoExibida().map(CampanhaVersao::getRegra)
                .or(() -> Optional.ofNullable(ultimaSimulacao).map(SimulacaoGravada::regraJson));
    }

    public Optional<String> resultadoExibido() {
        return versaoExibida().map(CampanhaVersao::getResultado)
                .or(() -> Optional.ofNullable(ultimaSimulacao).map(SimulacaoGravada::resultadoJson));
    }

    private Optional<CampanhaVersao> versaoExibida() {
        return campanha.getStatus() == StatusCampanha.RASCUNHO ? Optional.empty() : versaoAtual();
    }

    public SituacaoCampanha situacao() {
        Optional<CampanhaVersao> atual = versaoAtual();
        return new SituacaoCampanha(campanha.getId(), campanha.getStatus(), campanha.getAutor().getId(),
                atual.map(CampanhaVersao::getNumero).orElse(null),
                atual.map(CampanhaVersao::getSimulacaoId).orElse(null),
                ultimaSimulacao == null ? null : ultimaSimulacao.id(),
                simulacaoEmAndamentoId);
    }
}
