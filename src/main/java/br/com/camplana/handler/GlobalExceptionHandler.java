package br.com.camplana.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<BadRequestExceptionDetails> handleBadRequestException(
            BadRequestException exception,
            HttpServletRequest request
    ) {

        BadRequestExceptionDetails details = BadRequestExceptionDetails.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(exception.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(details);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ValidationExceptionDetails> handleValidationException(
            ValidationException exception,
            HttpServletRequest request
    ) {

        ValidationExceptionDetails details = ValidationExceptionDetails.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(exception.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(details);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationExceptionDetails> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {

        ValidationExceptionDetails details = ValidationExceptionDetails.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Erro de validação.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(details);
    }

    // Erro de negócio da Lana (ex.: 422 regra incompleta ou falha na geração do código):
    // repassa status e corpo para o front mostrar a etapa e a mensagem ao usuário.
    @ExceptionHandler(LanaRespostaException.class)
    public ResponseEntity<String> handleLanaResposta(LanaRespostaException exception) {
        return ResponseEntity
                .status(exception.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(exception.getCorpo());
    }

    @ExceptionHandler(LanaIndisponivelException.class)
    public ResponseEntity<BadRequestExceptionDetails> handleLanaIndisponivel(
            LanaIndisponivelException exception,
            HttpServletRequest request
    ) {

        BadRequestExceptionDetails details = BadRequestExceptionDetails.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_GATEWAY.value())
                .error(HttpStatus.BAD_GATEWAY.getReasonPhrase())
                .message(exception.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(details);
    }
}
