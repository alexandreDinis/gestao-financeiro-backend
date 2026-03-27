package com.gestao.financeiro.dto.request;

import com.gestao.financeiro.entity.enums.Periodicidade;
import com.gestao.financeiro.entity.enums.TipoTransacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransacaoRecorrenteRequest(
        @NotBlank(message = "Descrição é obrigatória")
        String descricao,

        @NotNull(message = "Valor é obrigatório")
        @Positive(message = "Valor deve ser positivo")
        BigDecimal valor,

        @NotNull(message = "Tipo é obrigatório")
        TipoTransacao tipo,

        @NotNull(message = "Periodicidade é obrigatória")
        Periodicidade periodicidade,

        @NotNull(message = "Data de início é obrigatória")
        LocalDate dataInicio,

        LocalDate dataFim,

        Integer diaVencimento,

        Long categoriaId,

        @NotNull(message = "Conta é obrigatória")
        Long contaId
) {}
