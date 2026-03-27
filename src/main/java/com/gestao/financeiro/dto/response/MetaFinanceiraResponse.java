package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record MetaFinanceiraResponse(
        Long id,
        String nome,
        BigDecimal valorAlvo,
        BigDecimal valorAtual,
        Double progresso,
        LocalDate prazo,
        String descricao,
        Boolean concluida,
        LocalDateTime createdAt
) {}
