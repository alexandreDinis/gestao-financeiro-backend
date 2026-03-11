package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TenantAdminResponse(
        Long id,
        String nome,
        String subdominio,
        String status,
        LocalDateTime criadoEm,
        String planoAtual,
        BigDecimal mrr
) {}
