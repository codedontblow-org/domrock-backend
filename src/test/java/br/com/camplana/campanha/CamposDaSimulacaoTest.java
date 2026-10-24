package br.com.camplana.campanha;

import br.com.camplana.dto.campanha.PeriodoCampanha;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CamposDaSimulacaoTest {

    private final CamposDaSimulacao campos = new CamposDaSimulacao(JsonMapper.builder().build());

    @Test
    void periodoVemDoParametroPeriodoDaRegra() {
        String regra = """
                {"parametros": [
                  {"key": "pct_acrescimo", "value": 1.0},
                  {"key": "periodo", "value": {"data_inicio": "2025-11-24", "data_fim": "2025-11-30"}}
                ]}""";

        assertThat(campos.periodo(regra))
                .isEqualTo(new PeriodoCampanha(LocalDate.of(2025, 11, 24), LocalDate.of(2025, 11, 30)));
    }

    @Test
    void regraSemPeriodoFalhaMostrandoOQueChegou() {
        String regra = "{\"parametros\": [{\"key\": \"pct_acrescimo\", \"value\": 1.0}]}";

        assertThatThrownBy(() -> campos.periodo(regra))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parametros[key=periodo]")
                .hasMessageContaining("pct_acrescimo");
    }

    @Test
    void custoAdicionalEhADiferencaDosTotais() {
        String resultado = "{\"totais\": {\"baseline\": 480263.24, \"diferenca\": 23736.10}}";

        assertThat(campos.custoAdicional(resultado)).isEqualByComparingTo(new BigDecimal("23736.10"));
    }

    @Test
    void conflitosContaOsItensDaLista() {
        assertThat(campos.quantidadeConflitos("{\"conflitos\": [{}, {}]}")).isEqualTo(2);
    }

    @Test
    void resultadoSemConflitosContaZero() {
        assertThat(campos.quantidadeConflitos("{\"totais\": {}}")).isZero();
    }
}
