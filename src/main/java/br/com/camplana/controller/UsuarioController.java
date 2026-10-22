package br.com.camplana.controller;

import br.com.camplana.dto.usuario.ResumoResponse;
import br.com.camplana.dto.usuario.UsuarioPostBody;
import br.com.camplana.dto.usuario.UsuarioPutBody;
import br.com.camplana.dto.usuario.UsuarioResponse;
import br.com.camplana.entity.Usuario;
import br.com.camplana.mapper.UsuarioMapper;
import br.com.camplana.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioMapper usuarioMapper;

    @PostMapping
    public ResponseEntity<UsuarioResponse> create(
            @Valid @RequestBody UsuarioPostBody body
    ) {
        Usuario usuario = usuarioMapper.toEntity(body);
        Usuario salvo = usuarioService.create(usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioMapper.toResponse(salvo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> findById(@PathVariable Integer id) {
        return ResponseEntity.ok(usuarioMapper.toResponse(usuarioService.findById(id)));
    }

    @GetMapping
    public ResponseEntity<Page<UsuarioResponse>> listAll(Pageable pageable) {
        return ResponseEntity.ok(usuarioService.listAll(pageable).map(usuarioMapper::toResponse));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody UsuarioPutBody body,
            @AuthenticationPrincipal Usuario principal
    ) {
        Usuario usuario = usuarioService.findById(id);
        usuarioMapper.updateEntity(usuario, body);
        return ResponseEntity.ok(usuarioMapper.toResponse(usuarioService.update(usuario, principal.getId())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<UsuarioResponse> desativar(
            @PathVariable Integer id,
            @AuthenticationPrincipal Usuario principal
    ) {
        Usuario desativado = usuarioService.desativar(id, principal.getId());
        return ResponseEntity.ok(usuarioMapper.toResponse(desativado));
    }

    @PatchMapping("/{id}/reativar")
    public ResponseEntity<UsuarioResponse> reativar(@PathVariable Integer id) {
        Usuario reativado = usuarioService.reativar(id);
        return ResponseEntity.ok(usuarioMapper.toResponse(reativado));
    }

    @GetMapping("/resumo")
    public ResponseEntity<ResumoResponse> getResumo() {
        return ResponseEntity.ok(usuarioService.getResumo());
    }
}
