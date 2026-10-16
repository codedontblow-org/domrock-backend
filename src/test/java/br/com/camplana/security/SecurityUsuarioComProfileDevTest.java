package br.com.camplana.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("dev")
class SecurityUsuarioComProfileDevTest extends SecurityTestBase {

    @Test void headerDevVale_AnaListaUsuarios() throws Exception {
        mockMvc.perform(get("/api/usuarios").header("X-Usuario-Dev", "4"))
                .andExpect(status().isOk());
    }
    @Test void headerDevRespeitaPerfil_GabrielaRecebe403() throws Exception {
        mockMvc.perform(get("/api/usuarios").header("X-Usuario-Dev", "1"))
                .andExpect(status().isForbidden());
    }
    @Test void headerDevComIdInexistenteRecebe401() throws Exception {
        mockMvc.perform(get("/api/usuarios").header("X-Usuario-Dev", "999"))
                .andExpect(status().isUnauthorized());
    }
    @Test void headerDevMalFormadoRecebe401() throws Exception {
        mockMvc.perform(get("/api/usuarios").header("X-Usuario-Dev", "abc"))
                .andExpect(status().isUnauthorized());
    }
    @Test void headerDevDeUsuarioInativoRecebe401() throws Exception {
        ana.setAtivo(false);
        mockMvc.perform(get("/api/usuarios").header("X-Usuario-Dev", "4"))
                .andExpect(status().isUnauthorized());
    }
}