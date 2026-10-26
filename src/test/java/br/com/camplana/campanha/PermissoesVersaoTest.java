package br.com.camplana.campanha;

import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.Usuario;
import br.com.camplana.exception.ConflictException;
import br.com.camplana.exception.ForbiddenException;
import org.junit.jupiter.api.Test;

import static br.com.camplana.campanha.AcaoVersao.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissoesVersaoTest {

    private static final int GABRIELA = 1;

    private static final SituacaoVersao AGUARDANDO = new SituacaoVersao(12L, 1, GABRIELA, null, false);
    private static final SituacaoVersao PEDE_RESIMULACAO = new SituacaoVersao(12L, 1, GABRIELA, null, true);
    private static final SituacaoVersao DECIDIDA =
            new SituacaoVersao(12L, 1, GABRIELA, "Esta versão já foi decidida por Sílvia em 21/10/2026", false);

    private static Usuario usuario(int id, Perfil perfil) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setPerfil(perfil);
        return usuario;
    }

    private static final Usuario SERGIO = usuario(2, Perfil.SUPERVISOR);

    @Test
    void outroSupervisorPodeAprovarReprovarEResimular() {
        assertThat(PermissoesVersao.acoes(AGUARDANDO, SERGIO)).containsExactly(APROVAR, REPROVAR, RESIMULAR);
    }

    @Test
    void comResimulacaoPendenteAprovarSaiDasAcoesEDa409() {
        assertThat(PermissoesVersao.acoes(PEDE_RESIMULACAO, SERGIO)).containsExactly(REPROVAR, RESIMULAR);
        assertThatThrownBy(() -> PermissoesVersao.exigir(APROVAR, PEDE_RESIMULACAO, SERGIO))
                .isInstanceOf(ConflictException.class).hasMessage("Simule de novo antes de aprovar");
    }

    @Test
    void autorEGerenteSoLeem() {
        assertThat(PermissoesVersao.acoes(AGUARDANDO, usuario(GABRIELA, Perfil.SUPERVISOR))).isEmpty();
        assertThat(PermissoesVersao.acoes(AGUARDANDO, usuario(3, Perfil.GERENTE))).isEmpty();
        assertThatThrownBy(() -> PermissoesVersao.exigir(REPROVAR, AGUARDANDO, usuario(GABRIELA, Perfil.SUPERVISOR)))
                .isInstanceOf(ForbiddenException.class).hasMessage("Outra pessoa precisa aprovar esta campanha");
    }

    @Test
    void versaoDecididaNaoTemAcoesEO409DizQuemDecidiu() {
        assertThat(PermissoesVersao.acoes(DECIDIDA, SERGIO)).isEmpty();
        assertThatThrownBy(() -> PermissoesVersao.exigir(RESIMULAR, DECIDIDA, SERGIO))
                .isInstanceOf(ConflictException.class).hasMessage("Esta versão já foi decidida por Sílvia em 21/10/2026");
    }
}
