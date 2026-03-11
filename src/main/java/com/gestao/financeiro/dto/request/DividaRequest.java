package com.gestao.financeiro.dto.request;

import com.gestao.financeiro.entity.enums.TipoDivida;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DividaRequest(
        @NotNull(message = "Pessoa é obrigatória")
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

        @NotNull(message = "Número de parcelas é obrigatório")
        @Positive(message = "Parcelas devem ser pelo menos 1")
        Integer parcelas
) {}
