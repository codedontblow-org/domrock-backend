package br.com.camplana.handler;

import br.com.camplana.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import br.com.camplana.exception.BadRequestException;
import br.com.camplana.exception.BadRequestExceptionDetails;
import br.com.camplana.exception.LanaIndisponivelException;
import br.com.camplana.exception.LanaRespostaException;
import br.com.camplana.exception.ValidationException;
import br.com.camplana.exception.ValidationExceptionDetails;

import java.time.LocalDateTime;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErroResponse> handleNotFoundException(NotFoundException ex, HttpServletRequest req) {
        return erro(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErroResponse> handleBadRequest(BadRequestException ex, HttpServletRequest req) {
        return erro(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErroResponse> handleValidation(ValidationException ex, HttpServletRequest req) {
        return erro(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErroResponse> handleConflict(ConflictException ex, HttpServletRequest req) {
        return erro(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return erro(HttpStatus.FORBIDDEN, ex.getMessage(), req);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErroResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return erro(status, ex.getReason() != null ? ex.getReason() : ex.getMessage(), req);
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
