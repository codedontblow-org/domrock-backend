package br.com.camplana.controller;

import br.com.camplana.campanha.CampanhaApiBase;
import br.com.camplana.client.LanaClient;
import br.com.camplana.dto.ChatRequest;
import br.com.camplana.entity.CampanhaVersao;
import br.com.camplana.entity.MensagemChat;
import br.com.camplana.entity.Simulacao;
import br.com.camplana.entity.StatusSimulacao;
import br.com.camplana.exception.LanaIndisponivelException;
import br.com.camplana.exception.LanaRespostaException;
import br.com.camplana.repository.MensagemChatRepository;
import br.com.camplana.repository.SimulacaoRepository;
import br.com.camplana.service.SimulacaoPersistenceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatApiTest extends CampanhaApiBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    @MockitoBean
    private LanaClient lanaClient;

    @Autowired
    private MensagemChatRepository mensagemChatRepository;

    @Autowired
    private SimulacaoRepository simulacaoRepository;

    @Autowired
    private SimulacaoPersistenceService persistenceService;

    @Test
    void gravacaoEmTransacaoSalvaMensagemDoUsuarioERespostaDaLanaJuntas() throws Exception {
        String chatId = "chat-transacao-1";
        given(lanaClient.invocarAgente(any(), any())).willReturn(JSON.readTree(
                "{\"response\":\"Preenchi a ficha.\",\"regra\":{\"rule_id\":\"r1\"},\"trilha\":{\"modelo\":\"m1\",\"passos\":[]}}"
        ));

        mvc.perform(post("/api/chat")
                        .header("Authorization", autorizacao(gabriela))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"" + chatId + "\",\"message\":\"Quero +1%\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Preenchi a ficha."));

        List<MensagemChat> msgs = mensagemChatRepository.findByChatIdOrderByCriadaEmAscIdAsc(chatId);
        assertThat(msgs).hasSize(2);

        MensagemChat usuarioMsg = msgs.get(0);
        assertThat(usuarioMsg.getPapel()).isEqualTo("usuario");
        assertThat(usuarioMsg.getTexto()).isEqualTo("Quero +1%");
        assertThat(usuarioMsg.getUsuario().getId()).isEqualTo(gabriela.getId());

        MensagemChat lanaMsg = msgs.get(1);
        assertThat(lanaMsg.getPapel()).isEqualTo("lana");
        assertThat(lanaMsg.getTexto()).isEqualTo("Preenchi a ficha.");
        assertThat(lanaMsg.getFicha()).contains("rule_id").contains("r1");
        assertThat(lanaMsg.getTrilha()).contains("modelo").contains("m1");
    }

    @Test
    void nadaGravadoQuandoLanaFalhaCom502() throws Exception {
        String chatId = "chat-falha-502";
        given(lanaClient.invocarAgente(any(), any())).willThrow(new LanaIndisponivelException("Lana fora"));
        mvc.perform(post("/api/chat")
                        .header("Authorization", autorizacao(gabriela))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"" + chatId + "\",\"message\":\"oi 502\"}"))
                .andExpect(status().isBadGateway());
        assertThat(mensagemChatRepository.findByChatIdOrderByCriadaEmAscIdAsc(chatId)).isEmpty();
    }

    @Test
    void nadaGravadoQuandoLanaFalhaCom503() throws Exception {
        String chatId = "chat-falha-503";
        given(lanaClient.invocarAgente(any(), any())).willThrow(new LanaRespostaException(503, "{\"etapa\":\"cota_llm\"}"));
        mvc.perform(post("/api/chat")
                        .header("Authorization", autorizacao(gabriela))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"" + chatId + "\",\"message\":\"oi 503\"}"))
                .andExpect(status().isServiceUnavailable());
        assertThat(mensagemChatRepository.findByChatIdOrderByCriadaEmAscIdAsc(chatId)).isEmpty();
    }

    @Test
    void nadaGravadoQuandoLanaFalhaCom504() throws Exception {
        String chatId = "chat-falha-504";
        given(lanaClient.invocarAgente(any(), any())).willThrow(new LanaRespostaException(504, "{\"etapa\":\"tempo\"}"));
        mvc.perform(post("/api/chat")
                        .header("Authorization", autorizacao(gabriela))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"" + chatId + "\",\"message\":\"oi 504\"}"))
                .andExpect(status().isGatewayTimeout());
        assertThat(mensagemChatRepository.findByChatIdOrderByCriadaEmAscIdAsc(chatId)).isEmpty();
    }

    @Test
    void envioDoHistoricoComTrilhaNoInvokeAgent() throws Exception {
        String chatId = "chat-com-historico";
        Instant t1 = Instant.parse("2026-10-21T12:00:00Z");
        Instant t2 = Instant.parse("2026-10-21T12:01:00Z");

        mensagemChatRepository.save(new MensagemChat(
                chatId, gabriela, "usuario", null, null,
                "Quanto vendeu out/2025?", null, null, t1
        ));
        mensagemChatRepository.save(new MensagemChat(
                chatId, gabriela, "lana", null, null,
                "Vendeu R$ 333.666,01.",
                "{\"rule_id\":\"r1\"}",
                "{\"modelo\":\"gemini\",\"passos\":[{\"ordem\":1,\"ferramenta\":\"consultar_banco\"}]}",
                t2
        ));

        given(lanaClient.invocarAgente(any(), any())).willReturn(JSON.readTree("{\"response\":\"Continuando...\"}"));

        mvc.perform(post("/api/chat")
                        .header("Authorization", autorizacao(gabriela))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"" + chatId + "\",\"message\":\"E novembro?\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> captorHistorico = ArgumentCaptor.forClass(String.class);
        verify(lanaClient).invocarAgente(any(), captorHistorico.capture());

        String historicoEnviado = captorHistorico.getValue();
        assertThat(historicoEnviado).contains("\"papel\":\"usuario\"");
        assertThat(historicoEnviado).contains("Quanto vendeu out/2025?");
        assertThat(historicoEnviado).contains("\"papel\":\"lana\"");
        assertThat(historicoEnviado).contains("Vendeu R$ 333.666,01.");
        assertThat(historicoEnviado).contains("\"trilha\"");
        assertThat(historicoEnviado).contains("consultar_banco");
        assertThat(historicoEnviado).doesNotContain("E novembro?"); // mensagem atual não vai no histórico
    }

    @Test
    void getMensagensDevolveHistoricoEmOrdem() throws Exception {
        String chatId = "chat-get-ordem";
        Instant t1 = Instant.parse("2026-10-21T12:58:10Z");
        Instant t2 = Instant.parse("2026-10-21T12:58:14Z");
        Instant t3 = Instant.parse("2026-10-21T12:59:02Z");
        Instant t4 = Instant.parse("2026-10-21T13:05:00Z");

        mensagemChatRepository.save(new MensagemChat(
                chatId, gabriela, "usuario", null, null,
                "Quero +1% para todas as marcas", null, null, t1
        ));
        mensagemChatRepository.save(new MensagemChat(
                chatId, gabriela, "lana", null, null,
                "Preenchi a ficha.", "{\"rule_id\":\"bf-1\"}", null, t2
        ));
        Simulacao sim = persistenceService.criarSimulacaoInicial(gabriela, chatId, null, "{\"regra\":1}");
        mensagemChatRepository.save(new MensagemChat(
                chatId, gabriela, "sistema", "SIMULACAO", sim.getId(),
                "Simulação concluída: custo adicional de R$ 23.736,10", null, null, t3
        ));
        mensagemChatRepository.save(new MensagemChat(
                chatId, gabriela, "sistema", "STATUS", null,
                "Enviado para análise — v1", null, null, t4
        ));

        mvc.perform(get("/api/chat/" + chatId + "/mensagens")
                        .header("Authorization", autorizacao(gabriela)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chat_id").value(chatId))
                .andExpect(jsonPath("$.mensagens[0].papel").value("usuario"))
                .andExpect(jsonPath("$.mensagens[0].texto").value("Quero +1% para todas as marcas"))
                .andExpect(jsonPath("$.mensagens[1].papel").value("lana"))
                .andExpect(jsonPath("$.mensagens[1].texto").value("Preenchi a ficha."))
                .andExpect(jsonPath("$.mensagens[1].regra.rule_id").value("bf-1"))
                .andExpect(jsonPath("$.mensagens[1].trilha").isEmpty())
                .andExpect(jsonPath("$.mensagens[2].papel").value("sistema"))
                .andExpect(jsonPath("$.mensagens[2].tipo").value("SIMULACAO"))
                .andExpect(jsonPath("$.mensagens[2].simulacao_id").value(sim.getId()))
                .andExpect(jsonPath("$.mensagens[2].texto").value("Simulação concluída: custo adicional de R$ 23.736,10"))
                .andExpect(jsonPath("$.mensagens[3].papel").value("sistema"))
                .andExpect(jsonPath("$.mensagens[3].tipo").value("STATUS"))
                .andExpect(jsonPath("$.mensagens[3].texto").value("Enviado para análise — v1"));
    }

    @Test
    void getMensagensPermissoesDonoESupervisorComCampanhaSubmetida() throws Exception {
        String chatId = "chat-permissoes";
        long idCampanha = criarCampanha(chatId);
        mensagemChatRepository.save(new MensagemChat(
                chatId, gabriela, "usuario", null, null, "Oi", null, null, Instant.now()
        ));

        mvc.perform(get("/api/chat/" + chatId + "/mensagens").header("Authorization", autorizacao(gabriela)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/chat/" + chatId + "/mensagens").header("Authorization", autorizacao(sergio)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/campanhas/" + idCampanha + "/submeter").header("Authorization", autorizacao(gabriela)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/chat/" + chatId + "/mensagens").header("Authorization", autorizacao(sergio)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/chat/chat-inexistente/mensagens").header("Authorization", autorizacao(gabriela)))
                .andExpect(status().isNotFound());
    }

    @Test
    void postChatDeUsuarioNaConversaDeOutroRecebe403ENadaEGravado() throws Exception {
        String chatId = "chat-outro-usuario";
        mensagemChatRepository.save(new MensagemChat(
                chatId, gabriela, "usuario", null, null, "Msg Gabriela", null, null, Instant.now()
        ));

        mvc.perform(post("/api/chat")
                        .header("Authorization", autorizacao(sergio))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"" + chatId + "\",\"message\":\"Tentando invadir\"}"))
                .andExpect(status().isForbidden());

        List<MensagemChat> msgs = mensagemChatRepository.findByChatIdOrderByCriadaEmAscIdAsc(chatId);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0).getUsuario().getId()).isEqualTo(gabriela.getId());
    }

    @Test
    void postChatDeUsuarioBRecebe403EmConversaQueSoTemSimulacaoDoUsuarioAENadaEGravado() throws Exception {
        String chatId = "chat-so-simulacao-usuario-a";
        Simulacao sim = persistenceService.criarSimulacaoInicial(gabriela, chatId, null, "{\"regra\":1}");
        persistenceService.concluirSimulacao(sim.getId(), "{\"totais\":{\"diferenca\":100.0}}", null, "c", "m", 1, 10L);

        mvc.perform(post("/api/chat")
                        .header("Authorization", autorizacao(sergio))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"" + chatId + "\",\"message\":\"Tentando assumir o chat\"}"))
                .andExpect(status().isForbidden());

        List<MensagemChat> msgs = mensagemChatRepository.findByChatIdOrderByCriadaEmAscIdAsc(chatId);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0).getPapel()).isEqualTo("sistema");
        assertThat(msgs.get(0).getTipo()).isEqualTo("SIMULACAO");
        assertThat(msgs.get(0).getUsuario().getId()).isEqualTo(gabriela.getId());
    }

    @Test
    void postChatDevolveMensagensSistemaGravadasApenasAposUltimaMensagemDoUsuario() throws Exception {
        String chatId = "chat-mensagens-sistema-corte";
        gravarHistoricoComReprovacaoParaCorte(chatId);
        given(lanaClient.invocarAgente(any(), any())).willReturn(JSON.readTree("{\"response\":\"Ajustando.\"}"));

        mvc.perform(post("/api/chat")
                        .header("Authorization", autorizacao(gabriela))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"" + chatId + "\",\"message\":\"Vou ajustar\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagens_sistema").isArray())
                .andExpect(jsonPath("$.mensagens_sistema.length()").value(1))
                .andExpect(jsonPath("$.mensagens_sistema[0].texto").value("Reprovado por Sérgio: meta muito alta"));
    }

    private void gravarHistoricoComReprovacaoParaCorte(String chatId) {
        Instant agora = Instant.now();
        mensagemChatRepository.save(new MensagemChat(
                chatId, gabriela, "sistema", "STATUS", null, "Mensagem Antiga de Sistema", null, null, agora.minusSeconds(30)
        ));
        mensagemChatRepository.save(new MensagemChat(
                chatId, gabriela, "usuario", null, null, "Oi", null, null, agora.minusSeconds(20)
        ));
        mensagemChatRepository.save(new MensagemChat(
                chatId, gabriela, "lana", null, null, "Olá!", null, null, agora.minusSeconds(10)
        ));
        mensagemChatRepository.save(new MensagemChat(
                chatId, gabriela, "sistema", "STATUS", null, "Reprovado por Sérgio: meta muito alta", null, null, agora
        ));
    }

    @Test
    void primeiroTurnoCapturaMensagemDeSistemaGravadaDuranteProcessamentoDaLana() throws Exception {
        String chatId = "chat-primeiro-turno-sistema";
        given(lanaClient.invocarAgente(any(), any())).willAnswer(invocation -> {
            mensagemChatRepository.save(new MensagemChat(
                    chatId, gabriela, "sistema", "STATUS", null, "Mensagem concorrente de sistema", null, null, Instant.now()
            ));
            return JSON.readTree("{\"response\":\"Resposta da Lana\"}");
        });

        mvc.perform(post("/api/chat")
                        .header("Authorization", autorizacao(gabriela))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chat_id\":\"" + chatId + "\",\"message\":\"Primeira mensagem\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagens_sistema").isArray())
                .andExpect(jsonPath("$.mensagens_sistema.length()").value(1))
                .andExpect(jsonPath("$.mensagens_sistema[0].texto").value("Mensagem concorrente de sistema"));
    }

    @Test
    void mudancasDeStatusDaCampanhaGeramMensagensDeSistema() throws Exception {
        // 1. Salvar campanha em RASCUNHO a partir de uma simulação
        String chatId = "chat-campanha-ciclo";
        long idCampanha = criarCampanha(chatId);

        // 2. Submeter (RASCUNHO -> AGUARDANDO_APROVACAO): grava "Enviado para análise — v1"
        mvc.perform(post("/api/campanhas/" + idCampanha + "/submeter")
                        .header("Authorization", autorizacao(gabriela)))
                .andExpect(status().isOk());

        List<MensagemChat> msgsSubmeter = mensagemChatRepository.findByChatIdOrderByCriadaEmAscIdAsc(chatId);
        assertThat(msgsSubmeter).anySatisfy(m -> {
            assertThat(m.getPapel()).isEqualTo("sistema");
            assertThat(m.getTipo()).isEqualTo("STATUS");
            assertThat(m.getTexto()).isEqualTo("Enviado para análise — v1");
        });

        CampanhaVersao versaoSubmetida = versaoRepository.findByCampanhaIdOrderByNumeroAsc(idCampanha).get(0);
        assertThat(versaoSubmetida.getUltimaMensagemId()).isNotNull();
        MensagemChat msgUltima = mensagemChatRepository.findById(versaoSubmetida.getUltimaMensagemId()).orElseThrow();
        assertThat(msgUltima.getTexto()).isEqualTo("Enviado para análise — v1");

        // 3. Decidir REPROVAR: grava "Reprovado por Sérgio: ..."
        mvc.perform(post("/api/campanhas/" + idCampanha + "/decidir")
                        .header("Authorization", autorizacao(sergio))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versao\":1,\"decisao\":\"REPROVAR\",\"justificativa\":\"meta baixa\"}"))
                .andExpect(status().isOk());

        List<MensagemChat> msgsReprovar = mensagemChatRepository.findByChatIdOrderByCriadaEmAscIdAsc(chatId);
        assertThat(msgsReprovar).anySatisfy(m -> {
            assertThat(m.getTexto()).isEqualTo("Reprovado por Sérgio: meta baixa");
        });

        // 4. Editar (REPROVADA -> RASCUNHO): grava "Voltou para rascunho — a próxima submissão será a v2"
        mvc.perform(post("/api/campanhas/" + idCampanha + "/editar")
                        .header("Authorization", autorizacao(gabriela)))
                .andExpect(status().isOk());

        List<MensagemChat> msgsEditar = mensagemChatRepository.findByChatIdOrderByCriadaEmAscIdAsc(chatId);
        assertThat(msgsEditar).anySatisfy(m -> {
            assertThat(m.getTexto()).isEqualTo("Voltou para rascunho — a próxima submissão será a v2");
        });

        // 5. Arquivar: grava "Arquivada"
        mvc.perform(post("/api/campanhas/" + idCampanha + "/arquivar")
                        .header("Authorization", autorizacao(gabriela)))
                .andExpect(status().isOk());

        List<MensagemChat> msgsArquivar = mensagemChatRepository.findByChatIdOrderByCriadaEmAscIdAsc(chatId);
        assertThat(msgsArquivar).anySatisfy(m -> {
            assertThat(m.getTexto()).isEqualTo("Arquivada");
        });
    }

    @Test
    void simulacaoConcluidaGeraMensagemDeSistemaEErroNaoGeraMensagem() {
        String chatId = "chat-simulacao-msg";
        Simulacao simConcluida = persistenceService.criarSimulacaoInicial(gabriela, chatId, null, "{\"regra\":1}");
        String resultadoJson = "{\"totais\":{\"diferenca\":23736.10}}";

        // Concluir simulação gera mensagem em pt-BR
        persistenceService.concluirSimulacao(simConcluida.getId(), resultadoJson, null, "codigo", "modelo", 1, 100L);

        List<MensagemChat> msgs = mensagemChatRepository.findByChatIdOrderByCriadaEmAscIdAsc(chatId);
        assertThat(msgs).hasSize(1);
        MensagemChat msg = msgs.get(0);
        assertThat(msg.getPapel()).isEqualTo("sistema");
        assertThat(msg.getTipo()).isEqualTo("SIMULACAO");
        assertThat(msg.getSimulacaoId()).isEqualTo(simConcluida.getId());
        assertThat(msg.getTexto()).isEqualTo("Simulação concluída: custo adicional de R$ 23.736,10");

        // Simulação com erro não gera mensagem
        String chatErro = "chat-simulacao-erro";
        Simulacao simErro = persistenceService.criarSimulacaoInicial(gabriela, chatErro, null, "{\"regra\":1}");
        persistenceService.falharSimulacao(simErro.getId(), "{\"etapa\":\"erro\"}");

        assertThat(mensagemChatRepository.findByChatIdOrderByCriadaEmAscIdAsc(chatErro)).isEmpty();
    }
}
