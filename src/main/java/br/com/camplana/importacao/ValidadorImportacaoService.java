package br.com.camplana.importacao;

import br.com.camplana.dto.importacao.PendenciaImportacao;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.*;

@Service
public class ValidadorImportacaoService {

    private static final List<String> COLS_RH = List.of(
            "Data_Ref", "Cod_Marca", "Descri_Marca", "Cod_Loja", "Descr_Loja",
            "Matricula", "Data_Admiss", "Cod_Cargo", "Descri_Cargo"
    );

    private static final List<String> COLS_VENDAS = List.of("Date_Ref", "Matricula", "Vlr _Venda");

    private static final List<String> COLS_COMISSAO = List.of(
            "Cod_Marca", "Descr_Marca", "Cod_Cargo", "Descri_Cargo", "%_Comiss"
    );

    public record ResultadoValidacao(List<PendenciaImportacao> pendencias, boolean valido) {
        public boolean temImpeditivo() {
            return pendencias.stream()
                    .anyMatch(p -> PendenciaImportacao.IMPEDITIVO.equalsIgnoreCase(p.severidade()));
        }
    }

    public ResultadoValidacao validarLote(
            String nomeRh, List<Map<String, String>> linhasRh,
            String nomeVendas, List<Map<String, String>> linhasVendas,
            String nomeComissao, List<Map<String, String>> linhasComissao
    ) {
        List<PendenciaImportacao> pendencias = new ArrayList<>();

        // Validação: Competência deve ser a mesma nos arquivos informados
        YearMonth compRh = CompetenciaExtractor.doNomeArquivo(nomeRh);
        YearMonth compVendas = CompetenciaExtractor.doNomeArquivo(nomeVendas);
        if (compRh != null && compVendas != null && !compRh.equals(compVendas)) {
            pendencias.add(new PendenciaImportacao(
                    "Cruzamento",
                    0,
                    "Competencia",
                    "Competência divergente entre RH (" + compRh + ") e Vendas (" + compVendas + ")",
                    PendenciaImportacao.IMPEDITIVO
            ));
        }

        // A Comissao pode ir sozinha (A6-79): uma base ja importada sem a BASE_COMMISS_FINAL nao tinha
        // como receber os %, porque reenviar RH e Vendas do mes cai em "competencia ja foi importada".
        boolean apenasComissao = (linhasRh == null || linhasRh.isEmpty())
                && (linhasVendas == null || linhasVendas.isEmpty())
                && (linhasComissao != null && !linhasComissao.isEmpty());

        // Validação de Colunas Obrigatórias
        if (!apenasComissao) {
            validarColunas(nomeRh, linhasRh, COLS_RH, pendencias);
            validarColunas(nomeVendas, linhasVendas, COLS_VENDAS, pendencias);
        }
        if (linhasComissao != null && !linhasComissao.isEmpty()) {
            validarColunas(nomeComissao, linhasComissao, COLS_COMISSAO, pendencias);
        }

        // Se faltar coluna ou planilha vazia, interrompe antes de checar dados
        if (pendencias.stream().anyMatch(p -> PendenciaImportacao.IMPEDITIVO.equalsIgnoreCase(p.severidade()))) {
            return new ResultadoValidacao(pendencias, false);
        }

        // Validação de Tipos de Dados e Datas nas Células
        validarTiposEDatasRh(nomeRh, linhasRh, pendencias);
        validarTiposEDatasVendas(nomeVendas, linhasVendas, pendencias);
        validarTiposEDatasComissao(nomeComissao, linhasComissao, pendencias);

        Set<String> matriculasRh = new HashSet<>();
        Set<String> paresMarcaCargoRh = new HashSet<>();

        if (linhasRh != null) {
            for (int i = 0; i < linhasRh.size(); i++) {
                Map<String, String> r = linhasRh.get(i);
                int numLinha = i + 2; // Linha 1 é cabeçalho

                String mat = r.get("Matricula");
                if (mat == null || mat.isBlank()) {
                    pendencias.add(new PendenciaImportacao(nomeRh, numLinha, "Matricula", "Matrícula não informada", PendenciaImportacao.IMPEDITIVO));
                } else {
                    matriculasRh.add(mat.trim());
                }

                // Matrícula sem loja ou cargo no RH gera IMPEDITIVO
                String codLoja = r.get("Cod_Loja");
                if (codLoja == null || codLoja.isBlank()) {
                    pendencias.add(new PendenciaImportacao(nomeRh, numLinha, "Cod_Loja", "Funcionário sem loja informada", PendenciaImportacao.IMPEDITIVO));
                }

                String codCargo = r.get("Cod_Cargo");
                if (codCargo == null || codCargo.isBlank()) {
                    pendencias.add(new PendenciaImportacao(nomeRh, numLinha, "Cod_Cargo", "Funcionário sem cargo informado", PendenciaImportacao.IMPEDITIVO));
                }

                String codMarca = r.get("Cod_Marca");
                if (codMarca != null && codCargo != null && !codMarca.isBlank() && !codCargo.isBlank()) {
                    paresMarcaCargoRh.add(codMarca.trim() + "#" + codCargo.trim());
                }
            }
        }

        // Validação Cruzada: Vendas -> RH
        Set<String> matriculasComVenda = new HashSet<>();
        if (linhasVendas != null) {
            for (int i = 0; i < linhasVendas.size(); i++) {
                Map<String, String> v = linhasVendas.get(i);
                int numLinha = i + 2;

                String matriculaVenda = v.get("Matricula");
                if (matriculaVenda != null && !matriculaVenda.isBlank()) {
                    String mat = matriculaVenda.trim();
                    matriculasComVenda.add(mat);

                    // AVISO e nao IMPEDITIVO (decisao do PO, 19/10): a base real tem 60 vendas assim em
                    // ago-dez/2025 (R$ 97.871,75) e, como impeditivo, so julho entrava. O ImportadorVendasService
                    // ja descarta a venda sem RH na competencia; o aviso so torna o descarte visivel.
                    if (!matriculasRh.contains(mat)) {
                        pendencias.add(new PendenciaImportacao(
                                "Cruzamento",
                                numLinha,
                                "Matricula",
                                "matrícula " + mat + " não encontrada no RH da competência; a venda de "
                                        + v.get("Date_Ref") + " (R$ " + v.get("Vlr _Venda") + ") não será gravada",
                                PendenciaImportacao.AVISO
                        ));
                    }
                }
            }
        }

        // Validação Cruzada: Par Marca x Cargo do RH na tabela de Comissão
        if (linhasComissao != null && !linhasComissao.isEmpty()) {
            Set<String> paresComissao = new HashSet<>();
            for (Map<String, String> c : linhasComissao) {
                String codMarca = c.get("Cod_Marca");
                String codCargo = c.get("Cod_Cargo");
                if (codMarca != null && codCargo != null) {
                    paresComissao.add(codMarca.trim() + "#" + codCargo.trim());
                }
            }

            for (String par : paresMarcaCargoRh) {
                if (!paresComissao.contains(par)) {
                    String[] partes = par.split("#");
                    pendencias.add(new PendenciaImportacao(
                            "Cruzamento",
                            0,
                            "Comissao",
                            "Marca " + partes[0] + " com Cargo " + partes[1] + " sem % definido na comissão",
                            PendenciaImportacao.IMPEDITIVO
                    ));
                }
            }
        }

        // Funcionário ativo sem venda no mês
        for (String mat : matriculasRh) {
            if (!matriculasComVenda.contains(mat)) {
                pendencias.add(new PendenciaImportacao(
                        nomeRh,
                        0,
                        "Matricula",
                        "Funcionário com matrícula " + mat + " não registrou vendas no mês",
                        PendenciaImportacao.AVISO
                ));
            }
        }

        return new ResultadoValidacao(pendencias, !pendencias.stream().anyMatch(p -> PendenciaImportacao.IMPEDITIVO.equalsIgnoreCase(p.severidade())));
    }

