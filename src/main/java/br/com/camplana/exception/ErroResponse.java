package br.com.camplana.exception;

import org.springframework.http.HttpStatus;
import java.time.Instant;

public record ErroResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
    public static ErroResponse of(HttpStatus status, String message, String path) {
        return new ErroResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path);
    }
}