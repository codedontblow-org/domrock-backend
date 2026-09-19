package br.com.camplana.importacao;

import java.time.YearMonth;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompetenciaExtractor {

    private static final Pattern PADRAO_NOME = Pattern.compile(
            "(JAN|FEV|MAR|ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ)(\\d{2})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Map<String, Integer> MES_PT = Map.ofEntries(
            Map.entry("JAN", 1), Map.entry("FEV", 2), Map.entry("MAR", 3),
            Map.entry("ABR", 4), Map.entry("MAI", 5), Map.entry("JUN", 6),
            Map.entry("JUL", 7), Map.entry("AGO", 8), Map.entry("SET", 9),
            Map.entry("OUT", 10), Map.entry("NOV", 11), Map.entry("DEZ", 12)
    );

    public static YearMonth doNomeArquivo(String nomeArquivo) {
        Matcher m = PADRAO_NOME.matcher(nomeArquivo == null ? "" : nomeArquivo);
        if (!m.find()) return null;

        int mes = MES_PT.get(m.group(1).toUpperCase(Locale.ROOT));
        int ano = 2000 + Integer.parseInt(m.group(2));
        return YearMonth.of(ano, mes);
    }
}