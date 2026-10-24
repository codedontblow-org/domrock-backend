package br.com.camplana.campanha;

import br.com.camplana.entity.StatusCampanha;

/**
 * O que as permissões precisam saber de uma campanha, montado pelo serviço a partir do banco e do
 * registro de simulações. Campos {@code null} = não existe (sem versão, sem simulação).
 *
 * @param versaoAtual            número da última versão submetida
 * @param simulacaoDaVersaoAtual simulação que a última versão congelou
 * @param ultimaConcluidaId      última simulação concluída da campanha
 * @param simulacaoEmAndamentoId simulação PENDENTE ou RODANDO
 */
public record SituacaoCampanha(
        long campanhaId,
        StatusCampanha status,
        int autorId,
        Integer versaoAtual,
        Long simulacaoDaVersaoAtual,
        Long ultimaConcluidaId,
        Long simulacaoEmAndamentoId
) {}
