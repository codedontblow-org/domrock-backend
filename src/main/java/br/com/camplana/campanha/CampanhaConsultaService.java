package br.com.camplana.campanha;

import br.com.camplana.dto.campanha.CampanhaAprovadaResponse;
import br.com.camplana.dto.campanha.CampanhaResponse;
import br.com.camplana.dto.campanha.CampanhaResumoResponse;
import br.com.camplana.dto.campanha.FilaCampanhaResponse;
import br.com.camplana.dto.campanha.VersaoCampanhaResponse;
import br.com.camplana.entity.Campanha;
import br.com.camplana.entity.CampanhaVersao;
import br.com.camplana.entity.StatusCampanha;
import br.com.camplana.entity.Usuario;
import br.com.camplana.exception.BadRequestException;
import br.com.camplana.exception.NotFoundException;
import br.com.camplana.repository.CampanhaRepository;
import br.com.camplana.repository.CampanhaVersaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Leituras da campanha (contrato 5.4, 5.5 e 5.6). O filtro padrão e quem vê o quê ficam aqui, não no
 * front.
 *
 * <pre>consulta.minhas(gabriela, null); // todas menos as arquivadas</pre>
 */
@Service
@Transactional(readOnly = true)
public class CampanhaConsultaService {

    private final CampanhaRepository campanhaRepository;
    private final CampanhaVersaoRepository versaoRepository;
    private final CarregadorRetratoCampanha carregador;
    private final CampanhaRespostaMontador montador;

    public CampanhaConsultaService(CampanhaRepository campanhaRepository, CampanhaVersaoRepository versaoRepository,
                                   CarregadorRetratoCampanha carregador, CampanhaRespostaMontador montador) {
        this.campanhaRepository = campanhaRepository;
        this.versaoRepository = versaoRepository;
        this.carregador = carregador;
        this.montador = montador;
    }

    public CampanhaResponse abrir(long campanhaId, Usuario leitor) {
        RetratoCampanha retrato = carregador.carregar(comAutor(campanhaId));
        PermissoesCampanha.exigirLeitura(retrato.situacao(), leitor);
        return montador.campanha(retrato, leitor);
    }

    public VersaoCampanhaResponse versao(long campanhaId, int numero, Usuario leitor) {
        RetratoCampanha retrato = carregador.carregar(comAutor(campanhaId));
        PermissoesCampanha.exigirLeitura(retrato.situacao(), leitor);
        CampanhaVersao versao = retrato.versao(numero).orElseThrow(() ->
                new NotFoundException("A campanha " + campanhaId + " não tem a versão " + numero));
        return montador.versao(retrato, versao);
    }

    /** Sem {@code status}: todas menos as arquivadas, da atualizada mais recentemente (contrato 5.4). */
    public List<CampanhaResumoResponse> minhas(Usuario autor, String status) {
        List<Campanha> campanhas = status == null
                ? campanhaRepository.findByAutorIdAndStatusNotOrderByAtualizadaEmDesc(autor.getId(), StatusCampanha.ARQUIVADA)
                : campanhaRepository.findByAutorIdAndStatusOrderByAtualizadaEmDesc(autor.getId(), statusValido(status));
        return campanhas.stream().map(campanha -> montador.resumo(carregador.carregar(campanha))).toList();
    }

    /** Fila do Supervisor (5.5): o que outros submeteram, da mais antiga para a mais nova. */
    public List<FilaCampanhaResponse> fila(Usuario supervisor) {
        return versaoRepository
                .findUltimasPorStatusMenosDoAutor(StatusCampanha.AGUARDANDO_APROVACAO, supervisor.getId())
                .stream().map(montador::itemDaFila).toList();
    }

    /**
     * Campanhas Passadas: todas as aprovadas, de qualquer autor, para Gerente e Supervisor. Aprovada
     * não volta a rascunho, então a última versão é a aprovada.
     */
    public List<CampanhaAprovadaResponse> aprovadas() {
        return versaoRepository.findUltimasPorStatus(StatusCampanha.APROVADA)
                .stream().map(montador::aprovada).toList();
    }

    private Campanha comAutor(long campanhaId) {
        return campanhaRepository.findComAutor(campanhaId)
                .orElseThrow(() -> new NotFoundException("Campanha " + campanhaId + " não encontrada"));
    }

    private static StatusCampanha statusValido(String status) {
        return Arrays.stream(StatusCampanha.values())
                .filter(valor -> valor.name().equals(status))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Status inválido: " + status
                        + "; esperado um de " + Arrays.toString(StatusCampanha.values())));
    }
}
