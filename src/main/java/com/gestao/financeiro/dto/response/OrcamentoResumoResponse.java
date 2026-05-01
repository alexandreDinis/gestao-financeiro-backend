package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resumo do orçamento: limite vs gasto real por categoria.
 * Suporta hierarquia: quando o orçamento é de uma categoria pai,
 * inclui breakdown dos gastos por subcategoria filha.
 */
public record OrcamentoResumoResponse(
        Long orcamentoId,
        Long categoriaId,
        String categoriaNome,
        String categoriaCor,
        BigDecimal limite,
        BigDecimal gasto,
        BigDecimal restante,
        Double percentual,
        List<SubcategoriaGasto> subcategorias
) {
    /**
     * Gasto individual de uma subcategoria filha.
     */
    public record SubcategoriaGasto(
            Long categoriaId,
            String nome,
            String cor,
            BigDecimal gasto
    ) {}
}
