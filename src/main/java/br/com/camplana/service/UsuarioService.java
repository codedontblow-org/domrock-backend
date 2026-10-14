package br.com.camplana.service;

import br.com.camplana.entity.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UsuarioService {

    Usuario create(Usuario usuario);

    Usuario findById(Integer id);

    Page<Usuario> listAll(Pageable pageable);

    Usuario update(Usuario usuario);
}