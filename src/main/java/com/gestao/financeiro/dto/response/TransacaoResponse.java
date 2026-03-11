package com.gestao.financeiro.dto.response;

import com.gestao.financeiro.entity.enums.StatusTransacao;
import com.gestao.financeiro.entity.enums.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TransacaoResponse(
        Long id,
        String descricao,
        BigDecimal valor,
        LocalDate data,
        LocalDate dataVencimento,
        LocalDate dataPagamento,
        TipoTransacao tipo,
        StatusTransacao status,
        String observacao,
        CategoriaResponse categoria,
        List<LancamentoResponse> lancamentos,
        LocalDateTime createdAt
) {}
