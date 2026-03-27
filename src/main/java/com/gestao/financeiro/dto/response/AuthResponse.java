package com.gestao.financeiro.dto.response;

public record AuthResponse(
        String accessToken,
        String tokenType,
        UsuarioResponse usuario
) {}
