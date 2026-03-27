package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CartaoCreditoResponse(
        Long id,
        Long contaId,
        String contaNome,
        String bandeira,
        BigDecimal limite,
        Integer diaFechamento,
        Integer diaVencimento,
        LocalDateTime createdAt
) {}
