package com.gestao.financeiro.service;

import com.gestao.financeiro.config.TenantContext;
import com.gestao.financeiro.dto.request.PrevisaoAjusteRequest;
import com.gestao.financeiro.dto.response.PrevisaoMesResponse;
import com.gestao.financeiro.dto.response.RelatorioPrevisaoResponse;
import com.gestao.financeiro.dto.response.EstimativaVariavelDTO;
import com.gestao.financeiro.dto.response.EstimativaPorCategoriaDTO;
import com.gestao.financeiro.dto.response.AjusteManualDTO;
import com.gestao.financeiro.entity.Conta;
import com.gestao.financeiro.entity.PrevisaoAjuste;
import com.gestao.financeiro.entity.TransacaoRecorrente;
import com.gestao.financeiro.entity.enums.TipoConta;
import com.gestao.financeiro.entity.enums.TipoDespesa;
import com.gestao.financeiro.repository.ContaRepository;
import com.gestao.financeiro.repository.LancamentoRepository;
import com.gestao.financeiro.repository.ParcelaDividaRepository;
import com.gestao.financeiro.repository.ParcelaRepository;
import com.gestao.financeiro.repository.PrevisaoAjusteRepository;
import com.gestao.financeiro.repository.TransacaoRecorrenteRepository;
import com.gestao.financeiro.repository.TransacaoRepository;
import com.gestao.financeiro.entity.enums.TipoDivida;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.gestao.financeiro.repository.projection.GastoMensalCategoriaProjection;

@Service
@RequiredArgsConstructor
public class PrevisaoCaixaService {

    private final ContaRepository contaRepository;
    private final LancamentoRepository lancamentoRepository;
    private final TransacaoRecorrenteRepository transacaoRecorrenteRepository;
    private final PrevisaoAjusteRepository previsaoAjusteRepository;
    private final TransacaoRepository transacaoRepository;
    private final ParcelaDividaRepository parcelaDividaRepository;
    private final ParcelaRepository parcelaRepository;

