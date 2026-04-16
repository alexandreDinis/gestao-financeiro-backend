package com.gestao.financeiro.dto.request;

import com.gestao.financeiro.entity.enums.Periodicidade;
import com.gestao.financeiro.entity.enums.TipoDivida;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DividaRequest(
        Long pessoaId,

        @NotBlank(message = "Descrição é obrigatória")
        String descricao,

        @NotNull(message = "Tipo de dívida é obrigatória")
        TipoDivida tipo,

        @NotNull(message = "Valor total é obrigatório")
        @Positive(message = "Valor total deve ser positivo")
        BigDecimal valorTotal,

        @NotNull(message = "Data de início é obrigatória")
        LocalDate dataInicio,

        LocalDate dataFim,

        String observacao,

        Integer parcelas,

        // ─── Campos de Recorrência ──────────────────────
        Boolean recorrente,
        Periodicidade periodicidade,
        Integer diaVencimento,
        BigDecimal valorParcelaRecorrente
) {}

