package br.com.camplana.campanha;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Provisório até o A6-66 criar a tabela {@code simulacao}: não acha nenhuma simulação, então salvar
 * responde 400 ("Simule antes de salvar") e o app sobe normal. O A6-66 troca esta classe por uma
 * implementação em JPA; nada mais muda na campanha.
 */
@Component
public class RegistroSimulacoesVazio implements RegistroSimulacoes {

    @Override
    public Optional<SimulacaoGravada> buscar(long simulacaoId) {
        return Optional.empty();
    }

    @Override
    public Optional<SimulacaoGravada> ultimaConcluida(long campanhaId) {
        return Optional.empty();
    }

    @Override
    public Optional<Long> emAndamento(long campanhaId) {
        return Optional.empty();
    }

    @Override
    public void vincularConversa(String chatId, long campanhaId) {
        // sem tabela de simulação ainda: nada a vincular
    }
}
