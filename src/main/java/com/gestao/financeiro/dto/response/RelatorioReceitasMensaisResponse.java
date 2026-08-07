package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Relatório detalhado de receitas mensais com estatísticas anuais e agrupamento por categoria.
 */
public record RelatorioReceitasMensaisResponse(
        int mes,
        int ano,
        BigDecimal totalMes,
        BigDecimal totalAno,
        BigDecimal mediaMensal,
        List<RelatorioGastosMensaisResponse.CategoriaGastoResponse> categorias
) {}
