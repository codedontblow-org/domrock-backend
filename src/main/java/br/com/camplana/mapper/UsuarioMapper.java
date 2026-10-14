package br.com.camplana.mapper;

import br.com.camplana.dto.usuario.UsuarioPutBody;
import br.com.camplana.dto.usuario.UsuarioPostBody;
import br.com.camplana.dto.usuario.UsuarioResponse;
import br.com.camplana.entity.Usuario;
import org.springframework.stereotype.Component;

@Component
public class UsuarioMapper {

    public Usuario toEntity(UsuarioPostBody body) {
        Usuario usuario = new Usuario();

        usuario.setNome(body.nome());
        usuario.setEmail(body.email());
        usuario.setSenha(body.senha());
        usuario.setPerfil(body.perfil());

        return usuario;
    }

    public void updateEntity(Usuario usuario, UsuarioPutBody body) {
        if (body.nome() != null)   usuario.setNome(body.nome());
        if (body.email() != null)  usuario.setEmail(body.email());
        if (body.perfil() != null) usuario.setPerfil(body.perfil());
    }

    public UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                usuario.getPerfil(), usuario.getAtivo());
    }
}