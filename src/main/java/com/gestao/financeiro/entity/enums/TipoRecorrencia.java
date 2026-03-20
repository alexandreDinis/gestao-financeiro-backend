package com.gestao.financeiro.entity.enums;

public enum TipoRecorrencia {
    FIXA,      // Valor sempre igual
    VARIAVEL;  // Valor muda todo mês (ex: Luz)

    public com.gestao.financeiro.entity.enums.TipoDespesa toTipoDespesa() {
        return this == FIXA ? com.gestao.financeiro.entity.enums.TipoDespesa.FIXA : com.gestao.financeiro.entity.enums.TipoDespesa.VARIAVEL;
    }
}
