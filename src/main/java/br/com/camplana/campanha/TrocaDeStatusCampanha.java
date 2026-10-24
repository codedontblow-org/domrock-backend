package br.com.camplana.campanha;

import br.com.camplana.entity.Campanha;
import br.com.camplana.entity.CampanhaStatusLog;
import br.com.camplana.entity.CampanhaVersao;
import br.com.camplana.entity.StatusCampanha;
import br.com.camplana.entity.Usuario;
import br.com.camplana.repository.CampanhaStatusLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * O único lugar que muda o status de uma campanha: valida a transição (409), muda o status e grava
 * a linha do log, sempre juntos. O "decidir" do A6-68 também passa por aqui.
 *
 * <pre>trocaDeStatus.trocar(campanha, StatusCampanha.REPROVADA, sergio, versao, "a meta ...");</pre>
 */
@Component
public class TrocaDeStatusCampanha {

    private final CampanhaStatusLogRepository logRepository;
    private final Clock relogio;

    public TrocaDeStatusCampanha(CampanhaStatusLogRepository logRepository, Clock relogio) {
        this.logRepository = logRepository;
        this.relogio = relogio;
    }

    /** A linha "(nova) -> RASCUNHO" do log. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarCriacao(Campanha campanha, Usuario autor) {
        logRepository.save(new CampanhaStatusLog(campanha, null, null, StatusCampanha.RASCUNHO,
                autor, campanha.getCriadaEm(), null));
    }

    /** {@code versao} vai no submeter e no decidir; nas outras trocas, {@code null}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void trocar(Campanha campanha, StatusCampanha para, Usuario usuario, CampanhaVersao versao,
                       String justificativa) {
        TransicoesCampanha.exigir(campanha.getId(), campanha.getStatus(), para);
        Instant agora = relogio.instant();
        logRepository.save(new CampanhaStatusLog(campanha, versao, campanha.getStatus(), para,
                usuario, agora, justificativa));
        campanha.setStatus(para);
        campanha.setAtualizadaEm(agora);
    }
}
