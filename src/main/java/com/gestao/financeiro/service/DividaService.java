package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.DividaRequest;
import com.gestao.financeiro.dto.request.PagarParcelaDividaRequest;
import com.gestao.financeiro.dto.response.DividaResponse;
import com.gestao.financeiro.dto.response.DividasResumoResponse;
import com.gestao.financeiro.dto.response.ParcelaDividaResponse;
import com.gestao.financeiro.entity.*;
import com.gestao.financeiro.entity.enums.*;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.exception.ResourceNotFoundException;
import com.gestao.financeiro.mapper.DividaMapper;
import com.gestao.financeiro.repository.*;
import com.gestao.financeiro.config.TenantContext;
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
import java.time.YearMonth;
import java.util.List;

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
    private final TransacaoRepository transacaoRepository;



    public DividasResumoResponse listar(TipoDivida tipo, Long pessoaId, Integer ano, Integer mes, StatusDivida status, Pageable pageable) {
        String statusStr = status != null ? status.name() : null;
        
        Page<Divida> page = dividaRepository.buscarComFiltros(tipo, pessoaId, ano, mes, statusStr, pageable);
        
        BigDecimal totalGeral;
        if (ano != null) {
            totalGeral = dividaRepository.somarParcelasNoMes(tipo, pessoaId, ano, mes, statusStr);
        } else {
            totalGeral = dividaRepository.somarValorRestante(tipo, pessoaId, statusStr);
        }

        if (totalGeral == null) totalGeral = BigDecimal.ZERO;

        return new DividasResumoResponse(
                page.getContent().stream().map(d -> dividaMapper.toResponse(d, ano, mes, status)).toList(),
                totalGeral,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber()
        );
    }

    public DividasResumoResponse listarTodos(TipoDivida tipo, Long pessoaId, Integer ano, Integer mes, StatusDivida status) {
        String statusStr = status != null ? status.name() : null;

        java.util.List<Divida> dividas = dividaRepository.buscarComFiltrosSemPaginacao(tipo, pessoaId, ano, mes, statusStr);

        BigDecimal totalGeral;
        if (ano != null) {
            totalGeral = dividaRepository.somarParcelasNoMes(tipo, pessoaId, ano, mes, statusStr);
        } else {
            totalGeral = dividaRepository.somarValorRestante(tipo, pessoaId, statusStr);
        }
        if (totalGeral == null) totalGeral = BigDecimal.ZERO;

        return new DividasResumoResponse(
                dividas.stream().map(d -> dividaMapper.toResponse(d, ano, mes, status)).toList(),
                totalGeral,
                dividas.size(),
                1,
                0
        );
    }

    public DividaResponse buscarPorId(Long id) {
        return dividaMapper.toResponse(findById(id));
    }

    @Transactional
    public DividaResponse criar(DividaRequest request) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("Tenant ID não encontrado no contexto");
        }

        Pessoa pessoa = pessoaRepository.findById(request.pessoaId())
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", request.pessoaId()));

        Divida divida = dividaMapper.toEntity(request);
        divida.setTenantId(tenantId);
        divida.setPessoa(pessoa);

        pessoa.setTotalEmprestimos(pessoa.getTotalEmprestimos() + 1);
        pessoaRepository.save(pessoa);

        boolean isRecorrente = Boolean.TRUE.equals(request.recorrente());

        if (isRecorrente) {
            // Dívida recorrente: gera primeira parcela se data de início já passou ou é hoje
            BigDecimal valorParcela = request.valorParcelaRecorrente() != null
                    ? request.valorParcelaRecorrente()
                    : request.valorTotal();

            divida.setValorTotal(valorParcela);
            divida.setValorRestante(valorParcela);

            LocalDate hoje = LocalDate.now();
            if (!request.dataInicio().isAfter(hoje)) {
                int dia = request.diaVencimento() != null
                        ? Math.min(request.diaVencimento(), request.dataInicio().lengthOfMonth())
                        : request.dataInicio().getDayOfMonth();
                LocalDate vencimento = request.dataInicio().withDayOfMonth(dia);

                ParcelaDivida parcela = ParcelaDivida.builder()
                        .numeroParcela(1)
                        .valor(valorParcela)
                        .dataVencimento(vencimento)
                        .build();
                divida.adicionarParcela(parcela);
            }
        } else {
            // Dívida parcelada tradicional
            int numParcelas = request.parcelas() != null ? request.parcelas() : 1;
            BigDecimal valorParcela = request.valorTotal()
                    .divide(BigDecimal.valueOf(numParcelas), 2, RoundingMode.HALF_UP);

            LocalDate dataVencimentoBase = request.dataInicio();

            for (int i = 0; i < numParcelas; i++) {
                BigDecimal valorEstaParcela = valorParcela;

                if (i == numParcelas - 1) {
                    BigDecimal jaDistribuido = valorParcela.multiply(BigDecimal.valueOf(numParcelas - 1));
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
        }

        divida = dividaRepository.save(divida);
        log.info("[tenant={}] Dívida criada: id={} pessoa={} valor={} recorrente={}",
                tenantId, divida.getId(), pessoa.getNome(), request.valorTotal(), isRecorrente);

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
                null, // idempotencyKey
                false, // geradoAutomaticamente
                null, // recorrenciaId
                StatusTransacao.PAGO
        );

        com.gestao.financeiro.dto.response.TransacaoResponse txResponse = transacaoService.criar(txRequest);
        
        // Em seguida marcamos o vínculo manualmente, pois service wrapper retorna DTO
        // Utilizamos getReferenceById para evitar carregar a entidade inteira mas garantir o vínculo JPA correto
        parcela.setTransacaoGerada(transacaoRepository.getReferenceById(txResponse.id()));
        
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
    public DividaResponse cancelarRecorrencia(Long id) {
        Divida divida = findById(id);
        if (!Boolean.TRUE.equals(divida.getRecorrente())) {
            throw new BusinessException("Esta dívida não é recorrente.");
        }
        divida.setRecorrente(false);
        dividaRepository.save(divida);
        log.info("[tenant={}] Recorrência cancelada para dívida id={}", divida.getTenantId(), id);
        return dividaMapper.toResponse(divida);
    }

    @Transactional
    public void deletar(Long id) {
        Divida divida = findById(id);
        divida.softDelete();
        dividaRepository.save(divida);
        log.info("[tenant={}] Dívida deletada: id={}", divida.getTenantId(), id);
    }

    // ─── Scheduler de Dívidas Recorrentes ────────────────────────

    /**
     * Roda todo dia às 06:05 — gera parcelas para dívidas recorrentes ativas.
     */
    @Scheduled(cron = "0 5 6 * * *")
    @Transactional
    public void processarDividasRecorrentes() {
        log.info("Iniciando processamento de dívidas recorrentes...");
        LocalDate hoje = LocalDate.now();
        YearMonth mesAtual = YearMonth.from(hoje);

        List<Divida> recorrentes = dividaRepository.findByRecorrenteTrue();
        int geradas = 0;

        for (Divida divida : recorrentes) {
            // Verificar se passou da data fim
            if (divida.getDataFim() != null && hoje.isAfter(divida.getDataFim())) {
                divida.setRecorrente(false);
                dividaRepository.save(divida);
                log.info("[tenant={}] Dívida recorrente id={} encerrada (data fim atingida)", divida.getTenantId(), divida.getId());
                continue;
            }

            // Verificar se já existe parcela para este mês
            int dia = divida.getDiaVencimento() != null
                    ? Math.min(divida.getDiaVencimento(), mesAtual.lengthOfMonth())
                    : divida.getDataInicio().getDayOfMonth();

            // Só gera se o dia de vencimento já chegou ou passou neste mês
            if (hoje.getDayOfMonth() < dia) continue;

            LocalDate vencimentoMes = mesAtual.atDay(dia);

            boolean jaExiste = divida.getParcelas().stream()
                    .anyMatch(p -> {
                        YearMonth ymParcela = YearMonth.from(p.getDataVencimento());
                        return ymParcela.equals(mesAtual);
                    });

            if (jaExiste) continue;

            // Gerar nova parcela
            BigDecimal valor = divida.getValorParcelaRecorrente() != null
                    ? divida.getValorParcelaRecorrente()
                    : divida.getValorTotal();

            int proximoNumero = divida.getParcelas().size() + 1;

            ParcelaDivida parcela = ParcelaDivida.builder()
                    .numeroParcela(proximoNumero)
                    .valor(valor)
                    .dataVencimento(vencimentoMes)
                    .build();
            divida.adicionarParcela(parcela);

            // Atualizar valorTotal acumulado e valorRestante
            divida.setValorTotal(divida.getValorTotal().add(valor));
            divida.setValorRestante(divida.getValorRestante().add(valor));

            dividaRepository.save(divida);
            geradas++;
            log.info("[tenant={}] Parcela #{} gerada para dívida recorrente id={} valor={}",
                    divida.getTenantId(), proximoNumero, divida.getId(), valor);
        }

        log.info("Processamento de dívidas recorrentes finalizado: {} parcelas geradas", geradas);
    }

    private Divida findById(Long id) {
        return dividaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dívida", id));
    }
}
