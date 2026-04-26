package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.TransacaoRequest;
import com.gestao.financeiro.dto.response.LancamentoResponse;
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
import com.gestao.financeiro.repository.TransacaoRecorrenteRepository;
import com.gestao.financeiro.repository.TransacaoRepository;
import com.gestao.financeiro.repository.ParcelaRepository;
import com.gestao.financeiro.config.TenantContext;
import com.gestao.financeiro.entity.enums.OrigemLancamento;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TransacaoService {

    private final ContaRepository contaRepository;
    private final CategoriaRepository categoriaRepository;
    private final TransacaoRepository transacaoRepository;
    private final ParcelaRepository parcelaRepository;
    private final TransacaoRecorrenteRepository transacaoRecorrenteRepository;
    private final TransacaoMapper transacaoMapper;
    private final CartaoCreditoService cartaoCreditoService;



    public Page<LancamentoResponse> listar(
            LocalDate dataInicio, LocalDate dataFim,
            Long categoriaId, Long contaId,
            TipoTransacao tipo, TipoDespesa tipoDespesa,
            StatusTransacao status, Boolean geradoAutomaticamente,
            String busca,
            Pageable pageable) {

        Long tenantId = TenantContext.getTenantId();

        // 1. Fetch Standard Transactions (numeroParcelas <= 1)
        Page<Transacao> transacoesPage = transacaoRepository.buscarComFiltros(
                dataInicio, dataFim, categoriaId, contaId, tipo, tipoDespesa, status, geradoAutomaticamente, busca, pageable
        );

        // 2. Fetch Installments for the period (Impact-based)
        // Parcelas are filtered by dataVencimento so each installment appears in its due month.
        // May: teste (1/8), June: teste (2/8), July: teste (3/8) ...
        List<Parcela> parcelas = new ArrayList<>();
        if (tipo == null || tipo == TipoTransacao.DESPESA) {
             // Use more reasonable date boundaries for PostgreSQL
             LocalDate filterInicio = dataInicio != null ? dataInicio : LocalDate.of(1900, 1, 1);
             LocalDate filterFim = dataFim != null ? dataFim : LocalDate.of(2100, 12, 31);
             
             parcelas = parcelaRepository.findByTenantIdAndDataVencimentoBetween(
                tenantId, 
                filterInicio, 
                filterFim
            );
        }

        // 3. Projected Recurring Transactions (Assinaturas)
        // We project occurrences for the filtered period that haven't been materialized yet.
        List<LancamentoResponse> projecoes = new ArrayList<>();
        if (geradoAutomaticamente == null || geradoAutomaticamente) {
            LocalDate filterInicio = dataInicio != null ? dataInicio : LocalDate.now().withDayOfMonth(1);
            LocalDate filterFim = dataFim != null ? dataFim : LocalDate.now().plusMonths(3).withDayOfMonth(1).minusDays(1);
            
            YearMonth startMonth = YearMonth.from(filterInicio);
            YearMonth endMonth = YearMonth.from(filterFim);
            
            List<TransacaoRecorrente> recurrences = transacaoRecorrenteRepository.findByAtivaTrueAndTenantId(tenantId);
            
            for (TransacaoRecorrente rec : recurrences) {
                // Apply basic filters
                if (tipo != null && rec.getTipo() != tipo) continue;
                if (categoriaId != null && (rec.getCategoria() == null || !rec.getCategoria().getId().equals(categoriaId))) continue;
                if (busca != null && !rec.getDescricao().toLowerCase().contains(busca.toLowerCase())) continue;
                
                // Iterate through months in range
                YearMonth current = startMonth;
                while (!current.isAfter(endMonth)) {
                    LocalDate occurrenceDate = current.atDay(Math.min(rec.getDiaVencimento() != null ? rec.getDiaVencimento() : rec.getDataInicio().getDayOfMonth(), current.lengthOfMonth()));
                    
                    if (rec.isAtivaEm(occurrenceDate)) {
                        // Check if already materialized
                        boolean exists = transacaoRepository.existsByRecorrenciaIdAndReferenciaIgnoreSoftDelete(rec.getId(), current.toString());
                        if (!exists) {
                            projecoes.add(new LancamentoResponse(
                                null, // Virtual
                                rec.getDescricao() + " (Previsto)",
                                rec.getValor(),
                                occurrenceDate,
                                rec.getTipo(),
                                null, null,
                                OrigemLancamento.RECORRENCIA_PROJETADA,
                                rec.getCategoria() != null ? rec.getCategoria().getNome() : null,
                                rec.getCategoria() != null ? rec.getCategoria().getId() : null,
                                rec.getConta() != null ? rec.getConta().getNome() : null,
                                rec.getConta() != null ? rec.getConta().getId() : null,
                                null,
                                StatusTransacao.PENDENTE,
                                null,
                                true,
                                null,
                                rec.getValor(),
                                null,
                                occurrenceDate,
                                rec.getId()
                            ));
                        }
                    }
                    current = current.plusMonths(1);
                }
            }
        }

        // 3. Map to Ledger Response
        Stream<LancamentoResponse> standardStream = transacoesPage.getContent().stream()
                .map(transacaoMapper::toLedgerResponse);

        Stream<LancamentoResponse> installmentStream = parcelas.stream()
                .filter(p -> {
                    // Apply filters manually to installments if they were fetched avulsos
                    if (p.getTransacao() == null) return false;
                    
                    if (categoriaId != null) {
                        if (p.getTransacao().getCategoria() == null) return false;
                        if (!categoriaId.equals(p.getTransacao().getCategoria().getId())) return false;
                    }
                    
                    if (busca != null) {
                        String desc = p.getTransacao().getDescricao();
                        if (desc == null || !desc.toLowerCase().contains(busca.toLowerCase())) return false;
                    }
                    return true;
                })
                .map(transacaoMapper::toLedgerResponse);

        // 4. Merge, Sort by dataReferencia (Impact Date) and Paginate in memory
        List<LancamentoResponse> combined = Stream.concat(
                    Stream.concat(standardStream, installmentStream),
                    projecoes.stream()
                )
                .sorted(Comparator.comparing(LancamentoResponse::dataReferencia))
                .toList();
        
        // Proper in-memory pagination logic
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), combined.size());
        
        List<LancamentoResponse> paginatedList = new ArrayList<>();
        if (start < combined.size()) {
            paginatedList = combined.subList(start, end);
        }

        return new PageImpl<>(paginatedList, pageable, combined.size());
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
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("Tenant ID não encontrado no contexto");
        }

        // Idempotência
        if (request.idempotencyKey() != null) {
            var existente = transacaoRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existente.isPresent()) {
                log.info("[tenant={}] Transação idempotente retornada: key={}", tenantId, request.idempotencyKey());
                return transacaoMapper.toResponse(existente.get());
            }
        }

        // Validações
        Long origemId = Objects.requireNonNull(request.contaOrigemId(), "ID da conta de origem não pode ser nulo");
        Conta contaOrigem = contaRepository.findById(origemId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta de origem", origemId));

        Categoria categoria = null;
        if (request.categoriaId() != null) {
            Long catId = request.categoriaId();
            categoria = categoriaRepository.findById(catId)
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria", catId));
        }

        if (request.tipo() == TipoTransacao.TRANSFERENCIA && request.contaDestinoId() == null) {
            throw new BusinessException("Transferência requer conta de destino.");
        }

        if (request.tipo() == TipoTransacao.TRANSFERENCIA
                && request.contaOrigemId().equals(request.contaDestinoId())) {
            throw new BusinessException("Conta de origem e destino não podem ser iguais.");
        }

        // Determina status inicial
        StatusTransacao statusInicial = request.status();
        if (statusInicial == null) {
            // Default: PAGO se a data for hoje ou passada, E não for cartão de crédito
            boolean isCartao = contaOrigem.getTipo() == com.gestao.financeiro.entity.enums.TipoConta.CARTAO_CREDITO;
            boolean isFutura = request.data().isAfter(LocalDate.now());
            
            statusInicial = (isCartao || isFutura) ? StatusTransacao.PENDENTE : StatusTransacao.PAGO;
        }

        // Monta transação
        Transacao transacao = Transacao.builder()
                .descricao(request.descricao())
                .valor(request.valor())
                .data(request.data())
                .dataVencimento(request.dataVencimento())
                .tipo(request.tipo())
                .tipoDespesa(request.tipoDespesa())
                .status(statusInicial)
                .observacao(request.observacao())
                .idempotencyKey(request.idempotencyKey())
                .geradoAutomaticamente(request.geradoAutomaticamente() != null && request.geradoAutomaticamente())
                .recorrenciaId(request.recorrenciaId())
                .referencia(request.referencia())
                .categoria(categoria)
                .valorPrevisto(request.valorPrevisto())
                .build();
        transacao.setTenantId(tenantId);

        // Salva a transação inicialmente para garantir que tenha um ID 
        // caso precise ser referenciada por outras entidades (ex: Parcelas de Cartão)
        transacao = transacaoRepository.saveAndFlush(transacao);

        // Gera lançamentos contábeis
        if (contaOrigem.getTipo() == com.gestao.financeiro.entity.enums.TipoConta.CARTAO_CREDITO 
                && request.tipo() == TipoTransacao.DESPESA) {
            // Se for cartão, o serviço de cartão lida com Lancamento + Fatura + Parcela
            cartaoCreditoService.registrarTransacaoNoCartao(transacao, contaOrigem.getId());
        } else {
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
                    Long destinoId = Objects.requireNonNull(request.contaDestinoId(), "ID da conta de destino não pode ser nulo");
                    Conta contaDestino = contaRepository.findById(destinoId)
                            .orElseThrow(() -> new ResourceNotFoundException("Conta de destino", destinoId));

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
        }

        transacao = transacaoRepository.save(transacao);
        log.info("[tenant={}] Transação criada: id={} tipo={} valor={} lancamentos={}",
                tenantId, transacao.getId(), transacao.getTipo(), transacao.getValor(),
                transacao.getLancamentos().size());

        return transacaoMapper.toResponse(transacao);
    }

    /**
     * Atualiza uma transação existente preservando o valorPrevisto original se houver.
     */
    @Transactional
    public TransacaoResponse atualizar(Long id, TransacaoRequest request) {
        Transacao transacao = findById(id);

        if (transacao.getStatus() == StatusTransacao.PAGO || transacao.getStatus() == StatusTransacao.CANCELADO) {
            throw new BusinessException("Não é possível editar uma transação paga ou cancelada.");
        }

        // Atualiza campos permitidos
        transacao.setDescricao(request.descricao());
        transacao.setValor(request.valor());
        transacao.setData(request.data());
        transacao.setDataVencimento(request.dataVencimento());
        transacao.setObservacao(request.observacao());
        
        // Atualiza categoria se fornecida
        if (request.categoriaId() != null) {
            Categoria categoria = categoriaRepository.findById(request.categoriaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria", request.categoriaId()));
            transacao.setCategoria(categoria);
        } else {
            transacao.setCategoria(null);
        }

        // Atualiza conta se fornecida e diferente
        Conta novaConta = null;
        if (request.contaOrigemId() != null) {
            novaConta = contaRepository.findById(request.contaOrigemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Conta", request.contaOrigemId()));
            
            for (Lancamento l : transacao.getLancamentos()) {
                l.setConta(novaConta);
            }
        }

        // Atualiza valor nos lançamentos existentes
        transacao.getLancamentos().forEach(l -> {
             l.setValor(request.valor());
             l.setDescricao((l.getDirecao() == com.gestao.financeiro.entity.enums.DirecaoLancamento.DEBITO ? "Despesa: " : "Receita: ") + request.descricao());
        });

        transacao = transacaoRepository.save(transacao);
        return transacaoMapper.toResponse(transacao);
    }

    /**
     * Marcar transação como paga, permitindo ajuste de valor e conta.
     */
    @Transactional
    public TransacaoResponse pagar(Long id, java.math.BigDecimal novoValor, Long novaContaId, LocalDate dataPagamento) {
        Transacao transacao = findById(id);

        if (transacao.getStatus() == StatusTransacao.PAGO) {
            throw new BusinessException("Transação já está paga.");
        }
        if (transacao.getStatus() == StatusTransacao.CANCELADO) {
            throw new BusinessException("Não é possível pagar transação cancelada.");
        }

        // Ajusta valor se fornecido
        if (novoValor != null) {
            transacao.setValor(novoValor);
            transacao.getLancamentos().forEach(l -> l.setValor(novoValor));
        }

        // Ajusta conta se fornecida
        if (novaContaId != null) {
            Conta conta = contaRepository.findById(novaContaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conta", novaContaId));
            
            // Para Despesas/Receitas, atualiza a conta do lançamento
            // Para Transferências, assumimos que a nova conta é a conta de ORIGEM
            for (Lancamento l : transacao.getLancamentos()) {
                if (transacao.getTipo() == TipoTransacao.TRANSFERENCIA) {
                    if (l.getDirecao() == DirecaoLancamento.DEBITO) {
                        l.setConta(conta);
                    }
                } else {
                    l.setConta(conta);
                }
            }
        }

        transacao.setStatus(StatusTransacao.PAGO);
        transacao.setDataPagamento(dataPagamento != null ? dataPagamento : LocalDate.now());
        
        // Se a data do lançamento for diferente da data do pagamento e o usuário ajustou,
        // podemos opcionalmente atualizar a data do lançamento também. 
        // Mas por agora vamos apenas atualizar a data da transação se for fornecida.
        if (dataPagamento != null) {
            transacao.setData(dataPagamento);
        }

        transacao = transacaoRepository.save(transacao);
        log.info("[tenant={}] Transação paga com ajuste: id={} valor={} contaId={}", 
                transacao.getTenantId(), id, transacao.getValor(), novaContaId);

        return transacaoMapper.toResponse(transacao);
    }

    /**
     * Marcar transação como paga (sem ajustes).
     */
    @Transactional
    public TransacaoResponse pagar(Long id) {
        return pagar(id, null, null, null);
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
        com.gestao.financeiro.util.ValidationUtils.validateId(id, "Transação");
        return transacaoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transação", id));
    }
}
