package br.com.camplana.campanha;

import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.StatusCampanha;
import br.com.camplana.entity.Usuario;
import br.com.camplana.exception.BadRequestException;
import br.com.camplana.exception.ConflictException;
import br.com.camplana.exception.ForbiddenException;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static br.com.camplana.entity.StatusCampanha.*;

/**
 * Quem pode fazer o quê numa campanha (contrato 5.2, 5.6 e 5.7). A mesma regra monta as
 * {@code acoes} que o front mostra e barra as rotas, então botão e permissão nunca divergem
 * (front burro). A ordem das verificações é a do contrato: autor (403), status (409), simulação.
 *
 * <pre>PermissoesCampanha.exigir(AcaoCampanha.SUBMETER, situacao, usuario.getId());</pre>
 */
public final class PermissoesCampanha {

    private PermissoesCampanha() {
    }

    /** As ações que {@code usuarioId} pode fazer agora; vazia = só leitura. */
    public static List<AcaoCampanha> acoes(SituacaoCampanha situacao, int usuarioId) {
        return Arrays.stream(AcaoCampanha.values())
                .filter(acao -> violacao(acao, situacao, usuarioId).isEmpty())
                .toList();
    }

    /** Lança o 403, 409 ou 400 que a rota da ação deve responder. */
    public static void exigir(AcaoCampanha acao, SituacaoCampanha situacao, int usuarioId) {
        violacao(acao, situacao, usuarioId).ifPresent(erro -> {
            throw erro;
        });
    }

    /** O autor sempre vê; os Supervisores, depois da primeira submissão (contrato 5.6). */
    public static void exigirLeitura(SituacaoCampanha situacao, Usuario leitor) {
        boolean autor = situacao.autorId() == leitor.getId();
        boolean supervisorComVersao = leitor.getPerfil() == Perfil.SUPERVISOR && situacao.versaoAtual() != null;
        if (!autor && !supervisorComVersao) {
            throw new ForbiddenException("Sem permissão para ver a campanha " + situacao.campanhaId());
        }
    }

    private static Optional<RuntimeException> violacao(AcaoCampanha acao, SituacaoCampanha situacao, int usuarioId) {
        if (situacao.autorId() != usuarioId) {
            return Optional.of(new ForbiddenException(
                    "Só quem criou a campanha " + situacao.campanhaId() + " pode " + acao));
        }
        return switch (acao) {
            case RENOMEAR -> soEmRascunho(situacao);
            case SUBMETER -> transicao(situacao, AGUARDANDO_APROVACAO).or(() -> simulacaoParaSubmeter(situacao));
            case EDITAR -> transicao(situacao, RASCUNHO);
            case ARQUIVAR -> transicao(situacao, ARQUIVADA);
        };
    }

    private static Optional<RuntimeException> transicao(SituacaoCampanha situacao, StatusCampanha para) {
        return TransicoesCampanha.conflito(situacao.campanhaId(), situacao.status(), para).map(erro -> erro);
    }

    private static Optional<RuntimeException> soEmRascunho(SituacaoCampanha situacao) {
        if (situacao.status() == RASCUNHO) {
            return Optional.empty();
        }
        return Optional.of(new ConflictException("Renomear só vale em RASCUNHO: campanha "
                + situacao.campanhaId() + " está em " + situacao.status()));
    }

    // A versão congela a última simulação concluída: com uma rodando, ela seria mais velha do que a
    // tela; sem simulação nova depois da reprovação, a v2 repetiria a v1 já reprovada.
    private static Optional<RuntimeException> simulacaoParaSubmeter(SituacaoCampanha situacao) {
        if (situacao.simulacaoEmAndamentoId() != null) {
            return Optional.of(new ConflictException(
                    "Aguarde a simulação " + situacao.simulacaoEmAndamentoId() + " terminar"));
        }
        if (situacao.ultimaConcluidaId() == null) {
            return Optional.of(new BadRequestException("Simule antes de submeter: a campanha "
                    + situacao.campanhaId() + " não tem simulação concluída"));
        }
        if (Objects.equals(situacao.ultimaConcluidaId(), situacao.simulacaoDaVersaoAtual())) {
            return Optional.of(new BadRequestException("Simule de novo antes de submeter: a v"
                    + situacao.versaoAtual() + " usou a simulação " + situacao.ultimaConcluidaId()));
        }
        return Optional.empty();
    }
}
