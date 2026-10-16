package br.com.camplana.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
class SecurityUsuarioForaDoProfileDevTest extends SecurityTestBase {

    @Test void headerDevEhIgnoradoForaDoDev() throws Exception {
        mockMvc.perform(get("/api/usuarios").header("X-Usuario-Dev", "4"))
                .andExpect(status().isUnauthorized());
    }
    @Test void headerDevNaoSobrepoeOTokenForaDoDev() throws Exception {
        // token da Gabriela (sem permissão) + header da Ana: vale o token → 403
        mockMvc.perform(get("/api/usuarios")
                        .header("Authorization", bearer(gabriela))
                        .header("X-Usuario-Dev", "4"))
                .andExpect(status().isForbidden());
    }
}