package com.gestao.financeiro.dto.response;

import com.gestao.financeiro.entity.enums.StatusDivida;
import com.gestao.financeiro.entity.enums.TipoDivida;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DividaResponse(
        Long id,
        Long pessoaId,
        String pessoaNome,
        String descricao,
        TipoDivida tipo,
        BigDecimal valorTotal,
        BigDecimal valorRestante,
        LocalDate dataInicio,
        LocalDate dataFim,
        StatusDivida status,
        String observacao,
        List<ParcelaDividaResponse> parcelas,
        LocalDateTime createdAt
) {}
