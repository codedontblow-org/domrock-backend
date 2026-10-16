package br.com.camplana.importacao;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LeitorXlsx {

    public List<Map<String, String>> ler(MultipartFile arquivo) throws IOException {
        List<Map<String, String>> linhas = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(arquivo.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            Row cabecalho = sheet.getRow(sheet.getFirstRowNum());
            if (cabecalho == null)
                return linhas;

            Map<Integer, String> colunaParaHeader = new LinkedHashMap<>();
            for (Cell c : cabecalho) {
                String texto = celulaComoTexto(c);
                if (!texto.isBlank()) {
                    colunaParaHeader.put(c.getColumnIndex(), texto.trim());
                }
            }

            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;

                Map<String, String> mapa = new LinkedHashMap<>();
                boolean algumaCelulaPreenchida = false;

                for (Map.Entry<Integer, String> entry : colunaParaHeader.entrySet()) {
                    Cell cell = row.getCell(entry.getKey(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String valor = celulaComoTexto(cell);
                    if (!valor.isBlank())
                        algumaCelulaPreenchida = true;
                    mapa.put(entry.getValue(), valor);
                }

                if (algumaCelulaPreenchida)
                    linhas.add(mapa);
            }
        }
        return linhas;
    }

    private String celulaComoTexto(Cell cell) {

        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {

            case STRING:
                return cell.getStringCellValue().trim();

            case NUMERIC:

                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue()
                            .toLocalDate()
                            .format(DateTimeFormatter.ISO_LOCAL_DATE);
                }

                return BigDecimal
                        .valueOf(cell.getNumericCellValue())
                        .stripTrailingZeros()
                        .toPlainString();

            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());

            case FORMULA:

                // Pega o resultado armazenado da fórmula,
                // sem chamar celulaComoTexto() novamente.
                switch (cell.getCachedFormulaResultType()) {

                    case STRING:
                        return cell.getStringCellValue().trim();

                    case NUMERIC:

                        if (DateUtil.isCellDateFormatted(cell)) {
                            return cell.getLocalDateTimeCellValue()
                                    .toLocalDate()
                                    .format(DateTimeFormatter.ISO_LOCAL_DATE);
                        }

                        return BigDecimal
                                .valueOf(cell.getNumericCellValue())
                                .stripTrailingZeros()
                                .toPlainString();

                    case BOOLEAN:
                        return String.valueOf(cell.getBooleanCellValue());

                    default:
                        return "";
                }

            case BLANK:
                return "";

            default:
                return "";
        }
    }
}