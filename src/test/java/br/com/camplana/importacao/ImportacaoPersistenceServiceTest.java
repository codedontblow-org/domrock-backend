package br.com.camplana.importacao;

import br.com.camplana.entity.Importacao;
import br.com.camplana.repository.ImportacaoRepository;
import br.com.camplana.service.ImportadorComissaoService;
import br.com.camplana.service.ImportadorRhService;
import br.com.camplana.service.ImportadorVendasService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportacaoPersistenceServiceTest {

    @Mock
    private ImportacaoRepository importacaoRepository;

    @Mock
    private ImportadorRhService importadorRh;

    @Mock
    private ImportadorVendasService importadorVendas;

    @Mock
    private ImportadorComissaoService importadorComissao;

    private ImportacaoPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        persistenceService = new ImportacaoPersistenceService(
                importacaoRepository, importadorRh, importadorVendas, importadorComissao, JsonMapper.builder().build());
    }

    @Test
    @DisplayName("Falha no job grava a mensagem na coluna erro e no relatório")
    void finalizarComErroPreencheColunaErro() {
        Importacao job = jobProcessando(7L);
        when(importacaoRepository.findById(7L)).thenReturn(Optional.of(job));

        persistenceService.finalizarJobComErro(7L, "Competencia 2025-07 ja foi importada (RH)");

        assertEquals("FALHA", job.getStatus());
        assertEquals("Competencia 2025-07 ja foi importada (RH)", job.getErro());
        assertTrue(job.getPendencias().contains("Competencia 2025-07 ja foi importada (RH)"), job.getPendencias());
        assertNotNull(job.getConcluidaEm());
        verify(importacaoRepository).save(job);
    }

    @Test
    @DisplayName("Jobs PROCESSANDO encontrados na subida viram FALHA, com o motivo e a orientação de reenviar")
    void encerrarJobsInterrompidosMarcaFalha() {
        Importacao primeiro = jobProcessando(1L);
        Importacao segundo = jobProcessando(2L);
        when(importacaoRepository.findByStatus("PROCESSANDO")).thenReturn(List.of(primeiro, segundo));

        int encerrados = persistenceService.encerrarJobsInterrompidos();

        assertEquals(2, encerrados);
        for (Importacao job : List.of(primeiro, segundo)) {
            assertEquals("FALHA", job.getStatus());
            assertTrue(job.getErro().contains("reiniciou"), job.getErro());
            assertTrue(job.getErro().contains("envie as planilhas de novo"), job.getErro());
            assertNotNull(job.getConcluidaEm());
        }
        verify(importacaoRepository).save(primeiro);
        verify(importacaoRepository).save(segundo);
    }

    @Test
    @DisplayName("Sem job PROCESSANDO na subida, nada é alterado")
    void encerrarJobsInterrompidosSemJobsNaoGrava() {
        when(importacaoRepository.findByStatus("PROCESSANDO")).thenReturn(List.of());

        assertEquals(0, persistenceService.encerrarJobsInterrompidos());
        verify(importacaoRepository, never()).save(any());
    }

    private Importacao jobProcessando(Long id) {
        Importacao job = new Importacao();
        job.setId(id);
        job.setStatus("PROCESSANDO");
        return job;
    }
}
