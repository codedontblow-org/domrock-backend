package br.com.camplana.exception;

/** A Lana não respondeu (fora do ar ou timeout). */
public class LanaIndisponivelException extends RuntimeException {

    public LanaIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
