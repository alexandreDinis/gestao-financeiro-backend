package com.gestao.financeiro.dto.response;

import com.gestao.financeiro.entity.enums.RoleUsuario;

import java.time.LocalDateTime;

public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        RoleUsuario role,
        Boolean ativo,
        Long tenantId,
        LocalDateTime createdAt
) {}
