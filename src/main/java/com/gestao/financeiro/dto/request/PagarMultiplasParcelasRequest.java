package com.gestao.financeiro.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PagarMultiplasParcelasRequest(
        @NotEmpty(message = "Selecione ao menos uma parcela")
        List<Long> parcelaIds,

        @NotNull(message = "Conta destino/origem é obrigatória")
        Long contaId,

        Long categoriaId,

        LocalDate dataPagamento,

        BigDecimal desconto
) {}
