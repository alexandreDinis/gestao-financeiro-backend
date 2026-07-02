package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;

public record EstimativaPorCategoriaDTO(
        Long categoriaId,
        String nome,
        BigDecimal media
) {}
