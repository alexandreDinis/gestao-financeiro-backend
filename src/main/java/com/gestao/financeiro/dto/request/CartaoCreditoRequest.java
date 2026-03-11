package com.gestao.financeiro.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CartaoCreditoRequest(
        @NotNull(message = "Conta é obrigatória")
        Long contaId,

        @NotBlank(message = "Bandeira é obrigatória")
        String bandeira,

        @NotNull(message = "Limite é obrigatório")
        @Positive(message = "Limite deve ser positivo")
        BigDecimal limite,

        @NotNull(message = "Dia de fechamento é obrigatório")
        Integer diaFechamento,

        @NotNull(message = "Dia de vencimento é obrigatório")
        Integer diaVencimento
) {}
