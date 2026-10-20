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

            // Linha 0 Cabeçalho mapeando o índice físico da coluna para o nome
            Row headerRow = rowIterator.next();
            Map<Integer, String> colunaParaHeader = new LinkedHashMap<>();
            for (Cell c : headerRow) {
                String texto = dataFormatter.formatCellValue(c).trim();
                if (!texto.isBlank()) {
                    colunaParaHeader.put(c.getColumnIndex(), texto);
                }
            }

            if (colunaParaHeader.isEmpty()) {
                return linhas;
            }

            // Demais linhas: Dados
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                if (linhaVazia(row)) {
                    continue;
                }

                Map<String, String> linhaDados = new LinkedHashMap<>();
                for (Map.Entry<Integer, String> e : colunaParaHeader.entrySet()) {
                    Cell cell = row.getCell(e.getKey(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    linhaDados.put(e.getValue(), formatarValorCelula(cell));
                }
                linhas.add(linhaDados);
            }
        }

        return linhas;
    }

    private String formatarValorCelula(Cell cell) {
        if (cell == null) {
            return "";
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> formatarNumericoOuData(cell);
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> switch (cell.getCachedFormulaResultType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> formatarNumericoOuData(cell);
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                default -> "";
            };
            default -> "";
        };
    }

    private String formatarNumericoOuData(Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue()
                    .toLocalDate()
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return java.math.BigDecimal.valueOf(cell.getNumericCellValue())
                .stripTrailingZeros()
                .toPlainString();
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
