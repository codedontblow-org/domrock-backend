package br.com.camplana.campanha;

import br.com.camplana.entity.CampanhaStatusLog;
import br.com.camplana.entity.Simulacao;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static br.com.camplana.entity.StatusCampanha.AGUARDANDO_APROVACAO;
import static br.com.camplana.entity.StatusCampanha.APROVADA;
import static org.assertj.core.api.Assertions.assertThat;

class PendenciasDaVersaoTest {

    private static final Instant SEMANA_DO_VENDEDOR_APROVADA = Instant.parse("2026-10-21T13:40:00Z");

    private static CampanhaStatusLog aprovacao(Instant em) {
        return new CampanhaStatusLog(null, null, AGUARDANDO_APROVACAO, APROVADA, null, em, null);
    }

    private static Simulacao resimulacaoCriadaEm(Instant criadaEm) {
        Simulacao simulacao = new Simulacao();
        simulacao.setCriadaEm(criadaEm);
        return simulacao;
    }

    @Test
    void semAprovacaoDepoisDaSubmissaoNaoPedeResimulacao() {
        assertThat(new PendenciasDaVersao(List.of(), null).resimulacaoNecessaria()).isFalse();
    }

    @Test
    void aprovacaoDepoisDaSubmissaoPedeResimulacao() {
        var pendencias = new PendenciasDaVersao(List.of(aprovacao(SEMANA_DO_VENDEDOR_APROVADA)), null);

        assertThat(pendencias.resimulacaoNecessaria()).isTrue();
    }

    @Test
    void resimulacaoAnteriorAUltimaAprovacaoNaoVale() {
        var pendencias = new PendenciasDaVersao(
                List.of(aprovacao(SEMANA_DO_VENDEDOR_APROVADA.minusSeconds(600)), aprovacao(SEMANA_DO_VENDEDOR_APROVADA)),
                resimulacaoCriadaEm(SEMANA_DO_VENDEDOR_APROVADA.minusSeconds(60)));

        assertThat(pendencias.resimulacaoNecessaria()).isTrue();
    }

    @Test
    void resimulacaoDepoisDaUltimaAprovacaoResolve() {
        var pendencias = new PendenciasDaVersao(List.of(aprovacao(SEMANA_DO_VENDEDOR_APROVADA)),
                resimulacaoCriadaEm(SEMANA_DO_VENDEDOR_APROVADA.plusSeconds(60)));

        assertThat(pendencias.resimulacaoNecessaria()).isFalse();
    }
}
