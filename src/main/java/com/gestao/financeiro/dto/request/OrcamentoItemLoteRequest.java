package com.gestao.financeiro.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record OrcamentoItemLoteRequest(
        @NotNull(message = "ID da categoria é obrigatório")
        Long categoriaId,

        @NotNull(message = "Limite é obrigatório")
        @PositiveOrZero(message = "Limite deve ser maior ou igual a zero")
        BigDecimal limite
) {}
