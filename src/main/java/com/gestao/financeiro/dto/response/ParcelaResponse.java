package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParcelaResponse(
        Long id,
        Integer numeroParcela,
        Integer totalParcelas,
        BigDecimal valorParcela,
        LocalDate dataVencimento,
        Boolean paga,
        String descricaoTransacao
) {}
