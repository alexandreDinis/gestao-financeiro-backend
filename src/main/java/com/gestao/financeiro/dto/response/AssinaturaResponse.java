package com.gestao.financeiro.dto.response;

import com.gestao.financeiro.entity.enums.StatusAssinatura;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AssinaturaResponse(
        Long id,
        Long tenantId,
        String planoNome,
        String planoTipo,
        BigDecimal valorMensal,
        LocalDate dataInicio,
        LocalDate dataFim,
        StatusAssinatura status
) {}
