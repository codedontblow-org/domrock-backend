package br.com.camplana.campanha;

import br.com.camplana.dto.simulacao.SimulacaoCriadaResponse;
import br.com.camplana.entity.Simulacao;
import br.com.camplana.entity.Usuario;
import br.com.camplana.service.SimulacaoJobService;
import br.com.camplana.service.SimulacaoPersistenceService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Re-simulação na aprovação (contrato 5.9): simula de novo a ficha congelada da versão com a % vigente
 * de hoje. É um job de simulação como o do 4.1, ligado à versão; o front acompanha pelo 4.2.
 *
 * <pre>resimulacao.resimular(12L, 1, sergio); // 202 com o id do job</pre>
 */
@Service
public class ResimulacaoService {

    private final CampanhaConsultaService consulta;
    private final SimulacaoPersistenceService persistencia;
    private final SimulacaoJobService jobs;
    private final ObjectMapper objectMapper;

    public ResimulacaoService(CampanhaConsultaService consulta, SimulacaoPersistenceService persistencia,
                              SimulacaoJobService jobs, ObjectMapper objectMapper) {
        this.consulta = consulta;
        this.persistencia = persistencia;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
    }

    // Sem @Transactional, como o SimulacaoManagerService: o job assíncrono precisa achar a linha já gravada.
    public SimulacaoCriadaResponse resimular(long campanhaId, int numero, Usuario supervisor) {
        VersaoParaResimular pedido = consulta.versaoParaResimular(campanhaId, numero, supervisor);
        Simulacao criada = persistencia.criarResimulacao(supervisor, pedido.chatId(), pedido.versao());
        // O chat_id da campanha faz o contexto (4.3) excluir a própria campanha e somar as aprovadas depois.
        Map<String, Object> payloadLana = Map.of(
                "chat_id", pedido.chatId(),
                "regra", objectMapper.readTree(pedido.versao().getRegra()));
        try {
            jobs.processarSimulacaoAsync(criada.getId(), payloadLana);
        } catch (Exception ex) {
            persistencia.falharSimulacao(criada.getId(), SimulacaoJobService.ERRO_INTERNO);
            throw ex;
        }
        return new SimulacaoCriadaResponse(criada.getId(), criada.getStatus(), criada.getEtapa());
    }
}
