package br.com.camplana.dto.usuario;

import br.com.camplana.entity.Perfil;

public record UsuarioResponse(Integer id, String nome, String email, Perfil perfil, Boolean ativo) {}