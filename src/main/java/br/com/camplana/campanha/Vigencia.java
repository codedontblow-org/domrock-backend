package br.com.camplana.campanha;

import br.com.camplana.dto.campanha.PeriodoCampanha;
import br.com.camplana.entity.StatusCampanha;

import java.time.LocalDate;

/** Calculada na leitura, nunca gravada (contrato 5.1). */
public enum Vigencia {
    FUTURA,
    VIGENTE,
    ENCERRADA;

    /**
     * {@code null} fora de APROVADA. As bordas do período contam como vigentes.
     *
     * <pre>Vigencia.calcular(APROVADA, periodo, LocalDate.now(clock)); // ENCERRADA para 2025</pre>
     */
    public static Vigencia calcular(StatusCampanha status, PeriodoCampanha periodo, LocalDate hoje) {
        if (status != StatusCampanha.APROVADA || periodo == null) {
            return null;
        }
        if (hoje.isBefore(periodo.dataInicio())) {
            return FUTURA;
        }
        return hoje.isAfter(periodo.dataFim()) ? ENCERRADA : VIGENTE;
    }
}
