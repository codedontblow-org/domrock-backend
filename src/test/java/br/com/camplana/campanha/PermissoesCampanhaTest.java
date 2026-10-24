package br.com.camplana.campanha;

import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.StatusCampanha;
import br.com.camplana.entity.Usuario;
import br.com.camplana.exception.BadRequestException;
import br.com.camplana.exception.ConflictException;
import br.com.camplana.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static br.com.camplana.campanha.AcaoCampanha.*;
import static br.com.camplana.entity.StatusCampanha.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissoesCampanhaTest {

    private static final int GABRIELA = 1;
    private static final int SERGIO = 2;

    /** Campanha 12 da Gabriela, sem versão, com a simulação 345 concluída e nada rodando. */
    private static SituacaoCampanha rascunho() {
        return new SituacaoCampanha(12L, RASCUNHO, GABRIELA, null, null, 345L, null);
    }

    private static SituacaoCampanha comStatus(StatusCampanha status) {
        return new SituacaoCampanha(12L, status, GABRIELA, 1, 345L, 345L, null);
    }

    @Test
    void rascunhoComSimulacaoNovaPermiteRenomearSubmeterEArquivar() {
        assertThat(PermissoesCampanha.acoes(rascunho(), GABRIELA)).containsExactly(RENOMEAR, SUBMETER, ARQUIVAR);
    }

    @Test
    void reprovadaPermiteEditarEArquivar() {
        assertThat(PermissoesCampanha.acoes(comStatus(REPROVADA), GABRIELA)).containsExactly(EDITAR, ARQUIVAR);
    }

    @ParameterizedTest
    @EnumSource(value = StatusCampanha.class, names = {"AGUARDANDO_APROVACAO", "APROVADA", "ARQUIVADA"})
    void semAcaoDoAutorForaDeRascunhoEReprovada(StatusCampanha status) {
        assertThat(PermissoesCampanha.acoes(comStatus(status), GABRIELA)).isEmpty();
    }

    @Test
    void quemNaoCriouNaoTemAcoes() {
        assertThat(PermissoesCampanha.acoes(rascunho(), SERGIO)).isEmpty();
    }

    @Test
    void acaoDeQuemNaoCriouDa403AntesDoStatus() {
        assertThatThrownBy(() -> PermissoesCampanha.exigir(SUBMETER, comStatus(APROVADA), SERGIO))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Só quem criou a campanha 12 pode SUBMETER");
    }

    @Test
    void levarAprovadaARascunhoDa409ComATransicao() {
        assertThatThrownBy(() -> PermissoesCampanha.exigir(EDITAR, comStatus(APROVADA), GABRIELA))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Transição inválida: campanha 12 está em APROVADA, pedido RASCUNHO");
    }

    @Test
    void renomearForaDeRascunhoDa409() {
        assertThatThrownBy(() -> PermissoesCampanha.exigir(RENOMEAR, comStatus(REPROVADA), GABRIELA))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Renomear só vale em RASCUNHO: campanha 12 está em REPROVADA");
    }

    @Test
    void submeterComSimulacaoRodandoDa409ESomeDasAcoes() {
        var rodando = new SituacaoCampanha(12L, RASCUNHO, GABRIELA, null, null, 345L, 346L);

        assertThatThrownBy(() -> PermissoesCampanha.exigir(SUBMETER, rodando, GABRIELA))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Aguarde a simulação 346 terminar");
        assertThat(PermissoesCampanha.acoes(rodando, GABRIELA)).doesNotContain(SUBMETER);
    }

    @Test
    void submeterSemSimulacaoConcluidaDa400() {
        var semSimulacao = new SituacaoCampanha(12L, RASCUNHO, GABRIELA, null, null, null, null);

        assertThatThrownBy(() -> PermissoesCampanha.exigir(SUBMETER, semSimulacao, GABRIELA))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Simule antes de submeter: a campanha 12 não tem simulação concluída");
    }

    @Test
    void submeterDeNovoComASimulacaoDaVersaoAnteriorDa400() {
        var voltouParaRascunho = new SituacaoCampanha(12L, RASCUNHO, GABRIELA, 1, 345L, 345L, null);

        assertThatThrownBy(() -> PermissoesCampanha.exigir(SUBMETER, voltouParaRascunho, GABRIELA))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Simule de novo antes de submeter: a v1 usou a simulação 345");
    }

    @Test
    void submeterDeNovoComSimulacaoNovaPassa() {
        var simulouDeNovo = new SituacaoCampanha(12L, RASCUNHO, GABRIELA, 1, 345L, 350L, null);

        assertThatCode(() -> PermissoesCampanha.exigir(SUBMETER, simulouDeNovo, GABRIELA))
                .doesNotThrowAnyException();
    }

    @Test
    void autorSempreVe() {
        assertThatCode(() -> PermissoesCampanha.exigirLeitura(rascunho(), usuario(GABRIELA, Perfil.GERENTE)))
                .doesNotThrowAnyException();
    }

    @Test
    void supervisorVeDepoisDaPrimeiraSubmissao() {
        assertThatCode(() -> PermissoesCampanha.exigirLeitura(comStatus(REPROVADA), usuario(SERGIO, Perfil.SUPERVISOR)))
                .doesNotThrowAnyException();
    }

    @Test
    void supervisorNaoVeRascunhoNuncaSubmetido() {
        assertThatThrownBy(() -> PermissoesCampanha.exigirLeitura(rascunho(), usuario(SERGIO, Perfil.SUPERVISOR)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Sem permissão para ver a campanha 12");
    }

    @Test
    void outroGerenteNaoVeMesmoSubmetida() {
        assertThatThrownBy(() -> PermissoesCampanha.exigirLeitura(comStatus(AGUARDANDO_APROVACAO), usuario(5, Perfil.GERENTE)))
                .isInstanceOf(ForbiddenException.class);
    }

    private static Usuario usuario(int id, Perfil perfil) {
        var usuario = new Usuario();
        usuario.setId(id);
        usuario.setPerfil(perfil);
        return usuario;
    }
}
