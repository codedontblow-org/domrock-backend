package br.com.camplana.campanha;

import br.com.camplana.dto.campanha.IntercorrenciaResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Fake em memória de {@link FonteIntercorrencias} para testes unitários.
 */
public class FakeFonteIntercorrencias implements FonteIntercorrencias {

    private final List<IntercorrenciaResponse> respostas = new ArrayList<>();

    public FakeFonteIntercorrencias() {}

    public FakeFonteIntercorrencias(List<IntercorrenciaResponse> respostas) {
        this.respostas.addAll(respostas);
    }

    public void adicionar(IntercorrenciaResponse intercorrencia) {
        this.respostas.add(intercorrencia);
    }

    @Override
    public List<IntercorrenciaResponse> buscarPorEscopo(EscopoCampanha escopo) {
        return List.copyOf(respostas);
    }
}
