package br.com.camplana.dto;

import br.com.camplana.entity.Perfil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UsuarioPutBody(

        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
        String nome,

        @Email(message = "Email inválido")
        @Size(max = 150, message = "Email deve ter no máximo 150 caracteres")
        String email,

        @Size(min = 6, max = 255, message = "Senha deve ter entre 6 e 255 caracteres")
        String senha,

        Perfil perfil
) {
}