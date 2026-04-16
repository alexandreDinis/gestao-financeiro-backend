package com.gestao.financeiro.dto.response;

import com.gestao.financeiro.entity.enums.StatusTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParcelaDividaResponse(
        Long id,
        Long dividaId,
        String nomeDivida,
        Integer numeroParcela,
        BigDecimal valor,
        LocalDate dataVencimento,
        StatusTransacao status,
        LocalDate dataPagamento,
        Long transacaoGeradaId
) {}
