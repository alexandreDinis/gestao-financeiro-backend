package com.gestao.financeiro.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PagarParcelaDividaRequest(
        @NotNull(message = "Conta destino/origem é obrigatória")
        Long contaId,

        Long categoriaId,

        LocalDate dataPagamento,

        BigDecimal valorPago,

        String descontarExcedenteDe,

        @PositiveOrZero(message = "Juros não pode ser negativo")
        BigDecimal valorJuros,

        @PositiveOrZero(message = "Desconto não pode ser negativo")
        BigDecimal valorDesconto,

        String linhaDigitavel
) {}
