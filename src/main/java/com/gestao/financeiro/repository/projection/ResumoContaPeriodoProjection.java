package com.gestao.financeiro.repository.projection;

import java.math.BigDecimal;

/**
 * Projection para créditos e débitos agregados por conta num período.
 */
public interface ResumoContaPeriodoProjection {
    Long getContaId();
    String getTipoConta();
    BigDecimal getTotalCreditos();
    BigDecimal getTotalDebitos();
}
