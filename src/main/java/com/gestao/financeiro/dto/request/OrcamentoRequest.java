package com.gestao.financeiro.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record OrcamentoRequest(
        @NotNull(message = "Limite é obrigatório")
        @Positive(message = "Limite deve ser positivo")
        BigDecimal limite,

        @NotNull(message = "Categoria é obrigatória")
        Long categoriaId,

        @NotNull(message = "Mês é obrigatório")
        Integer mes,

        @NotNull(message = "Ano é obrigatório")
        Integer ano
) {}
