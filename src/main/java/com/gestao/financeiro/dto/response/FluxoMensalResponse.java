package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;

public record FluxoMensalResponse(
        Integer mes,
        Integer ano,
        BigDecimal receitas,
        BigDecimal despesas,
        BigDecimal saldo
) {}
