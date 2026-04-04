package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.response.DashboardResponse;
import com.gestao.financeiro.dto.response.DashboardResponse.*;
import com.gestao.financeiro.entity.*;
import com.gestao.financeiro.entity.enums.TipoConta;
import com.gestao.financeiro.entity.enums.TipoDivida;
import com.gestao.financeiro.entity.enums.TipoTransacao;
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
import java.time.YearMonth;
import java.time.ZoneId;
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
    private final TransacaoRecorrenteRepository transacaoRecorrenteRepository;
    private final TransacaoRecorrenteService transacaoRecorrenteService;
    private final ParcelaDividaRepository parcelaDividaRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Ponto de entrada
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public DashboardResponse getDashboard() {
        // Garantir que as recorrências estão em dia antes de montar o dashboard
        try {
            transacaoRecorrenteService.processarRecorrencias();
        } catch (Exception e) {
            log.error("Erro ao processar recorrências no dashboard: {}", e.getMessage());
        }

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
        LocalDate inicioMes = hoje.with(TemporalAdjusters.firstDayOfMonth());

        if (diasDecorridos == 0) {
            return new ProjecaoMes(0, diasTotais,
                    mesAtual.receitas(), mesAtual.despesas(), mesAtual.saldo());
        }

        // Base da projeção: ALL transações do mês (PAGO + PENDENTE), exceto CANCELADO
        List<ResumoContaPeriodoProjection> resumosComPendentes =
                lancamentoRepository.resumoTodasContasComPendentes(inicioMes, fimMes);

        Set<Long> idsCartao = contaRepository.findByAtivaTrue(Pageable.unpaged()).getContent()
                .stream()
                .filter(c -> c.getTipo() == TipoConta.CARTAO_CREDITO)
                .map(Conta::getId)
                .collect(Collectors.toSet());

        BigDecimal receitasBase = BigDecimal.ZERO;
        BigDecimal despesasBase = BigDecimal.ZERO;

        for (ResumoContaPeriodoProjection r : resumosComPendentes) {
            boolean isCartao = idsCartao.contains(r.getContaId());
            if (isCartao) {
                BigDecimal liquido = r.getTotalDebitos().subtract(r.getTotalCreditos());
                if (liquido.compareTo(BigDecimal.ZERO) > 0) {
                    despesasBase = despesasBase.add(liquido);
                }
            } else {
                receitasBase = receitasBase.add(r.getTotalCreditos());
                despesasBase = despesasBase.add(r.getTotalDebitos());
            }
        }

        // Adicionar recorrências que ainda NÃO geraram transação este mês
        BigDecimal fixoPendenteReceita = BigDecimal.ZERO;
        BigDecimal fixoPendenteDespesa = BigDecimal.ZERO;

        YearMonth referencia = YearMonth.from(hoje);
        List<TransacaoRecorrente> recorrentes = transacaoRecorrenteRepository.findByAtivaTrue();

        for (TransacaoRecorrente rec : recorrentes) {
            boolean jaGerada = transacaoRepository.existsByRecorrenciaIdAndReferencia(rec.getId(), referencia);
            if (!jaGerada && rec.isAtivaEm(hoje)) {
                if (rec.getTipo() == TipoTransacao.RECEITA) {
                    fixoPendenteReceita = fixoPendenteReceita.add(rec.getValor());
                } else if (rec.getTipo() == TipoTransacao.DESPESA) {
                    fixoPendenteDespesa = fixoPendenteDespesa.add(rec.getValor());
                }
            }
        }

        // Adicionar parcelas de dívidas/empréstimos pendentes do mês (que não têm transação gerada)
                List<ParcelaDivida> parcelasPendentes = parcelaDividaRepository.findProximasParcelas(inicioMes, fimMes, org.springframework.data.domain.PageRequest.of(0, 100));

        for (ParcelaDivida p : parcelasPendentes) {
            // Só adicionar se ainda não tem transação (senão já foi contada na base)
            if (p.getTransacaoGerada() == null) {
                if (p.getDivida().getTipo() == TipoDivida.A_RECEBER) {
                    fixoPendenteReceita = fixoPendenteReceita.add(p.getValor());
                } else if (p.getDivida().getTipo() == TipoDivida.A_PAGAR) {
                    fixoPendenteDespesa = fixoPendenteDespesa.add(p.getValor());
                }
            }
        }

        // Projeção: base (PAGO + PENDENTE já registradas) + itens que ainda vão ser gerados
        BigDecimal recProj  = receitasBase.add(fixoPendenteReceita).setScale(2, RoundingMode.HALF_UP);
        BigDecimal despProj = despesasBase.add(fixoPendenteDespesa).setScale(2, RoundingMode.HALF_UP);

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
                    .getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("pt-BR")));

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
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        LocalDate hojeBr = LocalDate.now(zone);
        LocalDate limite = hojeBr.plusDays(30);

        // 1. Buscar do repositório de transações (limitado a 50)
        List<Transacao> transacoes = transacaoRepository.findProximosVencimentos(hojeBr, limite, PageRequest.of(0, 50));

        // 2. Buscar do repositório de parcelas de dívidas (limitado a 50)
        List<ParcelaDivida> parcelas = parcelaDividaRepository.findProximasParcelas(hojeBr, limite, PageRequest.of(0, 50));

        if (transacoes.isEmpty() && parcelas.isEmpty()) {
            return new ProximosVencimentos(List.of(), List.of(), List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
        }

        List<Vencimento> todos = new ArrayList<>();

        // Mapear transações
        transacoes.forEach(t -> todos.add(mapTransacaoToVencimento(t, hojeBr)));

        // Mapear parcelas
        parcelas.forEach(p -> todos.add(mapParcelaToVencimento(p, hojeBr)));

        // 3. Ordenação multinível: Atrasados -> Hoje -> Futuro
        todos.sort(Comparator.comparing(Vencimento::atrasado, Comparator.reverseOrder())
                .thenComparing(Vencimento::venceHoje, Comparator.reverseOrder())
                .thenComparing(Vencimento::dataVencimento));

        List<Vencimento> v7  = todos.stream().filter(v -> v.diasRestantes() <= 7).toList();
        List<Vencimento> v15 = todos.stream().filter(v -> v.diasRestantes() <= 15).toList();

        BigDecimal t7  = v7.stream().map(Vencimento::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal t30 = todos.stream().map(Vencimento::valor).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ProximosVencimentos(v7, v15, todos, t7, t30);
    }

    private Vencimento mapTransacaoToVencimento(Transacao t, LocalDate hoje) {
        String conta = t.getLancamentos() != null && !t.getLancamentos().isEmpty()
                ? t.getLancamentos().iterator().next().getConta().getNome() : "-";

        int dias = (int) java.time.temporal.ChronoUnit.DAYS.between(hoje, t.getData());
        boolean atrasado = t.getData().isBefore(hoje);
        boolean venceHoje = t.getData().isEqual(hoje);

        return new Vencimento(
                "TRANSACAO-" + t.getId(),
                t.getId(),
                null, // parcelaId
                t.getDescricao(),
                t.getValor(),
                t.getData(),
                dias,
                conta,
                com.gestao.financeiro.entity.enums.OrigemVencimento.TRANSACAO,
                t.getTipo() == TipoTransacao.RECEITA ? com.gestao.financeiro.entity.enums.TipoMovimentacao.RECEITA : com.gestao.financeiro.entity.enums.TipoMovimentacao.DESPESA,
                atrasado,
                venceHoje
        );
    }

    private Vencimento mapParcelaToVencimento(ParcelaDivida p, LocalDate hoje) {
        String conta = "Dívida: " + p.getDivida().getDescricao();
        
        int dias = (int) java.time.temporal.ChronoUnit.DAYS.between(hoje, p.getDataVencimento());
        boolean atrasado = p.getDataVencimento().isBefore(hoje);
        boolean venceHoje = p.getDataVencimento().isEqual(hoje);

        return new Vencimento(
                "PARCELA-" + p.getId(),
                null, // transacaoId
                p.getId(),
                p.getDivida().getDescricao() + " (" + p.getNumeroParcela() + "/" + (p.getDivida().getParcelas() != null ? p.getDivida().getParcelas().size() : "?") + ")",
                p.getValor(),
                p.getDataVencimento(),
                dias,
                conta,
                com.gestao.financeiro.entity.enums.OrigemVencimento.PARCELA,
                p.getDivida().getTipo() == TipoDivida.A_RECEBER ? com.gestao.financeiro.entity.enums.TipoMovimentacao.RECEITA : com.gestao.financeiro.entity.enums.TipoMovimentacao.DESPESA,
                atrasado,
                venceHoje
        );
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
