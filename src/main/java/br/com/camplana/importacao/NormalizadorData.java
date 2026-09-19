package br.com.camplana.importacao;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class NormalizadorData {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    public static LocalDate parse(String valor) {
        return LocalDate.parse(valor.trim(), ISO);
    }
}