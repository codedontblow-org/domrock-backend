package br.com.camplana.campanha;

import br.com.camplana.entity.CampanhaVersao;

/** A versão que o Supervisor vai re-simular (contrato 5.9) e a conversa da campanha, para o pedido à Lana. */
public record VersaoParaResimular(CampanhaVersao versao, String chatId) {}
