package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record DividasResumoResponse(
    List<DividaResponse> items,
    BigDecimal totalGeral,
    long totalItems,
    int totalPages,
    int currentPage
) {}
