package br.com.camplana.campanha;

import java.time.Instant;

/**
 * Uma simulação do registro do A6-66, do jeito que a campanha precisa. Ficha e resultado vêm como
 * o JSON da Lana, sem virar classe Java (ADR-002).
 *
 * @param status PENDENTE, RODANDO, CONCLUIDA ou ERRO (contrato 4.2)
 */
public record SimulacaoGravada(
        long id,
        int usuarioId,
        String chatId,
        String status,
        String regraJson,
        String resultadoJson,
        Instant concluidaEm
) {
    public boolean concluida() {
        return "CONCLUIDA".equals(status);
    }
}
