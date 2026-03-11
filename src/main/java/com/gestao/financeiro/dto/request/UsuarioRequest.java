package com.gestao.financeiro.dto.request;

import com.gestao.financeiro.entity.enums.RoleUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UsuarioRequest(
        @NotBlank(message = "Nome é obrigatório")
        String nome,

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email,

        String senha,

        @NotNull(message = "Role é obrigatória")
        RoleUsuario role
) {}
