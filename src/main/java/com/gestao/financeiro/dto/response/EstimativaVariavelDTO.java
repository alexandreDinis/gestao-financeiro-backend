package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record EstimativaVariavelDTO(
        BigDecimal valor,
        BigDecimal minimo,
        BigDecimal maximo,
        int mesesConsiderados,
        List<EstimativaPorCategoriaDTO> porCategoria
) {}
