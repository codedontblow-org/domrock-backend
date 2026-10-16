package br.com.camplana.service;

import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.Usuario;
import br.com.camplana.exception.BadRequestException;
import br.com.camplana.exception.ConflictException;
import br.com.camplana.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImp implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Usuario create(Usuario usuario) {
        usuarioRepository.findByEmailIgnoreCase(usuario.getEmail()).ifPresent(existente -> {
            throw new ConflictException("Já existe um usuário com este e-mail");
        });

        usuario.setSenha(passwordEncoder.encode(usuario.getSenha()));
        return usuarioRepository.save(usuario);
    }

    @Override
    public Usuario findById(Integer id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Usuário não encontrado."));
    }

    @Override
    public Page<Usuario> listAll(Pageable pageable) {
        return usuarioRepository.findAll(pageable);
    }

    @Override
    public Usuario update(Usuario usuario, Integer adminLogadoId) {
        usuarioRepository.findByEmailIgnoreCase(usuario.getEmail())
                .ifPresent(existente -> {
                    if (!existente.getId().equals(usuario.getId())) {
                        throw new ConflictException("Já existe um usuário com este e-mail");
                    }
                });

        if (usuario.getId().equals(adminLogadoId) && !Perfil.ADMIN.equals(usuario.getPerfil())) {
            throw new BadRequestException("Você não pode remover o perfil de Administrador da própria conta");
        }

        return usuarioRepository.save(usuario);
    }

    @Override
    public Usuario desativar(Integer id, Integer adminLogadoId) {
        if (id.equals(adminLogadoId)) {
            throw new BadRequestException("Você não pode desativar a própria conta");
        }

        Usuario usuario = findById(id);
        usuario.setAtivo(false);
        return usuarioRepository.save(usuario);
    }

    @Override
    public Usuario reativar(Integer id) {
        Usuario usuario = findById(id);
        usuario.setAtivo(true);
        return usuarioRepository.save(usuario);
    }
}