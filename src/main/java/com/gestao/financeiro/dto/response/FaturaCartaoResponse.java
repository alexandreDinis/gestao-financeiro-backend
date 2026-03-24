package com.gestao.financeiro.dto.response;

import com.gestao.financeiro.entity.enums.StatusFatura;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record FaturaCartaoResponse(
        Long id,
        Long cartaoId,
        String cartaoBandeira,
        Integer mesReferencia,
        Integer anoReferencia,
        BigDecimal valorTotal,
        String dataVencimento,
        StatusFatura status,
        List<ParcelaResponse> parcelas,
        LocalDateTime createdAt
) {}
