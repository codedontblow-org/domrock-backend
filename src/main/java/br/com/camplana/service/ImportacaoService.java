package br.com.camplana.service;

import br.com.camplana.importacao.DetectorTipoArquivo;
import br.com.camplana.importacao.LeitorXlsx;
import br.com.camplana.importacao.TipoArquivo;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ImportacaoService {

    public record ResultadoImportacaoArquivo(
            String nomeArquivo,
            String tipoDetectado,
            boolean sucesso,
            int linhasProcessadas,
            String erro
    ) {}

    private final LeitorXlsx leitorXlsx;
    private final ImportadorRhService importadorRh;
    private final ImportadorVendasService importadorVendas;
    private final ImportadorComissaoService importadorComissao;

    public ImportacaoService(LeitorXlsx leitorXlsx, ImportadorRhService importadorRh,
                             ImportadorVendasService importadorVendas, ImportadorComissaoService importadorComissao) {
        this.leitorXlsx = leitorXlsx;
        this.importadorRh = importadorRh;
        this.importadorVendas = importadorVendas;
        this.importadorComissao = importadorComissao;
    }

    public List<ResultadoImportacaoArquivo> importarLote(List<MultipartFile> arquivos) {
        List<ResultadoImportacaoArquivo> resultados = new ArrayList<>();
        for (MultipartFile arquivo : arquivos) {
            resultados.add(importarArquivo(arquivo));
        }
        return resultados;
    }

    private ResultadoImportacaoArquivo importarArquivo(MultipartFile arquivo) {
        String nome = arquivo.getOriginalFilename();

        try {
            if (nome == null || !nome.toLowerCase().endsWith(".xlsx")) {
                throw new IllegalArgumentException("Apenas arquivos .xlsx sao aceitos: " + nome);
            }

            TipoArquivo tipo = DetectorTipoArquivo.detectar(nome)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Nao foi possivel identificar o tipo pelo nome do arquivo: " + nome));

            List<Map<String, String>> linhas = leitorXlsx.ler(arquivo);

            int processadas = switch (tipo) {
                case RH -> importadorRh.importar(nome, linhas);
                case VENDAS -> importadorVendas.importar(nome, linhas);
                case COMISSAO -> importadorComissao.importar(linhas);
            };

            return new ResultadoImportacaoArquivo(nome, tipo.name(), true, processadas, null);

        } catch (Exception e) {
            String tipoDetectado = DetectorTipoArquivo.detectar(nome).map(Enum::name).orElse("DESCONHECIDO");
            return new ResultadoImportacaoArquivo(nome, tipoDetectado, false, 0, e.getMessage());
        }
    }
}