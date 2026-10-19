package br.com.camplana.importacao;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeitorXlsxTest {

    private LeitorXlsx leitor;

    @BeforeEach
    void setUp() {
        leitor = new LeitorXlsx();
    }

    @Test
    @DisplayName("Data sai em ISO mesmo com a célula formatada como dd/mm/aaaa")
    void dataSaiEmIso() throws IOException {
        byte[] xlsx = planilha(sheet -> {
            cabecalho(sheet, "Data_Admiss");
            CellStyle estilo = sheet.getWorkbook().createCellStyle();
            estilo.setDataFormat(sheet.getWorkbook().getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            Cell cell = sheet.createRow(1).createCell(0);
            cell.setCellValue(Date.from(LocalDate.of(2025, 10, 1).atStartOfDay(ZoneId.systemDefault()).toInstant()));
            cell.setCellStyle(estilo);
        });

        List<Map<String, String>> linhas = leitor.ler(xlsx);

        assertEquals(1, linhas.size());
        assertEquals("2025-10-01", linhas.get(0).get("Data_Admiss"));
    }

    @Test
    @DisplayName("Números saem sem zeros à direita e sem notação científica")
    void numerosSaemEmNotacaoPlana() throws IOException {
        byte[] xlsx = planilha(sheet -> {
            cabecalho(sheet, "Inteiro", "Decimal", "Pequeno");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(1500);
            row.createCell(1).setCellValue(1500.5);
            row.createCell(2).setCellValue(0.0001);
        });

        Map<String, String> linha = leitor.ler(xlsx).get(0);

        assertEquals("1500", linha.get("Inteiro"));
        assertEquals("1500.5", linha.get("Decimal"));
        assertEquals("0.0001", linha.get("Pequeno"));
    }

    @Test
    @DisplayName("Texto sai sem espaços nas pontas, no cabeçalho e no valor")
    void textoSaiSemEspacosNasPontas() throws IOException {
        byte[] xlsx = planilha(sheet -> {
            cabecalho(sheet, "  Matricula  ");
            sheet.createRow(1).createCell(0).setCellValue("  MATRIC-1  ");
        });

        assertEquals("MATRIC-1", leitor.ler(xlsx).get(0).get("Matricula"));
    }

    @Test
    @DisplayName("Fórmula devolve o resultado calculado")
    void formulaDevolveOResultado() throws IOException {
        byte[] xlsx = planilha(sheet -> {
            cabecalho(sheet, "Total");
            sheet.createRow(1).createCell(0).setCellFormula("1+2");
        });

        assertEquals("3", leitor.ler(xlsx).get(0).get("Total"));
    }

    @Test
    @DisplayName("Cabeçalho em branco no meio não desloca os valores das colunas seguintes")
    void cabecalhoEmBrancoNaoDesalinhaColunas() throws IOException {
        byte[] xlsx = planilha(sheet -> {
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Matricula");
            header.createCell(2).setCellValue("Valor");
            Row dados = sheet.createRow(1);
            dados.createCell(0).setCellValue("MATRIC-1");
            dados.createCell(1).setCellValue("lixo");
            dados.createCell(2).setCellValue(10);
        });

        Map<String, String> linha = leitor.ler(xlsx).get(0);

        assertEquals("MATRIC-1", linha.get("Matricula"));
        assertEquals("10", linha.get("Valor"));
    }

    @Test
    @DisplayName("Linhas totalmente vazias são ignoradas")
    void linhasVaziasSaoIgnoradas() throws IOException {
        byte[] xlsx = planilha(sheet -> {
            cabecalho(sheet, "Matricula");
            sheet.createRow(1).createCell(0).setCellValue("MATRIC-1");
            sheet.createRow(2);
            sheet.createRow(3).createCell(0).setCellValue("MATRIC-2");
        });

        List<Map<String, String>> linhas = leitor.ler(xlsx);

        assertEquals(2, linhas.size());
        assertEquals("MATRIC-2", linhas.get(1).get("Matricula"));
    }

    @Test
    @DisplayName("Conteúdo vazio, nulo ou só com cabeçalho devolve lista vazia")
    void conteudoSemDadosDevolveListaVazia() throws IOException {
        byte[] soCabecalho = planilha(sheet -> cabecalho(sheet, "Matricula"));

        assertTrue(leitor.ler(new byte[0]).isEmpty());
        assertTrue(leitor.ler((byte[]) null).isEmpty());
        assertTrue(leitor.ler(soCabecalho).isEmpty());
    }

    private byte[] planilha(Consumer<Sheet> preencher) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            preencher.accept(workbook.createSheet("dados"));
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(saida);
            return saida.toByteArray();
        }
    }

    private void cabecalho(Sheet sheet, String... nomes) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < nomes.length; i++) {
            row.createCell(i).setCellValue(nomes[i]);
        }
    }
}