package br.com.camplana.exception;

import lombok.Data;
import lombok.experimental.SuperBuilder;
import java.time.LocalDateTime;

@Data
@SuperBuilder
public class ExceptionDetails {
    protected LocalDateTime timestamp;
    protected Integer status;
    protected String error;
    protected String message;
    protected String path;
}