package br.com.camplana.service;

import br.com.camplana.entity.Usuario;
import br.com.camplana.dto.usuario.ResumoResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UsuarioService {

    Usuario create(Usuario usuario);

    Usuario findById(Integer id);

    Page<Usuario> listAll(Pageable pageable);

    Usuario update(Usuario usuario, Integer adminLogadoId);

    Usuario desativar(Integer id, Integer adminLogadoId);

    Usuario reativar(Integer id);

    ResumoResponse getResumo();
}