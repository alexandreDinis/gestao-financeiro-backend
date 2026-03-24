package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.response.DashboardResponse;
import com.gestao.financeiro.dto.response.DashboardResponse.*;
import com.gestao.financeiro.entity.Conta;
import com.gestao.financeiro.repository.ContaRepository;
import com.gestao.financeiro.repository.LancamentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class DashboardService {

    private final ContaRepository contaRepository;
    private final LancamentoRepository lancamentoRepository;

    /**
     * Retorna todos os dados do dashboard em uma única chamada.
     */
    public DashboardResponse getDashboard() {
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = hoje.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate fimMes = hoje.with(TemporalAdjusters.lastDayOfMonth());
        LocalDate fimSemana = hoje.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        // Saldo por conta e Resumo do mês
        List<SaldoConta> saldoPorConta = new ArrayList<>();
        BigDecimal saldoTotal = BigDecimal.ZERO;
        BigDecimal receitasMes = BigDecimal.ZERO;
        BigDecimal despesasMes = BigDecimal.ZERO;

        List<Conta> contas = contaRepository.findByAtivaTrue(
                org.springframework.data.domain.Pageable.unpaged()).getContent();

        for (Conta conta : contas) {
            BigDecimal saldo = contaRepository.calcularSaldo(conta.getId());
            if (saldo == null) {
                saldo = conta.getSaldoInicial() != null ? conta.getSaldoInicial() : BigDecimal.ZERO;
            }
            
            log.info("Auditoria Conta: {} | Saldo Calculado: {}", conta.getNome(), saldo);

            saldoPorConta.add(new SaldoConta(
                    conta.getId(),
                    conta.getNome(),
                    conta.getTipo().name(),
                    saldo
            ));

            if (conta.getTipo() != com.gestao.financeiro.entity.enums.TipoConta.CARTAO_CREDITO) {
                saldoTotal = saldoTotal.add(saldo != null ? saldo : BigDecimal.ZERO);
            }

            // Resumo do mês
            BigDecimal creditos = lancamentoRepository.somarCreditosPorContaEPeriodo(conta.getId(), inicioMes, fimMes);
            BigDecimal debitos = lancamentoRepository.somarDebitosPorContaEPeriodo(conta.getId(), inicioMes, fimMes);
            
            log.info("Auditoria Mensal Conta: {} | Creditos: {} | Debitos: {}", conta.getNome(), creditos, debitos);

            if (conta.getTipo() == com.gestao.financeiro.entity.enums.TipoConta.CARTAO_CREDITO) {
                despesasMes = despesasMes.add(debitos.subtract(creditos));
            } else {
                receitasMes = receitasMes.add(creditos);
                despesasMes = despesasMes.add(debitos);
            }
        }

        ResumoMes mesAtual = new ResumoMes(
                receitasMes,
                despesasMes,
                receitasMes.subtract(despesasMes)
        );

        // TODO: Contas a pagar/receber será implementado na Fase 4 (Dívidas)
        ResumoContas contasEmpty = new ResumoContas(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        log.info("Dashboard gerado: saldoTotal={} receitasMes={} despesasMes={}",
                saldoTotal, receitasMes, despesasMes);

        return new DashboardResponse(
                saldoTotal,
                saldoPorConta,
                mesAtual,
                contasEmpty,
                contasEmpty
        );
    }
}
