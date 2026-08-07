package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record PrevisaoMesResponse(
        String mes,
        BigDecimal saldoInicial,
        BigDecimal receitasFixas,
        List<ItemPrevisaoDetalhamentoDTO> detalhamentoReceitasFixas,
        BigDecimal despesasFixas,
        List<ItemPrevisaoDetalhamentoDTO> detalhamentoDespesasFixas,
        EstimativaVariavelDTO estimativaVariavel,
        BigDecimal totalDespesasEstimadas,
        AjusteManualDTO ajusteManual,
        BigDecimal saldoFinal
) {}
