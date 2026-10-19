package br.com.camplana.importacao;

import br.com.camplana.dto.importacao.PendenciaImportacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ValidadorImportacaoServiceTest {

    private ValidadorImportacaoService validador;

    @BeforeEach
    void setUp() {
        validador = new ValidadorImportacaoService();
    }

    // AVISO e nao IMPEDITIVO: a base real tem 60 vendas assim em ago-dez/2025 e, como impeditivo,
    // so julho entrava. O ImportadorVendasService ja descarta essas vendas; o aviso as torna visiveis.
    @Test
    @DisplayName("Venda de matrícula ausente do RH gera AVISO com data e valor, sem barrar a carga")
    void deveGerarAvisoQuandoMatriculaVendaNaoExisteNoRh() {
        // RH contém MATRIC-001
        Map<String, String> linhaRh = criarLinhaRh("MATRIC-001", "M01", "C01");
        List<Map<String, String>> linhasRh = List.of(linhaRh);

        // Vendas contém MATRIC-999 (inexistente no RH)
        Map<String, String> linhaVenda = criarLinhaVendas("MATRIC-999");
        List<Map<String, String>> linhasVendas = List.of(linhaVenda);

        // Comissão para M01 e C01
        Map<String, String> linhaComissao = criarLinhaComissao("M01", "C01");
        List<Map<String, String>> linhasComissao = List.of(linhaComissao);

        ValidadorImportacaoService.ResultadoValidacao resultado = validador.validarLote(
                "BASE RH_OUT25.xlsx", linhasRh,
                "BASE_VENDAS_OUT25.xlsx", linhasVendas,
                "BASE_COMMISS_FINAL.xlsx", linhasComissao
        );

        assertFalse(resultado.temImpeditivo());
        assertTrue(resultado.valido());

        PendenciaImportacao aviso = resultado.pendencias().stream()
                .filter(p -> "Cruzamento".equals(p.origem()) && p.motivo().contains("MATRIC-999"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Sem pendência de Cruzamento para a MATRIC-999: " + resultado.pendencias()));

        assertEquals(PendenciaImportacao.AVISO, aviso.severidade());
        assertEquals(2, aviso.linha());
        assertTrue(aviso.motivo().contains("não encontrada no RH da competência"), aviso.motivo());
        assertTrue(aviso.motivo().contains("2025-10-15") && aviso.motivo().contains("1500.00"), aviso.motivo());
        assertTrue(aviso.motivo().contains("não será gravada"), aviso.motivo());
    }

    @Test
    @DisplayName("Deve gerar AVISO se houver funcionário no RH sem vendas registradas no mês")
    void deveGerarAvisoQuandoFuncionarioNaoPossuiVendas() {
        // RH contém MATRIC-001 e MATRIC-002
        List<Map<String, String>> linhasRh = List.of(
                criarLinhaRh("MATRIC-001", "M01", "C01"),
                criarLinhaRh("MATRIC-002", "M01", "C01")
        );

        // Vendas contém apenas MATRIC-001
        List<Map<String, String>> linhasVendas = List.of(
                criarLinhaVendas("MATRIC-001")
        );

        List<Map<String, String>> linhasComissao = List.of(
                criarLinhaComissao("M01", "C01")
        );

        ValidadorImportacaoService.ResultadoValidacao resultado = validador.validarLote(
                "BASE RH_OUT25.xlsx", linhasRh,
                "BASE_VENDAS_OUT25.xlsx", linhasVendas,
                "BASE_COMMISS_FINAL.xlsx", linhasComissao
        );

        assertFalse(resultado.temImpeditivo());
        assertTrue(resultado.valido());

        boolean temAviso = resultado.pendencias().stream()
                .anyMatch(p -> PendenciaImportacao.AVISO.equals(p.severidade()) 
                        && p.motivo().contains("MATRIC-002"));

        assertTrue(temAviso, "Deve conter pendência de severidade AVISO para a MATRIC-002");
    }

    @Test
    @DisplayName("Deve gerar IMPEDITIVO se faltar coluna obrigatória na planilha")
    void deveGerarImpeditivoSeFaltarColunaObrigatoria() {
        Map<String, String> linhaIncompleta = new LinkedHashMap<>();
        linhaIncompleta.put("Matricula", "MATRIC-001");
        // Omitindo as demais colunas obrigatórias

        ValidadorImportacaoService.ResultadoValidacao resultado = validador.validarLote(
                "BASE RH_OUT25.xlsx", List.of(linhaIncompleta),
                "BASE_VENDAS_OUT25.xlsx", Collections.emptyList(),
                "BASE_COMMISS_FINAL.xlsx", Collections.emptyList()
        );

        assertTrue(resultado.temImpeditivo());
        assertFalse(resultado.valido());
    }

    private Map<String, String> criarLinhaRh(String matricula, String codMarca, String codCargo) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Data_Ref", "2025-10-01");
        map.put("Cod_Marca", codMarca);
        map.put("Descri_Marca", "MARCA TESTE");
        map.put("Cod_Loja", "L01");
        map.put("Descr_Loja", "LOJA TESTE");
        map.put("Matricula", matricula);
        map.put("Data_Admiss", "2020-01-01");
        map.put("Data_Demiss", "");
        map.put("Cod_Cargo", codCargo);
        map.put("Descri_Cargo", "VENDEDOR");
        return map;
    }

    private Map<String, String> criarLinhaVendas(String matricula) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Date_Ref", "2025-10-15");
        map.put("Matricula", matricula);
        map.put("Vlr _Venda", "1500.00");
        return map;
    }

    private Map<String, String> criarLinhaComissao(String codMarca, String codCargo) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Cod_Marca", codMarca);
        map.put("Descr_Marca", "MARCA TESTE");
        map.put("Cod_Cargo", codCargo);
        map.put("Descri_Cargo", "VENDEDOR");
        map.put("%_Comiss", "0.025");
        return map;
    }

    @Test
    @DisplayName("Deve validar com sucesso lote consistente (Carga Válida)")
    void deveValidarLoteConsistenteComSucesso() {
        List<Map<String, String>> rh = List.of(criarLinhaRh("MATRIC-001", "M01", "C01"));
        List<Map<String, String>> vendas = List.of(criarLinhaVendas("MATRIC-001"));
        List<Map<String, String>> comissao = List.of(criarLinhaComissao("M01", "C01"));

        ValidadorImportacaoService.ResultadoValidacao res = validador.validarLote(
                "BASE RH_OUT25.xlsx", rh,
                "BASE_VENDAS_OUT25.xlsx", vendas,
                "BASE_COMMISS_FINAL.xlsx", comissao
        );

        assertTrue(res.valido());
        assertFalse(res.temImpeditivo());
        assertTrue(res.pendencias().isEmpty());
    }

    @Test
    @DisplayName("Deve gerar IMPEDITIVO se houver divergência de competência entre os arquivos")
    void deveGerarImpeditivoParaCompetenciasDivergentes() {
        List<Map<String, String>> rh = List.of(criarLinhaRh("MATRIC-001", "M01", "C01"));
        List<Map<String, String>> vendas = List.of(criarLinhaVendas("MATRIC-001"));

        ValidadorImportacaoService.ResultadoValidacao res = validador.validarLote(
                "BASE RH_OUT25.xlsx", rh,
                "BASE_VENDAS_SET25.xlsx", vendas,
                "BASE_COMMISS_FINAL.xlsx", Collections.emptyList()
        );

        assertTrue(res.temImpeditivo());
        boolean temDivergencia = res.pendencias().stream()
                .anyMatch(p -> "Competencia".equals(p.campo()) && PendenciaImportacao.IMPEDITIVO.equals(p.severidade()));
        assertTrue(temDivergencia);
    }

    @Test
    @DisplayName("Deve gerar IMPEDITIVO se funcionário no RH estiver sem loja ou sem cargo")
    void deveGerarImpeditivoSeFuncionarioSemLojaOuCargo() {
        Map<String, String> rhSemCargo = criarLinhaRh("MATRIC-001", "M01", ""); // cargo vazio
        List<Map<String, String>> vendas = List.of(criarLinhaVendas("MATRIC-001"));

        ValidadorImportacaoService.ResultadoValidacao res = validador.validarLote(
                "BASE RH_OUT25.xlsx", List.of(rhSemCargo),
                "BASE_VENDAS_OUT25.xlsx", vendas,
                "BASE_COMMISS_FINAL.xlsx", Collections.emptyList()
        );

        assertTrue(res.temImpeditivo());
        boolean semCargo = res.pendencias().stream()
                .anyMatch(p -> "Cod_Cargo".equals(p.campo()) && PendenciaImportacao.IMPEDITIVO.equals(p.severidade()));
        assertTrue(semCargo);
    }

    @Test
    @DisplayName("Deve gerar IMPEDITIVO se Data_Admiss não estiver no formato YYYY-MM-DD")
    void deveGerarImpeditivoParaDataAdmissaoInvalida() {
        Map<String, String> rh = criarLinhaRh("MATRIC-001", "M01", "C01");
        rh.put("Data_Admiss", "01/10/2025");
 
        ValidadorImportacaoService.ResultadoValidacao res = validarOut25(
                List.of(rh), List.of(criarLinhaVendas("MATRIC-001")), List.of(criarLinhaComissao("M01", "C01")));
 
        assertTrue(res.temImpeditivo());
        assertTrue(temImpeditivoEm(res, "Data_Admiss", 2));
    }
 
    @Test
    @DisplayName("Deve gerar IMPEDITIVO se Data_Ref do RH for inválida")
    void deveGerarImpeditivoParaDataRefDoRhInvalida() {
        Map<String, String> rh = criarLinhaRh("MATRIC-001", "M01", "C01");
        rh.put("Data_Ref", "2025/10/01");
 
        ValidadorImportacaoService.ResultadoValidacao res = validarOut25(
                List.of(rh), List.of(criarLinhaVendas("MATRIC-001")), List.of(criarLinhaComissao("M01", "C01")));
 
        assertTrue(temImpeditivoEm(res, "Data_Ref", 2));
    }
 
    @Test
    @DisplayName("Deve gerar IMPEDITIVO se Date_Ref de Vendas for inválida")
    void deveGerarImpeditivoParaDateRefDeVendasInvalida() {
        Map<String, String> venda = criarLinhaVendas("MATRIC-001");
        venda.put("Date_Ref", "15/10/2025");
 
        ValidadorImportacaoService.ResultadoValidacao res = validarOut25(
                List.of(criarLinhaRh("MATRIC-001", "M01", "C01")), List.of(venda), List.of(criarLinhaComissao("M01", "C01")));
 
        assertTrue(temImpeditivoEm(res, "Date_Ref", 2));
    }
 
    @Test
    @DisplayName("Deve gerar IMPEDITIVO se o valor da venda não for numérico")
    void deveGerarImpeditivoParaValorDeVendaNaoNumerico() {
        Map<String, String> venda = criarLinhaVendas("MATRIC-001");
        venda.put("Vlr _Venda", "abc");
 
        ValidadorImportacaoService.ResultadoValidacao res = validarOut25(
                List.of(criarLinhaRh("MATRIC-001", "M01", "C01")), List.of(venda), List.of(criarLinhaComissao("M01", "C01")));
 
        assertTrue(temImpeditivoEm(res, "Vlr _Venda", 2));
    }
 
    @Test
    @DisplayName("Deve gerar IMPEDITIVO se o percentual de comissão não for numérico")
    void deveGerarImpeditivoParaPercentualDeComissaoNaoNumerico() {
        Map<String, String> comissao = criarLinhaComissao("M01", "C01");
        comissao.put("%_Comiss", "abc");
 
        ValidadorImportacaoService.ResultadoValidacao res = validarOut25(
                List.of(criarLinhaRh("MATRIC-001", "M01", "C01")), List.of(criarLinhaVendas("MATRIC-001")), List.of(comissao));
 
        assertTrue(temImpeditivoEm(res, "%_Comiss", 2));
    }
 
    @Test
    @DisplayName("O número da linha do erro acompanha a posição na planilha (cabeçalho é a linha 1)")
    void numeroDaLinhaDoErroAcompanhaAPosicao() {
        Map<String, String> segundaLinha = criarLinhaRh("MATRIC-002", "M01", "C01");
        segundaLinha.put("Data_Admiss", "ontem");
 
        ValidadorImportacaoService.ResultadoValidacao res = validarOut25(
                List.of(criarLinhaRh("MATRIC-001", "M01", "C01"), segundaLinha),
                List.of(criarLinhaVendas("MATRIC-001"), criarLinhaVendas("MATRIC-002")),
                List.of(criarLinhaComissao("M01", "C01")));
 
        assertTrue(temImpeditivoEm(res, "Data_Admiss", 3));
        assertFalse(temImpeditivoEm(res, "Data_Admiss", 2));
    }
 
    @Test
    @DisplayName("Todos os erros de tipo do lote são reportados, não só o primeiro")
    void deveReportarTodosOsErrosDeTipoDoLote() {
        Map<String, String> rh = criarLinhaRh("MATRIC-001", "M01", "C01");
        rh.put("Data_Admiss", "ontem");
        Map<String, String> venda = criarLinhaVendas("MATRIC-001");
        venda.put("Vlr _Venda", "abc");
 
        ValidadorImportacaoService.ResultadoValidacao res = validarOut25(
                List.of(rh), List.of(venda), List.of(criarLinhaComissao("M01", "C01")));
 
        assertTrue(temImpeditivoEm(res, "Data_Admiss", 2));
        assertTrue(temImpeditivoEm(res, "Vlr _Venda", 2));
    }
 
    @Test
    @DisplayName("Datas ISO e números decimais válidos não geram pendência")
    void formatosValidosNaoGeramPendencia() {
        Map<String, String> venda = criarLinhaVendas("MATRIC-001");
        venda.put("Vlr _Venda", "1234.5");
        Map<String, String> comissao = criarLinhaComissao("M01", "C01");
        comissao.put("%_Comiss", "0.0175");
 
        ValidadorImportacaoService.ResultadoValidacao res = validarOut25(
                List.of(criarLinhaRh("MATRIC-001", "M01", "C01")), List.of(venda), List.of(comissao));
 
        assertFalse(res.temImpeditivo());
        assertTrue(res.pendencias().isEmpty());
    }
 
    private ValidadorImportacaoService.ResultadoValidacao validarOut25(
            List<Map<String, String>> rh, List<Map<String, String>> vendas, List<Map<String, String>> comissao) {
        return validador.validarLote(
                "BASE RH_OUT25.xlsx", rh,
                "BASE_VENDAS_OUT25.xlsx", vendas,
                "BASE_COMMISS_FINAL.xlsx", comissao
        );
    }
 
    private boolean temImpeditivoEm(ValidadorImportacaoService.ResultadoValidacao res, String campo, int linha) {
        return res.pendencias().stream().anyMatch(p ->
                campo.equals(p.campo())
                        && p.linha() == linha
                        && PendenciaImportacao.IMPEDITIVO.equals(p.severidade()));
    }
}
