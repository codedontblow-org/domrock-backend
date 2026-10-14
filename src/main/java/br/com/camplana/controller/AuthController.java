package br.com.camplana.controller;

import br.com.camplana.dto.auth.LoginRequest;
import br.com.camplana.dto.auth.LoginResponse;
import br.com.camplana.entity.Usuario;
import br.com.camplana.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final DaoAuthenticationProvider authenticationProvider;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> efetuarLogin(@RequestBody @Valid LoginRequest dto) {
        var authentication = authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getSenha()));
        var usuario = (Usuario) authentication.getPrincipal();

        var validate = jwtService.getExpirationDate();
        var response = LoginResponse.builder()
                .token(jwtService.gerarToken(usuario, validate))
                .perfil(usuario.getPerfil().name())
                .validade(validate)
                .usuario(LoginResponse.LoginUserResponse.builder()
                        .id(usuario.getId())
                        .nome(usuario.getNome())
                        .email(usuario.getEmail())
                        .build())
                .build();

        return ResponseEntity.ok(response);
    }
}
