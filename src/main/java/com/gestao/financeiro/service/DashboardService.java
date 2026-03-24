package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.response.DashboardResponse;
import com.gestao.financeiro.dto.response.DashboardResponse.*;
import com.gestao.financeiro.entity.*;
import com.gestao.financeiro.entity.enums.TipoConta;
import com.gestao.financeiro.repository.*;
import com.gestao.financeiro.repository.projection.GastoCategoriaProjection;
import com.gestao.financeiro.repository.projection.ResumoContaPeriodoProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dashboard Service V2 - Refatorado para alta performance e modularidade.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class DashboardService {

    private static final int ULTIMAS_TRANSACOES_LIMITE = 10;

    private final ContaRepository           contaRepository;
    private final LancamentoRepository      lancamentoRepository;
    private final TransacaoRepository       transacaoRepository;
    private final MetaFinanceiraRepository  metaRepository;
    private final OrcamentoRepository       orcamentoRepository;
    private final CartaoCreditoRepository   cartaoCreditoRepository;
    private final FaturaCartaoRepository    faturaCartaoRepository;
    private final AlertaScoreCalculator     alertaCalculator;

    // ─────────────────────────────────────────────────────────────────────────
    // Ponto de entrada
    // ─────────────────────────────────────────────────────────────────────────

    public DashboardResponse getDashboard() {
        LocalDate hoje      = LocalDate.now();
        LocalDate inicioMes = hoje.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate fimMes    = hoje.with(TemporalAdjusters.lastDayOfMonth());

        // ── 1. Contas e saldos ───────────────────────────────────────────────
        List<Conta> contas = contaRepository.findByAtivaTrue(Pageable.unpaged()).getContent();

        List<SaldoConta> saldoPorConta = contas.stream()
                .map(c -> new SaldoConta(c.getId(), c.getNome(), c.getTipo().name(),
                        calcularSaldoConta(c)))
                .toList();

        BigDecimal saldoTotal = saldoPorConta.stream()
                .filter(sc -> !sc.tipo().equals(TipoConta.CARTAO_CREDITO.name()))
                .map(SaldoConta::saldo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── 2. Resumo do mês — UMA query agregada para todas as contas ───────
        ResumoMes mesAtual = buildResumoMes(contas, inicioMes, fimMes);

        // ── 3. Gastos por categoria — busca uma vez, reutiliza em dois blocos ─
        List<GastoCategoriaProjection> gastosCat = lancamentoRepository
                .somarGastosPorCategoriaPeriodo(inicioMes, fimMes);

        Map<Long, BigDecimal> gastosPorCatId = gastosCat.stream()
                .collect(Collectors.toMap(
                        GastoCategoriaProjection::getCategoriaId,
                        GastoCategoriaProjection::getTotal));

        // ── 4. Demais blocos ─────────────────────────────────────────────────
        ComparativoMes          comparativo  = buildComparativo(hoje, mesAtual);
        ProjecaoMes             projecao     = buildProjecao(hoje, fimMes, mesAtual);
        List<GastoPorCategoria> topCat       = buildGastosPorCategoria(gastosCat, mesAtual.despesas());
        List<FluxoMensal>       fluxo        = buildFluxoCaixa(hoje);
        List<UltimaTransacao>   ultimas      = buildUltimasTransacoes();
        ProximosVencimentos     vencimentos  = buildProximosVencimentos(hoje);
        List<ResumoMeta>        metas        = buildMetas();
        List<ResumoOrcamento>   orcamentos   = buildOrcamentos(hoje, inicioMes, fimMes, gastosPorCatId);
        List<ResumoCartao>      cartoes      = buildCartoes(contas, hoje);
        List<Alerta>            alertas      = alertaCalculator.calcular(
                saldoPorConta, orcamentos, metas, vencimentos, cartoes);

        log.info("Dashboard v2 gerado — saldo={} receitas={} despesas={} alertas={}",
                saldoTotal, mesAtual.receitas(), mesAtual.despesas(), alertas.size());

        return new DashboardResponse(
                saldoTotal, saldoPorConta, mesAtual,
                comparativo, projecao, topCat,
                fluxo, ultimas, vencimentos,
                metas, orcamentos, cartoes, alertas
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Resumo do mês — query agregada (sem loop N*2)
    // ─────────────────────────────────────────────────────────────────────────

    private ResumoMes buildResumoMes(List<Conta> contas, LocalDate inicio, LocalDate fim) {
        List<ResumoContaPeriodoProjection> resumos =
                lancamentoRepository.resumoTodasContasPorPeriodo(inicio, fim);

        Set<Long> idsCartao = contas.stream()
                .filter(c -> c.getTipo() == TipoConta.CARTAO_CREDITO)
                .map(Conta::getId)
                .collect(Collectors.toSet());

        BigDecimal receitas = BigDecimal.ZERO;
        BigDecimal despesas = BigDecimal.ZERO;

        for (ResumoContaPeriodoProjection r : resumos) {
            boolean isCartao = idsCartao.contains(r.getContaId());
            if (isCartao) {
                BigDecimal liquido = r.getTotalDebitos().subtract(r.getTotalCreditos());
                if (liquido.compareTo(BigDecimal.ZERO) > 0) {
                    despesas = despesas.add(liquido);
                }
            } else {
                receitas = receitas.add(r.getTotalCreditos());
                despesas = despesas.add(r.getTotalDebitos());
            }
        }

        return new ResumoMes(receitas, despesas, receitas.subtract(despesas));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Comparativo mês atual vs anterior
    // ─────────────────────────────────────────────────────────────────────────

    private ComparativoMes buildComparativo(LocalDate hoje, ResumoMes mesAtual) {
        LocalDate inicioAnt = hoje.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth());
        LocalDate fimAnt    = hoje.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());

        BigDecimal recAnt  = lancamentoRepository.somarTotalCreditosPeriodo(inicioAnt, fimAnt);
        BigDecimal despAnt = lancamentoRepository.somarTotalDebitosPeriodo(inicioAnt, fimAnt);

        return new ComparativoMes(
                mesAtual.receitas(), recAnt,  variacaoPct(recAnt,  mesAtual.receitas()),
                mesAtual.despesas(), despAnt, variacaoPct(despAnt, mesAtual.despesas())
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Projeção linear do mês
    // ─────────────────────────────────────────────────────────────────────────

    private ProjecaoMes buildProjecao(LocalDate hoje, LocalDate fimMes, ResumoMes mesAtual) {
        int diasDecorridos = hoje.getDayOfMonth();
        int diasTotais     = fimMes.getDayOfMonth();

        if (diasDecorridos == 0) {
            return new ProjecaoMes(0, diasTotais,
                    mesAtual.receitas(), mesAtual.despesas(), mesAtual.saldo());
        }

        BigDecimal fator = BigDecimal.valueOf((double) diasTotais / diasDecorridos);
        BigDecimal recProj  = mesAtual.receitas().multiply(fator).setScale(2, RoundingMode.HALF_UP);
        BigDecimal despProj = mesAtual.despesas().multiply(fator).setScale(2, RoundingMode.HALF_UP);

        return new ProjecaoMes(diasDecorridos, diasTotais,
                recProj, despProj, recProj.subtract(despProj));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Gastos por categoria — top 8, reutiliza dados já buscados
    // ─────────────────────────────────────────────────────────────────────────

    private List<GastoPorCategoria> buildGastosPorCategoria(
            List<GastoCategoriaProjection> gastos, BigDecimal totalDespesas) {

        BigDecimal base = totalDespesas.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ONE : totalDespesas;

        return gastos.stream().limit(8).map(g -> {
            double pct = g.getTotal()
                    .divide(base, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
            return new GastoPorCategoria(
                    g.getCategoriaId(), g.getNomeCategoria(),
                    g.getTotal(), round2(pct));
        }).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Fluxo de caixa — últimos 6 meses
    // ─────────────────────────────────────────────────────────────────────────

    private List<FluxoMensal> buildFluxoCaixa(LocalDate hoje) {
        List<FluxoMensal> fluxo = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate ref    = hoje.minusMonths(i);
            LocalDate inicio = ref.with(TemporalAdjusters.firstDayOfMonth());
            LocalDate fim    = ref.with(TemporalAdjusters.lastDayOfMonth());

            BigDecimal rec  = lancamentoRepository.somarTotalCreditosPeriodo(inicio, fim);
            BigDecimal desp = lancamentoRepository.somarTotalDebitosPeriodo(inicio, fim);
            String label = capitalize(Month.of(ref.getMonthValue())
                    .getDisplayName(TextStyle.SHORT, new Locale("pt", "BR")));

            fluxo.add(new FluxoMensal(ref.getYear(), ref.getMonthValue(),
                    label, rec, desp, rec.subtract(desp)));
        }
        return fluxo;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Últimas transações
    // ─────────────────────────────────────────────────────────────────────────

    private List<UltimaTransacao> buildUltimasTransacoes() {
        return transacaoRepository
                .findUltimasTransacoes(PageRequest.of(0, ULTIMAS_TRANSACOES_LIMITE))
                .stream().map(t -> {
                    String conta = t.getLancamentos() != null && !t.getLancamentos().isEmpty()
                            ? t.getLancamentos().iterator().next().getConta().getNome() : "-";
                    String cat = t.getCategoria() != null ? t.getCategoria().getNome() : "-";
                    return new UltimaTransacao(t.getId(), t.getDescricao(), t.getValor(),
                            t.getTipo().name(), t.getStatus().name(),
                            t.getData(), cat, conta);
                }).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Próximos vencimentos
    // ─────────────────────────────────────────────────────────────────────────

    private ProximosVencimentos buildProximosVencimentos(LocalDate hoje) {
        List<Transacao> pendentes = transacaoRepository
                .findProximosVencimentos(hoje, hoje.plusDays(30));

        List<Vencimento> todos = pendentes.stream().map(t -> {
            String conta = t.getLancamentos() != null && !t.getLancamentos().isEmpty()
                    ? t.getLancamentos().iterator().next().getConta().getNome() : "-";
            int dias = (int) (t.getData().toEpochDay() - hoje.toEpochDay());
            return new Vencimento(t.getId(), t.getDescricao(), t.getValor(),
                    t.getData(), dias, conta);
        }).toList();

        List<Vencimento> v7  = todos.stream().filter(v -> v.diasRestantes() <= 7).toList();
        List<Vencimento> v15 = todos.stream().filter(v -> v.diasRestantes() <= 15).toList();

        BigDecimal t7  = v7.stream().map(Vencimento::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal t30 = todos.stream().map(Vencimento::valor).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ProximosVencimentos(v7, v15, todos, t7, t30);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. Metas financeiras
    // ─────────────────────────────────────────────────────────────────────────

    private List<ResumoMeta> buildMetas() {
        LocalDate hoje = LocalDate.now();
        return metaRepository.findAllAtivas().stream().map(m -> {
            BigDecimal alvo  = nvl(m.getValorAlvo());
            BigDecimal atual = nvl(m.getValorAtual());
            double pct = alvo.compareTo(BigDecimal.ZERO) > 0
                    ? atual.divide(alvo, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0.0;
            boolean atrasada = m.getPrazo() != null && m.getPrazo().isBefore(hoje);
            return new ResumoMeta(m.getId(), m.getNome(), alvo, atual,
                    round2(pct), m.getPrazo(), atrasada);
        }).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. Orçamentos — sem query extra por categoria (usa mapa em memória)
    // ─────────────────────────────────────────────────────────────────────────

    private List<ResumoOrcamento> buildOrcamentos(LocalDate hoje, LocalDate inicio, LocalDate fim,
                                                   Map<Long, BigDecimal> gastosPorCatId) {
        return orcamentoRepository
                .findByMesAndAnoWithCategoria(hoje.getMonthValue(), hoje.getYear())
                .stream().map(o -> {
                    BigDecimal limite  = nvl(o.getLimite());
                    BigDecimal gasto   = gastosPorCatId.getOrDefault(
                            o.getCategoria().getId(), BigDecimal.ZERO);
                    BigDecimal dispon  = limite.subtract(gasto);

                    double pct = limite.compareTo(BigDecimal.ZERO) > 0
                            ? gasto.divide(limite, 4, RoundingMode.HALF_UP)
                                     .multiply(BigDecimal.valueOf(100)).doubleValue()
                            : 0.0;

                    return new ResumoOrcamento(
                            o.getId(), o.getCategoria().getNome(),
                            limite, gasto, dispon, round2(pct),
                            gasto.compareTo(limite) > 0
                    );
                }).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. Cartões de crédito — utilizado baseado na fatura, não no saldo
    // ─────────────────────────────────────────────────────────────────────────

    private List<ResumoCartao> buildCartoes(List<Conta> contas, LocalDate hoje) {
        return contas.stream()
                .filter(c -> c.getTipo() == TipoConta.CARTAO_CREDITO)
                .flatMap(conta -> cartaoCreditoRepository.findByContaId(conta.getId())
                        .stream().map(cartao -> {
                            BigDecimal limite = nvl(cartao.getLimite());

                            BigDecimal faturaAtual = faturaCartaoRepository
                                    .findByCartaoIdAndMesReferenciaAndAnoReferencia(
                                            cartao.getId(), hoje.getMonthValue(), hoje.getYear())
                                    .map(f -> nvl(f.getValorTotal()))
                                    .orElse(BigDecimal.ZERO);

                            BigDecimal utilizado = faturaAtual.compareTo(BigDecimal.ZERO) > 0
                                    ? faturaAtual
                                    : calcularSaldoConta(conta).negate().max(BigDecimal.ZERO);

                            BigDecimal disponivel = limite.subtract(utilizado).max(BigDecimal.ZERO);

                            double pct = limite.compareTo(BigDecimal.ZERO) > 0
                                    ? utilizado.divide(limite, 4, RoundingMode.HALF_UP)
                                            .multiply(BigDecimal.valueOf(100)).doubleValue()
                                    : 0.0;

                            LocalDate proximoVencimento = calcularProximoVencimento(cartao, hoje);

                            return new ResumoCartao(
                                    cartao.getId(), conta.getNome(),
                                    limite, utilizado, disponivel, round2(pct),
                                    faturaAtual, proximoVencimento
                            );
                        }))
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers internos
    // ─────────────────────────────────────────────────────────────────────────

    private BigDecimal calcularSaldoConta(Conta conta) {
        BigDecimal saldo = contaRepository.calcularSaldo(conta.getId());
        if (saldo == null) saldo = nvl(conta.getSaldoInicial());
        log.debug("Saldo [{}]: {}", conta.getNome(), saldo);
        return saldo;
    }

    private LocalDate calcularProximoVencimento(CartaoCredito cartao, LocalDate hoje) {
        if (cartao.getDiaVencimento() == null) return null;
        int dia = Math.min(cartao.getDiaVencimento(), hoje.lengthOfMonth());
        LocalDate venc = hoje.withDayOfMonth(dia);
        if (venc.isBefore(hoje)) {
            LocalDate prox = hoje.plusMonths(1);
            venc = prox.withDayOfMonth(Math.min(cartao.getDiaVencimento(), prox.lengthOfMonth()));
        }
        return venc;
    }

    private Double variacaoPct(BigDecimal anterior, BigDecimal atual) {
        if (anterior == null || anterior.compareTo(BigDecimal.ZERO) == 0) return null;
        return atual.subtract(anterior)
                .divide(anterior, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private BigDecimal nvl(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
