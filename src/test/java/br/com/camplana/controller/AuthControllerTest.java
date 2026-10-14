package br.com.camplana.controller;

import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.Usuario;
import br.com.camplana.repository.UsuarioRepository;
import br.com.camplana.security.JwtService;
import br.com.camplana.security.PasswordConfig;
import br.com.camplana.security.SecurityConfig;
import br.com.camplana.handler.SecurityErrorHandler;
import br.com.camplana.security.UsuarioDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        properties = "api.security.token.secret=segredo-de-teste-com-mais-de-32-caracteres"
)
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtService.class,
        UsuarioDetailsService.class, PasswordConfig.class})
class AuthControllerTest {

    private static final String SENHA = "123456";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UsuarioRepository usuarioRepository;

    private Usuario gabriela;

    @BeforeEach
    void setUp() {
        gabriela = new Usuario();
        gabriela.setId(1);
        gabriela.setNome("Gabriela");
        gabriela.setEmail("gabriela@camplana.com.br");
        gabriela.setSenha(new BCryptPasswordEncoder().encode(SENHA));
        gabriela.setPerfil(Perfil.GERENTE);
        gabriela.setAtivo(true);
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String senha) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"));
    }

    @Test
    void loginComSenhaCertaDevolveTokenEPerfil() throws Exception {
        when(usuarioRepository.findByEmail(gabriela.getEmail())).thenReturn(Optional.of(gabriela));

        login(gabriela.getEmail(), SENHA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.perfil").value("GERENTE"))
                .andExpect(jsonPath("$.usuario.id").value(1))
                .andExpect(jsonPath("$.usuario.senha").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    @Test
    void senhaErradaDevolve401Generico() throws Exception {
        when(usuarioRepository.findByEmail(gabriela.getEmail())).thenReturn(Optional.of(gabriela));

        login(gabriela.getEmail(), "errada")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
    }

    @Test
    void emailInexistenteDevolveOMesmo401() throws Exception {
        when(usuarioRepository.findByEmail("naoexiste@camplana.com.br")).thenReturn(Optional.empty());

        login("naoexiste@camplana.com.br", SENHA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
    }

    @Test
    void usuarioInativoDevolveOMesmo401() throws Exception {
        gabriela.setAtivo(false);
        when(usuarioRepository.findByEmail(gabriela.getEmail())).thenReturn(Optional.of(gabriela));

        login(gabriela.getEmail(), SENHA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
    }
}