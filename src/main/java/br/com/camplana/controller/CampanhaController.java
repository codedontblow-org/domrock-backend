package br.com.camplana.controller;

import br.com.camplana.campanha.CampanhaConsultaService;
import br.com.camplana.campanha.CampanhaService;
import br.com.camplana.dto.campanha.*;
import br.com.camplana.entity.Usuario;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Perfis na matriz do SecurityConfig (a fila é só SUPERVISOR); autor e status, em PermissoesCampanha.
@RestController
@RequestMapping("/api/campanhas")
public class CampanhaController {

    private final CampanhaService campanhaService;
    private final CampanhaConsultaService consulta;

    public CampanhaController(CampanhaService campanhaService, CampanhaConsultaService consulta) {
        this.campanhaService = campanhaService;
        this.consulta = consulta;
    }

    @PostMapping
    public ResponseEntity<CampanhaResponse> salvar(@Valid @RequestBody SalvarCampanhaRequest pedido,
                                                   @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campanhaService.salvar(pedido, usuario));
    }

    @GetMapping("/minhas")
    public List<CampanhaResumoResponse> minhas(@RequestParam(required = false) String status,
                                               @AuthenticationPrincipal Usuario usuario) {
        return consulta.minhas(usuario, status);
    }

    @GetMapping("/fila")
    public List<FilaCampanhaResponse> fila(@AuthenticationPrincipal Usuario usuario) {
        return consulta.fila(usuario);
    }

    @GetMapping("/{id}")
    public CampanhaResponse abrir(@PathVariable long id, @AuthenticationPrincipal Usuario usuario) {
        return consulta.abrir(id, usuario);
    }

    @GetMapping("/{id}/versoes/{numero}")
    public VersaoCampanhaResponse versao(@PathVariable long id, @PathVariable int numero,
                                         @AuthenticationPrincipal Usuario usuario) {
        return consulta.versao(id, numero, usuario);
    }

    @PutMapping("/{id}")
    public CampanhaResponse renomear(@PathVariable long id, @RequestBody RenomearCampanhaRequest pedido,
                                     @AuthenticationPrincipal Usuario usuario) {
        return campanhaService.renomear(id, pedido.nome(), usuario);
    }

    @PostMapping("/{id}/submeter")
    public CampanhaResponse submeter(@PathVariable long id, @AuthenticationPrincipal Usuario usuario) {
        return campanhaService.submeter(id, usuario);
    }

    @PostMapping("/{id}/editar")
    public CampanhaResponse editar(@PathVariable long id, @AuthenticationPrincipal Usuario usuario) {
        return campanhaService.editar(id, usuario);
    }

    @PostMapping("/{id}/arquivar")
    public CampanhaResponse arquivar(@PathVariable long id, @AuthenticationPrincipal Usuario usuario) {
        return campanhaService.arquivar(id, usuario);
    }
}
