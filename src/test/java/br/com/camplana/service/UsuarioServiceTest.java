package br.com.camplana.service;

import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.Usuario;
import br.com.camplana.exception.BadRequestException;
import br.com.camplana.exception.ConflictException;
import br.com.camplana.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private UsuarioServiceImp service;

    private Usuario ana;
    private Usuario carlos;

    @BeforeEach
    void setUp() {
        ana = usuario(1, "Ana", "ana@camplana.com.br", Perfil.ADMIN);
        carlos = usuario(2, "Carlos", "carlos@camplana.dev", Perfil.SUPERVISOR);
    }

    // ─── CREATE ───────────────────────────────────────────────────────────────

    @Test
    void criarUsuarioNovoRetornaUsuarioSalvo() {
        when(usuarioRepository.findByEmailIgnoreCase(carlos.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Usuario resultado = service.create(carlos);

        assertThat(resultado.getNome()).isEqualTo("Carlos");
        assertThat(resultado.getSenha()).isEqualTo("hash");
        verify(usuarioRepository).save(carlos);
    }

    @Test
    void criarComEmailDuplicadoLancaConflict() {
        when(usuarioRepository.findByEmailIgnoreCase("carlos@camplana.dev"))
                .thenReturn(Optional.of(carlos));

        assertThatThrownBy(() -> service.create(carlos))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Já existe um usuário com este e-mail");
    }

    @Test
    void criarComEmailEmCaseDiferenteLancaConflict() {
        Usuario duplicado = usuario(3, "Duplicado", "CARLOS@CAMPLANA.DEV", Perfil.GERENTE);
        when(usuarioRepository.findByEmailIgnoreCase("CARLOS@CAMPLANA.DEV"))
                .thenReturn(Optional.of(carlos));

        assertThatThrownBy(() -> service.create(duplicado))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Já existe um usuário com este e-mail");
    }

    // ─── UPDATE ───────────────────────────────────────────────────────────────

    @Test
    void atualizarEmailDuplicadoDeOutroUsuarioLancaConflict() {
        Usuario outro = usuario(3, "Outro", "carlos@camplana.dev", Perfil.GERENTE);
        when(usuarioRepository.findByEmailIgnoreCase("carlos@camplana.dev"))
                .thenReturn(Optional.of(carlos));

        assertThatThrownBy(() -> service.update(outro, ana.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void adminNaoPodeRemoverProprioPerfilDeAdmin() {
        ana.setPerfil(Perfil.GERENTE);
        when(usuarioRepository.findByEmailIgnoreCase(ana.getEmail())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(ana, ana.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Você não pode remover o perfil de Administrador da própria conta");
    }

    // ─── DESATIVAR ────────────────────────────────────────────────────────────

    @Test
    void desativarPropriaContaLancaBadRequest() {
        assertThatThrownBy(() -> service.desativar(ana.getId(), ana.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Você não pode desativar a própria conta");
    }

    @Test
    void desativarOutroUsuarioSetaAtivoFalse() {
        when(usuarioRepository.findById(carlos.getId())).thenReturn(Optional.of(carlos));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Usuario resultado = service.desativar(carlos.getId(), ana.getId());

        assertThat(resultado.getAtivo()).isFalse();
        verify(usuarioRepository).save(carlos);
    }

    // ─── REATIVAR ─────────────────────────────────────────────────────────────

    @Test
    void reativarUsuarioDesativadoSetaAtivoTrue() {
        carlos.setAtivo(false);
        when(usuarioRepository.findById(carlos.getId())).thenReturn(Optional.of(carlos));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Usuario resultado = service.reativar(carlos.getId());

        assertThat(resultado.getAtivo()).isTrue();
        verify(usuarioRepository).save(carlos);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Usuario usuario(int id, String nome, String email, Perfil perfil) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNome(nome);
        u.setEmail(email);
        u.setSenha("senha-hash");
        u.setPerfil(perfil);
        u.setAtivo(true);
        return u;
    }
}
