package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.CartaoCreditoRequest;
import com.gestao.financeiro.dto.request.CompraCartaoRequest;
import com.gestao.financeiro.dto.response.CartaoCreditoResponse;
import com.gestao.financeiro.dto.response.FaturaCartaoResponse;
import com.gestao.financeiro.dto.response.ParcelaResponse;
import com.gestao.financeiro.entity.*;
import com.gestao.financeiro.entity.enums.*;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.exception.ResourceNotFoundException;
import com.gestao.financeiro.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CartaoCreditoService {

    private final CartaoCreditoRepository cartaoRepository;
    private final ContaRepository contaRepository;
    private final CategoriaRepository categoriaRepository;
    private final FaturaCartaoRepository faturaRepository;
    private final ParcelaRepository parcelaRepository;
    private final TransacaoRepository transacaoRepository;

    private static final Long DEFAULT_TENANT_ID = 1L;

    // ========================= CARTÃO CRUD =========================

    public Page<CartaoCreditoResponse> listarCartoes(Pageable pageable) {
        return cartaoRepository.findAll(pageable).map(this::toCartaoResponse);
    }

    public CartaoCreditoResponse buscarCartao(Long id) {
        return toCartaoResponse(findCartaoById(id));
    }

    @Transactional
    public CartaoCreditoResponse criarCartao(CartaoCreditoRequest request) {
        Conta conta = contaRepository.findById(request.contaId())
                .orElseThrow(() -> new ResourceNotFoundException("Conta", request.contaId()));

        if (conta.getTipo() != TipoConta.CARTAO_CREDITO) {
            throw new BusinessException("A conta deve ser do tipo CARTAO_CREDITO.");
        }

        if (cartaoRepository.existsByContaId(request.contaId())) {
            throw new BusinessException("Esta conta já está vinculada a um cartão.");
        }

        CartaoCredito cartao = CartaoCredito.builder()
                .conta(conta)
                .bandeira(request.bandeira())
                .limite(request.limite())
                .diaFechamento(request.diaFechamento())
                .diaVencimento(request.diaVencimento())
                .build();
        cartao.setTenantId(DEFAULT_TENANT_ID);

        cartao = cartaoRepository.save(cartao);
        log.info("[tenant={}] Cartão criado: id={} bandeira={}", DEFAULT_TENANT_ID, cartao.getId(), cartao.getBandeira());

        return toCartaoResponse(cartao);
    }

    @Transactional
    public void deletarCartao(Long id) {
        CartaoCredito cartao = findCartaoById(id);
        cartao.softDelete();
        cartaoRepository.save(cartao);
        log.info("[tenant={}] Cartão removido: id={}", cartao.getTenantId(), id);
    }

    // ========================= COMPRA PARCELADA =========================

    /**
     * Registra compra no cartão com parcelamento.
     * 1. Cria Transação (tipo DESPESA)
     * 2. Distribui parcelas nas faturas futuras
     * 3. Cria/atualiza faturas automaticamente
     */
    @Transactional
    public FaturaCartaoResponse comprar(CompraCartaoRequest request) {
        CartaoCredito cartao = findCartaoById(request.cartaoId());

        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria", request.categoriaId()));

        // Cria transação de despesa
        Transacao transacao = Transacao.builder()
                .descricao(request.descricao())
                .valor(request.valor())
                .data(LocalDate.now())
                .tipo(TipoTransacao.DESPESA)
                .status(StatusTransacao.PENDENTE)
                .categoria(categoria)
                .observacao(request.parcelas() + "x no cartão " + cartao.getBandeira())
                .build();
        transacao.setTenantId(DEFAULT_TENANT_ID);

        // Cria lançamento DEBITO na conta do cartão
        Lancamento debito = Lancamento.builder()
                .conta(cartao.getConta())
                .valor(request.valor())
                .direcao(DirecaoLancamento.DEBITO)
                .descricao("Compra cartão: " + request.descricao())
                .build();
        transacao.addLancamento(debito);
        transacao = transacaoRepository.save(transacao);

        // Distribui parcelas nas faturas
        BigDecimal valorParcela = request.valor()
                .divide(BigDecimal.valueOf(request.parcelas()), 2, RoundingMode.HALF_UP);

        LocalDate dataCompra = LocalDate.now();
        FaturaCartao primeiraFatura = null;

        for (int i = 0; i < request.parcelas(); i++) {
            // Calcula em qual fatura cai essa parcela
            LocalDate dataRef = calcularDataFatura(dataCompra, cartao.getDiaFechamento(), i);
            int mesFatura = dataRef.getMonthValue();
            int anoFatura = dataRef.getYear();

            // Busca ou cria a fatura
            FaturaCartao fatura = faturaRepository
                    .findByCartaoIdAndMesReferenciaAndAnoReferencia(cartao.getId(), mesFatura, anoFatura)
                    .orElseGet(() -> criarFatura(cartao, mesFatura, anoFatura));

            // Ajusta última parcela para cobrir diferença de arredondamento
            BigDecimal valorEstaParcela = valorParcela;
            if (i == request.parcelas() - 1) {
                BigDecimal jaDistribuido = valorParcela.multiply(BigDecimal.valueOf(request.parcelas() - 1));
                valorEstaParcela = request.valor().subtract(jaDistribuido);
            }

            Parcela parcela = Parcela.builder()
                    .transacao(transacao)
                    .numeroParcela(i + 1)
                    .totalParcelas(request.parcelas())
                    .valorParcela(valorEstaParcela)
                    .dataVencimento(fatura.getDataVencimento())
                    .paga(false)
                    .build();
            fatura.adicionarParcela(parcela);
            faturaRepository.save(fatura);

            if (i == 0) primeiraFatura = fatura;
        }

        log.info("[tenant={}] Compra parcelada: transacao={} valor={} parcelas={} cartao={}",
                DEFAULT_TENANT_ID, transacao.getId(), request.valor(), request.parcelas(), cartao.getBandeira());

        return toFaturaResponse(primeiraFatura);
    }

    // ========================= FATURA =========================

    public List<FaturaCartaoResponse> listarFaturas(Long cartaoId) {
        findCartaoById(cartaoId);
        return faturaRepository.findByCartaoIdOrderByAnoReferenciaDescMesReferenciaDesc(cartaoId)
                .stream().map(this::toFaturaResponse).toList();
    }

    public FaturaCartaoResponse buscarFatura(Long faturaId) {
        return toFaturaResponse(findFaturaById(faturaId));
    }

    @Transactional
    public FaturaCartaoResponse pagarFatura(Long faturaId) {
        FaturaCartao fatura = findFaturaById(faturaId);

        if (fatura.getStatus() == StatusFatura.PAGA) {
            throw new BusinessException("Fatura já está paga.");
        }

        fatura.setStatus(StatusFatura.PAGA);
        fatura.getParcelas().forEach(p -> p.setPaga(true));

        // Cria transação de pagamento da fatura (DEBITO na conta pagadora)
        // O pagamento real da fatura será feito pelo usuário via transação normal
        fatura = faturaRepository.save(fatura);
        log.info("[tenant={}] Fatura paga: id={} valor={}", fatura.getTenantId(), faturaId, fatura.getValorTotal());

        return toFaturaResponse(fatura);
    }

    /**
     * Job diário: fecha faturas e marca atrasadas.
     */
    @Scheduled(cron = "0 30 6 * * *")
    @Transactional
    public void processarFaturas() {
        log.info("Processando faturas de cartão...");
        LocalDate hoje = LocalDate.now();

        List<FaturaCartao> abertas = faturaRepository.findByStatus(StatusFatura.ABERTA);
        for (FaturaCartao fatura : abertas) {
            CartaoCredito cartao = fatura.getCartao();

            // Fecha fatura se passou do dia de fechamento
            LocalDate dataFechamento = LocalDate.of(
                    fatura.getAnoReferencia(), fatura.getMesReferencia(),
                    Math.min(cartao.getDiaFechamento(), LocalDate.of(fatura.getAnoReferencia(), fatura.getMesReferencia(), 1).lengthOfMonth()));

            if (!hoje.isBefore(dataFechamento)) {
                fatura.setStatus(StatusFatura.FECHADA);
                faturaRepository.save(fatura);
                log.info("Fatura fechada: id={} valor={}", fatura.getId(), fatura.getValorTotal());
            }
        }

        // Marca atrasadas
        List<FaturaCartao> fechadas = faturaRepository.findByStatus(StatusFatura.FECHADA);
        for (FaturaCartao fatura : fechadas) {
            if (hoje.isAfter(fatura.getDataVencimento())) {
                fatura.setStatus(StatusFatura.ATRASADA);
                faturaRepository.save(fatura);
                log.info("Fatura atrasada: id={} vencimento={}", fatura.getId(), fatura.getDataVencimento());
            }
        }
    }

    // ========================= HELPERS =========================

    private FaturaCartao criarFatura(CartaoCredito cartao, int mes, int ano) {
        int diaVenc = Math.min(cartao.getDiaVencimento(),
                LocalDate.of(ano, mes, 1).lengthOfMonth());

        FaturaCartao fatura = FaturaCartao.builder()
                .cartao(cartao)
                .mesReferencia(mes)
                .anoReferencia(ano)
                .dataVencimento(LocalDate.of(ano, mes, diaVenc))
                .status(StatusFatura.ABERTA)
                .build();
        fatura.setTenantId(cartao.getTenantId());
        return faturaRepository.save(fatura);
    }

    /**
     * Calcula em qual mês/ano a parcela N cai, baseado no dia de fechamento.
     */
    private LocalDate calcularDataFatura(LocalDate dataCompra, int diaFechamento, int parcelaIndex) {
        // Se a compra é antes do fechamento, cai na fatura do mês seguinte
        // Se depois, cai na fatura 2 meses depois
        LocalDate ref = dataCompra;
        if (dataCompra.getDayOfMonth() > diaFechamento) {
            ref = ref.plusMonths(1);
        }
        return ref.plusMonths(parcelaIndex + 1).with(TemporalAdjusters.firstDayOfMonth());
    }

    private CartaoCredito findCartaoById(Long id) {
        return cartaoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cartão de crédito", id));
    }

    private FaturaCartao findFaturaById(Long id) {
        return faturaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fatura", id));
    }

    private CartaoCreditoResponse toCartaoResponse(CartaoCredito c) {
        return new CartaoCreditoResponse(
                c.getId(), c.getConta().getId(), c.getConta().getNome(),
                c.getBandeira(), c.getLimite(),
                c.getDiaFechamento(), c.getDiaVencimento(),
                c.getCreatedAt());
    }

    private FaturaCartaoResponse toFaturaResponse(FaturaCartao f) {
        List<ParcelaResponse> parcelas = f.getParcelas().stream()
                .map(p -> new ParcelaResponse(
                        p.getId(), p.getNumeroParcela(), p.getTotalParcelas(),
                        p.getValorParcela(), p.getDataVencimento(), p.getPaga(),
                        p.getTransacao().getDescricao()))
                .toList();

        return new FaturaCartaoResponse(
                f.getId(), f.getCartao().getId(), f.getCartao().getBandeira(),
                f.getMesReferencia(), f.getAnoReferencia(),
                f.getValorTotal(), f.getDataVencimento(), f.getStatus(),
                parcelas, f.getCreatedAt());
    }
}
