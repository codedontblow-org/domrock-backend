package br.com.camplana.handler;

import br.com.camplana.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErroResponse> handleBadRequest(BadRequestException ex, HttpServletRequest req) {
        return erro(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErroResponse> handleValidation(ValidationException ex, HttpServletRequest req) {
        return erro(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                     HttpServletRequest req) {
        return erro(HttpStatus.BAD_REQUEST, "Erro de validação.", req);
    }

    // Login: e-mail inexistente, senha errada ou usuário inativo. Mensagem única de propósito.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErroResponse> handleCredenciais(AuthenticationException ex, HttpServletRequest req) {
        log.warn("Falha de autenticação: {}", ex.getClass().getSimpleName());
        return erro(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos", req);
    }

    @ExceptionHandler(LanaRespostaException.class)
    public ResponseEntity<String> handleLanaResposta(LanaRespostaException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ex.getCorpo());
    }

    @ExceptionHandler(LanaIndisponivelException.class)
    public ResponseEntity<ErroResponse> handleLanaIndisponivel(LanaIndisponivelException ex,
                                                               HttpServletRequest req) {
        return erro(HttpStatus.BAD_GATEWAY, ex.getMessage(), req);
    }

    private ResponseEntity<ErroResponse> erro(HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(ErroResponse.of(status, message, req.getRequestURI()));
    }
}
