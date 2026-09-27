package br.com.camplana.exception;

import lombok.Getter;

/** A Lana respondeu com erro HTTP; o status e o corpo são repassados ao front. */
@Getter
public class LanaRespostaException extends RuntimeException {

    private final int status;
    private final String corpo;

    public LanaRespostaException(int status, String corpo) {
        super("Lana respondeu " + status + ": " + corpo);
        this.status = status;
        this.corpo = corpo;
    }
}
