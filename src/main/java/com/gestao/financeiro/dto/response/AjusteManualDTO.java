package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;

public record AjusteManualDTO(
        BigDecimal entrada,
        BigDecimal saida
) {}
