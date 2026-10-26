package br.com.camplana.campanha;

import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.Usuario;
import br.com.camplana.exception.ConflictException;
import br.com.camplana.exception.ForbiddenException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * O que o Supervisor pode fazer numa versão (contrato 5.6, 5.8 e 5.9). Como em
 * {@link PermissoesCampanha}, a mesma regra monta as {@code acoes} e barra as rotas: o front mostra os
 * botões que vierem e não deduz nada do perfil. Ordem do contrato 5.8: perfil (403), autor (403),
 * versão pendente (409) e, só para aprovar, re-simulação (409).
 *
 * <pre>PermissoesVersao.exigir(AcaoVersao.APROVAR, situacao, supervisor);</pre>
 */
public final class PermissoesVersao {

    private PermissoesVersao() {
    }

    /** As ações que {@code leitor} pode fazer agora na versão; vazia = só leitura. */
    public static List<AcaoVersao> acoes(SituacaoVersao situacao, Usuario leitor) {
        return Arrays.stream(AcaoVersao.values())
                .filter(acao -> violacao(acao, situacao, leitor).isEmpty())
                .toList();
    }

    /** Lança o 403 ou 409 que a rota da ação deve responder. */
    public static void exigir(AcaoVersao acao, SituacaoVersao situacao, Usuario usuario) {
        violacao(acao, situacao, usuario).ifPresent(erro -> {
            throw erro;
        });
    }

    private static Optional<RuntimeException> violacao(AcaoVersao acao, SituacaoVersao situacao, Usuario usuario) {
        if (usuario.getPerfil() != Perfil.SUPERVISOR) {
            return Optional.of(new ForbiddenException("Sem permissão para esta ação"));
        }
        if (situacao.autorId() == usuario.getId()) {
            return Optional.of(new ForbiddenException("Outra pessoa precisa aprovar esta campanha"));
        }
        if (!situacao.pendente()) {
            return Optional.of(new ConflictException(situacao.naoPendente()));
        }
        if (acao == AcaoVersao.APROVAR && situacao.resimulacaoNecessaria()) {
            return Optional.of(new ConflictException("Simule de novo antes de aprovar"));
        }
        return Optional.empty();
    }
}
