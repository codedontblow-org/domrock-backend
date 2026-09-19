package br.com.camplana.service;

import br.com.camplana.entity.Cargo;
import br.com.camplana.entity.Marca;
import br.com.camplana.entity.MarcaCargo;
import br.com.camplana.entity.MarcaCargoId;
import br.com.camplana.repository.CargoRepository;
import br.com.camplana.repository.MarcaCargoRepository;
import br.com.camplana.repository.MarcaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class ImportadorComissaoService {

    private final MarcaRepository marcaRepository;
    private final CargoRepository cargoRepository;
    private final MarcaCargoRepository marcaCargoRepository;

    public ImportadorComissaoService(MarcaRepository marcaRepository, CargoRepository cargoRepository,
                                     MarcaCargoRepository marcaCargoRepository) {
        this.marcaRepository = marcaRepository;
        this.cargoRepository = cargoRepository;
        this.marcaCargoRepository = marcaCargoRepository;
    }

    @Transactional
    public int importar(List<Map<String, String>> linhas) {
        if (linhas.isEmpty()) {
            throw new IllegalArgumentException("Arquivo de Comissao sem linhas de dados.");
        }

        LocalDate hoje = LocalDate.now();
        int processadas = 0;

        for (Map<String, String> linha : linhas) {
            String descrCargo = obrigatorio(linha, "Descri_Cargo");
            if (descrCargo.trim().equalsIgnoreCase("GERENTE QUIOSQUE")) {
                continue;
            }

            processarLinha(linha, hoje);
            processadas++;
        }
        return processadas;
    }

    private void processarLinha(Map<String, String> linha, LocalDate dateRef) {
        String codMarca = obrigatorio(linha, "Cod_Marca");
        String descrMarca = obrigatorio(linha, "Descr_Marca");
        String codCargo = obrigatorio(linha, "Cod_Cargo");
        String descrCargo = obrigatorio(linha, "Descri_Cargo");
        String pctTexto = obrigatorio(linha, "%_Comiss");

        Marca marca = marcaRepository.findByCodMarca(codMarca)
                .orElseGet(() -> marcaRepository.save(new Marca(null, codMarca, descrMarca)));

        Cargo cargo = cargoRepository.findByCodCargo(codCargo)
                .orElseGet(() -> cargoRepository.save(new Cargo(null, codCargo, descrCargo)));

        // %_Comiss chega como fracao crua ("0.025"): celula numerica com formato
        // de exibicao percentual, nao texto "2.50%". Convertendo pra nossa
        // convencao de armazenamento (numero percentual literal: 2.50 = 2,5%).
        BigDecimal fracao = new BigDecimal(pctTexto.trim());
        BigDecimal pctComiss = fracao.multiply(BigDecimal.valueOf(100));

        MarcaCargoId id = new MarcaCargoId(marca.getId(), cargo.getId(), dateRef);
        MarcaCargo marcaCargo = marcaCargoRepository.findById(id)
                .orElseGet(() -> new MarcaCargo(id, marca, cargo, null));
        marcaCargo.setPctComiss(pctComiss);
        marcaCargoRepository.save(marcaCargo);
    }

    private String obrigatorio(Map<String, String> linha, String coluna) {
        String valor = linha.get(coluna);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Coluna obrigatoria ausente/vazia: " + coluna + " em " + linha);
        }
        return valor;
    }
}