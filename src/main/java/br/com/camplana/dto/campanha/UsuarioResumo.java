package br.com.camplana.dto.campanha;

/** Autor, aprovador ou quem mudou o status: só id e nome (contrato 5.2). */
public record UsuarioResumo(Integer id, String nome) {}
