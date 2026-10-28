package br.com.camplana.campanha;

import br.com.camplana.dto.campanha.IntercorrenciaResponse;

import java.util.List;

/**
 * Consulta de eventos de RH que interferem nas campanhas do escopo especificado.
 */
public interface FonteIntercorrencias {

    /**
     * Retorna os eventos de RH (afastamentos, férias, licenças e demissões)
     * de colaboradores no escopo e período da campanha.
     */
    List<IntercorrenciaResponse> buscarPorEscopo(EscopoCampanha escopo);
}
