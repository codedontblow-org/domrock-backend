package br.com.camplana.campanha;

import br.com.camplana.dto.campanha.DecisaoRequest;
import br.com.camplana.entity.*;
import br.com.camplana.exception.BadRequestException;
import br.com.camplana.exception.ConflictException;
import br.com.camplana.exception.ForbiddenException;
import br.com.camplana.repository.CampanhaRepository;
import br.com.camplana.repository.CampanhaStatusLogRepository;
import br.com.camplana.repository.CampanhaVersaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisaoCampanhaServiceTest {

    @Mock CampanhaRepository campanhaRepository;
    @Mock CampanhaVersaoRepository versaoRepository;
    @Mock CampanhaStatusLogRepository logRepository;
    @Mock TrocaDeStatusCampanha trocaDeStatus;
    @Mock CarregadorRetratoCampanha carregador;
    @Mock CampanhaRespostaMontador montador;

    private DecisaoCampanhaService service;

    private final Instant AGORA = Instant.parse("2026-10-25T12:00:00Z");
    private final Instant SUBMETIDA_EM = Instant.parse("2026-10-24T10:00:00Z");
    private Campanha campanha;
    private CampanhaVersao versao;
    private Usuario gabriela;
    private Usuario sergio;
    private Usuario silvia;

    @BeforeEach
    void setup() {
        service = new DecisaoCampanhaService(campanhaRepository, versaoRepository, logRepository,
                trocaDeStatus, carregador, montador, Clock.fixed(AGORA, ZoneOffset.UTC));

        gabriela = usuario(2L, "Gabriela", Perfil.GERENTE);
        sergio = usuario(3L, "Sérgio", Perfil.SUPERVISOR);
        silvia = usuario(4L, "Sílvia", Perfil.SUPERVISOR);

        campanha = new Campanha();
        setId(campanha, 10L);
        campanha.setNome("Black Friday 2025");
        campanha.setAutor(gabriela);
        campanha.setStatus(StatusCampanha.AGUARDANDO_APROVACAO);

        versao = new CampanhaVersao(campanha, 1, 100L, "{}", "{}",
                LocalDate.of(2025, 11, 24), LocalDate.of(2025, 11, 30), SUBMETIDA_EM);
        setId(versao, 500L);

        lenient().when(campanhaRepository.findParaAlterar(10L)).thenReturn(Optional.of(campanha));
        lenient().when(versaoRepository.findByCampanhaIdAndNumero(10L, 1)).thenReturn(Optional.of(versao));
    }

    @Test
    void supervisor_aprova_versao_aguardando() {
        when(logRepository.existeAprovacaoConflitanteDepoisDe(eq(10L), any(), any(), any())).thenReturn(false);
        service.decidir(10L, 1, new DecisaoRequest(StatusCampanha.APROVADA, null), sergio);
        verify(trocaDeStatus).trocar(campanha, StatusCampanha.APROVADA, sergio, versao, null);
    }

    @Test
    void reprovar_sem_justificativa_400() {
        assertThatThrownBy(() ->
                service.decidir(10L, 1, new DecisaoRequest(StatusCampanha.REPROVADA, "   "), sergio))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("justificativa");
        verifyNoInteractions(trocaDeStatus);
    }

    @Test
    void autor_nao_pode_decidir_a_propria_403() {
        campanha.setAutor(sergio);
        assertThatThrownBy(() ->
                service.decidir(10L, 1, new DecisaoRequest(StatusCampanha.APROVADA, null), sergio))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Outra pessoa precisa aprovar esta campanha");
        verifyNoInteractions(trocaDeStatus);
    }

    @Test
    void versao_ja_decidida_409_com_mensagem_do_ultimo_decisor() {
        campanha.setStatus(StatusCampanha.APROVADA);
        CampanhaStatusLog logSilvia = new CampanhaStatusLog(
                campanha, versao, StatusCampanha.AGUARDANDO_APROVACAO, StatusCampanha.APROVADA,
                silvia, Instant.parse("2026-10-24T11:00:00Z"), null);
        when(logRepository.findFirstByVersaoIdOrderByEmDescIdDesc(500L)).thenReturn(Optional.of(logSilvia));
        assertThatThrownBy(() ->
                service.decidir(10L, 1, new DecisaoRequest(StatusCampanha.REPROVADA, "fora do orçamento"), sergio))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Sílvia");
        verifyNoInteractions(trocaDeStatus);
    }

    @Test
    void gerente_nao_pode_decidir_403() {
        assertThatThrownBy(() ->
                service.decidir(10L, 1, new DecisaoRequest(StatusCampanha.APROVADA, null), gabriela))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("supervisores");
    }

    @Test
    void aprovar_sem_re_simular_409() {
        when(logRepository.existeAprovacaoConflitanteDepoisDe(eq(10L), any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() ->
                service.decidir(10L, 1, new DecisaoRequest(StatusCampanha.APROVADA, null), sergio))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Simule de novo");
        verifyNoInteractions(trocaDeStatus);
    }

    private static Usuario usuario(Number id, String nome, Perfil perfil) {
        Usuario u = new Usuario();
        setId(u, id);
        u.setNome(nome);
        u.setPerfil(perfil);
        return u;
    }

    private static void setId(Object alvo, Number id) {
        try {
            var f = alvo.getClass().getDeclaredField("id");
            f.setAccessible(true);
            if (f.getType() == Integer.class) {
                f.set(alvo, id.intValue());
            } else if (f.getType() == Long.class) {
                f.set(alvo, id.longValue());
            } else {
                f.set(alvo, id);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}