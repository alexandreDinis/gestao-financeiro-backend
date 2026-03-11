package com.gestao.financeiro.dto.response;

import com.gestao.financeiro.entity.enums.TipoConta;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ContaResponse(
        Long id,
        String nome,
        TipoConta tipo,
        BigDecimal saldoInicial,
        BigDecimal saldoAtual,
        String cor,
        String icone,
        Boolean ativa,
        LocalDateTime createdAt
) {}
