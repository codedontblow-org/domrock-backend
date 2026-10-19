package br.com.camplana.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "E-mail não pode ser vazio")
    private String email;

    @NotBlank(message = "Senha não pode ser vazia")
    private String senha;
}