    private void validarColunas(String nomeArquivo, List<Map<String, String>> linhas, List<String> colunasObrigatorias, List<PendenciaImportacao> pendencias) {
        if (linhas == null || linhas.isEmpty()) {
            pendencias.add(new PendenciaImportacao(nomeArquivo, 0, "arquivo", "Planilha sem linhas de dados", PendenciaImportacao.IMPEDITIVO));
            return;
        }

        Map<String, String> primeiraLinha = linhas.get(0);
        for (String col : colunasObrigatorias) {
            if (!primeiraLinha.containsKey(col)) {
                pendencias.add(new PendenciaImportacao(
                        nomeArquivo,
                        1,
                        col,
                        "Coluna obrigatória não encontrada: " + col,
                        PendenciaImportacao.IMPEDITIVO
                ));
            }
        }
    }

    private void validarTiposEDatasRh(String nomeArquivo, List<Map<String, String>> linhasRh, List<PendenciaImportacao> pendencias) {
        if (linhasRh == null) return;
        for (int i = 0; i < linhasRh.size(); i++) {
            Map<String, String> linha = linhasRh.get(i);
            int numLinha = i + 2;

            String dataRef = linha.get("Data_Ref");
            if (dataRef != null && !dataRef.isBlank() && !isDataValida(dataRef)) {
                pendencias.add(new PendenciaImportacao(
                        nomeArquivo, numLinha, "Data_Ref",
                        "Data de referência inválida: '" + dataRef + "'. Formato esperado: YYYY-MM-DD.",
                        PendenciaImportacao.IMPEDITIVO
                ));
            }

            String dataAdmiss = linha.get("Data_Admiss");
            if (dataAdmiss != null && !dataAdmiss.isBlank() && !isDataValida(dataAdmiss)) {
                pendencias.add(new PendenciaImportacao(
                        nomeArquivo, numLinha, "Data_Admiss",
                        "Data de admissão inválida: '" + dataAdmiss + "'. Formato esperado: YYYY-MM-DD.",
                        PendenciaImportacao.IMPEDITIVO
                ));
            }
        }
    }

