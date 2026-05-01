package com.gestao.financeiro.dto.response;

import com.gestao.financeiro.entity.enums.OrigemLancamento;
import com.gestao.financeiro.entity.enums.StatusTransacao;
import com.gestao.financeiro.entity.enums.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO Unificado para o Ledger (Lançamentos).
 * Representa o "Impacto Financeiro" real de Transações e Parcelas.
 */
public record LancamentoResponse(
        Long id,
        String descricao,
        BigDecimal valor,
        LocalDate dataReferencia,
        TipoTransacao tipo,
        Integer numeroParcela,
        Integer totalParcelas,
        OrigemLancamento origem,
        String categoria,
        Long categoriaId,
        String conta,
        Long contaId,
        Long contaDestinoId,
        StatusTransacao status,
        Long transacaoId,
        Boolean geradoAutomaticamente,
        com.gestao.financeiro.entity.enums.TipoDespesa tipoDespesa,
        BigDecimal valorPrevisto,
        String observacao,
        LocalDate dataVencimento,
        Long recorrenciaId
) {}
