package br.com.camplana.dto.auth;

import lombok.Builder;
import lombok.Data;
import java.time.ZonedDateTime;

@Data
@Builder
public class LoginResponse {
    
    private String token;
    private String perfil;
    private ZonedDateTime validade;
    private LoginUserResponse usuario;

    @Data
    @Builder
    public static class LoginUserResponse {
        private Integer id;
        private String nome;
        private String email;
    }
}
