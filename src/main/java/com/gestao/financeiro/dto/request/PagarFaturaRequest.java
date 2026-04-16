package com.gestao.financeiro.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PagarFaturaRequest(
        @NotNull(message = "Conta de débito é obrigatória")
        Long contaId,

        LocalDate dataPagamento,

        String idempotencyKey
) {}
