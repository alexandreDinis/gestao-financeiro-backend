package com.gestao.financeiro.service;

import com.gestao.financeiro.config.TenantContext;
import com.gestao.financeiro.dto.request.PrevisaoAjusteRequest;
import com.gestao.financeiro.dto.response.PrevisaoMesResponse;
import com.gestao.financeiro.dto.response.RelatorioPrevisaoResponse;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

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

            BigDecimal totalEntradasPrevistas = entradasLancamento.add(entradasRecorrente).add(entradasDividas);
            BigDecimal totalSaidasPrevistas = saidasLancamento.add(saidasCartao).add(saidasRecorrente).add(saidasDividas);

            // Ajustes Manuais
            PrevisaoAjuste ajuste = previsaoAjusteRepository.findByTenantIdAndMesAndAno(tenantId, ref.getMonthValue(), ref.getYear())
                    .orElse(new PrevisaoAjuste());
            BigDecimal ajusteEntrada = nvl(ajuste.getAjusteEntrada());
            BigDecimal ajusteSaida = nvl(ajuste.getAjusteSaida());

            // Cálculo do Saldo Final com Efeito Cascata
            // Saldo Final = Saldo Inicial + Entradas + Ajuste Entrada - Saídas - Ajuste Saída
            BigDecimal saldoFinal = saldoCorrente
                    .add(totalEntradasPrevistas)
                    .add(ajusteEntrada)
                    .subtract(totalSaidasPrevistas)
                    .subtract(ajusteSaida);

            meses.add(new PrevisaoMesResponse(
                    ref.getMonthValue(),
                    ref.getYear(),
                    saldoCorrente,
                    totalEntradasPrevistas,
                    totalSaidasPrevistas,
                    ajusteEntrada,
                    ajusteSaida,
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
}
