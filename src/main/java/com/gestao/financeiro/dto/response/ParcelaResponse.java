package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;

public record ParcelaResponse(
        Long id,
        Integer numeroParcela,
        Integer totalParcelas,
        BigDecimal valorParcela,
        String dataVencimento,
        Boolean paga,
        String descricaoTransacao
) {}