    private void validarTiposEDatasVendas(String nomeArquivo, List<Map<String, String>> linhasVendas, List<PendenciaImportacao> pendencias) {
        if (linhasVendas == null) return;
        for (int i = 0; i < linhasVendas.size(); i++) {
            Map<String, String> linha = linhasVendas.get(i);
            int numLinha = i + 2;

            String dateRef = linha.get("Date_Ref");
            if (dateRef != null && !dateRef.isBlank() && !isDataValida(dateRef)) {
                pendencias.add(new PendenciaImportacao(
                        nomeArquivo, numLinha, "Date_Ref",
                        "Data de referência inválida: '" + dateRef + "'. Formato esperado: YYYY-MM-DD.",
                        PendenciaImportacao.IMPEDITIVO
                ));
            }

            String vlrVenda = linha.get("Vlr _Venda");
            if (vlrVenda != null && !vlrVenda.isBlank() && !isNumeroValido(vlrVenda)) {
                pendencias.add(new PendenciaImportacao(
                        nomeArquivo, numLinha, "Vlr _Venda",
                        "Valor de venda numérico inválido: '" + vlrVenda + "'.",
                        PendenciaImportacao.IMPEDITIVO
                ));
            }
        }
    }

    private void validarTiposEDatasComissao(String nomeArquivo, List<Map<String, String>> linhasComissao, List<PendenciaImportacao> pendencias) {
        if (linhasComissao == null) return;
        for (int i = 0; i < linhasComissao.size(); i++) {
            Map<String, String> linha = linhasComissao.get(i);
            int numLinha = i + 2;

            String pctComiss = linha.get("%_Comiss");
            if (pctComiss != null && !pctComiss.isBlank() && !isNumeroValido(pctComiss)) {
                pendencias.add(new PendenciaImportacao(
                        nomeArquivo, numLinha, "%_Comiss",
                        "Percentual de comissão numérico inválido: '" + pctComiss + "'.",
                        PendenciaImportacao.IMPEDITIVO
                ));
            }
        }
    }

    private boolean isDataValida(String valor) {
        try {
            java.time.LocalDate.parse(valor.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isNumeroValido(String valor) {
        try {
            new java.math.BigDecimal(valor.trim().replace(",", "."));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}