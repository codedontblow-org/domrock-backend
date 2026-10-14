package br.com.camplana.security;

import br.com.camplana.client.LanaClient;
import br.com.camplana.controller.*;
import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.Usuario;
import br.com.camplana.handler.SecurityErrorHandler;
import br.com.camplana.mapper.UsuarioMapper;
import br.com.camplana.repository.UsuarioRepository;
import br.com.camplana.service.ImportacaoService;
import br.com.camplana.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebMvcTest(
        controllers = {ChatController.class, SimulacaoController.class,
                ImportacaoController.class, UsuarioController.class},
        properties = "api.security.token.secret=segredo-de-teste-com-mais-de-32-caracteres"
)
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtService.class,
        UsuarioDetailsService.class, PasswordConfig.class})
public abstract class SecurityTestBase {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected JwtService jwtService;

    @MockitoBean protected UsuarioRepository usuarioRepository;
    @MockitoBean protected LanaClient lanaClient;
    @MockitoBean protected ImportacaoService importacaoService;
    @MockitoBean protected UsuarioService usuarioService;
    @MockitoBean protected UsuarioMapper usuarioMapper;

    protected Usuario gabriela, sergio, ana;

    @BeforeEach
    void criarUsuarios() {
        gabriela = usuario(1, "Gabriela", "gabriela@camplana.com.br", Perfil.GERENTE);
        sergio   = usuario(2, "Sérgio",   "sergio@camplana.com.br",   Perfil.SUPERVISOR);
        ana      = usuario(4, "Ana",      "ana@camplana.com.br",      Perfil.ADMIN);
        when(usuarioService.listAll(any())).thenReturn(Page.empty());
        when(importacaoService.importarLote(any())).thenReturn(List.of());
    }

    protected Usuario usuario(int id, String nome, String email, Perfil perfil) {
        var u = new Usuario();
        u.setId(id);
        u.setNome(nome);
        u.setEmail(email);
        u.setSenha("hash-irrelevante");
        u.setPerfil(perfil);
        u.setAtivo(true);
        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    protected String bearer(Usuario u) {
        return "Bearer " + jwtService.gerarToken(u, jwtService.getExpirationDate());
    }

    /** "Permitido" = a segurança deixou passar (nem 401 nem 403). */
    protected ResultMatcher liberado() {
        return result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }
}