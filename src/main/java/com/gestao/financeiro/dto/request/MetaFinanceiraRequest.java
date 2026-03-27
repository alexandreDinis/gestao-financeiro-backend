package com.gestao.financeiro.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MetaFinanceiraRequest(
        @NotBlank(message = "Nome é obrigatório")
        String nome,

        @NotNull(message = "Valor alvo é obrigatório")
        @Positive(message = "Valor alvo deve ser positivo")
        BigDecimal valorAlvo,

        LocalDate prazo,

        String descricao
) {}
