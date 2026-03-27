package com.gestao.financeiro.dto.request;

import com.gestao.financeiro.entity.enums.TipoTransacao;
import com.gestao.financeiro.entity.enums.TipoDespesa;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransacaoRequest(
        @NotBlank(message = "Descrição é obrigatória")
        String descricao,

        @NotNull(message = "Valor é obrigatório")
        @Positive(message = "Valor deve ser positivo")
        BigDecimal valor,

        @NotNull(message = "Data é obrigatória")
        LocalDate data,

        LocalDate dataVencimento,

        @NotNull(message = "Tipo é obrigatório")
        TipoTransacao tipo,

        TipoDespesa tipoDespesa,

        Long categoriaId,

        @NotNull(message = "Conta de origem é obrigatória")
        Long contaOrigemId,

        Long contaDestinoId,

        String observacao,
        
        String idempotencyKey,

        Boolean geradoAutomaticamente,

        Long recorrenciaId,

        com.gestao.financeiro.entity.enums.StatusTransacao status
) {}
