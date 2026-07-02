package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CartaoCreditoResponse(
        Long id,
        Long contaId,
        String contaNome,
        String bandeira,
        BigDecimal limiteTotal,
        BigDecimal utilizado,
        BigDecimal disponivel,
        BigDecimal valorFaturaAberta,
        BigDecimal valorFaturasFechadas,
        BigDecimal valorTotalDevido,
        Integer melhorDiaCompra,
        Integer diasParaFechar,
        java.time.LocalDate dataVencimentoFaturaAtual,
        Integer diaFechamento,
        Integer diaVencimento,
        java.time.LocalDateTime createdAt
) {}
