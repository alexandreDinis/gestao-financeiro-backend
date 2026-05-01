package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;

public record PrevisaoMesResponse(
        int mes,
        int ano,
        BigDecimal saldoInicial,
        BigDecimal entradasPrevistas,
        BigDecimal saidasPrevistas,
        BigDecimal ajusteEntrada,
        BigDecimal ajusteSaida,
        BigDecimal saldoFinal
) {}
