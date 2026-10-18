package br.com.camplana.importacao;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Component
public class LeitorXlsx {

    private final DataFormatter dataFormatter = new DataFormatter();

    public List<Map<String, String>> ler(MultipartFile arquivo) throws IOException {
        if (arquivo == null || arquivo.isEmpty()) {
            return Collections.emptyList();
        }
        return ler(arquivo.getBytes());
    }

    public List<Map<String, String>> ler(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return Collections.emptyList();
        }
        return ler(inputStream.readAllBytes());
    }

    public List<Map<String, String>> ler(byte[] conteudoXlsx) throws IOException {
        List<Map<String, String>> linhas = new ArrayList<>();

        if (conteudoXlsx == null || conteudoXlsx.length == 0) {
            return linhas;
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(conteudoXlsx);
             Workbook workbook = WorkbookFactory.create(bais)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return linhas;
            }

            Iterator<Row> rowIterator = sheet.rowIterator();
            if (!rowIterator.hasNext()) {
                return linhas;
            }

            // Linha 0: Cabeçalho
            Row headerRow = rowIterator.next();
            List<String> cabecalhos = extrairCabecalhos(headerRow);

            if (cabecalhos.isEmpty()) {
                return linhas;
            }

            // Demais linhas: Dados
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                if (linhaVazia(row)) {
                    continue;
                }

                Map<String, String> linhaDados = new LinkedHashMap<>();
                for (int i = 0; i < cabecalhos.size(); i++) {
                    String coluna = cabecalhos.get(i);
                    Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String valor = formatarValorCelula(cell);
                    linhaDados.put(coluna, valor);
                }
                linhas.add(linhaDados);
            }
        }

        return linhas;
    }

    private List<String> extrairCabecalhos(Row headerRow) {
        List<String> cabecalhos = new ArrayList<>();
        for (Cell cell : headerRow) {
            String valor = dataFormatter.formatCellValue(cell);
            if (valor != null && !valor.trim().isBlank()) {
                cabecalhos.add(valor.trim());
            }
        }
        return cabecalhos;
    }

    private String formatarValorCelula(Cell cell) {
        if (cell == null) {
            return "";
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield dataFormatter.formatCellValue(cell).trim();
                }
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num) && !Double.isInfinite(num)) {
                    yield String.valueOf((long) num);
                }
                yield String.valueOf(num);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> dataFormatter.formatCellValue(cell).trim();
            default -> "";
        };
    }

    private boolean linhaVazia(Row row) {
        if (row == null) return true;
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = dataFormatter.formatCellValue(cell);
                if (val != null && !val.trim().isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }
}
