package com.gestao.financeiro.repository.projection;

import java.math.BigDecimal;

public interface GastoMensalCategoriaProjection {
    Long getCategoriaId();
    String getNomeCategoria();
    Integer getMes();
    Integer getAno();
    BigDecimal getTotal();
}
