package br.com.camplana.dto.usuario;

import br.com.camplana.entity.Perfil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UsuarioPutBody(

        @Pattern(regexp = ".*\\S.*", message = "Nome não pode ser vazio ou apenas espaços")
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
        String nome,

        @Pattern(regexp = ".*\\S.*", message = "Email não pode ser vazio ou apenas espaços")
        @Email(message = "Email inválido")
        @Size(max = 150, message = "Email deve ter no máximo 150 caracteres")
        String email,

        Perfil perfil
) {}