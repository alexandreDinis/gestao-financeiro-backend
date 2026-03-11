package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resposta consolidada do dashboard — tudo em 1 chamada.
 */
public record DashboardResponse(
        BigDecimal saldoTotal,
        List<SaldoConta> saldoPorConta,
        ResumoMes mesAtual,
        ResumoContas contasAPagar,
        ResumoContas contasAReceber
) {

    public record SaldoConta(
            Long contaId,
            String conta,
            String tipo,
            BigDecimal saldo
    ) {}

    public record ResumoMes(
            BigDecimal receitas,
            BigDecimal despesas,
            BigDecimal saldo
    ) {}

    public record ResumoContas(
            BigDecimal total,
            BigDecimal vencendoEssaSemana,
            BigDecimal atrasado
    ) {}
}
