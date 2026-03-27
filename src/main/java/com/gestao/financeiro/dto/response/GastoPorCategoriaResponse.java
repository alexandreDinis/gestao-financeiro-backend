package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;

public record GastoPorCategoriaResponse(
        Long categoriaId,
        String categoriaNome,
        String cor,
        String icone,
        BigDecimal total
) {}
