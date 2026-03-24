package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.DividaRequest;
import com.gestao.financeiro.dto.request.PagarParcelaDividaRequest;
import com.gestao.financeiro.dto.response.DividaResponse;
import com.gestao.financeiro.dto.response.ParcelaDividaResponse;
import com.gestao.financeiro.entity.*;
import com.gestao.financeiro.entity.enums.*;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.exception.ResourceNotFoundException;
import com.gestao.financeiro.mapper.DividaMapper;
import com.gestao.financeiro.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class DividaService {

    private final DividaRepository dividaRepository;
    private final ParcelaDividaRepository parcelaRepository;
    private final PessoaRepository pessoaRepository;
    private final DividaMapper dividaMapper;
    private final TransacaoService transacaoService;
    private final ContaRepository contaRepository;
    private final CategoriaRepository categoriaRepository;

    private static final Long DEFAULT_TENANT_ID = 1L;

    public Page<DividaResponse> listar(TipoDivida tipo, Pageable pageable) {
        if (tipo != null) {
            return dividaRepository.findByTipo(tipo, pageable).map(dividaMapper::toResponse);
        }
        return dividaRepository.findAll(pageable).map(dividaMapper::toResponse);
    }

    public DividaResponse buscarPorId(Long id) {
        return dividaMapper.toResponse(findById(id));
    }

    @Transactional
    public DividaResponse criar(DividaRequest request) {
        Pessoa pessoa = pessoaRepository.findById(request.pessoaId())
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", request.pessoaId()));

        Divida divida = dividaMapper.toEntity(request);
        divida.setTenantId(DEFAULT_TENANT_ID);
        divida.setPessoa(pessoa);

        pessoa.setTotalEmprestimos(pessoa.getTotalEmprestimos() + 1);
        pessoaRepository.save(pessoa);

        // Gera as parcelas
        BigDecimal valorParcela = request.valorTotal()
                .divide(BigDecimal.valueOf(request.parcelas()), 2, RoundingMode.HALF_UP);

        LocalDate dataVencimentoBase = request.dataInicio();

        for (int i = 0; i < request.parcelas(); i++) {
            BigDecimal valorEstaParcela = valorParcela;
            
            // Corrige arredondamento na última parcela
            if (i == request.parcelas() - 1) {
                BigDecimal jaDistribuido = valorParcela.multiply(BigDecimal.valueOf(request.parcelas() - 1));
                valorEstaParcela = request.valorTotal().subtract(jaDistribuido);
            }

            LocalDate vencimento = dataVencimentoBase.plusMonths(i);

            ParcelaDivida parcela = ParcelaDivida.builder()
                    .numeroParcela(i + 1)
                    .valor(valorEstaParcela)
                    .dataVencimento(vencimento)
                    .build();
            divida.adicionarParcela(parcela);
        }

        divida = dividaRepository.save(divida);
        log.info("[tenant={}] Dívida criada: id={} pessoa={} valor={}", DEFAULT_TENANT_ID, divida.getId(), pessoa.getNome(), request.valorTotal());

        return dividaMapper.toResponse(divida);
    }

    @Transactional
    public ParcelaDividaResponse pagarParcela(Long parcelaId, PagarParcelaDividaRequest request) {
        ParcelaDivida parcela = parcelaRepository.findById(parcelaId)
                .orElseThrow(() -> new ResourceNotFoundException("Parcela da Dívida", parcelaId));

        if (parcela.getStatus() == StatusTransacao.PAGO) {
            throw new BusinessException("Esta parcela já foi paga.");
        }

        Divida divida = parcela.getDivida();
        Pessoa pessoa = divida.getPessoa();

        Conta conta = contaRepository.findById(request.contaId())
                .orElseThrow(() -> new ResourceNotFoundException("Conta", request.contaId()));

        Categoria categoria = null;
        if (request.categoriaId() != null) {
            categoria = categoriaRepository.findById(request.categoriaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria", request.categoriaId()));
        }

        LocalDate dataPag = request.dataPagamento() != null ? request.dataPagamento() : LocalDate.now();

        BigDecimal valorPago = request.valorPago() != null ? request.valorPago() : parcela.getValor();
        if (valorPago.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Valor pago deve ser maior que zero.");
        }
        if (valorPago.compareTo(parcela.getValor()) > 0) {
            throw new BusinessException("O valor pago não pode ser maior que o saldo atual da parcela.");
        }

        // 1. Verificar se é parcial
        ParcelaDivida novaParcelaPendente = null;
        if (valorPago.compareTo(parcela.getValor()) < 0) {
            BigDecimal saldoRestante = parcela.getValor().subtract(valorPago);
            
            // Reduz o valor da parcela atual para o que foi pago
            parcela.setValor(valorPago);
            
            // Cria uma nova parcela projetando o saldo devedor restante
            novaParcelaPendente = ParcelaDivida.builder()
                    .divida(divida)
                    .numeroParcela(parcela.getNumeroParcela()) 
                    .valor(saldoRestante)
                    .dataVencimento(parcela.getDataVencimento())
                    .status(StatusTransacao.PENDENTE)
                    .build();
        }

        // 2. Atualizar Parcela Atual
        parcela.setStatus(StatusTransacao.PAGO);
        parcela.setDataPagamento(dataPag);

        // 3. Transação para Ledger
        TipoTransacao tipoTx = divida.getTipo() == TipoDivida.A_RECEBER ? TipoTransacao.RECEITA : TipoTransacao.DESPESA;
        String desc = (tipoTx == TipoTransacao.RECEITA ? "Recebimento: " : "Pagamento: ") + divida.getDescricao() + " (Parcela " + parcela.getNumeroParcela() + ")";

        com.gestao.financeiro.dto.request.TransacaoRequest txRequest = new com.gestao.financeiro.dto.request.TransacaoRequest(
                desc,
                valorPago,
                dataPag,
                parcela.getDataVencimento(),
                tipoTx,
                null, // tipoDespesa
                categoria != null ? categoria.getId() : null,
                conta.getId(),
                null, // contaDestinoId
                "Pagamento da parcela " + parcela.getNumeroParcela() + " da dívida ID " + divida.getId(),
                null // idempotencyKey
        );

        com.gestao.financeiro.dto.response.TransacaoResponse txResponse = transacaoService.criar(txRequest);
        
        // Em seguida marcamos o vínculo manualmente, pois service wrapper retorna DTO
        // É melhor setar na própria transação que já foi gerada no transacaoService
        Transacao transacao = new Transacao();
        transacao.setId(txResponse.id());
        parcela.setTransacaoGerada(transacao); 
        
        // 4. Atualizar Pessoa Score
        boolean pagoNoPrazo = !dataPag.isAfter(parcela.getDataVencimento());
        pessoa.registrarPagamento(pagoNoPrazo);
        pessoaRepository.save(pessoa);

        // 5. Abater Dívida (saldoRestante)
        divida.abaterValor(valorPago);
        
        if (novaParcelaPendente != null) {
            divida.adicionarParcela(novaParcelaPendente);
        }
        
        dividaRepository.save(divida);
        parcela = parcelaRepository.save(parcela);
        
        // Refresh to get actual generated transacao mapping
        return dividaMapper.toParcelaResponse(parcela);
    }

    @Transactional
    public void deletar(Long id) {
        Divida divida = findById(id);
        divida.softDelete();
        dividaRepository.save(divida);
        log.info("[tenant={}] Dívida deletada: id={}", divida.getTenantId(), id);
    }

    private Divida findById(Long id) {
        return dividaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dívida", id));
    }
}
