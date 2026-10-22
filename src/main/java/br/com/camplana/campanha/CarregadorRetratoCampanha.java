package br.com.camplana.campanha;

import br.com.camplana.entity.Campanha;
import br.com.camplana.repository.CampanhaStatusLogRepository;
import br.com.camplana.repository.CampanhaVersaoRepository;
import org.springframework.stereotype.Component;

/** Monta o {@link RetratoCampanha}: duas consultas no banco e duas no registro de simulações. */
@Component
public class CarregadorRetratoCampanha {

    private final CampanhaVersaoRepository versaoRepository;
    private final CampanhaStatusLogRepository logRepository;
    private final RegistroSimulacoes registroSimulacoes;

    public CarregadorRetratoCampanha(CampanhaVersaoRepository versaoRepository,
                                     CampanhaStatusLogRepository logRepository,
                                     RegistroSimulacoes registroSimulacoes) {
        this.versaoRepository = versaoRepository;
        this.logRepository = logRepository;
        this.registroSimulacoes = registroSimulacoes;
    }

    public RetratoCampanha carregar(Campanha campanha) {
        long id = campanha.getId();
        return new RetratoCampanha(campanha,
                versaoRepository.findByCampanhaIdOrderByNumeroAsc(id),
                logRepository.findByCampanhaIdOrderByEmAscIdAsc(id),
                registroSimulacoes.ultimaConcluida(id).orElse(null),
                registroSimulacoes.emAndamento(id).orElse(null));
    }
}
