package br.com.camplana.controller;

import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.Usuario;
import br.com.camplana.handler.GlobalExceptionHandler;
import br.com.camplana.handler.SecurityErrorHandler;
import br.com.camplana.mapper.UsuarioMapper;
import br.com.camplana.repository.UsuarioRepository;
import br.com.camplana.security.JwtService;
import br.com.camplana.security.PasswordConfig;
import br.com.camplana.security.SecurityConfig;
import br.com.camplana.security.UsuarioDetailsService;
import br.com.camplana.service.UsuarioServiceImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@WebMvcTest(
        controllers = UsuarioController.class,
        properties = "api.security.token.secret=segredo-de-teste-com-mais-de-32-caracteres"
)
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtService.class,
        UsuarioDetailsService.class, PasswordConfig.class,
        UsuarioServiceImp.class, UsuarioMapper.class, GlobalExceptionHandler.class})
class UsuarioControllerTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;

    @MockitoBean UsuarioRepository usuarioRepository;

    private Usuario ana;
    private Usuario carlos;

    @BeforeEach
    void setUp() {
        ana = usuario(4, "Ana", "ana@camplana.com.br", Perfil.ADMIN);
        carlos = usuario(2, "Carlos", "carlos@camplana.dev", Perfil.SUPERVISOR);
    }

    @Test
    void deveRetornar400QuandoEmailEmBrancoNoPut() throws Exception {
        String payload = "{\"email\": \"\"}";

        mockMvc.perform(put("/api/usuarios/5")
                        .header("Authorization", bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criarComEmailJaCadastradoEmOutroCaseRecebe409() throws Exception {
        // o repositório real é case-insensitive; aqui simulamos que achou o Carlos
        when(usuarioRepository.findByEmailIgnoreCase("CARLOS@CAMPLANA.DEV"))
                .thenReturn(Optional.of(carlos));

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", bearer(ana))
                        .contentType(JSON)
                        .content("""
                                {"nome":"Outro","email":"CARLOS@CAMPLANA.DEV",
                                 "senha":"inicial123","perfil":"GERENTE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Já existe um usuário com este e-mail"));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void adminDesativarAPropriaContaRecebe400() throws Exception {
        mockMvc.perform(delete("/api/usuarios/4")
                        .header("Authorization", bearer(ana)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Você não pode desativar a própria conta"));

        verify(usuarioRepository, never()).save(any());
    }

    private Usuario usuario(int id, String nome, String email, Perfil perfil) {
        var u = new Usuario();
        u.setId(id);
        u.setNome(nome);
        u.setEmail(email);
        u.setSenha("hash-irrelevante");
        u.setPerfil(perfil);
        u.setAtivo(true);
        when(usuarioRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(u));
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private String bearer(Usuario u) {
        return "Bearer " + jwtService.gerarToken(u, jwtService.getExpirationDate());
    }
}