package br.com.camplana.service;

import br.com.camplana.dto.correcao.DecisaoRequest;
import br.com.camplana.dto.correcao.SolicitacaoDetalheResponse;
import br.com.camplana.dto.correcao.SolicitacaoRequest;
import br.com.camplana.entity.*;
import br.com.camplana.exception.BadRequestException;
import br.com.camplana.exception.ConflictException;
import br.com.camplana.exception.NotFoundException;
import br.com.camplana.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitacaoAlteracaoServiceTest {

    private static final String SEM_PERMISSAO = "Sem permissão para esta ação";
    private static final String OUTRA_PESSOA = "Outra pessoa precisa aprovar esta correção";
    private static final String BASE_MUDOU = "A base mudou desde o pedido";

    @Mock private SolicitacaoAlteracaoRepository solicitacaoRepository;
    @Mock private FuncionarioRepository funcionarioRepository;
    @Mock private FuncionarioLojaRepository funcionarioLojaRepository;
    @Mock private FuncionarioCargoRepository funcionarioCargoRepository;
    @Mock private LojaRepository lojaRepository;
    @Mock private CargoRepository cargoRepository;
    @Mock private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SolicitacaoAlteracaoService service;

    private Usuario gabriela;
    private Usuario sergio;
    private Usuario silvia;
    private Funcionario funcMatric38;
    private Marca marca;
    private Loja loja5;
    private Loja loja8;
    private Cargo cargo100;
    private Cargo cargo200;
    private LocalDate out2025;

    @BeforeEach
    void setUp() {
        service = new SolicitacaoAlteracaoService(
                solicitacaoRepository,
                funcionarioRepository,
                funcionarioLojaRepository,
                funcionarioCargoRepository,
                lojaRepository,
                cargoRepository,
                objectMapper,
                entityManager
        );

        gabriela = criarUsuario(1, "Gabriela", "gabriela@camplana.com.br", Perfil.GERENTE);
        sergio = criarUsuario(2, "Sérgio", "sergio@camplana.com.br", Perfil.SUPERVISOR);
        silvia = criarUsuario(3, "Sílvia", "silvia@camplana.com.br", Perfil.SUPERVISOR);

        funcMatric38 = new Funcionario(38, "MATRIC-38", LocalDate.of(2024, 1, 10), null);
        marca = new Marca(1, "M1", "Marca 1");
        loja5 = new Loja(5, "5", "Loja 5", marca);
        loja8 = new Loja(8, "8", "Loja 8", marca);
        // Ids internos diferentes dos códigos de propósito: pegam qualquer mistura entre os dois.
        cargo100 = new Cargo(1, "100", "VENDEDOR LOJA");
        cargo200 = new Cargo(3, "200", "VENDEDOR BALCAO");
        out2025 = LocalDate.of(2025, 10, 1);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Testes originais (A6-80): critérios 1 a 5 e reprovação sem justificativa
    // ═══════════════════════════════════════════════════════════════════════

    // ─── CRITÉRIO 1: Criação da solicitação (Seção 6.1) ───────────────────────

    @Test
    void gabrielaPedeTransferenciaMatric38DaLoja5ParaLoja8EmOut2025CriaSolicitacaoPendente() throws Exception {
        FuncionarioLoja alocacaoAtual = new FuncionarioLoja(
                new FuncionarioLojaId(funcMatric38.getId(), loja5.getId(), out2025),
                funcMatric38,
                loja5
        );

        when(funcionarioRepository.findByMatricula("MATRIC-38")).thenReturn(Optional.of(funcMatric38));
        when(funcionarioLojaRepository.existsByIdFuncionarioIdAndIdDateRef(funcMatric38.getId(), out2025))
                .thenReturn(true);
        when(funcionarioLojaRepository.findAlocacaoPorCodLoja(funcMatric38.getId(), out2025, "5"))
                .thenReturn(Optional.of(alocacaoAtual));
        when(lojaRepository.findByCodLoja("8")).thenReturn(Optional.of(loja8));
        when(solicitacaoRepository.save(any(SolicitacaoAlteracao.class))).thenAnswer(inv -> {
            SolicitacaoAlteracao s = inv.getArgument(0);
            s.setId(7);
            return s;
        });

        JsonNode antes = objectMapper.readTree("\"5\"");
        JsonNode depois = objectMapper.readTree("\"8\"");

        SolicitacaoRequest request = new SolicitacaoRequest(
                null,
                TipoAlteracao.TRANSFERENCIA_LOJA,
                "MATRIC-38",
                "2025-10",
                antes,
                depois,
                "Transferida para a LOJA-8 em outubro",
                null,
                OrigemSolicitacao.USUARIO
        );

        SolicitacaoDetalheResponse response = service.criarSolicitacao(request, gabriela);

        assertThat(response.id()).isEqualTo(7);
        assertThat(response.tipo()).isEqualTo(TipoAlteracao.TRANSFERENCIA_LOJA);
        assertThat(response.matricula()).isEqualTo("MATRIC-38");
        assertThat(response.competencia()).isEqualTo("2025-10");
        assertThat(response.antes()).isEqualTo("5");
        assertThat(response.depois()).isEqualTo("8");
        assertThat(response.status()).isEqualTo(StatusSolicitacao.PENDENTE);
        assertThat(response.autor().id()).isEqualTo(gabriela.getId());
        assertThat(response.autor().nome()).isEqualTo("Gabriela");
        assertThat(response.decisao()).isNull();

        ArgumentCaptor<SolicitacaoAlteracao> captor = ArgumentCaptor.forClass(SolicitacaoAlteracao.class);
        verify(solicitacaoRepository).save(captor.capture());

        SolicitacaoAlteracao salva = captor.getValue();
        assertThat(salva.getStatus()).isEqualTo(StatusSolicitacao.PENDENTE);
        assertThat(salva.getMatricula()).isEqualTo("MATRIC-38");
        assertThat(salva.getCompetencia()).isEqualTo(out2025);
        assertThat(salva.getAutor()).isEqualTo(gabriela);
    }

    // ─── CRITÉRIO 2: Aprovação por Sérgio aplica a correção na base (Seção 6.3)

    @Test
    void sergioAprovaSolicitacaoDeGabrielaEAplicaTransferenciaParaLoja8EmOut2025() {
        SolicitacaoAlteracao pendente = SolicitacaoAlteracao.builder()
                .id(10)
                .tipo(TipoAlteracao.TRANSFERENCIA_LOJA)
                .matricula("MATRIC-38")
                .competencia(out2025)
                .valorAntes("\"5\"")
                .valorDepois("\"8\"")
                .motivo("Transferência de loja")
                .autor(gabriela)
                .origem(OrigemSolicitacao.USUARIO)
                .status(StatusSolicitacao.PENDENTE)
                .criadaEm(LocalDateTime.now().minusHours(1))
                .build();

        FuncionarioLoja alocacaoLoja5 = new FuncionarioLoja(
                new FuncionarioLojaId(funcMatric38.getId(), loja5.getId(), out2025),
                funcMatric38,
                loja5
        );

        when(solicitacaoRepository.findByIdForUpdate(10)).thenReturn(Optional.of(pendente));
        when(funcionarioRepository.findByMatricula("MATRIC-38")).thenReturn(Optional.of(funcMatric38));
        when(funcionarioLojaRepository.findAlocacaoPorCodLoja(funcMatric38.getId(), out2025, "5"))
                .thenReturn(Optional.of(alocacaoLoja5));
        when(lojaRepository.findByCodLoja("8")).thenReturn(Optional.of(loja8));
        when(solicitacaoRepository.save(any(SolicitacaoAlteracao.class))).thenAnswer(inv -> inv.getArgument(0));

        DecisaoRequest decisao = new DecisaoRequest("APROVAR", "");

        SolicitacaoDetalheResponse resultado = service.decidirSolicitacao(10, decisao, sergio);

        verify(funcionarioLojaRepository).delete(alocacaoLoja5);
        ArgumentCaptor<FuncionarioLoja> novaAlocacaoCaptor = ArgumentCaptor.forClass(FuncionarioLoja.class);
        verify(funcionarioLojaRepository).save(novaAlocacaoCaptor.capture());

        FuncionarioLoja novaAlocacao = novaAlocacaoCaptor.getValue();
        assertThat(novaAlocacao.getId().getFuncionarioId()).isEqualTo(funcMatric38.getId());
        assertThat(novaAlocacao.getId().getLojaId()).isEqualTo(loja8.getId());
        assertThat(novaAlocacao.getId().getDateRef()).isEqualTo(out2025);
        assertThat(novaAlocacao.getLoja()).isEqualTo(loja8);

        assertThat(resultado.status()).isEqualTo(StatusSolicitacao.APROVADA);
        assertThat(resultado.decisao()).isNotNull();
        assertThat(resultado.decisao().decisao()).isEqualTo("APROVADA");
        assertThat(resultado.decisao().por().id()).isEqualTo(sergio.getId());
        assertThat(resultado.decisao().por().nome()).isEqualTo("Sérgio");
        assertThat(resultado.decisao().em()).isNotNull();
    }

    // ─── CRITÉRIO 3: Regra Quatro Olhos (403) ─────────────────────────────────

    @Test
    void autorTentandoAprovarPropriaSolicitacaoLancaAccessDeniedException() {
        SolicitacaoAlteracao solicitacaoDeGabriela = SolicitacaoAlteracao.builder()
                .id(11)
                .tipo(TipoAlteracao.TRANSFERENCIA_LOJA)
                .matricula("MATRIC-38")
                .competencia(out2025)
                .valorAntes("\"5\"")
                .valorDepois("\"8\"")
                .autor(gabriela)
                .origem(OrigemSolicitacao.USUARIO)
                .status(StatusSolicitacao.PENDENTE)
                .build();

        when(solicitacaoRepository.findByIdForUpdate(11)).thenReturn(Optional.of(solicitacaoDeGabriela));

        DecisaoRequest decisao = new DecisaoRequest("APROVAR", "");

        assertThatThrownBy(() -> service.decidirSolicitacao(11, decisao, gabriela))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Outra pessoa precisa aprovar esta correção");

        verify(funcionarioLojaRepository, never()).save(any());
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void supervisorQueAbriuASolicitacaoTentandoAprovarRecebeAccessDenied() {
        SolicitacaoAlteracao solicitacaoDoSergio = SolicitacaoAlteracao.builder()
                .id(12)
                .tipo(TipoAlteracao.TRANSFERENCIA_LOJA)
                .matricula("MATRIC-38")
                .competencia(out2025)
                .valorAntes("\"5\"")
                .valorDepois("\"8\"")
                .autor(sergio)
                .origem(OrigemSolicitacao.USUARIO)
                .status(StatusSolicitacao.PENDENTE)
                .build();

        when(solicitacaoRepository.findByIdForUpdate(12)).thenReturn(Optional.of(solicitacaoDoSergio));

        DecisaoRequest decisao = new DecisaoRequest("APROVAR", "");

        assertThatThrownBy(() -> service.decidirSolicitacao(12, decisao, sergio))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Outra pessoa precisa aprovar esta correção");
    }

    // ─── CRITÉRIO 4: Concorrência / Drift da Base (409) ───────────────────────

    @Test
    void quandoBaseMudouDesdeOPedidoSergioRecebeConflict409AoAprovar() {
        SolicitacaoAlteracao pendente = SolicitacaoAlteracao.builder()
                .id(13)
                .tipo(TipoAlteracao.TRANSFERENCIA_LOJA)
                .matricula("MATRIC-38")
                .competencia(out2025)
                .valorAntes("\"5\"")
                .valorDepois("\"8\"")
                .autor(gabriela)
                .origem(OrigemSolicitacao.USUARIO)
                .status(StatusSolicitacao.PENDENTE)
                .build();

        when(solicitacaoRepository.findByIdForUpdate(13)).thenReturn(Optional.of(pendente));
        when(funcionarioRepository.findByMatricula("MATRIC-38")).thenReturn(Optional.of(funcMatric38));
        when(funcionarioLojaRepository.findAlocacaoPorCodLoja(funcMatric38.getId(), out2025, "5"))
                .thenReturn(Optional.empty());

        DecisaoRequest decisao = new DecisaoRequest("APROVAR", "");

        assertThatThrownBy(() -> service.decidirSolicitacao(13, decisao, sergio))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A base mudou desde o pedido");

        verify(funcionarioLojaRepository, never()).delete(any());
        verify(funcionarioLojaRepository, never()).save(any());
    }

    // ─── CRITÉRIO 5: Solicitação aberta pela Lana e Fila do Supervisor ────────

    @Test
    void filaDoSupervisorExcluiSolicitacoesDoProprioSupervisorEMostraOrigemLana() {
        SolicitacaoAlteracao abertaPelaLana = SolicitacaoAlteracao.builder()
                .id(14)
                .tipo(TipoAlteracao.TRANSFERENCIA_LOJA)
                .matricula("MATRIC-38")
                .competencia(out2025)
                .valorAntes("\"5\"")
                .valorDepois("\"8\"")
                .autor(gabriela)
                .origem(OrigemSolicitacao.LANA)
                .chatId("chat-conversa-42")
                .status(StatusSolicitacao.PENDENTE)
                .criadaEm(LocalDateTime.now())
                .build();

        when(solicitacaoRepository.findByStatusAndAutorIdNotOrderByCriadaEmAsc(StatusSolicitacao.PENDENTE, sergio.getId()))
                .thenReturn(List.of(abertaPelaLana));

        List<SolicitacaoDetalheResponse> fila = service.listarFila(sergio);

        assertThat(fila).hasSize(1);
        SolicitacaoDetalheResponse item = fila.get(0);
        assertThat(item.descricaoOrigem()).isEqualTo("aberta pela Lana a pedido de Gabriela");
        assertThat(item.chatId()).isEqualTo("chat-conversa-42");
        assertThat(item.origem()).isEqualTo(OrigemSolicitacao.LANA);
    }

    // ─── REGRA DE NEGÓCIO ADICIONAL: Reprovação sem justificativa ─────────────

    @Test
    void reprovarSemJustificativaLancaBadRequest() {
        SolicitacaoAlteracao pendente = SolicitacaoAlteracao.builder()
                .id(15)
                .tipo(TipoAlteracao.TRANSFERENCIA_LOJA)
                .matricula("MATRIC-38")
                .competencia(out2025)
                .valorAntes("\"5\"")
                .valorDepois("\"8\"")
                .autor(gabriela)
                .origem(OrigemSolicitacao.USUARIO)
                .status(StatusSolicitacao.PENDENTE)
                .build();

        when(solicitacaoRepository.findByIdForUpdate(15)).thenReturn(Optional.of(pendente));

        DecisaoRequest decisaoSemJustificativa = new DecisaoRequest("REPROVAR", "   ");

        assertThatThrownBy(() -> service.decidirSolicitacao(15, decisaoSemJustificativa, sergio))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Justificativa obrigatória para reprovar");

        verify(solicitacaoRepository, never()).save(any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // B4: só códigos de negócio (cod_loja / cod_cargo), nunca id interno
    // ═══════════════════════════════════════════════════════════════════════

    @ParameterizedTest
    @ValueSource(strings = {"5", "true", "[\"5\"]", "{\"cod_loja\":\"5\"}"})
    void antesQueNaoEhTextoEhRecusado(String antesBruto) {
        prepararFuncionario(funcMatric38);

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.TRANSFERENCIA_LOJA, bruto(antesBruto), texto("8")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O valor 'antes' deve ser um texto");

        verify(solicitacaoRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"8", "false", "[\"8\"]", "{\"loja_id\":8}"})
    void depoisQueNaoEhTextoEhRecusado(String depoisBruto) {
        prepararFuncionario(funcMatric38);

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.TRANSFERENCIA_LOJA, texto("5"), bruto(depoisBruto)), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O valor 'depois' deve ser um texto");

        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void antesInformadoComOIdInternoDaLojaNaoConferePorqueSoOCodigoValeEInformaOValorAtual() {
        Loja lojaCodigo5ComId33 = new Loja(33, "5", "Loja 5", marca);
        FuncionarioLoja atual = alocacaoLoja(funcMatric38, lojaCodigo5ComId33);

        prepararFuncionario(funcMatric38);
        when(funcionarioLojaRepository.findAlocacaoPorCodLoja(38, out2025, "33")).thenReturn(Optional.empty());
        when(funcionarioLojaRepository.findAlocacoesDaCompetencia(38, out2025)).thenReturn(List.of(atual));

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.TRANSFERENCIA_LOJA, texto("33"), texto("8")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O valor 'antes' não confere com o estado atual da base (atual: 5)");

        verify(lojaRepository, never()).findById(any());
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void antesInformadoComOIdInternoDoCargoNaoConfere() {
        FuncionarioCargo atual = alocacaoCargo(funcMatric38, cargo100); // cod "100", id 1

        prepararFuncionario(funcMatric38);
        when(funcionarioCargoRepository.findAlocacaoPorCodCargo(38, out2025, "1")).thenReturn(Optional.empty());
        when(funcionarioCargoRepository.findAlocacoesDaCompetencia(38, out2025)).thenReturn(List.of(atual));

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.TROCA_CARGO, texto("1"), texto("200")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O valor 'antes' não confere com o estado atual da base (atual: 100)");

        verify(cargoRepository, never()).findById(any());
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void valoresSaoGravadosComOCodigoNormalizadoEmJsonTexto() {
        prepararFuncionario(funcMatric38);
        when(funcionarioLojaRepository.findAlocacaoPorCodLoja(38, out2025, "5"))
                .thenReturn(Optional.of(alocacaoLoja(funcMatric38, loja5)));
        when(lojaRepository.findByCodLoja("8")).thenReturn(Optional.of(loja8));
        salvarComoRecebido();

        service.criarSolicitacao(pedido(TipoAlteracao.TRANSFERENCIA_LOJA, texto(" 5 "), texto(" 8 ")), gabriela);

        SolicitacaoAlteracao salva = capturarSalva();
        assertThat(salva.getValorAntes()).isEqualTo("\"5\"");
        assertThat(salva.getValorDepois()).isEqualTo("\"8\"");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // B5: "depois" e demais entradas validados na criação (sem solicitação zumbi)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void lojaDeDestinoInexistenteEhRecusadaNaCriacaoSemFallbackParaIdInterno() {
        prepararFuncionario(funcMatric38);
        when(funcionarioLojaRepository.findAlocacaoPorCodLoja(38, out2025, "5"))
                .thenReturn(Optional.of(alocacaoLoja(funcMatric38, loja5)));
        when(lojaRepository.findByCodLoja("8")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.TRANSFERENCIA_LOJA, texto("5"), texto("8")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Loja de destino não encontrada: 8");

        verify(lojaRepository, never()).findById(any());
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void lojaDeDestinoIgualAAtualEhRecusada() {
        prepararFuncionario(funcMatric38);
        when(funcionarioLojaRepository.findAlocacaoPorCodLoja(38, out2025, "5"))
                .thenReturn(Optional.of(alocacaoLoja(funcMatric38, loja5)));

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.TRANSFERENCIA_LOJA, texto("5"), texto("5")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A loja de destino deve ser diferente da atual");

        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void lojaDeDestinoEmQueOFuncionarioJaEstaAlocadoNoMesEhRecusada() {
        prepararFuncionario(funcMatric38);
        when(funcionarioLojaRepository.findAlocacaoPorCodLoja(38, out2025, "5"))
                .thenReturn(Optional.of(alocacaoLoja(funcMatric38, loja5)));
        when(lojaRepository.findByCodLoja("8")).thenReturn(Optional.of(loja8));
        when(funcionarioLojaRepository.existsByIdFuncionarioIdAndIdLojaIdAndIdDateRef(38, 8, out2025))
                .thenReturn(true);

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.TRANSFERENCIA_LOJA, texto("5"), texto("8")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Funcionário já está alocado na loja 8 nesta competência");

        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void depoisAusenteEhRecusado() {
        prepararFuncionario(funcMatric38);

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.TRANSFERENCIA_LOJA, texto("5"), null), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O valor 'depois' é obrigatório");

        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void depoisNuloNoJsonEhRecusadoNaCriacaoEmVezDeFalharSoNaAprovacao() {
        prepararFuncionario(funcMatric38);

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.TRANSFERENCIA_LOJA, texto("5"), bruto("null")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O valor 'depois' é obrigatório");

        verify(solicitacaoRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-13", "2025-10-01", "10/2025", "2025-1", "outubro"})
    void competenciaForaDoFormatoYyyyMmEhRecusada(String competencia) {
        SolicitacaoRequest pedido = new SolicitacaoRequest(null, TipoAlteracao.TRANSFERENCIA_LOJA, "MATRIC-38",
                competencia, texto("5"), texto("8"), "motivo", null, OrigemSolicitacao.USUARIO);

        assertThatThrownBy(() -> service.criarSolicitacao(pedido, gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith("Formato de competência inválido");

        verifyNoInteractions(funcionarioRepository, solicitacaoRepository);
    }

    @Test
    void competenciaEmBrancoEhRecusada() {
        SolicitacaoRequest pedido = new SolicitacaoRequest(null, TipoAlteracao.TRANSFERENCIA_LOJA, "MATRIC-38",
                "  ", texto("5"), texto("8"), "motivo", null, OrigemSolicitacao.USUARIO);

        assertThatThrownBy(() -> service.criarSolicitacao(pedido, gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Competência é obrigatória");
    }

    @Test
    void matriculaInexistenteEhRecusada() {
        when(funcionarioRepository.findByMatricula("MATRIC-38")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.TRANSFERENCIA_LOJA, texto("5"), texto("8")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Matrícula não encontrada: MATRIC-38");

        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void funcionarioSemAlocacaoNaCompetenciaEhRecusado() {
        when(funcionarioRepository.findByMatricula("MATRIC-38")).thenReturn(Optional.of(funcMatric38));
        when(funcionarioLojaRepository.existsByIdFuncionarioIdAndIdDateRef(38, out2025)).thenReturn(false);

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.TRANSFERENCIA_LOJA, texto("5"), texto("8")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Funcionário não possui alocação na competência informada: 2025-10");

        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void tipoAusenteEhRecusado() {
        assertThatThrownBy(() -> service.criarSolicitacao(pedido(null, texto("5"), texto("8")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tipo de alteração é obrigatório");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // B3 (nível unitário): cobertura, duas lojas no mesmo mês
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void funcionarioEmCoberturaTransfereSomenteALojaIndicadaNoAntes() {
        Loja loja9 = new Loja(9, "9", "Loja 9", marca);
        FuncionarioLoja alocacaoLoja5 = alocacaoLoja(funcMatric38, loja5);
        FuncionarioLoja alocacaoLoja9 = alocacaoLoja(funcMatric38, loja9);

        SolicitacaoAlteracao pendente = pendente(30, TipoAlteracao.TRANSFERENCIA_LOJA, "\"9\"", "\"8\"", gabriela);
        when(solicitacaoRepository.findByIdForUpdate(30)).thenReturn(Optional.of(pendente));
        when(funcionarioRepository.findByMatricula("MATRIC-38")).thenReturn(Optional.of(funcMatric38));
        when(funcionarioLojaRepository.findAlocacaoPorCodLoja(38, out2025, "9")).thenReturn(Optional.of(alocacaoLoja9));
        when(lojaRepository.findByCodLoja("8")).thenReturn(Optional.of(loja8));
        salvarComoRecebido();

        aprovar(30);

        verify(funcionarioLojaRepository).delete(alocacaoLoja9);
        verify(funcionarioLojaRepository, never()).delete(alocacaoLoja5);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TROCA_CARGO: criação
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void trocaDeCargoDe100Para200CriaSolicitacaoPendenteComOsCodigos() {
        prepararFuncionario(funcMatric38);
        when(funcionarioCargoRepository.findAlocacaoPorCodCargo(38, out2025, "100"))
                .thenReturn(Optional.of(alocacaoCargo(funcMatric38, cargo100)));
        when(cargoRepository.findByCodCargo("200")).thenReturn(Optional.of(cargo200));
        salvarComoRecebido();

        SolicitacaoDetalheResponse response = service.criarSolicitacao(
                pedido(TipoAlteracao.TROCA_CARGO, texto("100"), texto("200")), gabriela);

        assertThat(response.tipo()).isEqualTo(TipoAlteracao.TROCA_CARGO);
        assertThat(response.antes()).isEqualTo("100");
        assertThat(response.depois()).isEqualTo("200");
        assertThat(response.status()).isEqualTo(StatusSolicitacao.PENDENTE);

        SolicitacaoAlteracao salva = capturarSalva();
        assertThat(salva.getValorAntes()).isEqualTo("\"100\"");
        assertThat(salva.getValorDepois()).isEqualTo("\"200\"");
    }

    @Test
    void trocaDeCargoComDestinoInexistenteEhRecusadaSemFallbackParaIdInterno() {
        prepararFuncionario(funcMatric38);
        when(funcionarioCargoRepository.findAlocacaoPorCodCargo(38, out2025, "100"))
                .thenReturn(Optional.of(alocacaoCargo(funcMatric38, cargo100)));
        when(cargoRepository.findByCodCargo("999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.TROCA_CARGO, texto("100"), texto("999")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cargo de destino não encontrado: 999");

        verify(cargoRepository, never()).findById(any());
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void trocaDeCargoParaOMesmoCargoEhRecusada() {
        prepararFuncionario(funcMatric38);
        when(funcionarioCargoRepository.findAlocacaoPorCodCargo(38, out2025, "100"))
                .thenReturn(Optional.of(alocacaoCargo(funcMatric38, cargo100)));

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.TROCA_CARGO, texto("100"), texto("100")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O cargo de destino deve ser diferente do atual");
    }

    @Test
    void trocaDeCargoParaCargoQueOFuncionarioJaTemNoMesEhRecusada() {
        prepararFuncionario(funcMatric38);
        when(funcionarioCargoRepository.findAlocacaoPorCodCargo(38, out2025, "100"))
                .thenReturn(Optional.of(alocacaoCargo(funcMatric38, cargo100)));
        when(cargoRepository.findByCodCargo("200")).thenReturn(Optional.of(cargo200));
        when(funcionarioCargoRepository.existsByIdFuncionarioIdAndIdCargoIdAndIdDateRef(38, 3, out2025))
                .thenReturn(true);

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.TROCA_CARGO, texto("100"), texto("200")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Funcionário já possui o cargo 200 nesta competência");
    }

    @Test
    void trocaDeCargoComAntesDivergenteInformaOCargoAtual() {
        prepararFuncionario(funcMatric38);
        when(funcionarioCargoRepository.findAlocacaoPorCodCargo(38, out2025, "200")).thenReturn(Optional.empty());
        when(funcionarioCargoRepository.findAlocacoesDaCompetencia(38, out2025))
                .thenReturn(List.of(alocacaoCargo(funcMatric38, cargo100)));

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.TROCA_CARGO, texto("200"), texto("100")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O valor 'antes' não confere com o estado atual da base (atual: 100)");
    }

    @Test
    void trocaDeCargoDeFuncionarioSemCargoNaCompetenciaInformaIsso() {
        prepararFuncionario(funcMatric38);
        when(funcionarioCargoRepository.findAlocacaoPorCodCargo(38, out2025, "100")).thenReturn(Optional.empty());
        when(funcionarioCargoRepository.findAlocacoesDaCompetencia(38, out2025)).thenReturn(List.of());

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.TROCA_CARGO, texto("100"), texto("200")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O valor 'antes' não confere com o estado atual da base (atual: sem cargo na competência)");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Tipos incompatíveis: valor de um tipo usado em outro
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void dataEmTrocaDeCargoNaoCorrespondeANenhumCargoEEhRecusada() {
        prepararFuncionario(funcMatric38);
        when(funcionarioCargoRepository.findAlocacaoPorCodCargo(38, out2025, "2025-10-01")).thenReturn(Optional.empty());
        when(funcionarioCargoRepository.findAlocacoesDaCompetencia(38, out2025))
                .thenReturn(List.of(alocacaoCargo(funcMatric38, cargo100)));

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.TROCA_CARGO, texto("2025-10-01"), texto("200")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith("O valor 'antes' não confere");
    }

    @Test
    void codigoDeCargoEmDataDeAdmissaoEhRecusadoComoDataInvalida() {
        prepararFuncionario(funcMatric38);

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.DATA_ADMISSAO, texto("100"), texto("2024-01-05")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Data inválida em 'antes': 100. Use YYYY-MM-DD.");
    }

    @Test
    void dataNoFormatoBrasileiroEmDataDeDemissaoEhRecusada() {
        prepararFuncionario(funcMatric38);

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.DATA_DEMISSAO, bruto("null"), texto("15/10/2025")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Data inválida em 'depois': 15/10/2025. Use YYYY-MM-DD.");
    }

    @Test
    void numeroEmDataDeAdmissaoEhRecusadoComoNaoTexto() {
        prepararFuncionario(funcMatric38);

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.DATA_ADMISSAO, bruto("20240110"), texto("2024-01-05")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O valor 'antes' deve ser um texto");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DATA_ADMISSAO / DATA_DEMISSAO: criação e invariantes
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void demissaoSemDemissaoAnteriorGravaAntesNuloEDevolveNuloNoContrato() {
        prepararFuncionario(funcMatric38);
        salvarComoRecebido();

        SolicitacaoDetalheResponse response = service.criarSolicitacao(
                pedido(TipoAlteracao.DATA_DEMISSAO, bruto("null"), texto("2025-10-15")), gabriela);

        assertThat(response.antes()).isNull();
        assertThat(response.depois()).isEqualTo("2025-10-15");
        SolicitacaoAlteracao salva = capturarSalva();
        assertThat(salva.getValorAntes()).isEqualTo("null");
        assertThat(salva.getValorDepois()).isEqualTo("\"2025-10-15\"");
    }

    @Test
    void demissaoComAntesAusenteTratadoComoSemDemissao() {
        prepararFuncionario(funcMatric38);
        salvarComoRecebido();

        SolicitacaoDetalheResponse response = service.criarSolicitacao(
                pedido(TipoAlteracao.DATA_DEMISSAO, null, texto("2025-10-15")), gabriela);

        assertThat(response.antes()).isNull();
    }

    @Test
    void demissaoAnteriorAAdmissaoEhRecusada() {
        prepararFuncionario(funcMatric38);

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.DATA_DEMISSAO, bruto("null"), texto("2023-12-31")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A data de demissão não pode ser anterior à data de admissão (2024-01-10)");
    }

    @Test
    void demissaoComAntesDivergenteDaBaseEhRecusada() {
        prepararFuncionario(funcMatric38); // sem demissão na base

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.DATA_DEMISSAO, texto("2025-09-30"), texto("2025-10-15")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O valor 'antes' não confere com o estado atual da base (atual: sem demissão)");
    }

    @Test
    void demissaoIgualAAtualEhRecusada() {
        Funcionario demitido = new Funcionario(38, "MATRIC-38", LocalDate.of(2024, 1, 10), LocalDate.of(2025, 9, 30));
        prepararFuncionario(demitido);

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.DATA_DEMISSAO, texto("2025-09-30"), texto("2025-09-30")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A nova data de demissão deve ser diferente da atual");
    }

    @Test
    void admissaoPosteriorADemissaoEhRecusada() {
        Funcionario demitido = new Funcionario(38, "MATRIC-38", LocalDate.of(2024, 1, 10), LocalDate.of(2025, 6, 30));
        prepararFuncionario(demitido);

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.DATA_ADMISSAO, texto("2024-01-10"), texto("2025-07-01")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A data de admissão não pode ser posterior à data de demissão (2025-06-30)");
    }

    @Test
    void admissaoComAntesDivergenteDaBaseEhRecusada() {
        prepararFuncionario(funcMatric38);

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.DATA_ADMISSAO, texto("2023-01-01"), texto("2024-01-05")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O valor 'antes' não confere com o estado atual da base (atual: 2024-01-10)");
    }

    @Test
    void admissaoIgualAAtualEhRecusada() {
        prepararFuncionario(funcMatric38);

        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.DATA_ADMISSAO, texto("2024-01-10"), texto("2024-01-10")), gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A nova data de admissão deve ser diferente da atual");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Aprovação: TROCA_CARGO, datas, ordem dos locks e drift (409)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void aprovarTrocaDeCargoTrocaATuplaSobOsDoisLocksNaOrdemCerta() {
        FuncionarioCargo atual = alocacaoCargo(funcMatric38, cargo100);
        SolicitacaoAlteracao pendente = pendente(40, TipoAlteracao.TROCA_CARGO, "\"100\"", "\"200\"", gabriela);

        when(solicitacaoRepository.findByIdForUpdate(40)).thenReturn(Optional.of(pendente));
        when(funcionarioRepository.findByMatricula("MATRIC-38")).thenReturn(Optional.of(funcMatric38));
        when(funcionarioCargoRepository.findAlocacaoPorCodCargo(38, out2025, "100")).thenReturn(Optional.of(atual));
        when(cargoRepository.findByCodCargo("200")).thenReturn(Optional.of(cargo200));
        salvarComoRecebido();

        SolicitacaoDetalheResponse resultado = aprovar(40);

        InOrder ordem = inOrder(solicitacaoRepository, entityManager, funcionarioCargoRepository);
        ordem.verify(solicitacaoRepository).findByIdForUpdate(40);
        ordem.verify(entityManager).refresh(funcMatric38, LockModeType.PESSIMISTIC_WRITE);
        ordem.verify(funcionarioCargoRepository).delete(atual);

        ArgumentCaptor<FuncionarioCargo> nova = ArgumentCaptor.forClass(FuncionarioCargo.class);
        verify(funcionarioCargoRepository).save(nova.capture());
        assertThat(nova.getValue().getId().getFuncionarioId()).isEqualTo(38);
        assertThat(nova.getValue().getId().getCargoId()).isEqualTo(cargo200.getId());
        assertThat(nova.getValue().getId().getDateRef()).isEqualTo(out2025);
        assertThat(resultado.status()).isEqualTo(StatusSolicitacao.APROVADA);
    }

    @Test
    void aprovarTrocaDeCargoComBaseAlteradaDa409SemTocarNaBase() {
        SolicitacaoAlteracao pendente = pendente(41, TipoAlteracao.TROCA_CARGO, "\"100\"", "\"200\"", gabriela);

        when(solicitacaoRepository.findByIdForUpdate(41)).thenReturn(Optional.of(pendente));
        when(funcionarioRepository.findByMatricula("MATRIC-38")).thenReturn(Optional.of(funcMatric38));
        when(funcionarioCargoRepository.findAlocacaoPorCodCargo(38, out2025, "100")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aprovar(41))
                .isInstanceOf(ConflictException.class)
                .hasMessage(BASE_MUDOU);

        verify(funcionarioCargoRepository, never()).delete(any());
        verify(funcionarioCargoRepository, never()).save(any());
        verify(solicitacaoRepository, never()).save(any());
        assertThat(pendente.getStatus()).isEqualTo(StatusSolicitacao.PENDENTE);
    }

    @Test
    void aprovarTrocaDeCargoComDestinoJaOcupadoNoMesDa409() {
        SolicitacaoAlteracao pendente = pendente(42, TipoAlteracao.TROCA_CARGO, "\"100\"", "\"200\"", gabriela);

        when(solicitacaoRepository.findByIdForUpdate(42)).thenReturn(Optional.of(pendente));
        when(funcionarioRepository.findByMatricula("MATRIC-38")).thenReturn(Optional.of(funcMatric38));
        when(funcionarioCargoRepository.findAlocacaoPorCodCargo(38, out2025, "100"))
                .thenReturn(Optional.of(alocacaoCargo(funcMatric38, cargo100)));
        when(cargoRepository.findByCodCargo("200")).thenReturn(Optional.of(cargo200));
        when(funcionarioCargoRepository.existsByIdFuncionarioIdAndIdCargoIdAndIdDateRef(38, 3, out2025))
                .thenReturn(true);

        assertThatThrownBy(() -> aprovar(42))
                .isInstanceOf(ConflictException.class)
                .hasMessage(BASE_MUDOU);

        verify(funcionarioCargoRepository, never()).delete(any());
    }

    @Test
    void aprovarTransferenciaComLojaDeDestinoRemovidaDesdeOPedidoDa409() {
        SolicitacaoAlteracao pendente = pendente(43, TipoAlteracao.TRANSFERENCIA_LOJA, "\"5\"", "\"8\"", gabriela);

        when(solicitacaoRepository.findByIdForUpdate(43)).thenReturn(Optional.of(pendente));
        when(funcionarioRepository.findByMatricula("MATRIC-38")).thenReturn(Optional.of(funcMatric38));
        when(funcionarioLojaRepository.findAlocacaoPorCodLoja(38, out2025, "5"))
                .thenReturn(Optional.of(alocacaoLoja(funcMatric38, loja5)));
        when(lojaRepository.findByCodLoja("8")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aprovar(43))
                .isInstanceOf(ConflictException.class)
                .hasMessage(BASE_MUDOU);

        verify(funcionarioLojaRepository, never()).delete(any());
    }

    @Test
    void aprovarTransferenciaTravaPrimeiroASolicitacaoDepoisOFuncionarioAntesDeEscrever() {
        FuncionarioLoja atual = alocacaoLoja(funcMatric38, loja5);
        SolicitacaoAlteracao pendente = pendente(44, TipoAlteracao.TRANSFERENCIA_LOJA, "\"5\"", "\"8\"", gabriela);

        when(solicitacaoRepository.findByIdForUpdate(44)).thenReturn(Optional.of(pendente));
        when(funcionarioRepository.findByMatricula("MATRIC-38")).thenReturn(Optional.of(funcMatric38));
        when(funcionarioLojaRepository.findAlocacaoPorCodLoja(38, out2025, "5")).thenReturn(Optional.of(atual));
        when(lojaRepository.findByCodLoja("8")).thenReturn(Optional.of(loja8));
        salvarComoRecebido();

        aprovar(44);

        InOrder ordem = inOrder(solicitacaoRepository, entityManager, funcionarioLojaRepository);
        ordem.verify(solicitacaoRepository).findByIdForUpdate(44);
        ordem.verify(entityManager).refresh(funcMatric38, LockModeType.PESSIMISTIC_WRITE);
        ordem.verify(funcionarioLojaRepository).delete(atual);
        ordem.verify(funcionarioLojaRepository).flush();
    }

    @Test
    void aprovarAdmissaoAtualizaADataDoFuncionario() {
        SolicitacaoAlteracaoPendenteDeAdmissao();
    }

    private void SolicitacaoAlteracaoPendenteDeAdmissao() {
        SolicitacaoAlteracao pendente = pendente(50, TipoAlteracao.DATA_ADMISSAO, "\"2024-01-10\"", "\"2024-01-05\"", gabriela);
        when(solicitacaoRepository.findByIdForUpdate(50)).thenReturn(Optional.of(pendente));
        when(funcionarioRepository.findByMatricula("MATRIC-38")).thenReturn(Optional.of(funcMatric38));
        salvarComoRecebido();

        SolicitacaoDetalheResponse resultado = aprovar(50);

        assertThat(funcMatric38.getDataAdmissao()).isEqualTo(LocalDate.of(2024, 1, 5));
        verify(funcionarioRepository).save(funcMatric38);
        verify(entityManager).refresh(funcMatric38, LockModeType.PESSIMISTIC_WRITE);
        assertThat(resultado.status()).isEqualTo(StatusSolicitacao.APROVADA);
    }

    @Test
    void aprovarAdmissaoComDataAtualDiferenteDoAntesDa409() {
        Funcionario alterado = new Funcionario(38, "MATRIC-38", LocalDate.of(2024, 2, 1), null);
        SolicitacaoAlteracao pendente = pendente(51, TipoAlteracao.DATA_ADMISSAO, "\"2024-01-10\"", "\"2024-01-05\"", gabriela);
        when(solicitacaoRepository.findByIdForUpdate(51)).thenReturn(Optional.of(pendente));
        when(funcionarioRepository.findByMatricula("MATRIC-38")).thenReturn(Optional.of(alterado));

        assertThatThrownBy(() -> aprovar(51))
                .isInstanceOf(ConflictException.class)
                .hasMessage(BASE_MUDOU);

        assertThat(alterado.getDataAdmissao()).isEqualTo(LocalDate.of(2024, 2, 1));
        verify(funcionarioRepository, never()).save(any());
    }

    @Test
    void aprovarDemissaoComAntesNuloRegistraADemissao() {
        SolicitacaoAlteracao pendente = pendente(52, TipoAlteracao.DATA_DEMISSAO, "null", "\"2025-10-15\"", gabriela);
        when(solicitacaoRepository.findByIdForUpdate(52)).thenReturn(Optional.of(pendente));
        when(funcionarioRepository.findByMatricula("MATRIC-38")).thenReturn(Optional.of(funcMatric38));
        salvarComoRecebido();

        SolicitacaoDetalheResponse resultado = aprovar(52);

        assertThat(funcMatric38.getDataDemissao()).isEqualTo(LocalDate.of(2025, 10, 15));
        verify(funcionarioRepository).save(funcMatric38);
        assertThat(resultado.antes()).isNull();
        assertThat(resultado.depois()).isEqualTo("2025-10-15");
    }

    @Test
    void aprovarDemissaoQuandoOFuncionarioJaFoiDemitidoDesdeOPedidoDa409() {
        Funcionario jaDemitido = new Funcionario(38, "MATRIC-38", LocalDate.of(2024, 1, 10), LocalDate.of(2025, 9, 30));
        SolicitacaoAlteracao pendente = pendente(53, TipoAlteracao.DATA_DEMISSAO, "null", "\"2025-10-15\"", gabriela);
        when(solicitacaoRepository.findByIdForUpdate(53)).thenReturn(Optional.of(pendente));
        when(funcionarioRepository.findByMatricula("MATRIC-38")).thenReturn(Optional.of(jaDemitido));

        assertThatThrownBy(() -> aprovar(53))
                .isInstanceOf(ConflictException.class)
                .hasMessage(BASE_MUDOU);

        assertThat(jaDemitido.getDataDemissao()).isEqualTo(LocalDate.of(2025, 9, 30));
        verify(funcionarioRepository, never()).save(any());
    }

    @Test
    void reprovarNaoTravaNemAlteraAFuncionarioEGravaAJustificativaSemEspacos() {
        SolicitacaoAlteracao pendente = pendente(54, TipoAlteracao.TRANSFERENCIA_LOJA, "\"5\"", "\"8\"", gabriela);
        when(solicitacaoRepository.findByIdForUpdate(54)).thenReturn(Optional.of(pendente));
        salvarComoRecebido();

        SolicitacaoDetalheResponse resultado = service.decidirSolicitacao(
                54, new DecisaoRequest("REPROVAR", "  loja de destino incorreta  "), sergio);

        assertThat(resultado.status()).isEqualTo(StatusSolicitacao.REPROVADA);
        assertThat(resultado.decisao().decisao()).isEqualTo("REPROVADA");
        assertThat(resultado.decisao().justificativa()).isEqualTo("loja de destino incorreta");
        verifyNoInteractions(funcionarioRepository, funcionarioLojaRepository, funcionarioCargoRepository, entityManager);
    }

    @Test
    void aprovarSemJustificativaGravaJustificativaNula() {
        SolicitacaoAlteracao pendente = pendente(55, TipoAlteracao.DATA_DEMISSAO, "null", "\"2025-10-15\"", gabriela);
        when(solicitacaoRepository.findByIdForUpdate(55)).thenReturn(Optional.of(pendente));
        when(funcionarioRepository.findByMatricula("MATRIC-38")).thenReturn(Optional.of(funcMatric38));
        salvarComoRecebido();

        SolicitacaoDetalheResponse resultado = aprovar(55);

        assertThat(resultado.decisao().justificativa()).isNull();
    }

    @Test
    void decidirSolicitacaoInexistenteRetornaNotFound() {
        when(solicitacaoRepository.findByIdForUpdate(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aprovar(999))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Solicitação não encontrada com ID: 999");
    }

    @Test
    void decisaoComValorDesconhecidoOuAusenteEhRecusada() {
        SolicitacaoAlteracao pendente = pendente(56, TipoAlteracao.TRANSFERENCIA_LOJA, "\"5\"", "\"8\"", gabriela);
        when(solicitacaoRepository.findByIdForUpdate(56)).thenReturn(Optional.of(pendente));

        assertThatThrownBy(() -> service.decidirSolicitacao(56, new DecisaoRequest("XPTO", null), sergio))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Decisão (APROVAR ou REPROVAR) é obrigatória");
        assertThatThrownBy(() -> service.decidirSolicitacao(56, new DecisaoRequest((String) null, null), sergio))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Decisão (APROVAR ou REPROVAR) é obrigatória");
        assertThatThrownBy(() -> service.decidirSolicitacao(56, null, sergio))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Decisão (APROVAR ou REPROVAR) é obrigatória");

        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void segundaDecisaoRecebe409ComQuemDecidiuPrimeiro() {
        SolicitacaoAlteracao jaAprovada = pendente(57, TipoAlteracao.TRANSFERENCIA_LOJA, "\"5\"", "\"8\"", gabriela);
        jaAprovada.setStatus(StatusSolicitacao.APROVADA);
        jaAprovada.setDecisor(sergio);
        jaAprovada.setDecididaEm(LocalDateTime.of(2026, 10, 21, 15, 0));
        when(solicitacaoRepository.findByIdForUpdate(57)).thenReturn(Optional.of(jaAprovada));

        assertThatThrownBy(() -> service.decidirSolicitacao(57, new DecisaoRequest("APROVAR", ""), silvia))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Esta correção já foi decidida por Sérgio em 21/10/2026");

        verifyNoInteractions(funcionarioRepository, entityManager);
    }

    @Test
    void diaDaDecisaoNaMensagemUsaOFusoDeSaoPauloEntreMeiaNoiteUtcEAsTresDaManha() {
        SolicitacaoAlteracao jaAprovada = pendente(58, TipoAlteracao.TRANSFERENCIA_LOJA, "\"5\"", "\"8\"", gabriela);
        jaAprovada.setStatus(StatusSolicitacao.APROVADA);
        jaAprovada.setDecisor(sergio);
        jaAprovada.setDecididaEm(LocalDateTime.of(2026, 10, 22, 1, 30)); // UTC; em São Paulo ainda é dia 21
        when(solicitacaoRepository.findByIdForUpdate(58)).thenReturn(Optional.of(jaAprovada));

        assertThatThrownBy(() -> service.decidirSolicitacao(58, new DecisaoRequest("APROVAR", ""), silvia))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Esta correção já foi decidida por Sérgio em 21/10/2026");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // B6: Quatro Olhos falha fechado
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void decisorNuloEhNegadoSemTocarEmNada() {
        assertThatThrownBy(() -> service.decidirSolicitacao(1, new DecisaoRequest("APROVAR", ""), null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(SEM_PERMISSAO);

        verifyNoInteractions(solicitacaoRepository, funcionarioRepository, entityManager);
    }

    @Test
    void decisorSemIdEhNegadoSemTocarEmNada() {
        Usuario semId = new Usuario();
        semId.setNome("Fantasma");
        semId.setPerfil(Perfil.SUPERVISOR);

        assertThatThrownBy(() -> service.decidirSolicitacao(1, new DecisaoRequest("APROVAR", ""), semId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(SEM_PERMISSAO);

        verifyNoInteractions(solicitacaoRepository, funcionarioRepository, entityManager);
    }

    @Test
    void solicitacaoSemAutorNaoPodeSerDecididaPorNinguem() {
        SolicitacaoAlteracao semAutor = pendente(60, TipoAlteracao.TRANSFERENCIA_LOJA, "\"5\"", "\"8\"", null);
        when(solicitacaoRepository.findByIdForUpdate(60)).thenReturn(Optional.of(semAutor));

        assertThatThrownBy(() -> aprovar(60))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(OUTRA_PESSOA);

        verifyNoInteractions(funcionarioRepository, entityManager);
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void solicitacaoComAutorSemIdNaoPodeSerDecidida() {
        Usuario autorSemId = new Usuario();
        autorSemId.setNome("Sem id");
        SolicitacaoAlteracao pendente = pendente(61, TipoAlteracao.TRANSFERENCIA_LOJA, "\"5\"", "\"8\"", autorSemId);
        when(solicitacaoRepository.findByIdForUpdate(61)).thenReturn(Optional.of(pendente));

        assertThatThrownBy(() -> aprovar(61))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(OUTRA_PESSOA);

        verifyNoInteractions(funcionarioRepository, entityManager);
    }

    @Test
    void autorTambemNaoPodeReprovarAPropriaSolicitacao() {
        SolicitacaoAlteracao doSergio = pendente(62, TipoAlteracao.TRANSFERENCIA_LOJA, "\"5\"", "\"8\"", sergio);
        when(solicitacaoRepository.findByIdForUpdate(62)).thenReturn(Optional.of(doSergio));

        assertThatThrownBy(() -> service.decidirSolicitacao(62, new DecisaoRequest("REPROVAR", "mudei de ideia"), sergio))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(OUTRA_PESSOA);

        verify(solicitacaoRepository, never()).save(any());
        assertThat(doSergio.getStatus()).isEqualTo(StatusSolicitacao.PENDENTE);
    }

    @Test
    void autorRecebe403MesmoQuandoASolicitacaoJaFoiDecidida() {
        // Ordem do contrato: 403 (autor) vem antes do 409 (já decidida).
        SolicitacaoAlteracao jaAprovada = pendente(63, TipoAlteracao.TRANSFERENCIA_LOJA, "\"5\"", "\"8\"", gabriela);
        jaAprovada.setStatus(StatusSolicitacao.APROVADA);
        jaAprovada.setDecisor(sergio);
        jaAprovada.setDecididaEm(LocalDateTime.of(2026, 10, 21, 15, 0));
        when(solicitacaoRepository.findByIdForUpdate(63)).thenReturn(Optional.of(jaAprovada));

        assertThatThrownBy(() -> service.decidirSolicitacao(63, new DecisaoRequest("APROVAR", ""), gabriela))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(OUTRA_PESSOA);
    }

    @Test
    void filaSemUsuarioNaoVazaNada() {
        assertThatThrownBy(() -> service.listarFila(null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(SEM_PERMISSAO);
        assertThatThrownBy(() -> service.listarFila(new Usuario()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(SEM_PERMISSAO);

        verifyNoInteractions(solicitacaoRepository);
    }

    @Test
    void filaVaziaDevolveListaVazia() {
        when(solicitacaoRepository.findByStatusAndAutorIdNotOrderByCriadaEmAsc(StatusSolicitacao.PENDENTE, 2))
                .thenReturn(List.of());

        assertThat(service.listarFila(sergio)).isEmpty();
    }

    @Test
    void detalheSemUsuarioEhNegado() {
        assertThatThrownBy(() -> service.buscarPorId(7, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(SEM_PERMISSAO);

        verifyNoInteractions(solicitacaoRepository);
    }

    @Test
    void gerenteQueNaoEhAutorNaoVeOsDetalhes() {
        Usuario outroGerente = criarUsuario(9, "Outro", "outro@camplana.com.br", Perfil.GERENTE);
        when(solicitacaoRepository.findById(7))
                .thenReturn(Optional.of(pendente(7, TipoAlteracao.TRANSFERENCIA_LOJA, "\"5\"", "\"8\"", gabriela)));

        assertThatThrownBy(() -> service.buscarPorId(7, outroGerente))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(SEM_PERMISSAO);
    }

    @Test
    void gerenteAutorEQualquerSupervisorVeemOsDetalhes() {
        when(solicitacaoRepository.findById(7))
                .thenReturn(Optional.of(pendente(7, TipoAlteracao.TRANSFERENCIA_LOJA, "\"5\"", "\"8\"", gabriela)));

        assertThat(service.buscarPorId(7, gabriela).id()).isEqualTo(7);
        assertThat(service.buscarPorId(7, sergio).id()).isEqualTo(7);
    }

    @Test
    void detalheDeSolicitacaoInexistenteRetornaNotFound() {
        when(solicitacaoRepository.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(404, sergio))
                .isInstanceOf(NotFoundException.class);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // B6: origem da solicitação (endpoint público x fluxo do chat)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void endpointPublicoRecusaOrigemLana() {
        SolicitacaoRequest forjado = new SolicitacaoRequest("chat-de-outra-pessoa", TipoAlteracao.TRANSFERENCIA_LOJA,
                "MATRIC-38", "2025-10", texto("5"), texto("8"), "motivo", null, OrigemSolicitacao.LANA);

        assertThatThrownBy(() -> service.criarSolicitacao(forjado, gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A origem LANA só é aceita pelo fluxo do chat");

        verifyNoInteractions(funcionarioRepository, solicitacaoRepository);
    }

    @Test
    void endpointPublicoDescartaOChatIdInformadoComOrigemUsuario() {
        prepararFuncionario(funcMatric38);
        when(funcionarioLojaRepository.findAlocacaoPorCodLoja(38, out2025, "5"))
                .thenReturn(Optional.of(alocacaoLoja(funcMatric38, loja5)));
        when(lojaRepository.findByCodLoja("8")).thenReturn(Optional.of(loja8));
        salvarComoRecebido();

        SolicitacaoRequest comChat = new SolicitacaoRequest("chat-qualquer", TipoAlteracao.TRANSFERENCIA_LOJA,
                "MATRIC-38", "2025-10", texto("5"), texto("8"), "motivo", null, OrigemSolicitacao.USUARIO);

        SolicitacaoDetalheResponse response = service.criarSolicitacao(comChat, gabriela);

        assertThat(capturarSalva().getChatId()).isNull();
        assertThat(response.origem()).isEqualTo(OrigemSolicitacao.USUARIO);
        assertThat(response.descricaoOrigem()).isEqualTo("aberta por Gabriela");
    }

    @Test
    void autorVemSempreDoUsuarioLogadoNuncaDoCorpo() {
        prepararFuncionario(funcMatric38);
        when(funcionarioLojaRepository.findAlocacaoPorCodLoja(38, out2025, "5"))
                .thenReturn(Optional.of(alocacaoLoja(funcMatric38, loja5)));
        when(lojaRepository.findByCodLoja("8")).thenReturn(Optional.of(loja8));
        salvarComoRecebido();

        SolicitacaoRequest comAutorNoCorpo = new SolicitacaoRequest(null, TipoAlteracao.TRANSFERENCIA_LOJA,
                "MATRIC-38", "2025-10", texto("5"), texto("8"), "motivo", "Sérgio", OrigemSolicitacao.USUARIO);

        SolicitacaoDetalheResponse response = service.criarSolicitacao(comAutorNoCorpo, gabriela);

        assertThat(response.autor().id()).isEqualTo(1);
        assertThat(capturarSalva().getAutor()).isSameAs(gabriela);
    }

    @Test
    void criarSemUsuarioLogadoEhNegado() {
        assertThatThrownBy(() -> service.criarSolicitacao(
                pedido(TipoAlteracao.TRANSFERENCIA_LOJA, texto("5"), texto("8")), null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(SEM_PERMISSAO);

        verifyNoInteractions(funcionarioRepository, solicitacaoRepository);
    }

    @Test
    void fluxoDoChatCriaComOrigemLanaChatIdAparadoEDescricaoDoContrato() {
        prepararFuncionario(funcMatric38);
        when(funcionarioLojaRepository.findAlocacaoPorCodLoja(38, out2025, "5"))
                .thenReturn(Optional.of(alocacaoLoja(funcMatric38, loja5)));
        when(lojaRepository.findByCodLoja("8")).thenReturn(Optional.of(loja8));
        salvarComoRecebido();

        SolicitacaoRequest daLana = new SolicitacaoRequest("  c1f0a7e2-chat  ", TipoAlteracao.TRANSFERENCIA_LOJA,
                "MATRIC-38", "2025-10", texto("5"), texto("8"), "pedido pelo chat", null, OrigemSolicitacao.LANA);

        SolicitacaoDetalheResponse response = service.criarSolicitacaoDaLana(daLana, gabriela);

        assertThat(response.origem()).isEqualTo(OrigemSolicitacao.LANA);
        assertThat(response.chatId()).isEqualTo("c1f0a7e2-chat");
        assertThat(response.descricaoOrigem()).isEqualTo("aberta pela Lana a pedido de Gabriela");
        assertThat(response.autor().id()).isEqualTo(gabriela.getId());
        assertThat(capturarSalva().getOrigem()).isEqualTo(OrigemSolicitacao.LANA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void fluxoDoChatExigeChatId(String chatIdVazio) {
        SolicitacaoRequest semChat = new SolicitacaoRequest(chatIdVazio, TipoAlteracao.TRANSFERENCIA_LOJA,
                "MATRIC-38", "2025-10", texto("5"), texto("8"), "motivo", null, OrigemSolicitacao.LANA);

        assertThatThrownBy(() -> service.criarSolicitacaoDaLana(semChat, gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("chat_id é obrigatório para solicitações abertas pela Lana");

        verifyNoInteractions(funcionarioRepository, solicitacaoRepository);
    }

    @Test
    void fluxoDoChatExigeChatIdPresente() {
        SolicitacaoRequest semChat = new SolicitacaoRequest(null, TipoAlteracao.TRANSFERENCIA_LOJA,
                "MATRIC-38", "2025-10", texto("5"), texto("8"), "motivo", null, OrigemSolicitacao.LANA);

        assertThatThrownBy(() -> service.criarSolicitacaoDaLana(semChat, gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("chat_id é obrigatório para solicitações abertas pela Lana");
    }

    @Test
    void fluxoDoChatRecusaChatIdMaiorQueACelulaDoBanco() {
        String longo = "c".repeat(101);
        SolicitacaoRequest grande = new SolicitacaoRequest(longo, TipoAlteracao.TRANSFERENCIA_LOJA,
                "MATRIC-38", "2025-10", texto("5"), texto("8"), "motivo", null, OrigemSolicitacao.LANA);

        assertThatThrownBy(() -> service.criarSolicitacaoDaLana(grande, gabriela))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("chat_id excede 100 caracteres");

        verifyNoInteractions(solicitacaoRepository);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // B7: timestamps em UTC, independentemente do fuso da JVM
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void criadaEmEhGravadaEmUtcMesmoComJvmNoFusoDeSaoPaulo() {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        try {
            prepararFuncionario(funcMatric38);
            when(funcionarioLojaRepository.findAlocacaoPorCodLoja(38, out2025, "5"))
                    .thenReturn(Optional.of(alocacaoLoja(funcMatric38, loja5)));
            when(lojaRepository.findByCodLoja("8")).thenReturn(Optional.of(loja8));
            salvarComoRecebido();

            service.criarSolicitacao(pedidoDeLoja("5", "8"), gabriela);

            LocalDateTime agoraUtc = LocalDateTime.now(ZoneOffset.UTC);
            assertThat(Duration.between(capturarSalva().getCriadaEm(), agoraUtc).abs())
                    .isLessThan(Duration.ofMinutes(1));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void decididaEmEhGravadaEmUtcMesmoComJvmNoFusoDeSaoPaulo() {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        try {
            SolicitacaoAlteracao pendente = pendente(70, TipoAlteracao.TRANSFERENCIA_LOJA, "\"5\"", "\"8\"", gabriela);
            when(solicitacaoRepository.findByIdForUpdate(70)).thenReturn(Optional.of(pendente));
            salvarComoRecebido();

            SolicitacaoDetalheResponse resultado = service.decidirSolicitacao(
                    70, new DecisaoRequest("REPROVAR", "motivo"), sergio);

            LocalDateTime agoraUtc = LocalDateTime.now(ZoneOffset.UTC);
            assertThat(Duration.between(resultado.decisao().em(), agoraUtc).abs()).isLessThan(Duration.ofMinutes(1));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Apoio
    // ═══════════════════════════════════════════════════════════════════════

    private JsonNode texto(String valor) {
        return objectMapper.readTree("\"" + valor + "\"");
    }

    private JsonNode bruto(String json) {
        return objectMapper.readTree(json);
    }

    private SolicitacaoRequest pedido(TipoAlteracao tipo, JsonNode antes, JsonNode depois) {
        return new SolicitacaoRequest(null, tipo, "MATRIC-38", "2025-10", antes, depois,
                "Correção de teste", null, OrigemSolicitacao.USUARIO);
    }

    private SolicitacaoRequest pedidoDeLoja(String antes, String depois) {
        return pedido(TipoAlteracao.TRANSFERENCIA_LOJA, texto(antes), texto(depois));
    }

    /** Stubs do começo do fluxo de criação: matrícula existe e há alocação na competência. */
    private void prepararFuncionario(Funcionario funcionario) {
        when(funcionarioRepository.findByMatricula(funcionario.getMatricula())).thenReturn(Optional.of(funcionario));
        when(funcionarioLojaRepository.existsByIdFuncionarioIdAndIdDateRef(funcionario.getId(), out2025))
                .thenReturn(true);
    }

    private FuncionarioLoja alocacaoLoja(Funcionario funcionario, Loja loja) {
        return new FuncionarioLoja(
                new FuncionarioLojaId(funcionario.getId(), loja.getId(), out2025), funcionario, loja);
    }

    private FuncionarioCargo alocacaoCargo(Funcionario funcionario, Cargo cargo) {
        return new FuncionarioCargo(
                new FuncionarioCargoId(funcionario.getId(), cargo.getId(), out2025), funcionario, cargo);
    }

    private SolicitacaoAlteracao pendente(int id, TipoAlteracao tipo, String antesJson, String depoisJson, Usuario autor) {
        return SolicitacaoAlteracao.builder()
                .id(id)
                .tipo(tipo)
                .matricula("MATRIC-38")
                .competencia(out2025)
                .valorAntes(antesJson)
                .valorDepois(depoisJson)
                .motivo("Correção de teste")
                .autor(autor)
                .origem(OrigemSolicitacao.USUARIO)
                .status(StatusSolicitacao.PENDENTE)
                .criadaEm(LocalDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private void salvarComoRecebido() {
        when(solicitacaoRepository.save(any(SolicitacaoAlteracao.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private SolicitacaoAlteracao capturarSalva() {
        ArgumentCaptor<SolicitacaoAlteracao> captor = ArgumentCaptor.forClass(SolicitacaoAlteracao.class);
        verify(solicitacaoRepository).save(captor.capture());
        return captor.getValue();
    }

    private SolicitacaoDetalheResponse aprovar(int id) {
        return service.decidirSolicitacao(id, new DecisaoRequest("APROVAR", ""), sergio);
    }

    private Usuario criarUsuario(int id, String nome, String email, Perfil perfil) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNome(nome);
        u.setEmail(email);
        u.setSenha("senha-hash");
        u.setPerfil(perfil);
        u.setAtivo(true);
        return u;
    }
}
