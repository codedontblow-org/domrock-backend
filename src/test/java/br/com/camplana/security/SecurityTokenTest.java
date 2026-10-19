package br.com.camplana.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityTokenTest extends SecurityTestBase {

    @Test
    void semTokenRecebe401ComJson() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Autenticação necessária"))
                .andExpect(jsonPath("$.path").value("/api/usuarios"))
                .andExpect(jsonPath("$.timestamp").value(org.hamcrest.Matchers.endsWith("Z")));
    }

    @Test
    void tokenForaDoPadraoRecebe401() throws Exception {
        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer isso.nao.eh.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenAssinadoComOutroSegredoRecebe401() throws Exception {
        String falso = JWT.create().withIssuer("camplana-api").withSubject(ana.getEmail())
                .withExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .sign(Algorithm.HMAC256("outro-segredo-qualquer-com-32-caracteres!!"));
        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + falso))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpiradoRecebe401() throws Exception {
        String expirado = JWT.create().withIssuer("camplana-api").withSubject(ana.getEmail())
                .withExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .sign(Algorithm.HMAC256("segredo-de-teste-com-mais-de-32-caracteres"));
        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + expirado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenComEmissorErradoRecebe401() throws Exception {
        String outroEmissor = JWT.create().withIssuer("outra-api").withSubject(ana.getEmail())
                .withExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .sign(Algorithm.HMAC256("segredo-de-teste-com-mais-de-32-caracteres"));
        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + outroEmissor))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioDesativadoDepoisDoLoginNaoEntraComTokenAntigo() throws Exception {
        String token = bearer(ana);          // token emitido quando ela estava ativa
        ana.setAtivo(false);                 // admin desativou depois
        mockMvc.perform(get("/api/usuarios").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeUsuarioQueNaoExisteMaisRecebe401() throws Exception {
        String token = bearer(ana);
        org.mockito.Mockito.when(usuarioRepository.findByEmail(ana.getEmail()))
                .thenReturn(java.util.Optional.empty());
        mockMvc.perform(get("/api/usuarios").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginEhPublicoMasOutraRotaSemTokenNao() throws Exception {
        mockMvc.perform(get("/api/chat")).andExpect(status().isUnauthorized());
    }
}