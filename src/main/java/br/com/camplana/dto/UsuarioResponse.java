package br.com.camplana.dto;

import br.com.camplana.entity.Perfil;

public record UsuarioResponse(
        Integer id,
        String nome,
        String email,
        Perfil perfil
) {
}