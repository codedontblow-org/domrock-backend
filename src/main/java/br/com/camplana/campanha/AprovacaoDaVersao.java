package br.com.camplana.campanha;

import br.com.camplana.entity.CampanhaStatusLog;
import br.com.camplana.entity.CampanhaVersao;
import br.com.camplana.entity.Simulacao;
import br.com.camplana.entity.StatusCampanha;
import br.com.camplana.entity.StatusSimulacao;
import br.com.camplana.repository.CampanhaStatusLogRepository;
import br.com.camplana.repository.SimulacaoRepository;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Lê o que pesa na decisão de uma versão (contrato 5.6 e 5.8): se ela ainda aguarda, que outras
 * campanhas do período foram aprovadas depois da submissão e a última re-simulação (5.9).
 *
 * <pre>
 * PendenciasDaVersao pendencias = aprovacao.pendencias(retrato, versao);
 * PermissoesVersao.exigir(AcaoVersao.APROVAR, aprovacao.situacao(retrato, versao, pendencias), sergio);
 * </pre>
 */
@Component
public class AprovacaoDaVersao {

    // O dia da decisão no horário de Brasília, como o usuário lê na mensagem do 409.
    private static final DateTimeFormatter DIA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("America/Sao_Paulo"));

    private final CampanhaStatusLogRepository logRepository;
    private final SimulacaoRepository simulacaoRepository;

    public AprovacaoDaVersao(CampanhaStatusLogRepository logRepository, SimulacaoRepository simulacaoRepository) {
        this.logRepository = logRepository;
        this.simulacaoRepository = simulacaoRepository;
    }

    public PendenciasDaVersao pendencias(RetratoCampanha retrato, CampanhaVersao versao) {
        return pendencias(versao, naoPendente(retrato, versao) == null);
    }

    /** Para a fila (5.5), em que toda versão está aguardando aprovação. */
    public PendenciasDaVersao pendenciasDeVersaoAguardando(CampanhaVersao versao) {
        return pendencias(versao, true);
    }

    public SituacaoVersao situacao(RetratoCampanha retrato, CampanhaVersao versao, PendenciasDaVersao pendencias) {
        return new SituacaoVersao(retrato.campanha().getId(), versao.getNumero(),
                retrato.campanha().getAutor().getId(), naoPendente(retrato, versao),
                pendencias.resimulacaoNecessaria());
    }

    /** O resultado sobre o qual a decisão vale: o da última re-simulação (5.9), senão o da submissão. */
    public String resultadoDecidido(CampanhaVersao versao) {
        return ultimaResimulacao(versao).map(Simulacao::getResultado).orElse(versao.getResultado());
    }

    private Optional<Simulacao> ultimaResimulacao(CampanhaVersao versao) {
        return simulacaoRepository
                .findFirstByVersaoIdAndStatusOrderByConcluidaEmDescIdDesc(versao.getId(), StatusSimulacao.CONCLUIDA);
    }

    private PendenciasDaVersao pendencias(CampanhaVersao versao, boolean pendente) {
        Simulacao resimulacao = ultimaResimulacao(versao).orElse(null);
        if (!pendente) {
            return new PendenciasDaVersao(List.of(), resimulacao);
        }
        List<CampanhaStatusLog> aprovadas = logRepository.findAprovacoesQueCruzamDepoisDe(
                versao.getCampanha().getId(), versao.getSubmetidaEm(), versao.getDataInicio(), versao.getDataFim());
        return new PendenciasDaVersao(aprovadas, resimulacao);
    }

    // Só a última versão, com a campanha ainda aguardando, pode ser decidida; vale a primeira decisão.
    private static String naoPendente(RetratoCampanha retrato, CampanhaVersao versao) {
        boolean pendente = retrato.campanha().getStatus() == StatusCampanha.AGUARDANDO_APROVACAO
                && retrato.versaoAtual().map(atual -> atual.getId().equals(versao.getId())).orElse(false);
        if (pendente) {
            return null;
        }
        return retrato.log().stream()
                .filter(linha -> linha.getVersao() != null && linha.getVersao().getId().equals(versao.getId()))
                .filter(linha -> linha.getPara() == StatusCampanha.APROVADA || linha.getPara() == StatusCampanha.REPROVADA)
                .findFirst()
                .map(decisao -> "Esta versão já foi decidida por " + decisao.getUsuario().getNome()
                        + " em " + DIA.format(decisao.getEm()))
                .orElseGet(() -> "A v" + versao.getNumero() + " da campanha " + retrato.campanha().getId()
                        + " não está aguardando aprovação");
    }
}
