package br.com.camplana.service;

import br.com.camplana.entity.*;
import br.com.camplana.importacao.CompetenciaExtractor;
import br.com.camplana.importacao.NormalizadorData;
import br.com.camplana.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Service
public class ImportadorRhService {

    private static final Map<String, String> DESCRICAO_CANONICA = Map.of("150", "GERENTE DE LOJA");

    private final MarcaRepository marcaRepository;
    private final LojaRepository lojaRepository;
    private final CargoRepository cargoRepository;
    private final FuncionarioRepository funcionarioRepository;
    private final FuncionarioLojaRepository funcionarioLojaRepository;
    private final FuncionarioCargoRepository funcionarioCargoRepository;

    public ImportadorRhService(MarcaRepository marcaRepository, LojaRepository lojaRepository,
                               CargoRepository cargoRepository, FuncionarioRepository funcionarioRepository,
                               FuncionarioLojaRepository funcionarioLojaRepository,
                               FuncionarioCargoRepository funcionarioCargoRepository) {
        this.marcaRepository = marcaRepository;
        this.lojaRepository = lojaRepository;
        this.cargoRepository = cargoRepository;
        this.funcionarioRepository = funcionarioRepository;
        this.funcionarioLojaRepository = funcionarioLojaRepository;
        this.funcionarioCargoRepository = funcionarioCargoRepository;
    }

    @Transactional
    public int importar(String nomeArquivo, List<Map<String, String>> linhas) {
        if (linhas.isEmpty()) {
            throw new IllegalArgumentException("Arquivo RH sem linhas de dados.");
        }

        YearMonth competenciaNome = CompetenciaExtractor.doNomeArquivo(nomeArquivo);
        if (competenciaNome == null) {
            throw new IllegalArgumentException(
                    "Nao foi possivel identificar a competencia pelo nome do arquivo: " + nomeArquivo);
        }

        YearMonth competenciaConteudo = null;

        for (Map<String, String> linha : linhas) {
            String dataRefTexto = linha.get("Data_Ref");
            if (dataRefTexto == null || dataRefTexto.isBlank()) {
                throw new IllegalArgumentException(
                        "Linha com Data_Ref vazio no arquivo " + nomeArquivo + ": " + linha);
            }

            YearMonth competenciaLinha = YearMonth.from(NormalizadorData.parse(dataRefTexto));
            if (competenciaConteudo == null) {
                competenciaConteudo = competenciaLinha;
            } else if (!competenciaConteudo.equals(competenciaLinha)) {
                throw new IllegalArgumentException(
                        "Arquivo " + nomeArquivo + " tem linhas de competencias diferentes: "
                                + competenciaConteudo + " e " + competenciaLinha);
            }
        }

        if (!competenciaNome.equals(competenciaConteudo)) {
            throw new IllegalArgumentException(
                    "Competencia do nome do arquivo (" + competenciaNome
                            + ") nao bate com a do conteudo (" + competenciaConteudo + "): " + nomeArquivo);
        }

        LocalDate dateRef = competenciaConteudo.atDay(1);

        if (funcionarioLojaRepository.existsByIdDateRef(dateRef)) {
            throw new IllegalStateException(
                    "Competencia " + competenciaConteudo + " ja foi importada (RH). Arquivo rejeitado: " + nomeArquivo);
        }

        int processadas = 0;
        for (Map<String, String> linha : linhas) {
            processarLinha(linha, dateRef);
            processadas++;
        }
        return processadas;
    }

    private void processarLinha(Map<String, String> linha, LocalDate dateRef) {
        String codMarca = obrigatorio(linha, "Cod_Marca");
        String descrMarca = obrigatorio(linha, "Descri_Marca");
        String codLoja = obrigatorio(linha, "Cod_Loja");
        String descrLoja = obrigatorio(linha, "Descr_Loja");
        String matricula = obrigatorio(linha, "Matricula");
        String dataAdmissTexto = obrigatorio(linha, "Data_Admiss");
        String dataDemissTexto = linha.get("Data_Demiss");
        String codCargo = obrigatorio(linha, "Cod_Cargo");
        String descrCargoLida = obrigatorio(linha, "Descri_Cargo");

        Marca marca = marcaRepository.findByCodMarca(codMarca)
                .orElseGet(() -> marcaRepository.save(new Marca(null, codMarca, descrMarca)));

        Loja loja = lojaRepository.findByCodLoja(codLoja)
                .orElseGet(() -> lojaRepository.save(new Loja(null, codLoja, descrLoja, marca)));

        String descrCargoCanonica = DESCRICAO_CANONICA.getOrDefault(codCargo, descrCargoLida);
        Cargo cargo = cargoRepository.findByCodCargo(codCargo)
                .orElseGet(() -> cargoRepository.save(new Cargo(null, codCargo, descrCargoCanonica)));

        LocalDate dataAdmissao = NormalizadorData.parse(dataAdmissTexto);
        LocalDate dataDemissao = (dataDemissTexto == null || dataDemissTexto.isBlank())
                ? null
                : NormalizadorData.parse(dataDemissTexto);

        Funcionario funcionario = funcionarioRepository.findByMatricula(matricula)
                .orElseGet(() -> new Funcionario(null, matricula, dataAdmissao, dataDemissao));
        funcionario.setDataAdmissao(dataAdmissao);
        funcionario.setDataDemissao(dataDemissao);
        funcionario = funcionarioRepository.save(funcionario);

        FuncionarioLojaId flId = new FuncionarioLojaId(funcionario.getId(), loja.getId(), dateRef);
        funcionarioLojaRepository.save(new FuncionarioLoja(flId, funcionario, loja));

        FuncionarioCargoId fcId = new FuncionarioCargoId(funcionario.getId(), cargo.getId(), dateRef);
        funcionarioCargoRepository.save(new FuncionarioCargo(fcId, funcionario, cargo));
    }

    private String obrigatorio(Map<String, String> linha, String coluna) {
        String valor = linha.get(coluna);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Coluna obrigatoria ausente/vazia: " + coluna + " em " + linha);
        }
        return valor;
    }
}