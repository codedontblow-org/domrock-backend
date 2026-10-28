package br.com.camplana.campanha;

import br.com.camplana.TestcontainersConfiguration;
import br.com.camplana.dto.campanha.IntercorrenciaResponse;
import br.com.camplana.dto.campanha.PeriodoCampanha;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validação com Testcontainers da implementação SQL de {@link FonteIntercorrencias}.
 * Testa o cruzamento temporal de eventos de RH com o período e vínculos de cargo e loja no escopo da campanha.
 */
@Import(TestcontainersConfiguration.class)
// Transactional: o @BeforeEach apaga e recria as tabelas base; o rollback devolve o banco de teste
// compartilhado como estava, para as outras classes não dependerem da ordem de execução.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FonteIntercorrenciasJdbcTest {

    @Autowired
    private FonteIntercorrenciasJdbc fonte;

    @Autowired
    private JdbcTemplate jdbc;

    private final PeriodoCampanha periodo = new PeriodoCampanha(
            LocalDate.parse("2025-07-10"),
            LocalDate.parse("2025-07-25")
    );
    private final EscopoCampanha escopo = new EscopoCampanha(periodo, Set.of("10"), Set.of("100"));

    @BeforeEach
    void limparEPrepararDados() {
        jdbc.execute("DELETE FROM evento_rh");
        jdbc.execute("DELETE FROM funcionario_cargo");
        jdbc.execute("DELETE FROM funcionario_loja");
        jdbc.execute("DELETE FROM funcionario");
        jdbc.execute("DELETE FROM loja");
        jdbc.execute("DELETE FROM cargo");
        jdbc.execute("DELETE FROM marca");

        // Marcas: 10 (no escopo), 20 (fora do escopo)
        jdbc.execute("INSERT INTO marca (id, cod_marca, descr_marca) VALUES (1, '10', 'Marca Preto')");
        jdbc.execute("INSERT INTO marca (id, cod_marca, descr_marca) VALUES (2, '20', 'Marca Branco')");

        // Cargos: 100 (no escopo), 150 (fora do escopo)
        jdbc.execute("INSERT INTO cargo (id, cod_cargo, descr_cargo) VALUES (1, '100', 'Vendedor loja')");
        jdbc.execute("INSERT INTO cargo (id, cod_cargo, descr_cargo) VALUES (2, '150', 'Gerente')");

        // Lojas: L10 (marca 10), L20 (marca 20)
        jdbc.execute("INSERT INTO loja (id, cod_loja, descr_loja, marca_id) VALUES (1, 'L10', 'Loja 10', 1)");
        jdbc.execute("INSERT INTO loja (id, cod_loja, descr_loja, marca_id) VALUES (2, 'L20', 'Loja 20', 2)");
    }

    private void cadastrarFuncionario(int id, String matricula, int cargoId, int lojaId, LocalDate mesRef) {
        jdbc.update("INSERT INTO funcionario (id, matricula, data_admissao) VALUES (?, ?, '2020-01-01')", id, matricula);
        jdbc.update("INSERT INTO funcionario_cargo (funcionario_id, cargo_id, date_ref) VALUES (?, ?, ?)", id, cargoId, mesRef);
        jdbc.update("INSERT INTO funcionario_loja (funcionario_id, loja_id, date_ref) VALUES (?, ?, ?)", id, lojaId, mesRef);
    }

    @Test
    void eventoDentroDoPeriodoERetornaCargoNoPrimeiroMes() {
        cadastrarFuncionario(1, "M-DENTRO", 1, 1, LocalDate.parse("2025-07-01"));
        jdbc.execute("""
            INSERT INTO evento_rh (id, matricula, tipo, data_inicio, data_fim, cod_loja, origem)
            VALUES (1, 'M-DENTRO', 'ferias', '2025-07-12', '2025-07-20', 'L10', 'rh_julho.xlsx')
            """);

        List<IntercorrenciaResponse> resultado = fonte.buscarPorEscopo(escopo);

        assertThat(resultado).hasSize(1);
        IntercorrenciaResponse evento = resultado.get(0);
        assertThat(evento.matricula()).isEqualTo("M-DENTRO");
        assertThat(evento.tipo()).isEqualTo("ferias");
        assertThat(evento.cargo()).isEqualTo("100");
        assertThat(evento.dataInicio()).isEqualTo(LocalDate.parse("2025-07-12"));
        assertThat(evento.dataFim()).isEqualTo(LocalDate.parse("2025-07-20"));
        assertThat(evento.origem()).isEqualTo("rh_julho.xlsx");
    }

    @Test
    void eventoForaDoPeriodoExcluido() {
        cadastrarFuncionario(1, "M-FORA", 1, 1, LocalDate.parse("2025-07-01"));
        // Terminou antes do início (data_fim < ini)
        jdbc.execute("""
            INSERT INTO evento_rh (id, matricula, tipo, data_inicio, data_fim, cod_loja, origem)
            VALUES (1, 'M-FORA', 'ferias', '2025-06-01', '2025-06-30', 'L10', 'rh_junho.xlsx')
            """);
        // Começa após o fim da campanha (data_inicio > fim)
        jdbc.execute("""
            INSERT INTO evento_rh (id, matricula, tipo, data_inicio, data_fim, cod_loja, origem)
            VALUES (2, 'M-FORA', 'afastamento', '2025-08-01', '2025-08-10', 'L10', 'rh_agosto.xlsx')
            """);

        List<IntercorrenciaResponse> resultado = fonte.buscarPorEscopo(escopo);

        assertThat(resultado).isEmpty();
    }

    @Test
    void demissaoDentroEForaDoPeriodo() {
        cadastrarFuncionario(1, "M-DEM-DENTRO", 1, 1, LocalDate.parse("2025-07-01"));
        cadastrarFuncionario(2, "M-DEM-FORA", 1, 1, LocalDate.parse("2025-07-01"));

        // Demissão dentro: data_inicio entre 2025-07-10 e 2025-07-25
        jdbc.execute("""
            INSERT INTO evento_rh (id, matricula, tipo, data_inicio, data_fim, cod_loja, origem)
            VALUES (1, 'M-DEM-DENTRO', 'demissao', '2025-07-15', NULL, 'L10', 'rh_julho.xlsx')
            """);
        // Demissão fora: data_inicio anterior ao início da campanha
        jdbc.execute("""
            INSERT INTO evento_rh (id, matricula, tipo, data_inicio, data_fim, cod_loja, origem)
            VALUES (2, 'M-DEM-FORA', 'demissao', '2025-07-05', NULL, 'L10', 'rh_julho.xlsx')
            """);

        List<IntercorrenciaResponse> resultado = fonte.buscarPorEscopo(escopo);

        assertThat(resultado).hasSize(1);
        IntercorrenciaResponse demissao = resultado.get(0);
        assertThat(demissao.matricula()).isEqualTo("M-DEM-DENTRO");
        assertThat(demissao.tipo()).isEqualTo("demissao");
        assertThat(demissao.dataInicio()).isEqualTo(LocalDate.parse("2025-07-15"));
        assertThat(demissao.dataFim()).isNull();
    }

    @Test
    void licencaSemTerminoIncluidaSeIniciouAteOFimDaCampanha() {
        cadastrarFuncionario(1, "M-LICENCA", 1, 1, LocalDate.parse("2025-07-01"));

        // Licença sem término com data_inicio anterior ao início da campanha, mas iniciada antes de fim
        jdbc.execute("""
            INSERT INTO evento_rh (id, matricula, tipo, data_inicio, data_fim, cod_loja, origem)
            VALUES (1, 'M-LICENCA', 'licenca_maternidade', '2025-07-01', NULL, 'L10', 'rh_julho.xlsx')
            """);

        List<IntercorrenciaResponse> resultado = fonte.buscarPorEscopo(escopo);

        assertThat(resultado).hasSize(1);
        IntercorrenciaResponse licenca = resultado.get(0);
        assertThat(licenca.matricula()).isEqualTo("M-LICENCA");
        assertThat(licenca.tipo()).isEqualTo("licenca_maternidade");
        assertThat(licenca.dataInicio()).isEqualTo(LocalDate.parse("2025-07-01"));
        assertThat(licenca.dataFim()).isNull();
    }

    @Test
    void coberturaSemDatasExcluida() {
        cadastrarFuncionario(1, "M-COB", 1, 1, LocalDate.parse("2025-07-01"));

        // Cobertura de loja sem datas (data_inicio null)
        jdbc.execute("""
            INSERT INTO evento_rh (id, matricula, tipo, data_inicio, data_fim, cod_loja, origem)
            VALUES (1, 'M-COB', 'cobertura_loja', NULL, NULL, 'L10', 'rh_julho.xlsx')
            """);

        List<IntercorrenciaResponse> resultado = fonte.buscarPorEscopo(escopo);

        assertThat(resultado).isEmpty();
    }

    @Test
    void funcionarioComCargoForaDoEscopoExcluido() {
        // Cargo 150 (Gerente) não está no escopo (escopo pede 100)
        cadastrarFuncionario(1, "M-CARGO-FORA", 2, 1, LocalDate.parse("2025-07-01"));

        jdbc.execute("""
            INSERT INTO evento_rh (id, matricula, tipo, data_inicio, data_fim, cod_loja, origem)
            VALUES (1, 'M-CARGO-FORA', 'afastamento', '2025-07-12', '2025-07-18', 'L10', 'rh_julho.xlsx')
            """);

        List<IntercorrenciaResponse> resultado = fonte.buscarPorEscopo(escopo);

        assertThat(resultado).isEmpty();
    }

    @Test
    void funcionarioComMarcaForaDoEscopoExcluida() {
        // Loja L20 possui marca 20, que não está no escopo (escopo pede marca 10)
        cadastrarFuncionario(1, "M-MARCA-FORA", 1, 2, LocalDate.parse("2025-07-01"));

        jdbc.execute("""
            INSERT INTO evento_rh (id, matricula, tipo, data_inicio, data_fim, cod_loja, origem)
            VALUES (1, 'M-MARCA-FORA', 'afastamento', '2025-07-12', '2025-07-18', 'L20', 'rh_julho.xlsx')
            """);

        List<IntercorrenciaResponse> resultado = fonte.buscarPorEscopo(escopo);

        assertThat(resultado).isEmpty();
    }

    @Test
    void eventoRepetidoComOrigemDiferenteViraUmaLinhaMantendoMenorId() {
        cadastrarFuncionario(1, "M-REP", 1, 1, LocalDate.parse("2025-07-01"));

        jdbc.execute("""
            INSERT INTO evento_rh (id, matricula, tipo, data_inicio, data_fim, cod_loja, origem)
            VALUES (10, 'M-REP', 'afastamento', '2025-07-11', '2025-07-18', 'L10', 'origem_julho.xlsx')
            """);
        jdbc.execute("""
            INSERT INTO evento_rh (id, matricula, tipo, data_inicio, data_fim, cod_loja, origem)
            VALUES (20, 'M-REP', 'afastamento', '2025-07-11', '2025-07-18', 'L10', 'origem_agosto.xlsx')
            """);

        List<IntercorrenciaResponse> resultado = fonte.buscarPorEscopo(escopo);

        assertThat(resultado).hasSize(1);
        IntercorrenciaResponse dedup = resultado.get(0);
        assertThat(dedup.matricula()).isEqualTo("M-REP");
        assertThat(dedup.origem()).isEqualTo("origem_julho.xlsx");
    }

    @Test
    void ordenacaoPorDataInicioEDesempatePorMatricula() {
        cadastrarFuncionario(1, "M-B", 1, 1, LocalDate.parse("2025-07-01"));
        cadastrarFuncionario(2, "M-A", 1, 1, LocalDate.parse("2025-07-01"));
        cadastrarFuncionario(3, "M-C", 1, 1, LocalDate.parse("2025-07-01"));

        jdbc.execute("""
            INSERT INTO evento_rh (id, matricula, tipo, data_inicio, data_fim, cod_loja, origem)
            VALUES (1, 'M-C', 'ferias', '2025-07-20', '2025-07-25', 'L10', 'origem1')
            """);
        jdbc.execute("""
            INSERT INTO evento_rh (id, matricula, tipo, data_inicio, data_fim, cod_loja, origem)
            VALUES (2, 'M-B', 'afastamento', '2025-07-15', '2025-07-19', 'L10', 'origem2')
            """);
        jdbc.execute("""
            INSERT INTO evento_rh (id, matricula, tipo, data_inicio, data_fim, cod_loja, origem)
            VALUES (3, 'M-A', 'afastamento', '2025-07-15', '2025-07-19', 'L10', 'origem3')
            """);

        List<IntercorrenciaResponse> resultado = fonte.buscarPorEscopo(escopo);

        assertThat(resultado).extracting(IntercorrenciaResponse::matricula)
                .containsExactly("M-A", "M-B", "M-C");
    }
}
