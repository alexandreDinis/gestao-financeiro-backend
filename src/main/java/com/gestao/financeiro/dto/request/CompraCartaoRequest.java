package com.gestao.financeiro.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CompraCartaoRequest(
        @NotNull(message = "Cartão é obrigatório")
        Long cartaoId,

        @NotNull(message = "Categoria é obrigatória")
        Long categoriaId,

        @jakarta.validation.constraints.NotBlank(message = "Descrição é obrigatória")
        String descricao,

        @NotNull(message = "Valor é obrigatório")
        @Positive(message = "Valor deve ser positivo")
        BigDecimal valor,

        @NotNull(message = "Número de parcelas é obrigatório")
        @Positive(message = "Parcelas devem ser positivas")
        Integer parcelas,

        @NotNull(message = "Data da compra é obrigatória")
        java.time.LocalDate data
) {}
