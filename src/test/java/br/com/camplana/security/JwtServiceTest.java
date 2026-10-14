package br.com.camplana.security;

import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.Usuario;
import com.auth0.jwt.JWT;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-caracteres";
    private final JwtService jwt = new JwtService(SEGREDO);

    private Usuario gabriela() {
        var u = new Usuario();
        u.setId(1); u.setNome("Gabriela");
        u.setEmail("gabriela@camplana.com.br"); u.setPerfil(Perfil.GERENTE);
        return u;
    }

    @Test
    void tokenCarregaIdPerfilEEmail() {
        var decoded = JWT.decode(jwt.gerarToken(gabriela(), jwt.getExpirationDate()));
        assertThat(decoded.getClaim("id").asInt()).isEqualTo(1);
        assertThat(decoded.getClaim("perfil").asString()).isEqualTo("GERENTE");
        assertThat(decoded.getSubject()).isEqualTo("gabriela@camplana.com.br");
    }

    @Test
    void validadeEhDeOitoHoras() {
        Instant exp = JWT.decode(jwt.gerarToken(gabriela(), jwt.getExpirationDate())).getExpiresAtAsInstant();
        assertThat(Duration.between(Instant.now(), exp))
                .isBetween(Duration.ofHours(8).minusSeconds(10), Duration.ofHours(8));
    }

    @Test
    void validaEDevolveOEmail() {
        String token = jwt.gerarToken(gabriela(), jwt.getExpirationDate());
        assertThat(jwt.getSubject(token)).isEqualTo("gabriela@camplana.com.br");
    }

    @Test
    void tokenAdulteradoEhRejeitado() {
        String token = jwt.gerarToken(gabriela(), jwt.getExpirationDate());
        String adulterado = token.substring(0, token.length() - 3) + "xyz";
        assertThatThrownBy(() -> jwt.getSubject(adulterado)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void tokenDeOutroSegredoEhRejeitado() {
        var outro = new JwtService("outro-segredo-qualquer-com-32-caracteres!!");
        String token = outro.gerarToken(gabriela(), outro.getExpirationDate());
        assertThatThrownBy(() -> jwt.getSubject(token)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void tokenExpiradoEhRejeitado() {
        String token = jwt.gerarToken(gabriela(), java.time.ZonedDateTime.now().minusMinutes(1));
        assertThatThrownBy(() -> jwt.getSubject(token)).isInstanceOf(RuntimeException.class);
    }
}