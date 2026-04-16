package com.gestao.financeiro.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Principal customizado contendo dados do usuário autenticado no JWT.
 * Usado pelo TenantFilter para extrair o tenantId de forma segura.
 */
@Getter
@AllArgsConstructor
public class AuthPrincipal {
    private final Long id;
    private final String email;
    private final String role;
    private final Long tenantId;
}
