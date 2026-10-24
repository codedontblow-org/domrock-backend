package br.com.camplana.campanha;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Até o A6-66, nenhuma simulação existe: salvar responde 400 em vez de quebrar. */
class RegistroSimulacoesVazioTest {

    private final RegistroSimulacoesVazio registro = new RegistroSimulacoesVazio();

    @Test
    void naoAchaNenhumaSimulacao() {
        assertThat(registro.buscar(345)).isEmpty();
        assertThat(registro.ultimaConcluida(12)).isEmpty();
        assertThat(registro.emAndamento(12)).isEmpty();
    }

    @Test
    void vincularConversaNaoFalha() {
        assertThatCode(() -> registro.vincularConversa("c1f0a7e2", 12)).doesNotThrowAnyException();
    }
}
