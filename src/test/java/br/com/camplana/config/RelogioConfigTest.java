package br.com.camplana.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class RelogioConfigTest {

    private final Clock relogio = new RelogioConfig().relogio();

    @Test
    void andaEmMilissegundos() {
        assertThat(relogio.instant().getNano() % 1_000_000).isZero();
    }

    @Test
    void datasDoNegocioNoFusoDeSaoPaulo() {
        assertThat(relogio.getZone()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
    }
}
