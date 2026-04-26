package com.gestao.financeiro.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PrevisaoAjusteRequest(
        @NotNull Integer mes,
        @NotNull Integer ano,
        BigDecimal ajusteEntrada,
        BigDecimal ajusteSaida
) {}
