package br.com.camplana.security;

import br.com.camplana.entity.Usuario;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class JwtService {

    private static final String ISSUER = "camplana-api";

    private final String secret;

    public JwtService(@Value("${api.security.token.secret}") String secret) {
        this.secret = secret;
    }

    public String gerarToken(Usuario usuario, ZonedDateTime validade) {
        try {
            return JWT.create()
                    .withIssuer(ISSUER)
                    .withSubject(usuario.getEmail())
                    .withClaim("id", usuario.getId())
                    .withClaim("perfil", usuario.getPerfil().name())
                    .withExpiresAt(validade.toInstant())
                    .sign(Algorithm.HMAC256(secret));
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Erro ao gerar token jwt", exception);
        }
    }

    public String getSubject(String tokenJWT) {
        try {
            return JWT.require(Algorithm.HMAC256(secret))
                    .withIssuer(ISSUER)
                    .build()
                    .verify(tokenJWT)
                    .getSubject();
        } catch (JWTVerificationException exception) {
            throw new RuntimeException("Token JWT inválido ou expirado!");
        }
    }

    public ZonedDateTime getExpirationDate() {
        return ZonedDateTime.now(ZoneId.of("UTC")).plus(8, ChronoUnit.HOURS);
    }
}
