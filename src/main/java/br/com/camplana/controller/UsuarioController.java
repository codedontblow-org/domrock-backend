package br.com.camplana.controller;

import br.com.camplana.dto.UsuarioPostBody;
import br.com.camplana.dto.UsuarioPutBody;
import br.com.camplana.dto.UsuarioResponse;
import br.com.camplana.entity.Usuario;
import br.com.camplana.mapper.UsuarioMapper;
import br.com.camplana.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;

@RestController
@RequestMapping("/usuario")
@RequiredArgsConstructor
public class UsuarioController {
    private final UsuarioService usuarioService;
    private final UsuarioMapper usuarioMapper;

    @PostMapping
    public ResponseEntity<UsuarioResponse> create(@Valid @RequestBody UsuarioPostBody body) {
        Usuario usuario = usuarioMapper.toEntity(body);
        Usuario savedUsuario = usuarioService.create(usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioMapper.toResponse(savedUsuario));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<UsuarioResponse> findById(@PathVariable Integer id) {
        Usuario savedUsuario = usuarioService.findById(id);
        return ResponseEntity.status(HttpStatus.OK).body(usuarioMapper.toResponse(savedUsuario));
    }

    @GetMapping
    public ResponseEntity<Page<UsuarioResponse>> listAll(Pageable pageable) {
        Page<Usuario> usuarios = usuarioService.listAll(pageable);
        Page<UsuarioResponse> response = usuarios.map(usuarioMapper::toResponse);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody UsuarioPutBody body) {
        Usuario usuario = usuarioService.findById(id);
        usuarioMapper.updateEntity(usuario, body);
        Usuario updatedUsuario = usuarioService.update(usuario);
        return ResponseEntity.ok(usuarioMapper.toResponse(updatedUsuario));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable Integer id) {
        usuarioService.remove(id);
        return ResponseEntity.noContent().build();
    }

}
