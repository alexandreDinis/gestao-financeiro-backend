package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;

public record PrevisaoMesResponse(
        String mes,
        BigDecimal saldoInicial,
        BigDecimal receitasFixas,
        BigDecimal despesasFixas,
        EstimativaVariavelDTO estimativaVariavel,
        AjusteManualDTO ajusteManual,
        BigDecimal saldoFinal
) {}
