package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.CartaoCreditoRequest;
import com.gestao.financeiro.dto.request.PagarFaturaRequest;
import com.gestao.financeiro.dto.request.CompraCartaoRequest;
import com.gestao.financeiro.dto.response.CartaoCreditoResponse;
import com.gestao.financeiro.dto.response.FaturaCartaoResponse;
import com.gestao.financeiro.dto.response.ParcelaResponse;
import com.gestao.financeiro.entity.*;
import com.gestao.financeiro.entity.enums.*;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.exception.ResourceNotFoundException;
import com.gestao.financeiro.exception.SaldoInsuficienteException;
import com.gestao.financeiro.repository.*;
import com.gestao.financeiro.repository.projection.CartaoCreditoResumoProjection;
import com.gestao.financeiro.config.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PessimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    private final IdempotencyControlRepository idempotencyRepository;
    private final com.gestao.financeiro.provider.DateProvider dateProvider;



    // ========================= CARTÃO CRUD =========================

    public Page<CartaoCreditoResponse> listarCartoes(Pageable pageable) {
        return cartaoRepository.findAll(pageable).map(this::toCartaoResponse);
    }

    public CartaoCreditoResponse buscarCartao(Long id) {
        return toCartaoResponse(findCartaoById(id));
    }

    @Transactional
    public CartaoCreditoResponse criarCartao(CartaoCreditoRequest request) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("Tenant ID não encontrado no contexto");
        }
        
        // Criar a conta passiva do cartão automaticamente
        Conta conta = Conta.builder()
                .nome(request.nomeCartao())
                .tipo(TipoConta.CARTAO_CREDITO)
                .saldoInicial(BigDecimal.ZERO)
                .ativa(true)
                .build();
        conta.setTenantId(tenantId);
        conta = contaRepository.save(conta);

        CartaoCredito cartao = CartaoCredito.builder()
                .conta(conta)
                .bandeira(request.bandeira())
                .limite(request.limite())
                .diaFechamento(request.diaFechamento())
                .diaVencimento(request.diaVencimento())
                .build();
        cartao.setTenantId(tenantId);

        cartao = cartaoRepository.save(cartao);
        log.info("[tenant={}] Cartão criado: id={} bandeira={}", tenantId, cartao.getId(), cartao.getBandeira());

        return toCartaoResponse(cartao);
    }

    @Transactional
    public CartaoCreditoResponse editarCartao(Long id, CartaoCreditoRequest request) {
        CartaoCredito cartao = findCartaoById(id);

        cartao.setBandeira(request.bandeira());
        cartao.setLimite(request.limite());
        cartao.setDiaFechamento(request.diaFechamento());
        cartao.setDiaVencimento(request.diaVencimento());
        
        // Atualizar também o nome da conta passiva vinculada
        Conta conta = cartao.getConta();
        conta.setNome(request.nomeCartao());
        contaRepository.save(conta);

        cartao = cartaoRepository.save(cartao);
        log.info("[tenant={}] Cartão atualizado: id={} bandeira={}", cartao.getTenantId(), cartao.getId(), cartao.getBandeira());

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
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("Tenant ID não encontrado no contexto");
        }

        CartaoCredito cartao = findCartaoById(request.cartaoId());

        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria", request.categoriaId()));

        LocalDate dataCompra = request.data() != null ? request.data() : dateProvider.now();

        // Cria transação de despesa
        Transacao transacao = Transacao.builder()
                .descricao(request.descricao())
                .valor(request.valor())
                .data(dataCompra)
                .tipo(TipoTransacao.DESPESA)
                .status(StatusTransacao.PENDENTE)
                .categoria(categoria)
                .numeroParcelas(request.parcelas())
                .observacao(request.parcelas() + "x no cartão " + cartao.getBandeira())
                .build();
        transacao.setTenantId(tenantId);

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

        FaturaCartao primeiraFatura = null;

        for (int i = 0; i < request.parcelas(); i++) {
            // Calcula o vencimento em que essa parcela cai (recalculando ciclo base e aplicando shift da parcela)
            LocalDate dataVencimentoBase = calcularDataVencimentoFatura(dataCompra, cartao.getDiaFechamento(), cartao.getDiaVencimento());
            LocalDate dataVencimentoParcela = dataVencimentoBase.plusMonths(i);
            
            // Garante dia válido no mês (ex: 31 em fevereiro)
            dataVencimentoParcela = dataVencimentoParcela.withDayOfMonth(
                    Math.min(cartao.getDiaVencimento(), dataVencimentoParcela.lengthOfMonth()));

            int mesFatura = dataVencimentoParcela.getMonthValue();
            int anoFatura = dataVencimentoParcela.getYear();

            // Identifica a fatura de destino (Respeitando Option A: Shift se PAGA)
            FaturaCartao fatura = obterFaturaParaLancamento(cartao, mesFatura, anoFatura);

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
                tenantId, transacao.getId(), request.valor(), request.parcelas(), cartao.getBandeira());

        return toFaturaResponse(primeiraFatura);
    }

    // ========================= FATURA =========================

    public List<FaturaCartaoResponse> listarFaturas(Long cartaoId) {
        findCartaoById(cartaoId);
        return faturaRepository.findByCartaoIdOrderByAnoReferenciaDescMesReferenciaDesc(cartaoId)
                .stream()
                .map(this::toFaturaResponse)
                .filter(f -> f.valorTotal().compareTo(BigDecimal.ZERO) > 0) // Remove faturas vazias (ex: transações deletadas)
                .toList();
    }

    public FaturaCartaoResponse buscarFatura(Long faturaId) {
        return toFaturaResponse(findFaturaById(faturaId));
    }

    @Transactional
    public FaturaCartaoResponse pagarFatura(Long faturaId, PagarFaturaRequest request) {
        String idempotencyKey = generateIdempotencyKey(faturaId, request);
        
        // 1. Checa idempotência (Sem Lock)
        Optional<IdempotencyControl> existingControl = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        if (existingControl.isPresent()) {
            IdempotencyControl control = existingControl.get();
            if (control.getStatus() == IdempotencyStatus.SUCCESS) {
                log.info("[Idempotency] Pagamento já processado com sucesso: {}", idempotencyKey);
                return buscarFatura(faturaId);
            }
            if (control.getStatus() == IdempotencyStatus.PROCESSING) {
                log.warn("[Idempotency] Pagamento em processamento: {}", idempotencyKey);
                throw new BusinessException("Pagamento em processamento. Aguarde alguns instantes.");
            }
        }

        // 2. Inicia/Atualiza controle para PROCESSING
        IdempotencyControl control = existingControl.orElseGet(() -> IdempotencyControl.builder()
                .idempotencyKey(idempotencyKey)
                .build());
        control.setStatus(IdempotencyStatus.PROCESSING);
        idempotencyRepository.saveAndFlush(control);

        try {
            log.info("[Pagamento] Iniciando processamento de fatura id={} contaId={}", faturaId, request.contaId());

            // 3. Ordem rigorosa de Lock (Conta -> Fatura) para evitar Deadlocks
            Conta contaPagadora = contaRepository.findByIdWithLock(request.contaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Conta", request.contaId()));
            
            FaturaCartao fatura = faturaRepository.findByIdWithLock(faturaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Fatura", faturaId));

            // 4. Validações Críticas (Com Lock)
            if (fatura.getStatus() == StatusFatura.PAGA) {
                updateIdempotencyStatus(control, IdempotencyStatus.SUCCESS);
                return toFaturaResponse(fatura);
            }

            if (fatura.getStatus() != StatusFatura.FECHADA && 
                fatura.getStatus() != StatusFatura.ATRASADA &&
                fatura.getStatus() != StatusFatura.ABERTA) {
                throw new BusinessException("Apenas faturas FECHADAS, ATRASADAS ou ABERTAS podem ser pagas.");
            }

            LocalDate dataPagamento = request.dataPagamento() != null ? request.dataPagamento() : dateProvider.now();

            // 5. Cálculo e Precisão Monetária (Ledger as Truth)
            BigDecimal valorFatura = fatura.getParcelas().stream()
                    .filter(p -> p.getTransacao() != null
                            && p.getTransacao().getDeletedAt() == null
                            && p.getTransacao().getStatus() != StatusTransacao.CANCELADO)
                    .map(Parcela::getValorParcela)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_EVEN);

            // Valida Saldo e Valor da Fatura
            if (valorFatura.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("Fatura sem lançamentos não pode ser paga.");
            }

            BigDecimal saldoAtual = contaRepository.calcularSaldo(contaPagadora.getId());
            if (saldoAtual == null) saldoAtual = BigDecimal.ZERO; // Fallback de segurança

            if (saldoAtual.compareTo(valorFatura) < 0) {
                throw new SaldoInsuficienteException(contaPagadora.getNome(), saldoAtual, valorFatura);
            }

            // 6. Registro Contábil (Atomic Ledger Entry)
            String descricaoSnapshot = String.format("Pagamento fatura %s - Ciclo %02d/%d", 
                    fatura.getCartao().getBandeira(), fatura.getMesReferencia(), fatura.getAnoReferencia());

            Transacao transacaoPagamento = Transacao.builder()
                    .descricao(descricaoSnapshot)
                    .valor(valorFatura)
                    .data(dataPagamento)
                    .dataPagamento(dataPagamento)
                    .tipo(TipoTransacao.DESPESA)
                    .status(StatusTransacao.PAGO)
                    .numeroParcelas(1)
                    .idempotencyKey(idempotencyKey)
                    .observacao("Snapshot: Valor original " + valorFatura)
                    .build();
            transacaoPagamento.setTenantId(fatura.getTenantId());

            // Lançamento CRÉDITO na conta do cartão
            Lancamento creditoCartao = Lancamento.builder()
                    .conta(fatura.getCartao().getConta())
                    .valor(valorFatura)
                    .direcao(DirecaoLancamento.CREDITO)
                    .descricao("Liquidação: " + descricaoSnapshot)
                    .build();
            transacaoPagamento.addLancamento(creditoCartao);

            // Lançamento DÉBITO na conta corrente
            Lancamento debitoContaCorrente = Lancamento.builder()
                    .conta(contaPagadora)
                    .valor(valorFatura)
                    .direcao(DirecaoLancamento.DEBITO)
                    .descricao("Débito: " + descricaoSnapshot)
                    .build();
            transacaoPagamento.addLancamento(debitoContaCorrente);

            transacaoRepository.save(transacaoPagamento);

            // 7. Consolidação
            fatura.setStatus(StatusFatura.PAGA);
            fatura.getParcelas().forEach(p -> p.setPaga(true));
            faturaRepository.save(fatura);

            updateIdempotencyStatus(control, IdempotencyStatus.SUCCESS);
            log.info("[Pagamento] Fatura paga com sucesso: id={} transacao={}", faturaId, transacaoPagamento.getId());

            return toFaturaResponse(fatura);

        } catch (PessimisticLockException e) {
            log.warn("[Pagamento] Falha de lock (concorrência) para fatura {}: {}", faturaId, e.getMessage());
            updateIdempotencyStatus(control, IdempotencyStatus.FAILED);
            throw new BusinessException("Sistema ocupado processando este pagamento. Tente novamente em alguns segundos.");
        } catch (DataIntegrityViolationException e) {
            log.error("[Pagamento] Violação de integridade (duplicate submission) para fatura {}: {}", faturaId, e.getMessage());
            updateIdempotencyStatus(control, IdempotencyStatus.SUCCESS);
            throw new BusinessException("Este pagamento já foi processado ou está em duplicidade.");
        } catch (Exception e) {
            log.error("[Pagamento] Erro inesperado ao pagar fatura {}: {}", faturaId, e.getMessage(), e);
            updateIdempotencyStatus(control, IdempotencyStatus.FAILED);
            throw e;
        }
    }

    private void updateIdempotencyStatus(IdempotencyControl control, IdempotencyStatus status) {
        control.setStatus(status);
        idempotencyRepository.save(control);
    }

    private String generateIdempotencyKey(Long faturaId, PagarFaturaRequest request) {
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            return request.idempotencyKey();
        }
        try {
            String raw = String.format("PAY_CC_%d_%d_%s", faturaId, request.contaId(), request.dataPagamento());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "PAY_CC_" + faturaId + "_" + System.currentTimeMillis();
        }
    }

    /**
     * Job diário: fecha faturas e marca atrasadas.
     */
    @Scheduled(cron = "0 30 6 * * *")
    @Transactional
    public void processarFaturas() {
        log.info("Processando faturas de cartão...");
        LocalDate hoje = dateProvider.now();

        List<FaturaCartao> faturasAtivas = faturaRepository.findByStatus(StatusFatura.ABERTA);
        for (FaturaCartao fatura : faturasAtivas) {
            if (fatura.getStatus() == StatusFatura.PAGA) continue; // Dupla proteção

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

    /**
     * Registra uma transação avulsa (como recorrência) no cartão.
     */
    @Transactional
    public void registrarTransacaoNoCartao(Transacao transacao, Long contaId) {
        CartaoCredito cartao = cartaoRepository.findByContaId(contaId)
                .orElseThrow(() -> new BusinessException("Cartão não encontrado para a conta id: " + contaId));

        // Criar lançamento DEBITO na conta do cartão
        Lancamento debito = Lancamento.builder()
                .conta(cartao.getConta())
                .valor(transacao.getValor())
                .direcao(DirecaoLancamento.DEBITO)
                .descricao(transacao.getDescricao())
                .build();
        transacao.addLancamento(debito);

        // Vincular à fatura (Respeitando Option A: Shift se PAGA)
        LocalDate dataVencimento = calcularDataVencimentoFatura(transacao.getData(), cartao.getDiaFechamento(), cartao.getDiaVencimento());
        FaturaCartao fatura = obterFaturaParaLancamento(cartao, dataVencimento.getMonthValue(), dataVencimento.getYear());

        Parcela parcela = Parcela.builder()
                .transacao(transacao)
                .numeroParcela(1)
                .totalParcelas(1)
                .valorParcela(transacao.getValor())
                .dataVencimento(fatura.getDataVencimento())
                .paga(false)
                .build();
        
        // Ensure Master transaction indicates it's a CC transaction with 1 installment
        transacao.setNumeroParcelas(1); 
        
        fatura.adicionarParcela(parcela);
        faturaRepository.save(fatura);
    }

    @Transactional
    public FaturaCartao obterFaturaParaLancamento(CartaoCredito cartao, int mes, int ano) {
        int limiteSeguranca = 24; // Máximo de 2 anos de shift
        int mesAtual = mes;
        int anoAtual = ano;

        while (limiteSeguranca-- > 0) {
            final int m = mesAtual;
            final int a = anoAtual;
            FaturaCartao fatura = faturaRepository
                    .findByCartaoIdAndMesReferenciaAndAnoReferencia(cartao.getId(), m, a)
                    .orElseGet(() -> criarFatura(cartao, m, a));

            if (fatura.getStatus() != StatusFatura.PAGA) {
                return fatura;
            }

            // Se está paga, pula para o próximo mês
            mesAtual++;
            if (mesAtual > 12) {
                mesAtual = 1;
                anoAtual++;
            }
        }
        throw new BusinessException("Não foi possível encontrar uma fatura disponível para lançamentos nos próximos 2 anos.");
    }

    private FaturaCartao criarFatura(CartaoCredito cartao, int mes, int ano) {
        // Garantimos que o dia de vencimento não ultrapasse o fim do mês
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
     * Engine de Ciclo de Faturamento Senior (4 passos):
     * 1. Determina o dia de fechamento real para o mês da compra.
     * 2. Shift de Ciclo: Se diaCompra > diaFechamento, cai no próximo ciclo.
     * 3. Define Vencimento Base: Baseado no mês do fechamento do ciclo.
     * 4. Ajuste de Vencimento Mensal: Se diaVencimento < diaFechamento, o vencimento é no mês seguinte ao fechamento.
     */
    public LocalDate calcularDataVencimentoFatura(LocalDate dataCompra, int diaFechamento, int diaVencimento) {
        // Passo 1 & 2: Determinar o fechamento do ciclo
        LocalDate fechamentoCiclo = dataCompra.withDayOfMonth(Math.min(diaFechamento, dataCompra.lengthOfMonth()));
        if (dataCompra.isAfter(fechamentoCiclo)) {
            fechamentoCiclo = fechamentoCiclo.plusMonths(1);
            fechamentoCiclo = fechamentoCiclo.withDayOfMonth(Math.min(diaFechamento, fechamentoCiclo.lengthOfMonth()));
        }

        // Passo 3: Vencimento baseado no mês do fechamento
        LocalDate vencimento = fechamentoCiclo.withDayOfMonth(Math.min(diaVencimento, fechamentoCiclo.lengthOfMonth()));

        // Passo 4: Regra fixa (vencimento posterior ao fechamento em um calendário civil)
        if (diaVencimento < diaFechamento) {
            vencimento = vencimento.plusMonths(1);
            vencimento = vencimento.withDayOfMonth(Math.min(diaVencimento, vencimento.lengthOfMonth()));
        }

        return vencimento;
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
        LocalDate hoje = dateProvider.now();
        
        // 1. Busca resumo financeiro em UMA query agregada (Alta Performance)
        CartaoCreditoResumoProjection resumo = parcelaRepository.getResumoFinanceiro(c.getId());
        
        BigDecimal utilizado = resumo.getTotalUtilizado();
        BigDecimal limiteTotal = c.getLimite();
        BigDecimal disponivel = limiteTotal.subtract(utilizado).max(BigDecimal.ZERO);
        
        double percentual = limiteTotal.compareTo(BigDecimal.ZERO) > 0
                ? utilizado.divide(limiteTotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        // 2. Cálculo de ciclo atual para "Valor da Fatura Aberta"
        LocalDate vencimentoAtual = calcularDataVencimentoFatura(hoje, c.getDiaFechamento(), c.getDiaVencimento());
        
        // 3. Melhor dia para compra (Dia seguinte ao fechamento)
        int melhorDia = c.getDiaFechamento() + 1;
        if (melhorDia > hoje.lengthOfMonth()) melhorDia = 1;

        // 4. Dias para fechar (Contagem regressiva inteligente)
        LocalDate fechamentoEsteMes = hoje.withDayOfMonth(Math.min(c.getDiaFechamento(), hoje.lengthOfMonth()));
        if (hoje.isAfter(fechamentoEsteMes)) {
            fechamentoEsteMes = fechamentoEsteMes.plusMonths(1);
            fechamentoEsteMes = fechamentoEsteMes.withDayOfMonth(Math.min(c.getDiaFechamento(), fechamentoEsteMes.lengthOfMonth()));
        }
        int diasParaFechar = (int) java.time.temporal.ChronoUnit.DAYS.between(hoje, fechamentoEsteMes);

        return new CartaoCreditoResponse(
                c.getId(), c.getConta().getId(), c.getConta().getNome(),
                c.getBandeira(), limiteTotal, utilizado, disponivel,
                resumo.getValorAberta(), resumo.getValorFechadas(), utilizado,
                melhorDia, diasParaFechar, vencimentoAtual,
                c.getCreatedAt());
    }

    private FaturaCartaoResponse toFaturaResponse(FaturaCartao f) {
        // Filtramos as parcelas cujas transações ainda existem (não foram deletadas)
        List<ParcelaResponse> parcelasRes = new ArrayList<>();
        BigDecimal totalReal = BigDecimal.ZERO;

        for (Parcela p : f.getParcelas()) {
            boolean ativa = true;
            String descricao = "Transação Excluída";
            try {
                if (p.getTransacao() != null && p.getTransacao().getDeletedAt() == null && p.getTransacao().getStatus() != StatusTransacao.CANCELADO) {
                    descricao = p.getTransacao().getDescricao();
                    totalReal = totalReal.add(p.getValorParcela());
                } else {
                    ativa = false;
                }
            } catch (EntityNotFoundException e) {
                ativa = false;
            }

            if (ativa) {
                parcelasRes.add(new ParcelaResponse(
                    p.getId(), p.getNumeroParcela(), p.getTotalParcelas(),
                    p.getValorParcela(), p.getDataVencimento().toString(), p.getPaga(),
                    descricao));
            }
        }

        // Lógica dinâmica para o status da fatura (Regra Temporal Fintech)
        StatusFatura statusCalculado = f.getStatus();
        if (statusCalculado != StatusFatura.PAGA) {
            LocalDate hoje = dateProvider.now();
            
            // Determina as datas críticas do ciclo desta fatura
            int diaFechamento = f.getCartao().getDiaFechamento();
            LocalDate dataVencimento = f.getDataVencimento();
            
            // O fechamento ocorre no mês de vencimento (venc >= fech) ou no mês anterior (venc < fech)
            LocalDate dataFechamento = dataVencimento.withDayOfMonth(Math.min(diaFechamento, dataVencimento.lengthOfMonth()));
            if (f.getCartao().getDiaVencimento() < diaFechamento) {
                dataFechamento = dataFechamento.minusMonths(1);
                dataFechamento = dataFechamento.withDayOfMonth(Math.min(diaFechamento, dataFechamento.lengthOfMonth()));
            }

            if (hoje.isAfter(dataVencimento)) {
                statusCalculado = StatusFatura.ATRASADA;
            } else if (hoje.isAfter(dataFechamento) || hoje.isEqual(dataFechamento)) {
                statusCalculado = StatusFatura.FECHADA;
            } else {
                statusCalculado = StatusFatura.ABERTA;
            }
        }

        return new FaturaCartaoResponse(
                f.getId(), f.getCartao().getId(), f.getCartao().getBandeira(),
                f.getMesReferencia(), f.getAnoReferencia(),
                totalReal, f.getDataVencimento().toString(), statusCalculado,
                parcelasRes, f.getCreatedAt());
    }
}
