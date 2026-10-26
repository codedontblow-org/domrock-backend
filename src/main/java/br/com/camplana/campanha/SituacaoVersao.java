package br.com.camplana.campanha;

/**
 * O que as ações da versão (contrato 5.6) precisam saber, montado pelo {@link AprovacaoDaVersao}.
 *
 * @param naoPendente            por que a versão não aguarda decisão (a mensagem do 409), ou {@code null}
 * @param resimulacaoNecessaria  outra campanha do período foi aprovada depois da submissão e não houve
 *                               re-simulação depois dela
 */
public record SituacaoVersao(
        long campanhaId,
        int numero,
        int autorId,
        String naoPendente,
        boolean resimulacaoNecessaria
) {

    public boolean pendente() {
        return naoPendente == null;
    }
}
