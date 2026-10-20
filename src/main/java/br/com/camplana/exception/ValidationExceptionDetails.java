package br.com.camplana.exception;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class ValidationExceptionDetails extends ExceptionDetails {
    // adicionar campos específicos de validação se necessário no futuro
}