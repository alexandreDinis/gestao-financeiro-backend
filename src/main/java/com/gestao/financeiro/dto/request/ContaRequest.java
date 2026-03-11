package com.gestao.financeiro.dto.request;

import com.gestao.financeiro.entity.enums.TipoConta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ContaRequest(
        @NotBlank(message = "Nome é obrigatório")
        String nome,

        @NotNull(message = "Tipo de conta é obrigatório")
        TipoConta tipo,

        @PositiveOrZero(message = "Saldo inicial deve ser zero ou positivo")
        BigDecimal saldoInicial,

        String cor,

        String icone
) {}
