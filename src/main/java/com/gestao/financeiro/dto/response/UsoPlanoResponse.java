package com.gestao.financeiro.dto.response;

import java.util.Map;

public record UsoPlanoResponse(
        String planoNome,
        Map<String, LimiteUso> limites
) {
    public record LimiteUso(long usoAtual, long limiteMaximo, boolean excedido) {}
}
