package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.TransacaoRequest;
import com.gestao.financeiro.dto.response.TransacaoResponse;
import com.gestao.financeiro.entity.*;
import com.gestao.financeiro.entity.enums.DirecaoLancamento;
import com.gestao.financeiro.entity.enums.StatusTransacao;
import com.gestao.financeiro.entity.enums.TipoTransacao;
import com.gestao.financeiro.entity.enums.TipoDespesa;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.exception.ResourceNotFoundException;
import com.gestao.financeiro.mapper.TransacaoMapper;
import com.gestao.financeiro.repository.ContaRepository;
import com.gestao.financeiro.repository.CategoriaRepository;
import com.gestao.financeiro.repository.TransacaoRepository;
import com.gestao.financeiro.repository.ParcelaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TransacaoService {

    private final ContaRepository contaRepository;
    private final CategoriaRepository categoriaRepository;
    private final TransacaoRepository transacaoRepository;
    private final ParcelaRepository parcelaRepository;
    private final TransacaoMapper transacaoMapper;

    private static final Long DEFAULT_TENANT_ID = 1L;

    public Page<TransacaoResponse> listar(
            LocalDate dataInicio, LocalDate dataFim,
            Long categoriaId, Long contaId,
            TipoTransacao tipo, TipoDespesa tipoDespesa,
            StatusTransacao status, Boolean geradoAutomaticamente,
            String busca,
            Pageable pageable) {

        return transacaoRepository.buscarComFiltros(
                dataInicio, dataFim, categoriaId, contaId, tipo, tipoDespesa, status, geradoAutomaticamente, busca, pageable
        ).map(transacaoMapper::toResponse);
    }

    public TransacaoResponse buscarPorId(Long id) {
        return transacaoMapper.toResponse(findById(id));
    }

    /**
     * Cria transação com lançamentos contábeis (double-entry).
     *
     * DESPESA: DEBITO na conta origem
     * RECEITA: CREDITO na conta origem
     * TRANSFERENCIA: DEBITO na origem + CREDITO no destino
     */
    @Transactional
    public TransacaoResponse criar(TransacaoRequest request) {
        // Idempotência
        if (request.idempotencyKey() != null) {
            var existente = transacaoRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existente.isPresent()) {
                log.info("[tenant={}] Transação idempotente retornada: key={}", DEFAULT_TENANT_ID, request.idempotencyKey());
                return transacaoMapper.toResponse(existente.get());
            }
        }

        // Validações
        Conta contaOrigem = contaRepository.findById(request.contaOrigemId())
                .orElseThrow(() -> new ResourceNotFoundException("Conta de origem", request.contaOrigemId()));

        Categoria categoria = null;
        if (request.categoriaId() != null) {
            categoria = categoriaRepository.findById(request.categoriaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria", request.categoriaId()));
        }

        if (request.tipo() == TipoTransacao.TRANSFERENCIA && request.contaDestinoId() == null) {
            throw new BusinessException("Transferência requer conta de destino.");
        }

        if (request.tipo() == TipoTransacao.TRANSFERENCIA
                && request.contaOrigemId().equals(request.contaDestinoId())) {
            throw new BusinessException("Conta de origem e destino não podem ser iguais.");
        }

        // Monta transação
        Transacao transacao = Transacao.builder()
                .descricao(request.descricao())
                .valor(request.valor())
                .data(request.data())
                .dataVencimento(request.dataVencimento())
                .tipo(request.tipo())
                .tipoDespesa(request.tipoDespesa())
                .status(StatusTransacao.PENDENTE)
                .observacao(request.observacao())
                .idempotencyKey(request.idempotencyKey())
                .geradoAutomaticamente(request.geradoAutomaticamente() != null && request.geradoAutomaticamente())
                .recorrenciaId(request.recorrenciaId())
                .categoria(categoria)
                .build();
        transacao.setTenantId(DEFAULT_TENANT_ID);

        // Gera lançamentos contábeis
        switch (request.tipo()) {
            case DESPESA -> {
                Lancamento debito = Lancamento.builder()
                        .conta(contaOrigem)
                        .valor(request.valor())
                        .direcao(DirecaoLancamento.DEBITO)
                        .descricao("Despesa: " + request.descricao())
                        .build();
                transacao.addLancamento(debito);
            }
            case RECEITA -> {
                Lancamento credito = Lancamento.builder()
                        .conta(contaOrigem)
                        .valor(request.valor())
                        .direcao(DirecaoLancamento.CREDITO)
                        .descricao("Receita: " + request.descricao())
                        .build();
                transacao.addLancamento(credito);
            }
            case TRANSFERENCIA -> {
                Conta contaDestino = contaRepository.findById(request.contaDestinoId())
                        .orElseThrow(() -> new ResourceNotFoundException("Conta de destino", request.contaDestinoId()));

                Lancamento debito = Lancamento.builder()
                        .conta(contaOrigem)
                        .valor(request.valor())
                        .direcao(DirecaoLancamento.DEBITO)
                        .descricao("Transferência para " + contaDestino.getNome())
                        .build();

                Lancamento credito = Lancamento.builder()
                        .conta(contaDestino)
                        .valor(request.valor())
                        .direcao(DirecaoLancamento.CREDITO)
                        .descricao("Transferência de " + contaOrigem.getNome())
                        .build();

                transacao.addLancamento(debito);
                transacao.addLancamento(credito);
            }
        }

        transacao = transacaoRepository.save(transacao);
        log.info("[tenant={}] Transação criada: id={} tipo={} valor={} lancamentos={}",
                DEFAULT_TENANT_ID, transacao.getId(), transacao.getTipo(), transacao.getValor(),
                transacao.getLancamentos().size());

        return transacaoMapper.toResponse(transacao);
    }

    /**
     * Marcar transação como paga.
     */
    @Transactional
    public TransacaoResponse pagar(Long id) {
        Transacao transacao = findById(id);

        if (transacao.getStatus() == StatusTransacao.PAGO) {
            throw new BusinessException("Transação já está paga.");
        }
        if (transacao.getStatus() == StatusTransacao.CANCELADO) {
            throw new BusinessException("Não é possível pagar transação cancelada.");
        }

        transacao.setStatus(StatusTransacao.PAGO);
        transacao.setDataPagamento(LocalDate.now());

        transacao = transacaoRepository.save(transacao);
        log.info("[tenant={}] Transação paga: id={}", transacao.getTenantId(), id);

        return transacaoMapper.toResponse(transacao);
    }

    /**
     * Cancelar transação — cria lançamentos de estorno (inversos).
     */
    @Transactional
    public TransacaoResponse cancelar(Long id) {
        Transacao transacao = findById(id);

        if (transacao.getStatus() == StatusTransacao.CANCELADO) {
            throw new BusinessException("Transação já está cancelada.");
        }

        transacao.setStatus(StatusTransacao.CANCELADO);

        // Cria transação de estorno com lançamentos inversos
        Transacao estorno = Transacao.builder()
                .descricao("ESTORNO: " + transacao.getDescricao())
                .valor(transacao.getValor())
                .data(LocalDate.now())
                .tipo(transacao.getTipo())
                .status(StatusTransacao.PAGO)
                .categoria(transacao.getCategoria())
                .observacao("Estorno automático da transação #" + id)
                .build();
        estorno.setTenantId(transacao.getTenantId());

        // Inverte direções dos lançamentos
        for (Lancamento original : transacao.getLancamentos()) {
            DirecaoLancamento direcaoInvertida = original.getDirecao() == DirecaoLancamento.DEBITO
                    ? DirecaoLancamento.CREDITO
                    : DirecaoLancamento.DEBITO;

            Lancamento lancamentoEstorno = Lancamento.builder()
                    .conta(original.getConta())
                    .valor(original.getValor())
                    .direcao(direcaoInvertida)
                    .descricao("Estorno: " + original.getDescricao())
                    .build();
            estorno.addLancamento(lancamentoEstorno);
        }

        // Remove parcelas se for cancelamento de compra no cartão
        var parcelas = parcelaRepository.findByTransacaoId(id);
        if (!parcelas.isEmpty()) {
            parcelaRepository.deleteAll(parcelas);
            log.info("[tenant={}] {} parcelas de cartão removidas devido a cancelamento da transação #{}", 
                transacao.getTenantId(), parcelas.size(), id);
        }

        transacaoRepository.save(transacao);
        transacaoRepository.save(estorno);

        log.info("[tenant={}] Transação cancelada com estorno: id={} estornoId={}",
                transacao.getTenantId(), id, estorno.getId());

        return transacaoMapper.toResponse(transacao);
    }

    @Transactional
    public void deletar(Long id) {
        Transacao transacao = findById(id);
        
        // Remove parcelas vinculadas se for compra de cartão
        var parcelas = parcelaRepository.findByTransacaoId(id);
        if (!parcelas.isEmpty()) {
            parcelaRepository.deleteAll(parcelas);
            log.info("[tenant={}] {} parcelas de cartão removidas devido a exclusão da transação #{}", 
                transacao.getTenantId(), parcelas.size(), id);
        }

        transacao.softDelete();
        transacaoRepository.save(transacao);
        log.info("[tenant={}] Transação soft-deleted: id={}", transacao.getTenantId(), id);
    }

    @Transactional
    public TransacaoResponse tornarManual(Long id) {
        Transacao t = findById(id);
        if (!t.getGeradoAutomaticamente()) {
            throw new BusinessException("Transação já é manual.");
        }
        
        t.setGeradoAutomaticamente(false);
        t.setRecorrenciaId(null);
        t.setReferencia(null);
        
        return transacaoMapper.toResponse(transacaoRepository.save(t));
    }

    private Transacao findById(Long id) {
        return transacaoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transação", id));
    }
}
