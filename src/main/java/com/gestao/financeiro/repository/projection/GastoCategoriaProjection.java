package com.gestao.financeiro.repository.projection;

import java.math.BigDecimal;

/**
 * Projection tipada para gastos agrupados por categoria.
 */
public interface GastoCategoriaProjection {
    Long getCategoriaId();
    String getNomeCategoria();
    BigDecimal getTotal();
}
