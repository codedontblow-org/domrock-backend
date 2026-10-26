package br.com.camplana.campanha;

import br.com.camplana.dto.campanha.CampanhaResponse;
import br.com.camplana.dto.campanha.DecisaoRequest;
import br.com.camplana.entity.Campanha;
import br.com.camplana.entity.CampanhaStatusLog;
import br.com.camplana.entity.CampanhaVersao;
import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.StatusCampanha;
import br.com.camplana.entity.Usuario;
import br.com.camplana.exception.BadRequestException;
import br.com.camplana.exception.ConflictException;
import br.com.camplana.exception.ForbiddenException;
import br.com.camplana.exception.NotFoundException;
import br.com.camplana.repository.CampanhaRepository;
import br.com.camplana.repository.CampanhaStatusLogRepository;
import br.com.camplana.repository.CampanhaVersaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Decisão do supervisor sobre uma versão (US-6 / A6-45). As verificações seguem a ordem da task,
 * para que a mensagem devolvida seja a mais específica possível (403/409/400 antes de gravar).
 */
@Service
public class DecisaoCampanhaService {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final CampanhaRepository campanhaRepository;
    private final CampanhaVersaoRepository versaoRepository;
    private final CampanhaStatusLogRepository logRepository;
    private final TrocaDeStatusCampanha trocaDeStatus;
    private final CarregadorRetratoCampanha carregador;
    private final CampanhaRespostaMontador montador;
    private final Clock relogio;

    public DecisaoCampanhaService(CampanhaRepository campanhaRepository,
                                  CampanhaVersaoRepository versaoRepository,
                                  CampanhaStatusLogRepository logRepository,
                                  TrocaDeStatusCampanha trocaDeStatus,
                                  CarregadorRetratoCampanha carregador,
                                  CampanhaRespostaMontador montador,
                                  Clock relogio) {
        this.campanhaRepository = campanhaRepository;
        this.versaoRepository = versaoRepository;
        this.logRepository = logRepository;
        this.trocaDeStatus = trocaDeStatus;
        this.carregador = carregador;
        this.montador = montador;
        this.relogio = relogio;
    }

    @Transactional
    public CampanhaResponse decidir(long campanhaId, int numeroVersao, DecisaoRequest pedido, Usuario decisor) {
        validarDecisaoPedida(pedido);

        Campanha campanha = campanhaRepository.findParaAlterar(campanhaId)
                .orElseThrow(() -> new NotFoundException("Campanha " + campanhaId + " não encontrada"));

        CampanhaVersao versao = versaoRepository.findByCampanhaIdAndNumero(campanhaId, numeroVersao)
                .orElseThrow(() -> new NotFoundException(
                        "Versão v" + numeroVersao + " da campanha " + campanhaId + " não encontrada"));

        // 1. Perfil Supervisor
        if (decisor.getPerfil() != Perfil.SUPERVISOR) {
            throw new ForbiddenException("Apenas supervisores podem decidir");
        }

        // 2. Decisor ≠ autor
        if (campanha.getAutor().getId().equals(decisor.getId())) {
            throw new ForbiddenException("Outra pessoa precisa aprovar esta campanha");
        }

        // 3. Versão ainda aguardando aprovação
        if (campanha.getStatus() != StatusCampanha.AGUARDANDO_APROVACAO) {
            throw new ConflictException(mensagemJaDecidida(versao));
        }

        // 4. Reprovar exige justificativa
        if (pedido.decisao() == StatusCampanha.REPROVADA
                && (pedido.justificativa() == null || pedido.justificativa().isBlank())) {
            throw new BadRequestException("Reprovação exige justificativa");
        }

        // 5. Re-simulação obrigatória (só ao aprovar)
        if (pedido.decisao() == StatusCampanha.APROVADA
                && logRepository.existeAprovacaoConflitanteDepoisDe(
                        campanhaId, versao.getDataInicio(), versao.getDataFim(), versao.getSubmetidaEm())) {
            throw new ConflictException("Simule de novo antes de aprovar: "
                    + "outra campanha do mesmo período foi aprovada depois desta submissão");
        }

        // 6. Grava o log + troca o status da campanha (o log carrega versão, decisor, data, decisão e justificativa)
        trocaDeStatus.trocar(campanha, pedido.decisao(), decisor, versao, pedido.justificativa());

        return montador.campanha(carregador.carregar(campanha), decisor);
    }

    private void validarDecisaoPedida(DecisaoRequest pedido) {
        if (pedido.decisao() != StatusCampanha.APROVADA
                && pedido.decisao() != StatusCampanha.REPROVADA) {
            throw new BadRequestException(
                    "Decisão inválida: esperado APROVADA ou REPROVADA, recebido " + pedido.decisao());
        }
    }

    private String mensagemJaDecidida(CampanhaVersao versao) {
        return logRepository.findFirstByVersaoIdOrderByEmDescIdDesc(versao.getId())
                .map(this::formatarJaDecidida)
                .orElse("Esta versão já foi decidida");
    }

    private String formatarJaDecidida(CampanhaStatusLog log) {
        return "Esta versão já foi decidida por " + log.getUsuario().getNome()
                + " em " + DATA_HORA.format(log.getEm());
    }
}