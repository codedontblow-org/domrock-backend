package br.com.camplana.controller;

import br.com.camplana.dto.correcao.DecisaoRequest;
import br.com.camplana.dto.correcao.SolicitacaoDetalheResponse;
import br.com.camplana.dto.correcao.SolicitacaoDetalheResponse.DecisaoResumo;
import br.com.camplana.dto.correcao.SolicitacaoDetalheResponse.UsuarioResumo;
import br.com.camplana.dto.correcao.SolicitacaoRequest;
import br.com.camplana.entity.*;
import br.com.camplana.exception.ConflictException;
import br.com.camplana.handler.GlobalExceptionHandler;
import br.com.camplana.handler.SecurityErrorHandler;
import br.com.camplana.repository.UsuarioRepository;
import br.com.camplana.security.JwtService;
import br.com.camplana.security.PasswordConfig;
import br.com.camplana.security.SecurityConfig;
import br.com.camplana.security.UsuarioDetailsService;
import br.com.camplana.service.SolicitacaoAlteracaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SolicitacaoAlteracaoController.class,
        properties = "api.security.token.secret=segredo-de-teste-com-mais-de-32-caracteres"
)
@Import({SecurityConfig.class, SecurityErrorHandler.class, JwtService.class,
        UsuarioDetailsService.class, PasswordConfig.class, GlobalExceptionHandler.class})
class SolicitacaoAlteracaoControllerTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @MockitoBean private SolicitacaoAlteracaoService service;
    @MockitoBean private UsuarioRepository usuarioRepository;

    private Usuario gabriela;
    private Usuario sergio;

    @BeforeEach
    void setUp() {
        gabriela = usuario(1, "Gabriela", "gabriela@camplana.com.br", Perfil.GERENTE);
        sergio = usuario(2, "Sérgio", "sergio@camplana.com.br", Perfil.SUPERVISOR);
    }

    @Test
    void postCriarSolicitacaoRetorna201ComObjetoCompletoSecao6_1() throws Exception {
        SolicitacaoDetalheResponse criada = new SolicitacaoDetalheResponse(
                7,
                TipoAlteracao.TRANSFERENCIA_LOJA,
                "MATRIC-38",
                "2025-10",
                "33",
                "8",
                "Transferida para a LOJA-8 em outubro",
                OrigemSolicitacao.USUARIO,
                null,
                StatusSolicitacao.PENDENTE,
                new UsuarioResumo(1, "Gabriela"),
                LocalDateTime.of(2026, 10, 21, 15, 10, 0),
                null,
                "aberta por Gabriela"
        );

        when(service.criarSolicitacao(any(SolicitacaoRequest.class), any(Usuario.class)))
                .thenReturn(criada);

        String payload = """
                {
                  "tipo": "TRANSFERENCIA_LOJA",
                  "matricula": "MATRIC-38",
                  "competencia": "2025-10",
                  "antes": "33",
                  "depois": "8",
                  "motivo": "Transferida para a LOJA-8 em outubro",
                  "origem": "USUARIO",
                  "chat_id": null
                }
                """;

        mockMvc.perform(post("/api/correcoes")
                        .header("Authorization", bearer(gabriela))
                        .contentType(JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.tipo").value("TRANSFERENCIA_LOJA"))
                .andExpect(jsonPath("$.matricula").value("MATRIC-38"))
                .andExpect(jsonPath("$.competencia").value("2025-10"))
                .andExpect(jsonPath("$.antes").value("33"))
                .andExpect(jsonPath("$.depois").value("8"))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.autor.id").value(1))
                .andExpect(jsonPath("$.autor.nome").value("Gabriela"))
                .andExpect(jsonPath("$.decisao").doesNotExist());
    }

    @Test
    void postDecidirRetorna200QuandoAprovadaComSucesso() throws Exception {
        SolicitacaoDetalheResponse detalhe = new SolicitacaoDetalheResponse(
                7,
                TipoAlteracao.TRANSFERENCIA_LOJA,
                "MATRIC-38",
                "2025-10",
                "33",
                "8",
                "Transferida para a LOJA-8 em outubro",
                OrigemSolicitacao.LANA,
                "chat-01",
                StatusSolicitacao.APROVADA,
                new UsuarioResumo(1, "Gabriela"),
                LocalDateTime.of(2026, 10, 21, 15, 10, 0),
                new DecisaoResumo("APROVADA", new UsuarioResumo(2, "Sérgio"), LocalDateTime.of(2026, 10, 21, 15, 20, 0), ""),
                "aberta pela Lana a pedido de Gabriela"
        );

        when(service.decidirSolicitacao(eq(7), any(DecisaoRequest.class), any(Usuario.class)))
                .thenReturn(detalhe);

        mockMvc.perform(post("/api/correcoes/7/decidir")
                        .header("Authorization", bearer(sergio))
                        .contentType(JSON)
                        .content("{\"decisao\": \"APROVAR\", \"justificativa\": \"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.decisao.por.nome").value("Sérgio"))
                .andExpect(jsonPath("$.descricao_origem").value("aberta pela Lana a pedido de Gabriela"));
    }

    @Test
    void postDecidirRetorna403QuandoViolaRegraQuatroOlhos() throws Exception {
        when(service.decidirSolicitacao(eq(7), any(DecisaoRequest.class), any(Usuario.class)))
                .thenThrow(new AccessDeniedException("Outra pessoa precisa aprovar esta correção"));

        mockMvc.perform(post("/api/correcoes/7/decidir")
                        .header("Authorization", bearer(sergio))
                        .contentType(JSON)
                        .content("{\"decisao\": \"APROVAR\", \"justificativa\": \"\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Outra pessoa precisa aprovar esta correção"));
    }

    @Test
    void postDecidirRetorna403QuandoGerenteTentaDecidir() throws Exception {
        mockMvc.perform(post("/api/correcoes/7/decidir")
                        .header("Authorization", bearer(gabriela))
                        .contentType(JSON)
                        .content("{\"decisao\": \"APROVAR\", \"justificativa\": \"\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void postDecidirRetorna409QuandoOcorreDriftNaBase() throws Exception {
        when(service.decidirSolicitacao(eq(7), any(DecisaoRequest.class), any(Usuario.class)))
                .thenThrow(new ConflictException("A base mudou desde o pedido"));

        mockMvc.perform(post("/api/correcoes/7/decidir")
                        .header("Authorization", bearer(sergio))
                        .contentType(JSON)
                        .content("{\"decisao\": \"APROVAR\", \"justificativa\": \"\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A base mudou desde o pedido"));
    }

    @Test
    void getFilaRetorna200ParaSupervisorE403ParaGerente() throws Exception {
        SolicitacaoDetalheResponse pendente = new SolicitacaoDetalheResponse(
                7,
                TipoAlteracao.TRANSFERENCIA_LOJA,
                "MATRIC-38",
                "2025-10",
                "33",
                "8",
                "Mudança de loja",
                OrigemSolicitacao.LANA,
                "chat-01",
                StatusSolicitacao.PENDENTE,
                new UsuarioResumo(1, "Gabriela"),
                LocalDateTime.now(),
                null,
                "aberta pela Lana a pedido de Gabriela"
        );

        when(service.listarFila(any(Usuario.class))).thenReturn(List.of(pendente));

        mockMvc.perform(get("/api/correcoes/fila")
                        .header("Authorization", bearer(sergio)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].matricula").value("MATRIC-38"))
                .andExpect(jsonPath("$[0].status").value("PENDENTE"))
                .andExpect(jsonPath("$[0].chat_id").value("chat-01"));

        mockMvc.perform(get("/api/correcoes/fila")
                        .header("Authorization", bearer(gabriela)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDetalhePorIdRetorna200() throws Exception {
        SolicitacaoDetalheResponse detalhe = new SolicitacaoDetalheResponse(
                7,
                TipoAlteracao.TRANSFERENCIA_LOJA,
                "MATRIC-38",
                "2025-10",
                "33",
                "8",
                "Mudança de loja",
                OrigemSolicitacao.LANA,
                "chat-01",
                StatusSolicitacao.PENDENTE,
                new UsuarioResumo(1, "Gabriela"),
                LocalDateTime.now(),
                null,
                "aberta pela Lana a pedido de Gabriela"
        );

        when(service.buscarPorId(eq(7), any(Usuario.class))).thenReturn(detalhe);

        mockMvc.perform(get("/api/correcoes/7")
                        .header("Authorization", bearer(gabriela)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.descricao_origem").value("aberta pela Lana a pedido de Gabriela"));
    }

    private Usuario usuario(int id, String nome, String email, Perfil perfil) {
        var u = new Usuario();
        u.setId(id);
        u.setNome(nome);
        u.setEmail(email);
        u.setSenha("hash-irrelevante");
        u.setPerfil(perfil);
        u.setAtivo(true);
        when(usuarioRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(u));
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private String bearer(Usuario u) {
        return "Bearer " + jwtService.gerarToken(u, jwtService.getExpirationDate());
    }
}
