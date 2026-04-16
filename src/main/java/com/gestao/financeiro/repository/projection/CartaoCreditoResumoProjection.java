package com.gestao.financeiro.repository.projection;

import java.math.BigDecimal;

/**
 * Projeção para busca agregada de dados financeiros de um cartão em uma única query.
 * Evita o problema de N+1 e múltiplas viagens ao banco de dados.
 */
public interface CartaoCreditoResumoProjection {
    BigDecimal getTotalUtilizado();
    BigDecimal getValorAberta();
    BigDecimal getValorFechadas();
}
