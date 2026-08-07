package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.response.DashboardResponse;
import com.gestao.financeiro.dto.response.DashboardResponse.*;
import com.gestao.financeiro.entity.*;
import com.gestao.financeiro.entity.enums.TipoConta;
import com.gestao.financeiro.entity.enums.TipoDivida;
import com.gestao.financeiro.entity.enums.TipoTransacao;
import com.gestao.financeiro.repository.*;
import com.gestao.financeiro.entity.enums.StatusFatura;
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
    private final CategoriaRepository       categoriaRepository;
    private final CartaoCreditoRepository   cartaoCreditoRepository;
    private final FaturaCartaoRepository    faturaCartaoRepository;
    private final AlertaScoreCalculator     alertaCalculator;
    private final TransacaoRecorrenteRepository transacaoRecorrenteRepository;
    private final TransacaoRecorrenteService transacaoRecorrenteService;
    private final ParcelaDividaRepository parcelaDividaRepository;
    private final ParcelaRepository parcelaRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Ponto de entrada
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public DashboardResponse getDashboard() {
        // Garantir que as recorrências estão em dia antes de montar o dashboard
        Long tenantId = com.gestao.financeiro.config.TenantContext.getTenantId();
        try {
            transacaoRecorrenteService.processarRecorrencias(tenantId);
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
        List<GastoCategoriaProjection> gastosLancamento = lancamentoRepository
                .somarGastosPorCategoriaPeriodo(inicioMes, fimMes);
        List<GastoCategoriaProjection> gastosParcela = parcelaRepository
                .somarParcelasPorCategoriaPeriodo(inicioMes, fimMes);

        Map<Long, BigDecimal> gastosPorCatId = new HashMap<>();
        gastosLancamento.forEach(g -> gastosPorCatId.merge(g.getCategoriaId(), g.getTotal(), BigDecimal::add));
        gastosParcela.forEach(g -> gastosPorCatId.merge(g.getCategoriaId(), g.getTotal(), BigDecimal::add));

        // Para os top categorias, precisamos de uma lista combinada e ordenada
        List<GastoCategoriaProjection> gastosCatCombinado = gastosPorCatId.entrySet().stream()
                .map(entry -> {
                    // Encontrar o nome da categoria de uma das fontes
                    String nome = gastosLancamento.stream()
                            .filter(g -> g.getCategoriaId().equals(entry.getKey()))
                            .map(GastoCategoriaProjection::getNomeCategoria)
                            .findFirst()
                            .orElseGet(() -> gastosParcela.stream()
                                    .filter(g -> g.getCategoriaId().equals(entry.getKey()))
                                    .map(GastoCategoriaProjection::getNomeCategoria)
                                    .findFirst()
                                    .orElse("Indefinida"));
                    
                    GastoCategoriaProjection proj = new GastoCategoriaProjection() {
                        @Override public Long getCategoriaId() { return entry.getKey(); }
                        @Override public String getNomeCategoria() { return nome; }
                        @Override public BigDecimal getTotal() { return entry.getValue(); }
                    };
                    return proj;
                })
                .sorted(Comparator.comparing(GastoCategoriaProjection::getTotal, Comparator.reverseOrder()))
                .toList();

        // ── 4. Demais blocos ─────────────────────────────────────────────────
        ComparativoMes          comparativo  = buildComparativo(hoje, mesAtual);
        ProjecaoMes             projecao     = buildProjecao(hoje, fimMes, mesAtual, saldoTotal);
        List<GastoPorCategoria> topCat       = buildGastosPorCategoria(gastosCatCombinado, mesAtual.despesas());
        List<FluxoMensal>       fluxo        = buildFluxoCaixa(hoje);
        List<UltimaTransacao>   ultimas      = buildUltimasTransacoes(tenantId);
        ProximosVencimentos     vencimentos  = buildProximosVencimentos(hoje, tenantId);
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
                // SKIP — cartão de crédito: parcelas são obrigações futuras (contas a pagar),
                // não saídas reais. Quando a fatura é paga, o débito na conta bancária
                // já é capturado como transação PAGA.
            } else {
                receitas = receitas.add(r.getTotalCreditos());
                despesas = despesas.add(r.getTotalDebitos());
            }
        }

        // Despesas = apenas transações PAGAS em contas não-cartão (saídas reais do mês)
        // Parcelas de cartão NÃO são incluídas aqui — elas aparecem em "Contas a Pagar"

        return new ResumoMes(receitas, despesas, receitas.subtract(despesas));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Comparativo mês atual vs anterior
    // ─────────────────────────────────────────────────────────────────────────

    private ComparativoMes buildComparativo(LocalDate hoje, ResumoMes mesAtual) {
        LocalDate inicioAnt = hoje.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth());
        LocalDate fimAnt    = hoje.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());

        BigDecimal recAnt  = lancamentoRepository.somarTotalCreditosPeriodo(inicioAnt, fimAnt);
        // Débitos sem cartão + parcelas do período (impacto real)
        BigDecimal despAnt = lancamentoRepository.somarTotalDebitosPeriodoSemCartao(inicioAnt, fimAnt)
                .add(parcelaRepository.somarParcelasPorVencimento(inicioAnt, fimAnt));

        return new ComparativoMes(
                mesAtual.receitas(), recAnt,  variacaoPct(recAnt,  mesAtual.receitas()),
                mesAtual.despesas(), despAnt, variacaoPct(despAnt, mesAtual.despesas())
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Projeção linear do mês
    // ─────────────────────────────────────────────────────────────────────────

    private ProjecaoMes buildProjecao(LocalDate hoje, LocalDate fimMes, ResumoMes mesAtual, BigDecimal saldoConsolidado) {
        int diasDecorridos = hoje.getDayOfMonth();
        int diasTotais     = fimMes.getDayOfMonth();

        if (diasDecorridos == 0) {
            return new ProjecaoMes(0, diasTotais,
                    mesAtual.receitas(), mesAtual.despesas(), saldoConsolidado,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }

        // ── Pendências: usar a mesma fonte de dados da tela "Contas a Pagar" ──
        // Busca TODOS os vencimentos do mês (mesma lógica de getTodosVencimentos)
        // e soma por tipo (DESPESA / RECEITA) para mostrar o total real pendente
        Long tenantId = com.gestao.financeiro.config.TenantContext.getTenantId();
        List<Vencimento> vencimentosMes = getTodosVencimentos(tenantId, hoje.getMonthValue(), hoje.getYear());

        BigDecimal despPend = vencimentosMes.stream()
                .filter(v -> v.tipo() == com.gestao.financeiro.entity.enums.TipoMovimentacao.DESPESA)
                .map(Vencimento::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal recPend = vencimentosMes.stream()
                .filter(v -> v.tipo() == com.gestao.financeiro.entity.enums.TipoMovimentacao.RECEITA)
                .map(Vencimento::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // Saldo projetado = Saldo atual + o que vai entrar - o que vai sair
        // (representa o saldo real previsto para o final do mês)
        BigDecimal saldoProjetado = saldoConsolidado
                .add(recPend)
                .subtract(despPend)
                .setScale(2, RoundingMode.HALF_UP);

        // Projeções de receita e despesa totais do mês (realizadas + pendentes)
        BigDecimal recProj  = mesAtual.receitas().add(recPend).setScale(2, RoundingMode.HALF_UP);
        BigDecimal despProj = mesAtual.despesas().add(despPend).setScale(2, RoundingMode.HALF_UP);

        return new ProjecaoMes(diasDecorridos, diasTotais,
                recProj, despProj, saldoProjetado, recPend, despPend);
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
            // Débitos sem cartão + parcelas do período (impacto real)
            BigDecimal desp = lancamentoRepository.somarTotalDebitosPeriodoSemCartao(inicio, fim)
                    .add(parcelaRepository.somarParcelasPorVencimento(inicio, fim));
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

    private List<UltimaTransacao> buildUltimasTransacoes(Long tenantId) {
        return transacaoRepository
                .findUltimasTransacoes(tenantId, PageRequest.of(0, ULTIMAS_TRANSACOES_LIMITE))
                .stream().map(t -> {
                    String conta = t.getLancamentos() != null && !t.getLancamentos().isEmpty()
                            ? t.getLancamentos().iterator().next().getConta().getNome() : "-";
                    String cat = t.getCategoria() != null ? t.getCategoria().getNome() : "-";
                    return new UltimaTransacao(t.getId(), t.getDescricao(), t.getValor(),
                            t.getTipo().name(), t.getStatus().name(),
                            t.getData(), cat, conta, t.getValorPrevisto());
                }).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Próximos vencimentos
    // ─────────────────────────────────────────────────────────────────────────

    private ProximosVencimentos buildProximosVencimentos(LocalDate hoje, Long tenantId) {
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        LocalDate hojeBr = LocalDate.now(zone);
        LocalDate limite = hojeBr.plusDays(30);

        // 1. Buscar do repositório de transações (limitado a 50)
        List<Transacao> transacoes = transacaoRepository.findProximosVencimentos(tenantId, hojeBr, hojeBr, limite, PageRequest.of(0, 50));

        // 2. Buscar do repositório de parcelas de cartão (NEW - Impact Based)
        List<Parcela> parcelasCartao = parcelaRepository.findProximasParcelas(hojeBr, hojeBr, limite, PageRequest.of(0, 50));

        // 3. Buscar do repositório de parcelas de dívidas (limitado a 50)
        List<ParcelaDivida> parcelasDivida = parcelaDividaRepository.findProximasParcelas(hojeBr, hojeBr, limite, PageRequest.of(0, 50));

        // 4. Buscar faturas abertas, fechadas ou atrasadas que tenham valor > 0
        List<FaturaCartao> faturasPendentes = faturaCartaoRepository.findByTenantIdAndStatusIn(
                tenantId, 
                List.of(StatusFatura.ABERTA, StatusFatura.FECHADA, StatusFatura.ATRASADA)
        ).stream()
        .filter(f -> calcularValorRealFatura(f).compareTo(BigDecimal.ZERO) > 0)
        .toList();

        // 5. Buscar projeções de recorrências (NEW - Impact Based)
        List<Vencimento> projecoes = projectRecurrences(tenantId, hojeBr, limite, hojeBr);

        List<Vencimento> rawList = new ArrayList<>();
        rawList.addAll(projecoes);

        // Mapear transações: Manter todas, o consolidate cuidará do filtro
        transacoes.forEach(t -> rawList.add(mapTransacaoToVencimento(t, hojeBr)));

        // Mapear parcelas de dívida
        parcelasDivida.forEach(p -> rawList.add(mapParcelaDividaToVencimento(p, hojeBr)));

        // Mapear faturas
        faturasPendentes.forEach(f -> rawList.add(mapFaturaToVencimento(f, hojeBr)));

        // Consolidação Inteligente (Agrupa CC)
        List<Vencimento> todos = consolidateVencimentos(rawList, tenantId, hojeBr);

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

        LocalDate dataVenc = t.getDataVencimento() != null ? t.getDataVencimento() : t.getData();
        int dias = (int) java.time.temporal.ChronoUnit.DAYS.between(hoje, dataVenc);
        boolean atrasado = dataVenc.isBefore(hoje);
        boolean venceHoje = dataVenc.isEqual(hoje);

        return new Vencimento(
                "TRANSACAO-" + t.getId(),
                t.getId(),
                null, // parcelaId
                t.getLancamentos() != null && !t.getLancamentos().isEmpty() ? t.getLancamentos().iterator().next().getConta().getId() : null,
                t.getDescricao(),
                t.getValor(),
                dataVenc,
                dias,
                conta,
                com.gestao.financeiro.entity.enums.OrigemVencimento.TRANSACAO,
                t.getTipo() == TipoTransacao.RECEITA ? com.gestao.financeiro.entity.enums.TipoMovimentacao.RECEITA : com.gestao.financeiro.entity.enums.TipoMovimentacao.DESPESA,
                atrasado,
                venceHoje,
                t.getValorPrevisto()
        );
    }

    private Vencimento mapParcelaCartaoToVencimento(Parcela p, LocalDate hoje) {
        Transacao t = p.getTransacao();
        String conta = t.getLancamentos() != null && !t.getLancamentos().isEmpty()
                ? t.getLancamentos().iterator().next().getConta().getNome() : "Cartão de Crédito";
        
        int dias = (int) java.time.temporal.ChronoUnit.DAYS.between(hoje, p.getDataVencimento());
        boolean atrasado = p.getDataVencimento().isBefore(hoje);
        boolean venceHoje = p.getDataVencimento().isEqual(hoje);

        return new Vencimento(
                "PARCELA-CARTAO-" + p.getId(),
                t.getId(),
                p.getId(),
                t.getLancamentos() != null && !t.getLancamentos().isEmpty() ? t.getLancamentos().iterator().next().getConta().getId() : null,
                t.getDescricao() + " (" + p.getNumeroParcela() + "/" + p.getTotalParcelas() + ")",
                p.getValorParcela(),
                p.getDataVencimento(),
                dias,
                conta,
                com.gestao.financeiro.entity.enums.OrigemVencimento.PARCELA,
                com.gestao.financeiro.entity.enums.TipoMovimentacao.DESPESA,
                atrasado,
                venceHoje,
                null // Parcela de cartão não tem valorPrevisto diretamente
        );
    }

    private Vencimento mapParcelaDividaToVencimento(ParcelaDivida p, LocalDate hoje) {
        String conta = "Dívida: " + p.getDivida().getDescricao();
        
        int dias = (int) java.time.temporal.ChronoUnit.DAYS.between(hoje, p.getDataVencimento());
        boolean atrasado = p.getDataVencimento().isBefore(hoje);
        boolean venceHoje = p.getDataVencimento().isEqual(hoje);

        return new Vencimento(
                "PARCELA-DIVIDA-" + p.getId(),
                null, // transacaoId
                p.getId(),
                null, // contaId (dívida não tem conta direta aqui)
                p.getDivida().getDescricao() + " (" + p.getNumeroParcela() + "/" + (p.getDivida().getParcelas() != null ? p.getDivida().getParcelas().size() : "?") + ")",
                p.getValor(),
                p.getDataVencimento(),
                dias,
                conta,
                com.gestao.financeiro.entity.enums.OrigemVencimento.PARCELA,
                p.getDivida().getTipo() == TipoDivida.A_RECEBER ? com.gestao.financeiro.entity.enums.TipoMovimentacao.RECEITA : com.gestao.financeiro.entity.enums.TipoMovimentacao.DESPESA,
                atrasado,
                venceHoje,
                null // Parcela de dívida não tem valorPrevisto
        );
    }

    private Vencimento mapFaturaToVencimento(FaturaCartao f, LocalDate hoje) {
        String conta = "Fatura: " + f.getCartao().getConta().getNome();
        int dias = (int) java.time.temporal.ChronoUnit.DAYS.between(hoje, f.getDataVencimento());
        boolean atrasado = f.getDataVencimento().isBefore(hoje);
        boolean venceHoje = f.getDataVencimento().isEqual(hoje);

        BigDecimal valorRealFatura = calcularValorRealFatura(f);

        return new Vencimento(
                "FATURA-" + f.getId(),
                null, // transacaoId
                f.getId(),
                f.getCartao().getConta().getId(),
                "Fatura " + f.getMesReferencia() + "/" + f.getAnoReferencia() + " - " + f.getCartao().getConta().getNome(),
                valorRealFatura,
                f.getDataVencimento(),
                dias,
                conta,
                com.gestao.financeiro.entity.enums.OrigemVencimento.FATURA,
                com.gestao.financeiro.entity.enums.TipoMovimentacao.DESPESA,
                atrasado,
                venceHoje,
                null
        );
    }

    public List<Vencimento> getTodosVencimentos(Long tenantId, Integer mes, Integer ano) {
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        LocalDate hojeBr = LocalDate.now(zone);
        
        LocalDate inicio;
        LocalDate limite;

        if (mes != null && ano != null) {
            inicio = LocalDate.of(ano, mes, 1);
            limite = inicio.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
        } else {
            // Se não informar, pega do mês atual
            inicio = hojeBr.with(java.time.temporal.TemporalAdjusters.firstDayOfMonth());
            limite = hojeBr.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
        }

        List<Transacao> transacoes = transacaoRepository.findProximosVencimentos(tenantId, hojeBr, inicio, limite, PageRequest.of(0, 500));
        List<Parcela> parcelasCartao = parcelaRepository.findProximasParcelas(hojeBr, inicio, limite, PageRequest.of(0, 500));
        List<ParcelaDivida> parcelasDivida = parcelaDividaRepository.findProximasParcelas(hojeBr, inicio, limite, PageRequest.of(0, 500));
        
        // 4. Buscar projeções de recorrências (NEW)
        List<Vencimento> projecoes = projectRecurrences(tenantId, inicio, limite, hojeBr);

        // Faturas: filtrar pelo mês/ano de referência (Incluindo ABERTAS com valor)
        List<FaturaCartao> faturasPendentes;
        List<StatusFatura> statusInteresse = List.of(StatusFatura.ABERTA, StatusFatura.FECHADA, StatusFatura.ATRASADA);

        if (mes != null && ano != null) {
             faturasPendentes = faturaCartaoRepository.findByTenantIdAndStatusIn(tenantId, statusInteresse)
                .stream()
                .filter(f -> calcularValorRealFatura(f).compareTo(BigDecimal.ZERO) > 0)
                .filter(f -> {
                    // Aparece se vencer no mês selecionado OU se estiver atrasada
                    boolean noMes = f.getDataVencimento().getMonthValue() == mes && f.getDataVencimento().getYear() == ano;
                    return noMes || f.getStatus() == StatusFatura.ATRASADA;
                })
                .toList();
        } else {
             faturasPendentes = faturaCartaoRepository.findByTenantIdAndStatusIn(tenantId, statusInteresse)
                .stream()
                .filter(f -> calcularValorRealFatura(f).compareTo(BigDecimal.ZERO) > 0)
                .toList();
        }

        List<Vencimento> rawList = new ArrayList<>();
        transacoes.forEach(t -> rawList.add(mapTransacaoToVencimento(t, hojeBr)));
        parcelasDivida.forEach(p -> rawList.add(mapParcelaDividaToVencimento(p, hojeBr)));
        faturasPendentes.forEach(f -> rawList.add(mapFaturaToVencimento(f, hojeBr)));
        rawList.addAll(projecoes);

        List<Vencimento> todos = consolidateVencimentos(rawList, tenantId, hojeBr);

        todos.sort(Comparator.comparing(Vencimento::dataVencimento));
        
        return todos;
    }

    /**
     * Calcula o valor real da fatura considerando apenas parcelas de transações ativas.
     * O campo valorTotal no banco pode ficar desatualizado quando transações são canceladas/deletadas.
     */
    private BigDecimal calcularValorRealFatura(FaturaCartao f) {
        return f.getParcelas().stream()
                .filter(p -> {
                    Transacao t = p.getTransacao();
                    return t != null
                            && t.getDeletedAt() == null
                            && t.getStatus() != com.gestao.financeiro.entity.enums.StatusTransacao.CANCELADO;
                })
                .map(Parcela::getValorParcela)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<Vencimento> consolidateVencimentos(List<Vencimento> rawList, Long tenantId, LocalDate hoje) {
        List<Conta> cartoes = contaRepository.findByTenantIdAndTipo(tenantId, TipoConta.CARTAO_CREDITO);
        Set<Long> cartaoContaIds = cartoes.stream().map(Conta::getId).collect(Collectors.toSet());
        
        List<Vencimento> filtered = new ArrayList<>();
        Map<String, BigDecimal> ccAggregation = new HashMap<>(); // Key: "CONTA_ID:YEAR_MONTH"
        
        for (Vencimento v : rawList) {
            if (v.contaId() != null && cartaoContaIds.contains(v.contaId())) {
                if (v.origem() == com.gestao.financeiro.entity.enums.OrigemVencimento.FATURA) {
                    filtered.add(v);
                } else if (v.origem() == com.gestao.financeiro.entity.enums.OrigemVencimento.RECORRENCIA) {
                    // Agrega apenas PROJEÇÕES (Recorrências que ainda não foram materializadas)
                    String key = v.contaId() + ":" + YearMonth.from(v.dataVencimento());
                    ccAggregation.merge(key, v.valor(), BigDecimal::add);
                } else {
                    // Transações reais no cartão: IGNORAR aqui, pois elas já estão somadas 
                    // no valor total da FATURA que vem do banco (ou estarão na fatura virtual).
                    // Isso evita a duplicidade de valores (Double Counting).
                }
            } else {
                filtered.add(v);
            }
        }
        
        List<Vencimento> result = new ArrayList<>();
        Set<String> handledKeys = new HashSet<>();
        
        for (Vencimento v : filtered) {
            if (v.origem() == com.gestao.financeiro.entity.enums.OrigemVencimento.FATURA) {
                String key = v.contaId() + ":" + YearMonth.from(v.dataVencimento());
                BigDecimal extra = ccAggregation.get(key);
                if (extra != null) {
                    result.add(new Vencimento(
                        v.idUnico(), v.transacaoId(), v.parcelaId(), v.contaId(),
                        v.descricao(), v.valor().add(extra), v.dataVencimento(),
                        v.diasRestantes(), v.conta(), v.origem(), v.tipo(),
                        v.atrasado(), v.venceHoje(), v.valorPrevisto() != null ? v.valorPrevisto().add(extra) : extra
                    ));
                    handledKeys.add(key);
                    continue;
                }
            }
            result.add(v);
        }
        
        for (Map.Entry<String, BigDecimal> entry : ccAggregation.entrySet()) {
            if (!handledKeys.contains(entry.getKey())) {
                String[] parts = entry.getKey().split(":");
                Long contaId = Long.parseLong(parts[0]);
                YearMonth ym = YearMonth.parse(parts[1]);
                
                Conta conta = contaRepository.findById(contaId).orElse(null);
                if (conta == null) continue;
                
                CartaoCredito cartao = cartaoCreditoRepository.findByContaId(contaId).orElse(null);
                if (cartao == null) continue;

                LocalDate vencimento = ym.atDay(Math.min(cartao.getDiaVencimento(), ym.lengthOfMonth()));
                int dias = (int) java.time.temporal.ChronoUnit.DAYS.between(hoje, vencimento);
                
                result.add(new Vencimento(
                    "VIRTUAL-FATURA-" + contaId + "-" + ym,
                    null, null, contaId,
                    "Fatura " + ym.getMonthValue() + "/" + ym.getYear() + " - " + conta.getNome() + " (Previsto)",
                    entry.getValue(),
                    vencimento,
                    dias,
                    "Fatura: " + conta.getNome(),
                    com.gestao.financeiro.entity.enums.OrigemVencimento.FATURA,
                    com.gestao.financeiro.entity.enums.TipoMovimentacao.DESPESA,
                    vencimento.isBefore(hoje),
                    vencimento.isEqual(hoje),
                    entry.getValue()
                ));
            }
        }
        
        return result;
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
                    Long catId = o.getCategoria().getId();
                    
                    // Buscar subcategorias (filhas)
                    List<Categoria> subcategorias = categoriaRepository.findByCategoriaPaiId(catId);
                    
                    // Calcula gasto da categoria pai
                    BigDecimal gastoPai = gastosPorCatId.getOrDefault(catId, BigDecimal.ZERO);
                    
                    // Calcula gasto das filhas e monta breakdown
                    BigDecimal gastoFilhasTotal = BigDecimal.ZERO;
                    List<ResumoOrcamento.SubcategoriaGasto> breakdown = new java.util.ArrayList<>();
                    
                    for (Categoria subcat : subcategorias) {
                        BigDecimal gastoSub = gastosPorCatId.getOrDefault(subcat.getId(), BigDecimal.ZERO);
                        if (gastoSub.compareTo(BigDecimal.ZERO) > 0) {
                            gastoFilhasTotal = gastoFilhasTotal.add(gastoSub);
                            breakdown.add(new ResumoOrcamento.SubcategoriaGasto(
                                    subcat.getNome(), gastoSub
                            ));
                        }
                    }

                    BigDecimal limite = nvl(o.getLimite());
                    BigDecimal gastoTotal = gastoPai.add(gastoFilhasTotal);
                    BigDecimal dispon = limite.subtract(gastoTotal);

                    double pct = limite.compareTo(BigDecimal.ZERO) > 0
                            ? gastoTotal.divide(limite, 4, RoundingMode.HALF_UP)
                                     .multiply(BigDecimal.valueOf(100)).doubleValue()
                            : 0.0;

                    return new ResumoOrcamento(
                            o.getId(), o.getCategoria().getNome(),
                            limite, gastoTotal, dispon, round2(pct),
                            gastoTotal.compareTo(limite) > 0,
                            breakdown.isEmpty() ? null : breakdown
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

                            // Professional logic: sum all invoices not PAGA (ABERTA, FECHADA, ATRASADA)
                            List<com.gestao.financeiro.entity.enums.StatusFatura> statusParaSomar = List.of(
                                com.gestao.financeiro.entity.enums.StatusFatura.ABERTA,
                                com.gestao.financeiro.entity.enums.StatusFatura.FECHADA,
                                com.gestao.financeiro.entity.enums.StatusFatura.ATRASADA
                            );
                            
                            List<FaturaCartao> faturasPendentes = faturaCartaoRepository.findByCartaoIdAndStatusIn(cartao.getId(), statusParaSomar);
                            
                            BigDecimal faturaTotalPendente = faturasPendentes.stream()
                                    .map(f -> nvl(f.getValorTotal()))
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                            BigDecimal faturaMesAtual = faturaCartaoRepository
                                    .findByCartaoIdAndMesReferenciaAndAnoReferencia(
                                            cartao.getId(), hoje.getMonthValue(), hoje.getYear())
                                    .map(f -> nvl(f.getValorTotal()))
                                    .orElse(BigDecimal.ZERO);

                            BigDecimal utilizado = faturaTotalPendente.compareTo(BigDecimal.ZERO) > 0
                                    ? faturaTotalPendente
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
                                    faturaMesAtual, proximoVencimento
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

    private List<Vencimento> projectRecurrences(Long tenantId, LocalDate inicio, LocalDate limite, LocalDate hoje) {
        List<Vencimento> projecoes = new ArrayList<>();
        YearMonth startMonth = YearMonth.from(inicio);
        YearMonth endMonth = YearMonth.from(limite);
        
        List<TransacaoRecorrente> recurrences = transacaoRecorrenteRepository.findByAtivaTrueAndTenantId(tenantId);
        log.info("[DEBUG] projectRecurrences: tenantId={}, startMonth={}, endMonth={}, found {} recurrences", tenantId, startMonth, endMonth, recurrences.size());
        
        for (TransacaoRecorrente rec : recurrences) {
            YearMonth current = startMonth;
            while (!current.isAfter(endMonth)) {
                LocalDate occurrenceDate = current.atDay(Math.min(rec.getDiaVencimento() != null ? rec.getDiaVencimento() : rec.getDataInicio().getDayOfMonth(), current.lengthOfMonth()));
                
                // Only project if it's within the requested period [inicio, limite]
                boolean inPeriod = !occurrenceDate.isBefore(inicio) && !occurrenceDate.isAfter(limite);
                boolean active = rec.isAtivaEm(occurrenceDate);
                log.info("[DEBUG] Checking occurrence: rec={}, date={}, inPeriod={}, active={}", rec.getDescricao(), occurrenceDate, inPeriod, active);

                if (inPeriod && active) {
                    // Check if already materialized
                    boolean exists = transacaoRepository.existsByRecorrenciaIdAndReferenciaIgnoreSoftDelete(rec.getId(), current.toString());
                    if (!exists) {
                        int dias = (int) java.time.temporal.ChronoUnit.DAYS.between(hoje, occurrenceDate);
                        boolean atrasado = occurrenceDate.isBefore(hoje);
                        boolean venceHoje = occurrenceDate.isEqual(hoje);

                        projecoes.add(new Vencimento(
                            "RECORRENCIA-PROJ-" + rec.getId() + "-" + current,
                            null, // transacaoId
                            null, // parcelaId
                            rec.getConta() != null ? rec.getConta().getId() : null,
                            rec.getDescricao() + " (Previsto)",
                            rec.getValor(),
                            occurrenceDate,
                            dias,
                            rec.getConta() != null ? rec.getConta().getNome() : "Assinatura",
                            com.gestao.financeiro.entity.enums.OrigemVencimento.RECORRENCIA,
                            rec.getTipo() == TipoTransacao.RECEITA ? com.gestao.financeiro.entity.enums.TipoMovimentacao.RECEITA : com.gestao.financeiro.entity.enums.TipoMovimentacao.DESPESA,
                            atrasado,
                            venceHoje,
                            rec.getValor()
                        ));
                    }
                }
                current = current.plusMonths(1);
            }
        }
        return projecoes;
    }

    private BigDecimal nvl(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