    @Transactional(readOnly = true)
    public RelatorioPrevisaoResponse gerarPrevisao(int mesesParaFrente) {
        Long tenantId = TenantContext.getTenantId();
        
        // 1. Somar saldo inicial real (todas as contas ativas, exceto cartão de crédito)
        List<Conta> contas = contaRepository.findByAtivaTrue(Pageable.unpaged()).getContent();
        BigDecimal saldoInicialReal = contas.stream()
                .filter(c -> c.getTipo() != TipoConta.CARTAO_CREDITO)
                .map(c -> {
                    BigDecimal s = contaRepository.calcularSaldo(c.getId());
                    return s != null ? s : (c.getSaldoInicial() != null ? c.getSaldoInicial() : BigDecimal.ZERO);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PrevisaoMesResponse> meses = new ArrayList<>();
        YearMonth mesAtual = YearMonth.now();
        BigDecimal saldoCorrente = saldoInicialReal;

        // Calcular Estimativa Variável (últimos 3 meses fechados)
        YearMonth mesPassado = mesAtual.minusMonths(1);
        LocalDate fimVariaveis = mesPassado.atEndOfMonth();
        LocalDate inicioVariaveis = mesPassado.minusMonths(2).atDay(1); // Mês -1, -2, -3
        EstimativaVariavelDTO estimativaVariavelBase = calcularEstimativaVariavel(inicioVariaveis, fimVariaveis);

        List<TransacaoRecorrente> recorrencias = transacaoRecorrenteRepository.findByAtivaTrueAndTenantId(tenantId);

        for (int i = 0; i < mesesParaFrente; i++) {
            YearMonth ref = mesAtual.plusMonths(i);
            LocalDate inicio = ref.atDay(1);
            LocalDate fim = ref.atEndOfMonth();

            // Lançamentos pendentes do mês
            BigDecimal entradasLancamento = nvl(lancamentoRepository.somarTotalCreditosPendentesPeriodo(inicio, fim));
            BigDecimal saidasLancamento = nvl(lancamentoRepository.somarTotalDebitosPendentesPeriodoSemCartao(inicio, fim));

            // Faturas de cartão projetadas (somamos as parcelas que VENCEM no mês)
            BigDecimal saidasCartao = nvl(parcelaRepository.somarParcelasPorVencimento(inicio, fim));

            // Dívidas entre Pessoas (Empréstimos/Recebíveis)
            BigDecimal entradasDividas = nvl(parcelaDividaRepository.somarParcelasPendentesPorPeriodoETipo(tenantId, inicio, fim, TipoDivida.A_RECEBER));
            BigDecimal saidasDividas = nvl(parcelaDividaRepository.somarParcelasPendentesPorPeriodoETipo(tenantId, inicio, fim, TipoDivida.A_PAGAR));

            // Avaliar recorrências que AINDA não foram geradas para este mês
            BigDecimal entradasRecorrente = BigDecimal.ZERO;
            BigDecimal saidasRecorrente = BigDecimal.ZERO;

            for (TransacaoRecorrente rec : recorrencias) {
                // Só considera se a recorrência já iniciou
                if (rec.getDataInicio() != null && YearMonth.from(rec.getDataInicio()).isAfter(ref)) {
                    continue;
                }
                
                // Se a transação já foi gerada no banco para este mês, o valor dela já está em entradasLancamento/saidasLancamento
                // (assumindo que a transação gerada não foi deletada)
                boolean jaGerada = transacaoRepository.existsByRecorrenciaIdAndReferenciaIgnoreSoftDelete(rec.getId(), ref.toString());
                
                if (!jaGerada) {
                    BigDecimal valor = nvl(rec.getValor());
                    if (rec.getTipo() == com.gestao.financeiro.entity.enums.TipoTransacao.RECEITA) {
                        entradasRecorrente = entradasRecorrente.add(valor);
                    } else {
                        saidasRecorrente = saidasRecorrente.add(valor);
                    }
                }
            }

            // O total de Receitas Fixas / Despesas Fixas agora agrupa apenas o que é previsível/conhecido
            BigDecimal receitasFixas = entradasLancamento.add(entradasRecorrente).add(entradasDividas);
            BigDecimal despesasFixas = saidasLancamento.add(saidasCartao).add(saidasRecorrente).add(saidasDividas);

            // Ajustes Manuais
            PrevisaoAjuste ajuste = previsaoAjusteRepository.findByTenantIdAndMesAndAno(tenantId, ref.getMonthValue(), ref.getYear())
                    .orElse(new PrevisaoAjuste());
            BigDecimal ajusteEntrada = nvl(ajuste.getAjusteEntrada());
            BigDecimal ajusteSaida = nvl(ajuste.getAjusteSaida());
            AjusteManualDTO ajusteManual = new AjusteManualDTO(ajusteEntrada, ajusteSaida);

            // Cálculo do Saldo Final com Efeito Cascata (Agregando 3 camadas)
            // Saldo Final = Saldo Inicial + Receitas Fixas - Despesas Fixas - Estimativa Variável + Ajustes
            BigDecimal saldoFinal = saldoCorrente
                    .add(receitasFixas)
                    .subtract(despesasFixas)
                    .subtract(estimativaVariavelBase.valor())
                    .add(ajusteEntrada)
                    .subtract(ajusteSaida);

            meses.add(new PrevisaoMesResponse(
                    ref.getYear() + "-" + String.format("%02d", ref.getMonthValue()),
                    saldoCorrente,
                    receitasFixas,
                    despesasFixas,
                    estimativaVariavelBase,
                    ajusteManual,
                    saldoFinal
            ));

            // Prepara para o próximo mês
            saldoCorrente = saldoFinal;
        }

        return new RelatorioPrevisaoResponse(saldoInicialReal, meses);
    }

    @Transactional
    public void salvarAjuste(PrevisaoAjusteRequest request) {
        Long tenantId = TenantContext.getTenantId();
        PrevisaoAjuste ajuste = previsaoAjusteRepository.findByTenantIdAndMesAndAno(tenantId, request.mes(), request.ano())
                .orElseGet(() -> PrevisaoAjuste.builder()
                        .tenantId(tenantId)
                        .mes(request.mes())
                        .ano(request.ano())
                        .build());

        ajuste.setAjusteEntrada(request.ajusteEntrada() != null ? request.ajusteEntrada() : BigDecimal.ZERO);
        ajuste.setAjusteSaida(request.ajusteSaida() != null ? request.ajusteSaida() : BigDecimal.ZERO);

        previsaoAjusteRepository.save(ajuste);
    }

    private BigDecimal nvl(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private EstimativaVariavelDTO calcularEstimativaVariavel(LocalDate inicio, LocalDate fim) {
        List<GastoMensalCategoriaProjection> gastos = transacaoRepository.somarGastosVariaveisMensaisPorCategoria(inicio, fim);
        
        YearMonth ymInicio = YearMonth.from(inicio);
        YearMonth ymFim = YearMonth.from(fim);
        List<YearMonth> meses = new ArrayList<>();
        YearMonth current = ymInicio;
        while (!current.isAfter(ymFim)) {
            meses.add(current);
            current = current.plusMonths(1);
        }
        int mesesReais = meses.size();
        if (mesesReais == 0) mesesReais = 1;

        Map<YearMonth, BigDecimal> totaisPorMes = new HashMap<>();
        Map<Long, String> nomes = new HashMap<>();
        Map<Long, BigDecimal> somaPorCategoria = new HashMap<>();

        for (GastoMensalCategoriaProjection g : gastos) {
            YearMonth ym = YearMonth.of(g.getAno(), g.getMes());
            totaisPorMes.put(ym, totaisPorMes.getOrDefault(ym, BigDecimal.ZERO).add(g.getTotal()));
            
            nomes.put(g.getCategoriaId(), g.getNomeCategoria());
            somaPorCategoria.put(g.getCategoriaId(), somaPorCategoria.getOrDefault(g.getCategoriaId(), BigDecimal.ZERO).add(g.getTotal()));
        }

        BigDecimal minGlobal = null;
        BigDecimal maxGlobal = null;
        BigDecimal somaGlobal = BigDecimal.ZERO;

        for (YearMonth ym : meses) {
            BigDecimal totalMes = totaisPorMes.getOrDefault(ym, BigDecimal.ZERO);
            somaGlobal = somaGlobal.add(totalMes);
            if (minGlobal == null || totalMes.compareTo(minGlobal) < 0) minGlobal = totalMes;
            if (maxGlobal == null || totalMes.compareTo(maxGlobal) > 0) maxGlobal = totalMes;
        }

        BigDecimal mediaGlobal = somaGlobal.divide(BigDecimal.valueOf(mesesReais), 2, RoundingMode.HALF_UP);
        if (minGlobal == null) minGlobal = BigDecimal.ZERO;
        if (maxGlobal == null) maxGlobal = BigDecimal.ZERO;

        List<EstimativaPorCategoriaDTO> categorias = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : somaPorCategoria.entrySet()) {
            BigDecimal mediaCat = entry.getValue().divide(BigDecimal.valueOf(mesesReais), 2, RoundingMode.HALF_UP);
            categorias.add(new EstimativaPorCategoriaDTO(entry.getKey(), nomes.get(entry.getKey()), mediaCat));
        }

        categorias.sort((a, b) -> b.media().compareTo(a.media()));

        return new EstimativaVariavelDTO(mediaGlobal, minGlobal, maxGlobal, mesesReais, categorias);
    }
}
