package br.com.camplana.campanha;

import br.com.camplana.dto.campanha.PeriodoCampanha;

import java.util.Collections;
import java.util.Set;

/**
 * Período, marcas e cargos de uma ficha, com "ALL" já trocado pelas seis marcas (contrato 2.1).
 *
 * <pre>daFicha.temMarcaECargoEmComum(daVersao); // true se as duas têm ao menos uma marca × cargo</pre>
 */
public record EscopoCampanha(PeriodoCampanha periodo, Set<String> marcas, Set<String> cargos) {

    /** Ao menos uma marca × cargo em comum (contrato 4.3); o cruzamento de período fica na consulta. */
    public boolean temMarcaECargoEmComum(EscopoCampanha outro) {
        return !Collections.disjoint(marcas, outro.marcas) && !Collections.disjoint(cargos, outro.cargos);
    }
}
