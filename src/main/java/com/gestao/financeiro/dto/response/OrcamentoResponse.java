package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrcamentoResponse(
        Long id,
        BigDecimal limite,
        Integer mes,
        Integer ano,
        Long categoriaId,
        String categoriaNome,
        String categoriaCor,
        LocalDateTime createdAt
) {}
