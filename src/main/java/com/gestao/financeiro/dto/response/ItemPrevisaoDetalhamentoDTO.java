package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;

public record ItemPrevisaoDetalhamentoDTO(
        String descricao,
        BigDecimal valor,
        String tipo
) {}
