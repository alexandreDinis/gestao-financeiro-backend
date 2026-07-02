package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Relatório detalhado de gastos mensais com agrupamento hierárquico
 * por categoria → subcategoria → transações individuais.
 *
 * Ordenação: categorias e subcategorias ordenadas por valor total (maior → menor).
 * Percentuais: calculados sobre o totalGeral do mês com RoundingMode.HALF_UP (2 casas).
 */
public record RelatorioGastosMensaisResponse(
        int mes,
        int ano,
        BigDecimal totalGeral,
        List<CategoriaGastoResponse> categorias
) {

    /**
     * Agrupamento por categoria pai.
     * Contém subcategorias e transações diretas (sem subcategoria).
     */
    public record CategoriaGastoResponse(
            Long categoriaId,
            String nome,
            String cor,
            String icone,
            BigDecimal totalCategoria,
            BigDecimal percentual,
            List<SubcategoriaGastoResponse> subcategorias,
            List<TransacaoGastoResponse> transacoesDiretas
    ) {}

    /**
     * Agrupamento por subcategoria dentro de uma categoria pai.
     */
    public record SubcategoriaGastoResponse(
            Long subcategoriaId,
            String nome,
            String cor,
            String icone,
            BigDecimal totalSubcategoria,
            BigDecimal percentual,
            List<TransacaoGastoResponse> transacoes
    ) {}

    /**
     * Transação individual dentro do relatório.
     */
    public record TransacaoGastoResponse(
            Long id,
            String descricao,
            BigDecimal valor,
            String data,
            String status,
            String origem
    ) {}
}
