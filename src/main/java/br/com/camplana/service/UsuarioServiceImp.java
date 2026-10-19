package br.com.camplana.service;

import br.com.camplana.entity.Usuario;
import br.com.camplana.exception.BadRequestException;
import br.com.camplana.exception.ValidationException;
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
        if (usuarioRepository.findByEmail(usuario.getEmail()).isPresent()) {
            throw new ValidationException("Email já cadastrado.");
        }

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
    public Usuario update(Usuario usuario) {
        usuarioRepository.findByEmail(usuario.getEmail())
                .ifPresent(usuarioExistente -> {
                    if (!usuarioExistente.getId().equals(usuario.getId())) {
                        throw new ValidationException("Email já cadastrado.");
                    }
                });
        return usuarioRepository.save(usuario);
    }
}