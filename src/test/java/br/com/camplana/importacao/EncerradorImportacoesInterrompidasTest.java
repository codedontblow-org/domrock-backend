package br.com.camplana.importacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EncerradorImportacoesInterrompidasTest {

    @Mock
    private ImportacaoPersistenceService persistenceService;

    @Test
    @DisplayName("Ao subir, encerra os jobs que ficaram PROCESSANDO no processo anterior")
    void aoSubirEncerraJobsInterrompidos() {
        when(persistenceService.encerrarJobsInterrompidos()).thenReturn(1);

        new EncerradorImportacoesInterrompidas(persistenceService).encerrarAoSubir();

        verify(persistenceService).encerrarJobsInterrompidos();
    }
}
