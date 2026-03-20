package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;

/**
 * Resumo do orçamento: limite vs gasto real por categoria.
 */
public record OrcamentoResumoResponse(
        Long orcamentoId,
        Long categoriaId,
        String categoriaNome,
        String categoriaCor,
        BigDecimal limite,
        BigDecimal gasto,
        BigDecimal restante,
        Double percentual
) {}
