package br.com.camplana.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import br.com.camplana.dto.usuario.UsuarioResponse;
import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.Usuario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class   SecurityPorPerfilTest extends SecurityTestBase {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    // ---------- /api/chat: Gerente e Supervisor sim, Admin não ----------
    @Test void chatGerentePermitido() throws Exception {
        mockMvc.perform(post("/api/chat").header("Authorization", bearer(gabriela))
                .contentType(JSON).content("{}")).andExpect(liberado());
    }
    @Test void chatSupervisorPermitido() throws Exception {
        mockMvc.perform(post("/api/chat").header("Authorization", bearer(sergio))
                .contentType(JSON).content("{}")).andExpect(liberado());
    }
    @Test void chatAdminRecebe403SemExecutar() throws Exception {
        // corpo vazio de propósito: o 403 tem que vir antes da validação (senão seria 400)
        mockMvc.perform(post("/api/chat").header("Authorization", bearer(ana))
                        .contentType(JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(lanaClient);
    }

    // ---------- /api/simulacoes ----------
    @Test void simulacaoGerentePermitido() throws Exception {
        mockMvc.perform(post("/api/simulacoes").header("Authorization", bearer(gabriela))
                .contentType(JSON).content("{\"regra\":{}}")).andExpect(liberado());
    }
    @Test void simulacaoSupervisorPermitido() throws Exception {
        mockMvc.perform(post("/api/simulacoes").header("Authorization", bearer(sergio))
                .contentType(JSON).content("{\"regra\":{}}")).andExpect(liberado());
    }
    @Test void simulacaoAdminRecebe403() throws Exception {
        mockMvc.perform(post("/api/simulacoes").header("Authorization", bearer(ana))
                        .contentType(JSON).content("{\"regra\":{}}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(lanaClient);
    }

    // ---------- /api/importacao: só Supervisor ----------
    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder importar() {
        return multipart("/api/importacao")
                .file(new MockMultipartFile("arquivos", "base.csv", "text/csv", "a;b".getBytes()));
    }
    @Test void importacaoSupervisorPermitido() throws Exception {
        mockMvc.perform(importar().header("Authorization", bearer(sergio))).andExpect(liberado());
    }
    @Test void importacaoGerenteRecebe403SemExecutar() throws Exception {
        mockMvc.perform(importar().header("Authorization", bearer(gabriela)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(importacaoService);
    }
    @Test void importacaoAdminRecebe403() throws Exception {
        mockMvc.perform(importar().header("Authorization", bearer(ana)))
                .andExpect(status().isForbidden());
    }

    // ---------- /api/usuarios: só Admin ----------
    @Test void usuariosAdminPermitido() throws Exception {
        mockMvc.perform(get("/api/usuarios").header("Authorization", bearer(ana)))
                .andExpect(status().isOk());
    }
    @Test void usuariosGerenteRecebe403() throws Exception {
        mockMvc.perform(get("/api/usuarios").header("Authorization", bearer(gabriela)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Sem permissão para esta ação"));
        verifyNoInteractions(usuarioService);
    }
    @Test void usuariosSupervisorRecebe403() throws Exception {
        mockMvc.perform(get("/api/usuarios").header("Authorization", bearer(sergio)))
                .andExpect(status().isForbidden());
    }
    @Test void criarUsuarioGerenteRecebe403() throws Exception {
        mockMvc.perform(post("/api/usuarios").header("Authorization", bearer(gabriela))
                        .contentType(JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test void deletarUsuarioSupervisorRecebe403() throws Exception {
        mockMvc.perform(delete("/api/usuarios/1").header("Authorization", bearer(sergio)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(usuarioService);
    }

    @Test void deletarUsuarioAdminPermitido() throws Exception {
        when(usuarioService.desativar(anyInt(), anyInt())).thenReturn(new Usuario());
        when(usuarioMapper.toResponse(any())).thenReturn(new UsuarioResponse(1, "x", "x@x.com", Perfil.SUPERVISOR, false));
        mockMvc.perform(delete("/api/usuarios/1").header("Authorization", bearer(ana)))
                .andExpect(liberado());
    }

    @Test void reativarUsuarioGerenteRecebe403() throws Exception {
        mockMvc.perform(patch("/api/usuarios/1/reativar").header("Authorization", bearer(gabriela)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(usuarioService);
    }

    @Test void reativarUsuarioAdminPermitido() throws Exception {
        when(usuarioService.reativar(anyInt())).thenReturn(new Usuario());
        when(usuarioMapper.toResponse(any())).thenReturn(new UsuarioResponse(1, "x", "x@x.com", Perfil.SUPERVISOR, true));
        mockMvc.perform(patch("/api/usuarios/1/reativar").header("Authorization", bearer(ana)))
                .andExpect(liberado());
    }

    // ---------- rota não mapeada: denyAll ----------
    @Test void rotaNaoMapeadaEhNegadaParaTodos() throws Exception {
        mockMvc.perform(get("/api/qualquer-coisa").header("Authorization", bearer(ana)))
                .andExpect(status().isForbidden());
    }
}
