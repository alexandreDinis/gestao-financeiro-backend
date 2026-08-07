package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;

public record OrcamentoSugestaoResponse(
        Long categoriaId,
        String categoriaNome,
        String categoriaCor,
        BigDecimal mediaHistorica,
        BigDecimal limiteAtual,
        BigDecimal limiteSugerido
) {}
