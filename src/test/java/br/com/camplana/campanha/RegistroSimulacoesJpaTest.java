package br.com.camplana.campanha;

import br.com.camplana.TestcontainersConfiguration;
import br.com.camplana.entity.*;
import br.com.camplana.repository.CampanhaRepository;
import br.com.camplana.repository.SimulacaoRepository;
import br.com.camplana.repository.UsuarioRepository;
import br.com.camplana.service.EncerradorSimulacoesInterrompidas;
import br.com.camplana.service.SimulacaoPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Valida a implementação JPA de {@link RegistroSimulacoes} sobre a tabela {@code simulacao} (V8). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class RegistroSimulacoesJpaTest {

    private static final String FICHA = FakeRegistroSimulacoes.REGRA_BLACK_FRIDAY;
    private static final String RESULTADO = FakeRegistroSimulacoes.RESULTADO_BLACK_FRIDAY;

    @Autowired private RegistroSimulacoesJpa registro;
    @Autowired private CamposDaSimulacao campos;
    @Autowired private SimulacaoPersistenceService persistenceService;
    @Autowired private EncerradorSimulacoesInterrompidas encerrador;
    @Autowired private SimulacaoRepository simulacaoRepository;
    @Autowired private CampanhaRepository campanhaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private JdbcTemplate jdbc;

    private Usuario gabriela;

    @BeforeEach
    void limpar() {
        jdbc.execute("DELETE FROM campanha_status_log");
        jdbc.execute("DELETE FROM campanha_versao");
        jdbc.execute("DELETE FROM simulacao");
        jdbc.execute("DELETE FROM campanha");
        gabriela = usuarioRepository.findById(1).orElseThrow();
    }

    @Test
    void buscarPreservaFichaEResultadoBrutosParaLeituraDosCamposDaCampanha() {
        Instant concluidaEm = Instant.parse("2026-10-21T12:59:02Z");
        Simulacao salva = salvarSimulacao("chat-1", null, StatusSimulacao.CONCLUIDA, concluidaEm);

        assertThat(jdbc.queryForObject("SELECT jsonb_typeof(ficha) FROM simulacao WHERE id = ?", String.class, salva.getId()))
                .isEqualTo("object");
        assertThat(jdbc.queryForObject("SELECT jsonb_typeof(resultado) FROM simulacao WHERE id = ?", String.class, salva.getId()))
                .isEqualTo("object");

        SimulacaoGravada gravada = registro.buscar(salva.getId()).orElseThrow();

        assertThat(gravada.id()).isEqualTo(salva.getId());
        assertThat(gravada.usuarioId()).isEqualTo(gabriela.getId());
        assertThat(gravada.chatId()).isEqualTo("chat-1");
        assertThat(gravada.status()).isEqualTo("CONCLUIDA");
        assertThat(gravada.concluida()).isTrue();
        assertThat(gravada.concluidaEm()).isEqualTo(concluidaEm);
        assertThat(campos.periodo(gravada.regraJson()).dataInicio().toString()).isEqualTo("2025-11-24");
        assertThat(campos.periodo(gravada.regraJson()).dataFim().toString()).isEqualTo("2025-11-30");
        assertThat(campos.custoAdicional(gravada.resultadoJson())).isEqualByComparingTo("23736.10");
        assertThat(campos.quantidadeConflitos(gravada.resultadoJson())).isZero();
    }

    @Test
    void vincularConversaEBuscarUltimaConcluidaEEmAndamento() {
        Instant t1 = Instant.parse("2026-10-21T12:50:00Z");
        Instant t2 = Instant.parse("2026-10-21T12:59:00Z");

        salvarSimulacao("chat-bf", null, StatusSimulacao.CONCLUIDA, t1);
        Simulacao maisRecente = salvarSimulacao("chat-bf", null, StatusSimulacao.CONCLUIDA, t2);
        Simulacao rodando = salvarSimulacao("chat-bf", null, StatusSimulacao.RODANDO, null);

        Campanha campanha = criarCampanha("chat-bf");
        registro.vincularConversa("chat-bf", campanha.getId());

        assertThat(registro.ultimaConcluida(campanha.getId()))
                .isPresent()
                .get()
                .extracting(SimulacaoGravada::id)
                .isEqualTo(maisRecente.getId());

        assertThat(registro.emAndamento(campanha.getId()))
                .contains(rodando.getId());
    }

    @Test
    void cicloDeVidaNoPersistenceServiceEEncerramentoDeInterrompidas() {
        Simulacao inicial = persistenceService.criarSimulacaoInicial(gabriela, "chat-job", null, FICHA);
        assertThat(inicial.getStatus()).isEqualTo(StatusSimulacao.PENDENTE);

        persistenceService.atualizarEtapa(inicial.getId(), EtapaSimulacao.EXECUTANDO);
        Simulacao emExecucao = simulacaoRepository.findById(inicial.getId()).orElseThrow();
        assertThat(emExecucao.getStatus()).isEqualTo(StatusSimulacao.RODANDO);
        assertThat(emExecucao.getEtapa()).isEqualTo(EtapaSimulacao.EXECUTANDO);

        encerrador.encerrarAoSubir();
        Simulacao encerrada = simulacaoRepository.findById(inicial.getId()).orElseThrow();
        assertThat(encerrada.getStatus()).isEqualTo(StatusSimulacao.ERRO);
        assertThat(encerrada.getEtapa()).isNull();
        assertThat(encerrada.getErro()).isEqualTo(SimulacaoPersistenceService.MOTIVO_INTERROMPIDA);
        assertThat(encerrada.getConcluidaEm()).isNotNull();
    }

    private Simulacao salvarSimulacao(String chatId, Campanha campanha, StatusSimulacao status, Instant concluidaEm) {
        Simulacao s = new Simulacao();
        s.setUsuario(gabriela);
        s.setChatId(chatId);
        s.setCampanha(campanha);
        s.setStatus(status);
        s.setFicha(FICHA);
        s.setResultado(status == StatusSimulacao.CONCLUIDA ? RESULTADO : null);
        s.setCriadaEm(Instant.parse("2026-10-21T12:45:00Z"));
        s.setConcluidaEm(concluidaEm);
        return simulacaoRepository.save(s);
    }

    private Campanha criarCampanha(String chatId) {
        Instant agora = Instant.parse("2026-10-21T13:05:00Z");
        Campanha c = new Campanha();
        c.setNome("Black Friday 2025");
        c.setAutor(gabriela);
        c.setChatId(chatId);
        c.setStatus(StatusCampanha.RASCUNHO);
        c.setCriadaEm(agora);
        c.setAtualizadaEm(agora);
        return campanhaRepository.save(c);
    }
}
