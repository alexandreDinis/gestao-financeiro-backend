package com.gestao.financeiro.dto.response;

import com.gestao.financeiro.entity.enums.DirecaoLancamento;

import java.math.BigDecimal;

public record LancamentoResponse(
        Long id,
        Long contaId,
        String contaNome,
        BigDecimal valor,
        DirecaoLancamento direcao,
        String descricao
) {}
