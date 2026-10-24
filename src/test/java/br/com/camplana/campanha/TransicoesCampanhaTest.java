package br.com.camplana.campanha;

import br.com.camplana.entity.StatusCampanha;
import br.com.camplana.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

import static br.com.camplana.entity.StatusCampanha.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransicoesCampanhaTest {

    // Tabela 5.1 do contrato. APROVADA -> ARQUIVADA só entra com o cancelamento (A6-88).
    private static final Set<String> VALIDAS = Set.of(
            "RASCUNHO>AGUARDANDO_APROVACAO", "RASCUNHO>ARQUIVADA",
            "AGUARDANDO_APROVACAO>APROVADA", "AGUARDANDO_APROVACAO>REPROVADA",
            "REPROVADA>RASCUNHO", "REPROVADA>ARQUIVADA");

    static Stream<Arguments> todosOsPares() {
        return Arrays.stream(StatusCampanha.values())
                .flatMap(de -> Arrays.stream(StatusCampanha.values()).map(para -> Arguments.of(de, para)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("todosOsPares")
    void permiteSoAsTransicoesDaTabela(StatusCampanha de, StatusCampanha para) {
        assertThat(TransicoesCampanha.permite(de, para)).isEqualTo(VALIDAS.contains(de + ">" + para));
    }

    @Test
    void transicaoInvalidaViraConflitoComStatusAtualEPedido() {
        assertThatThrownBy(() -> TransicoesCampanha.exigir(12L, APROVADA, RASCUNHO))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Transição inválida: campanha 12 está em APROVADA, pedido RASCUNHO");
    }

    @Test
    void transicaoValidaPassaSemErro() {
        assertThatCode(() -> TransicoesCampanha.exigir(12L, RASCUNHO, AGUARDANDO_APROVACAO))
                .doesNotThrowAnyException();
    }
}
