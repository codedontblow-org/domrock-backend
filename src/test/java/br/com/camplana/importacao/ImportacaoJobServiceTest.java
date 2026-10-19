package br.com.camplana.importacao;

import br.com.camplana.dto.importacao.PendenciaImportacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportacaoJobServiceTest {

    @Mock
    private ImportacaoPersistenceService persistenceService;

    @Mock
    private LeitorXlsx leitorXlsx;

    @Mock
    private ValidadorImportacaoService validador;

    private ImportacaoJobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new ImportacaoJobService(persistenceService, leitorXlsx, validador);
    }

    @Test
    @DisplayName("Quando houver erro impeditivo, deve abortar sem chamar a persistência atômica")
    void quandoHouverImpeditivoNaoDevePersistirDados() throws Exception {
        Long jobId = 100L;
        Map<String, byte[]> arquivos = Map.of("RH_OUT26.xlsx", new byte[]{1, 2, 3});

        when(leitorXlsx.ler(any(byte[].class))).thenReturn(List.of(Map.of("MATRICULA", "123")));

        List<PendenciaImportacao> pendenciasComImpeditivo = List.of(
                new PendenciaImportacao("RH", 2, "MATRICULA", "Matrícula duplicada", PendenciaImportacao.IMPEDITIVO)
        );

        when(validador.validarLote(anyString(), anyList(), anyString(), anyList(), anyString(), anyList()))
                .thenReturn(new ValidadorImportacaoService.ResultadoValidacao(pendenciasComImpeditivo, false));

        jobService.processarImportacaoAsync(jobId, arquivos);

        verify(persistenceService, never()).executarCargaAtomica(any(), any(), any(), any(), any());
        verify(persistenceService, times(1)).finalizarJobComPendencias(eq(jobId), any(), anyInt(), eq(pendenciasComImpeditivo));
    }

    @Test
    @DisplayName("Quando houver apenas avisos, deve executar a carga atômica e finalizar com sucesso")
    void quandoApenasAvisosDeveExecutarCargaAtomicaEFinalizarComSucesso() throws Exception {
        Long jobId = 101L;
        Map<String, byte[]> arquivos = Map.of("RH_OUT26.xlsx", new byte[]{1, 2, 3});

        when(leitorXlsx.ler(any(byte[].class))).thenReturn(List.of(Map.of("MATRICULA", "123")));

        List<PendenciaImportacao> apenasAvisos = List.of(
                new PendenciaImportacao("RH", 2, "VENDAS", "Colaborador sem vendas no mês", PendenciaImportacao.AVISO)
        );

        when(validador.validarLote(anyString(), anyList(), anyString(), anyList(), anyString(), anyList()))
                .thenReturn(new ValidadorImportacaoService.ResultadoValidacao(apenasAvisos, true));

        when(persistenceService.executarCargaAtomica(any(), any(), any(), any(), any()))
                .thenReturn(new ImportacaoPersistenceService.ResultadoCarga(1, Map.of("RH", 1, "VENDAS", 0, "COMISSAO", 0)));

        jobService.processarImportacaoAsync(jobId, arquivos);

        verify(persistenceService, times(1)).executarCargaAtomica(any(), any(), any(), any(), any());
        verify(persistenceService, times(1)).finalizarJobComSucesso(
                eq(jobId), any(), anyInt(), eq(1), anyMap(), eq(apenasAvisos)
        );
        verify(persistenceService, never()).finalizarJobComPendencias(anyLong(), any(), anyInt(), any());
    }
    @Test
    @DisplayName("Quando ocorrer falha na persistência atômica, deve capturar exceção e finalizar job com erro")
    void quandoFalharPersistenciaAtomicaDeveFinalizarComErro() throws Exception {
        Long jobId = 102L;
        Map<String, byte[]> arquivos = Map.of("RH_OUT26.xlsx", new byte[]{1, 2, 3});

        when(leitorXlsx.ler(any(byte[].class))).thenReturn(List.of(Map.of("MATRICULA", "123")));

        when(validador.validarLote(anyString(), anyList(), anyString(), anyList(), anyString(), anyList()))
                .thenReturn(new ValidadorImportacaoService.ResultadoValidacao(Collections.emptyList(), true));

        doThrow(new RuntimeException("Simulação de violação de integridade no banco"))
                .when(persistenceService).executarCargaAtomica(any(), any(), any(), any(), any());

        jobService.processarImportacaoAsync(jobId, arquivos);

        verify(persistenceService, times(1)).executarCargaAtomica(any(), any(), any(), any(), any());

        verify(persistenceService, never()).finalizarJobComSucesso(anyLong(), any(), anyInt(), anyInt(), anyMap(), any());

        verify(persistenceService, times(1)).finalizarJobComErro(eq(jobId), contains("Simulação de violação de integridade no banco"));
    }
}