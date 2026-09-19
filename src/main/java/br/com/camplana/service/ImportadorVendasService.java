package br.com.camplana.service;

import br.com.camplana.entity.Funcionario;
import br.com.camplana.entity.Venda;
import br.com.camplana.importacao.CompetenciaExtractor;
import br.com.camplana.importacao.NormalizadorData;
import br.com.camplana.repository.FuncionarioLojaRepository;
import br.com.camplana.repository.FuncionarioRepository;
import br.com.camplana.repository.VendaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Service
public class ImportadorVendasService {

    private static final Logger log = LoggerFactory.getLogger(ImportadorVendasService.class);

    private final FuncionarioRepository funcionarioRepository;
    private final FuncionarioLojaRepository funcionarioLojaRepository;
    private final VendaRepository vendaRepository;

    public ImportadorVendasService(FuncionarioRepository funcionarioRepository,
                                   FuncionarioLojaRepository funcionarioLojaRepository,
                                   VendaRepository vendaRepository) {
        this.funcionarioRepository = funcionarioRepository;
        this.funcionarioLojaRepository = funcionarioLojaRepository;
        this.vendaRepository = vendaRepository;
    }

    @Transactional
    public int importar(String nomeArquivo, List<Map<String, String>> linhas) {
        if (linhas.isEmpty()) {
            throw new IllegalArgumentException("Arquivo de Vendas sem linhas de dados.");
        }

        YearMonth competenciaNome = CompetenciaExtractor.doNomeArquivo(nomeArquivo);
        if (competenciaNome == null) {
            throw new IllegalArgumentException(
                    "Nao foi possivel identificar a competencia pelo nome do arquivo: " + nomeArquivo);
        }

        YearMonth competenciaConteudo = null;
        for (Map<String, String> linha : linhas) {
            String dateRefTexto = linha.get("Date_Ref");
            if (dateRefTexto == null || dateRefTexto.isBlank()) {
                throw new IllegalArgumentException(
                        "Linha com Date_Ref vazio no arquivo " + nomeArquivo + ": " + linha);
            }
            YearMonth mesLinha = YearMonth.from(NormalizadorData.parse(dateRefTexto));
            if (competenciaConteudo == null) {
                competenciaConteudo = mesLinha;
            } else if (!competenciaConteudo.equals(mesLinha)) {
                throw new IllegalArgumentException(
                        "Arquivo " + nomeArquivo + " tem vendas de competencias diferentes: "
                                + competenciaConteudo + " e " + mesLinha);
            }
        }

        if (!competenciaNome.equals(competenciaConteudo)) {
            throw new IllegalArgumentException(
                    "Competencia do nome do arquivo (" + competenciaNome
                            + ") nao bate com a do conteudo (" + competenciaConteudo + "): " + nomeArquivo);
        }

        LocalDate inicio = competenciaConteudo.atDay(1);
        LocalDate fim = competenciaConteudo.atEndOfMonth();

        if (vendaRepository.existsByDateRefBetween(inicio, fim)) {
            throw new IllegalStateException(
                    "Competencia " + competenciaConteudo + " ja foi importada (Vendas). Arquivo rejeitado: " + nomeArquivo);
        }

        int processadas = 0;
        for (Map<String, String> linha : linhas) {
            if (processarLinha(linha)) {
                processadas++;
            }
        }
        return processadas;
    }

    /** Retorna true se a venda foi gravada, false se foi ignorada por nao ter funcionario no RH da competencia. */
    private boolean processarLinha(Map<String, String> linha) {
        String matricula = obrigatorio(linha, "Matricula");
        String dateRefTexto = obrigatorio(linha, "Date_Ref");
        String vlrVendaTexto = obrigatorio(linha, "Vlr _Venda");

        LocalDate dateRef = NormalizadorData.parse(dateRefTexto);

        Funcionario funcionario = funcionarioRepository.findByMatricula(matricula).orElse(null);
        if (funcionario == null || !funcionarioLojaRepository.existsByIdFuncionarioIdAndIdDateRef(
                funcionario.getId(), dateRef.withDayOfMonth(1))) {
            log.warn("Venda ignorada: matricula {} nao consta no RH da competencia {}", matricula, YearMonth.from(dateRef));
            return false;
        }

        BigDecimal vlrVenda = new BigDecimal(vlrVendaTexto.trim());
        vendaRepository.save(new Venda(null, funcionario, dateRef, vlrVenda));
        return true;
    }

    private String obrigatorio(Map<String, String> linha, String coluna) {
        String valor = linha.get(coluna);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Coluna obrigatoria ausente/vazia: " + coluna + " em " + linha);
        }
        return valor;
    }
}