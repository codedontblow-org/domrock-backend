package br.com.camplana.campanha;

import br.com.camplana.entity.StatusCampanha;
import br.com.camplana.exception.ConflictException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static br.com.camplana.entity.StatusCampanha.*;

/**
 * Tabela de transições da seção 5.1 do contrato: a fonte única de "de -> para" que as rotas e as
 * {@code acoes} consultam. APROVADA -> ARQUIVADA fica de fora até o cancelamento (A6-88).
 *
 * <pre>TransicoesCampanha.exigir(12L, RASCUNHO, AGUARDANDO_APROVACAO);</pre>
 */
public final class TransicoesCampanha {

    private static final Map<StatusCampanha, Set<StatusCampanha>> PERMITIDAS = Map.of(
            RASCUNHO, EnumSet.of(AGUARDANDO_APROVACAO, ARQUIVADA),
            AGUARDANDO_APROVACAO, EnumSet.of(APROVADA, REPROVADA),
            REPROVADA, EnumSet.of(RASCUNHO, ARQUIVADA),
            APROVADA, EnumSet.noneOf(StatusCampanha.class),
            ARQUIVADA, EnumSet.noneOf(StatusCampanha.class));

    private TransicoesCampanha() {
    }

    public static boolean permite(StatusCampanha de, StatusCampanha para) {
        return PERMITIDAS.get(de).contains(para);
    }

    /** Lança 409 com o status atual e o pedido, no texto do contrato. */
    public static void exigir(long campanhaId, StatusCampanha de, StatusCampanha para) {
        conflito(campanhaId, de, para).ifPresent(erro -> {
            throw erro;
        });
    }

    /** O 409 que {@link #exigir} lançaria, sem lançar: as {@code acoes} usam para esconder o botão. */
    public static Optional<ConflictException> conflito(long campanhaId, StatusCampanha de, StatusCampanha para) {
        if (permite(de, para)) {
            return Optional.empty();
        }
        return Optional.of(new ConflictException(
                "Transição inválida: campanha " + campanhaId + " está em " + de + ", pedido " + para));
    }
}
