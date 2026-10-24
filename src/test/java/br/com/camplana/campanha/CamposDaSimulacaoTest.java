package br.com.camplana.campanha;

import br.com.camplana.dto.campanha.PeriodoCampanha;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CamposDaSimulacaoTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final CamposDaSimulacao campos = new CamposDaSimulacao(json);

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

    @Test
    void escopoTrazPeriodoMarcasECargosDaFicha() {
        assertThat(campos.escopo(json.readTree(ficha("2025-11-24", "2025-11-30", "[\"10\", \"30\"]", "[\"150\"]"))))
                .contains(new EscopoCampanha(new PeriodoCampanha(LocalDate.of(2025, 11, 24), LocalDate.of(2025, 11, 30)),
                        Set.of("10", "30"), Set.of("150")));
    }

    @Test
    void escopoTrocaAllPelasSeisMarcas() {
        Optional<EscopoCampanha> escopo = campos.escopo(json.readTree(
                ficha("2025-11-24", "2025-11-30", "[\"ALL\"]", "[\"100\"]")));

        assertThat(escopo).map(EscopoCampanha::marcas).contains(Set.of("10", "20", "30", "40", "50", "60"));
    }

    @Test
    void escopoDeFichaSemCargosVemVazio() {
        String semCargos = """
                {"parametros": [
                  {"key": "periodo", "value": {"data_inicio": "2025-11-24", "data_fim": "2025-11-30"}},
                  {"key": "marcas_alvo", "value": ["10"]}
                ]}""";

        assertThat(campos.escopo(json.readTree(semCargos))).isEmpty();
    }

    @Test
    void escopoComPeriodoInvertidoVemVazio() {
        assertThat(campos.escopo(json.readTree(ficha("2025-12-01", "2025-11-30", "[\"10\"]", "[\"150\"]")))).isEmpty();
    }

    @Test
    void escopoComDataInvalidaVemVazio() {
        assertThat(campos.escopo(json.readTree(ficha("2025-11-31", "2025-12-01", "[\"10\"]", "[\"150\"]")))).isEmpty();
    }

    @Test
    void pctAcrescimoPassaComoVeioNaFicha() {
        assertThat(campos.pctAcrescimo(json.readTree(ficha("2025-11-24", "2025-11-30", "[\"10\"]", "[\"150\"]"))))
                .isEqualTo(json.readTree("0.5"));
    }

    private static String ficha(String inicio, String fim, String marcas, String cargos) {
        return """
                {"parametros": [
                  {"key": "periodo", "value": {"data_inicio": "%s", "data_fim": "%s"}},
                  {"key": "pct_acrescimo", "value": 0.5},
                  {"key": "marcas_alvo", "value": %s},
                  {"key": "cargos_alvo", "value": %s}
                ]}""".formatted(inicio, fim, marcas, cargos);
    }
}
