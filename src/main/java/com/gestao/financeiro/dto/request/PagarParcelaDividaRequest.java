package com.gestao.financeiro.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PagarParcelaDividaRequest(
        @NotNull(message = "Conta destino/origem é obrigatória")
        Long contaId,

        Long categoriaId,

        LocalDate dataPagamento
) {}
