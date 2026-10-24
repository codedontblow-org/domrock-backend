package br.com.camplana.campanha;

import br.com.camplana.dto.campanha.PeriodoCampanha;
import br.com.camplana.entity.StatusCampanha;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class VigenciaTest {

    private static final PeriodoCampanha BLACK_FRIDAY =
            new PeriodoCampanha(LocalDate.of(2025, 11, 24), LocalDate.of(2025, 11, 30));

    @ParameterizedTest(name = "hoje {0} -> {1}")
    @CsvSource({
            "2025-11-23, FUTURA",
            "2025-11-24, VIGENTE",
            "2025-11-30, VIGENTE",
            "2025-12-01, ENCERRADA",
            "2026-10-21, ENCERRADA"
    })
    void aprovadaTemVigenciaPelaDataDeHoje(LocalDate hoje, Vigencia esperada) {
        assertThat(Vigencia.calcular(StatusCampanha.APROVADA, BLACK_FRIDAY, hoje)).isEqualTo(esperada);
    }

    @ParameterizedTest
    @EnumSource(value = StatusCampanha.class, names = "APROVADA", mode = EnumSource.Mode.EXCLUDE)
    void foraDeAprovadaNaoTemVigencia(StatusCampanha status) {
        assertThat(Vigencia.calcular(status, BLACK_FRIDAY, LocalDate.of(2025, 11, 25))).isNull();
    }

    @Test
    void semPeriodoNaoTemVigencia() {
        assertThat(Vigencia.calcular(StatusCampanha.APROVADA, null, LocalDate.of(2025, 11, 25))).isNull();
    }
}
