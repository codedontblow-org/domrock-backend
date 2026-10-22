package br.com.camplana.campanha;

import br.com.camplana.dto.campanha.*;
import br.com.camplana.entity.Campanha;
import br.com.camplana.entity.CampanhaStatusLog;
import br.com.camplana.entity.CampanhaVersao;
import br.com.camplana.entity.StatusCampanha;
import br.com.camplana.entity.Usuario;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Monta o JSON da seção 5 do contrato. Tudo o que a tela mostra sai pronto daqui (front burro):
 * vigência, "em simulação", período, custo e as {@code acoes} de quem está vendo.
 *
 * <pre>montador.campanha(carregador.carregar(campanha), usuarioLogado);</pre>
 */
@Component
public class CampanhaRespostaMontador {

    private final CamposDaSimulacao campos;
    private final Clock relogio;

    public CampanhaRespostaMontador(CamposDaSimulacao campos, Clock relogio) {
        this.campos = campos;
        this.relogio = relogio;
    }

    /** Campanha (5.2), com as {@code acoes} de {@code leitor}. */
    public CampanhaResponse campanha(RetratoCampanha retrato, Usuario leitor) {
        Campanha campanha = retrato.campanha();
        return new CampanhaResponse(campanha.getId(), campanha.getNome(), campanha.getStatus(),
                vigencia(retrato), retrato.simulacaoEmAndamentoId() != null,
                PermissoesCampanha.acoes(retrato.situacao(), leitor.getId()),
                usuario(campanha.getAutor()), campanha.getChatId(), periodo(retrato),
                retrato.situacao().versaoAtual(), ultimaSimulacao(retrato.ultimaSimulacao()),
                versoes(retrato), log(retrato), campanha.getCriadaEm(), campanha.getAtualizadaEm());
    }

    /** Item de "minhas campanhas" (5.4). */
    public CampanhaResumoResponse resumo(RetratoCampanha retrato) {
        Campanha campanha = retrato.campanha();
        return new CampanhaResumoResponse(campanha.getId(), campanha.getNome(), campanha.getStatus(),
                vigencia(retrato), retrato.simulacaoEmAndamentoId() != null,
                retrato.situacao().versaoAtual(), periodo(retrato),
                retrato.resultadoExibido().map(campos::custoAdicional).orElse(null),
                campanha.getAtualizadaEm());
    }

    /** Item da fila (5.5). {@code resimulacao_necessaria} fica com o A6-68. */
    public FilaCampanhaResponse itemDaFila(CampanhaVersao versao) {
        Campanha campanha = versao.getCampanha();
        return new FilaCampanhaResponse(campanha.getId(), campanha.getNome(), versao.getNumero(),
                usuario(campanha.getAutor()), versao.getSubmetidaEm(),
                new PeriodoCampanha(versao.getDataInicio(), versao.getDataFim()),
                campos.custoAdicional(versao.getResultado()), campos.quantidadeConflitos(versao.getResultado()),
                false);
    }

    /** Versão congelada (5.6). Ações, re-simulação e aprovadas depois ficam com o A6-68. */
    public VersaoCampanhaResponse versao(RetratoCampanha retrato, CampanhaVersao versao) {
        List<CampanhaStatusLog> daVersao = logDaVersao(retrato.log(), versao);
        return new VersaoCampanhaResponse(retrato.campanha().getId(), versao.getNumero(),
                statusDaVersao(daVersao), List.of(), usuario(retrato.campanha().getAutor()),
                versao.getSubmetidaEm(), versao.getSimulacaoId(), versao.getRegra(), versao.getResultado(),
                versao.getUltimaMensagemId(), false, List.of(), null, decisao(daVersao));
    }

    private Vigencia vigencia(RetratoCampanha retrato) {
        return Vigencia.calcular(retrato.campanha().getStatus(), periodo(retrato), LocalDate.now(relogio));
    }

    private PeriodoCampanha periodo(RetratoCampanha retrato) {
        return retrato.regraExibida().map(campos::periodo).orElse(null);
    }

    private static UltimaSimulacaoResponse ultimaSimulacao(SimulacaoGravada simulacao) {
        if (simulacao == null) {
            return null;
        }
        return new UltimaSimulacaoResponse(simulacao.id(), simulacao.regraJson(), simulacao.resultadoJson(),
                simulacao.concluidaEm());
    }

    private static List<VersaoResumoResponse> versoes(RetratoCampanha retrato) {
        return retrato.versoes().stream().map(versao -> {
            List<CampanhaStatusLog> daVersao = logDaVersao(retrato.log(), versao);
            return new VersaoResumoResponse(versao.getNumero(), statusDaVersao(daVersao),
                    versao.getSubmetidaEm(), decisao(daVersao));
        }).toList();
    }

    private static List<LogStatusResponse> log(RetratoCampanha retrato) {
        return retrato.log().stream().map(linha -> new LogStatusResponse(linha.getDe(), linha.getPara(),
                linha.getVersao() == null ? null : linha.getVersao().getNumero(),
                usuario(linha.getUsuario()), linha.getEm(), linha.getJustificativa())).toList();
    }

    private static List<CampanhaStatusLog> logDaVersao(List<CampanhaStatusLog> log, CampanhaVersao versao) {
        return log.stream()
                .filter(linha -> linha.getVersao() != null && Objects.equals(linha.getVersao().getId(), versao.getId()))
                .toList();
    }

    // A versão nasce com a linha do submeter, então sempre há ao menos uma linha
    private static StatusCampanha statusDaVersao(List<CampanhaStatusLog> daVersao) {
        return daVersao.get(daVersao.size() - 1).getPara();
    }

    // vale a primeira decisão da versão (contrato 5.8)
    private static DecisaoResponse decisao(List<CampanhaStatusLog> daVersao) {
        return daVersao.stream()
                .filter(linha -> linha.getPara() == StatusCampanha.APROVADA || linha.getPara() == StatusCampanha.REPROVADA)
                .findFirst()
                .map(linha -> new DecisaoResponse(linha.getPara(), usuario(linha.getUsuario()), linha.getEm(),
                        linha.getJustificativa()))
                .orElse(null);
    }

    private static UsuarioResumo usuario(Usuario usuario) {
        return new UsuarioResumo(usuario.getId(), usuario.getNome());
    }
}
