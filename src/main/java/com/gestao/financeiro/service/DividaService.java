package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.DividaRequest;
import com.gestao.financeiro.dto.request.PagarMultiplasParcelasRequest;
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




    @Transactional
    public DividasResumoResponse listar(TipoDivida tipo, Long pessoaId, Integer ano, Integer mes, StatusDivida status, Pageable pageable) {
        String statusStr = status != null ? status.name() : null;
        
        // Se está filtrando por mês, garantir que parcelas recorrentes estão geradas
        if (ano != null && mes != null) {
            gerarParcelasRecorrentesFaltantes(ano, mes, tipo, pessoaId);
        }
        
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

    @Transactional
    public DividasResumoResponse listarTodos(TipoDivida tipo, Long pessoaId, Integer ano, Integer mes, StatusDivida status) {
        String statusStr = status != null ? status.name() : null;

        // Se está filtrando por mês, garantir que parcelas recorrentes estão geradas
        if (ano != null && mes != null) {
            gerarParcelasRecorrentesFaltantes(ano, mes, tipo, pessoaId);
        }

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

        Pessoa pessoa = null;
        if (request.pessoaId() != null) {
            pessoa = pessoaRepository.findById(request.pessoaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Pessoa", request.pessoaId()));
            
            pessoa.setTotalEmprestimos(pessoa.getTotalEmprestimos() + 1);
            pessoaRepository.save(pessoa);
        }

        Divida divida = dividaMapper.toEntity(request);
        divida.setTenantId(tenantId);
        divida.setPessoa(pessoa);

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
                tenantId, divida.getId(), pessoa != null ? pessoa.getNome() : "N/A", request.valorTotal(), isRecorrente);

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

        if (request.contaId() == null) throw new BusinessException("Conta é obrigatória");
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
                YearMonth.from(parcela.getDataVencimento()), // referencia
                StatusTransacao.PAGO
        );

        com.gestao.financeiro.dto.response.TransacaoResponse txResponse = transacaoService.criar(txRequest);
        
        // Em seguida marcamos o vínculo manualmente, pois service wrapper retorna DTO
        // Utilizamos getReferenceById para evitar carregar a entidade inteira mas garantir o vínculo JPA correto
        parcela.setTransacaoGerada(transacaoRepository.getReferenceById(txResponse.id()));
        
        // 4. Atualizar Pessoa Score
        if (pessoa != null) {
            boolean pagoNoPrazo = !dataPag.isAfter(parcela.getDataVencimento());
            pessoa.registrarPagamento(pagoNoPrazo);
            pessoaRepository.save(pessoa);
        }

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
    public List<ParcelaDividaResponse> pagarMultiplasParcelas(PagarMultiplasParcelasRequest request) {
        if (request.parcelaIds() == null || request.parcelaIds().isEmpty()) {
            throw new BusinessException("Selecione ao menos uma parcela.");
        }

        // 1. Buscar todas as parcelas
        List<ParcelaDivida> parcelas = request.parcelaIds().stream()
                .map(id -> parcelaRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Parcela da Dívida", id)))
                .toList();

        // 2. Validar que todas são da mesma dívida e estão pendentes
        Divida divida = parcelas.get(0).getDivida();
        for (ParcelaDivida p : parcelas) {
            if (!p.getDivida().getId().equals(divida.getId())) {
                throw new BusinessException("Todas as parcelas devem ser da mesma dívida.");
            }
            if (p.getStatus() == StatusTransacao.PAGO) {
                throw new BusinessException("A parcela " + p.getNumeroParcela() + " já foi paga.");
            }
        }

        Pessoa pessoa = divida.getPessoa();

        if (request.contaId() == null) throw new BusinessException("Conta é obrigatória");
        Conta conta = contaRepository.findById(request.contaId())
                .orElseThrow(() -> new ResourceNotFoundException("Conta", request.contaId()));

        Categoria categoria = null;
        if (request.categoriaId() != null) {
            categoria = categoriaRepository.findById(request.categoriaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria", request.categoriaId()));
        }

        LocalDate dataPag = request.dataPagamento() != null ? request.dataPagamento() : LocalDate.now();

        // 3. Calcular totais
        BigDecimal totalOriginal = parcelas.stream()
                .map(ParcelaDivida::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal desconto = request.desconto() != null ? request.desconto() : BigDecimal.ZERO;
        if (desconto.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("O desconto não pode ser negativo.");
        }
        if (desconto.compareTo(totalOriginal) >= 0) {
            throw new BusinessException("O desconto não pode ser maior ou igual ao valor total das parcelas.");
        }

        BigDecimal totalComDesconto = totalOriginal.subtract(desconto);

        // 4. Ordenar parcelas por número
        List<ParcelaDivida> parcelasOrdenadas = parcelas.stream()
                .sorted((a, b) -> Integer.compare(a.getNumeroParcela(), b.getNumeroParcela()))
                .toList();

        // Descrição com range de parcelas
        String rangeParcelas = parcelasOrdenadas.size() == 1
                ? "Parcela " + parcelasOrdenadas.get(0).getNumeroParcela()
                : "Parcelas " + parcelasOrdenadas.get(0).getNumeroParcela() + "-" + parcelasOrdenadas.get(parcelasOrdenadas.size() - 1).getNumeroParcela();

        // 5. Gerar UMA transação para o lote
        TipoTransacao tipoTx = divida.getTipo() == TipoDivida.A_RECEBER ? TipoTransacao.RECEITA : TipoTransacao.DESPESA;
        String desc = (tipoTx == TipoTransacao.RECEITA ? "Recebimento: " : "Pagamento: ") + divida.getDescricao() + " (" + rangeParcelas + ")";
        String obs = "Pagamento em lote de " + parcelas.size() + " parcela(s) da dívida ID " + divida.getId();
        if (desconto.compareTo(BigDecimal.ZERO) > 0) {
            obs += " — Desconto: R$ " + desconto.setScale(2, RoundingMode.HALF_UP);
        }

        com.gestao.financeiro.dto.request.TransacaoRequest txRequest = new com.gestao.financeiro.dto.request.TransacaoRequest(
                desc,
                totalComDesconto,
                dataPag,
                parcelasOrdenadas.get(0).getDataVencimento(), // vencimento da primeira parcela
                tipoTx,
                null, // tipoDespesa
                categoria != null ? categoria.getId() : null,
                conta.getId(),
                null, // contaDestinoId
                obs,
                null, // idempotencyKey
                false, // geradoAutomaticamente
                null, // recorrenciaId
                YearMonth.from(parcelasOrdenadas.get(0).getDataVencimento()), // referencia
                StatusTransacao.PAGO
        );

        com.gestao.financeiro.dto.response.TransacaoResponse txResponse = transacaoService.criar(txRequest);
        Transacao txRef = transacaoRepository.getReferenceById(txResponse.id());

        // 6. Marcar todas as parcelas como pagas
        for (ParcelaDivida p : parcelasOrdenadas) {
            p.setStatus(StatusTransacao.PAGO);
            p.setDataPagamento(dataPag);
            p.setTransacaoGerada(txRef);
            parcelaRepository.save(p);
        }

        // 7. Atualizar pessoa score (uma vez por parcela no prazo)
        if (pessoa != null) {
            for (ParcelaDivida p : parcelasOrdenadas) {
                boolean pagoNoPrazo = !dataPag.isAfter(p.getDataVencimento());
                pessoa.registrarPagamento(pagoNoPrazo);
            }
            pessoaRepository.save(pessoa);
        }

        // 8. Abater dívida — usar totalComDesconto (valor real pago)
        divida.abaterValor(totalOriginal);
        // Se houve desconto, o valor restante pode ficar negativo, ajustamos
        if (divida.getValorRestante().compareTo(BigDecimal.ZERO) < 0) {
            divida.setValorRestante(BigDecimal.ZERO);
            divida.setStatus(StatusDivida.PAGA);
        }
        dividaRepository.save(divida);

        log.info("[tenant={}] Pagamento em lote: {} parcelas da dívida id={} — total={} desconto={} pago={}",
                divida.getTenantId(), parcelas.size(), divida.getId(), totalOriginal, desconto, totalComDesconto);

        return parcelasOrdenadas.stream()
                .map(dividaMapper::toParcelaResponse)
                .toList();
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
     * Executa ao iniciar a aplicação, caso o scheduler tenha sido perdido
     * (ex: máquina desligada às 06:05).
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional
    public void processarRecorrentesNaInicializacao() {
        log.info("Verificando dívidas recorrentes pendentes na inicialização...");
        processarDividasRecorrentes();
    }

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

            LocalDate vencimentoMes = mesAtual.atDay(dia);

            // Verificar existência no banco (inclui deletados via native query)
            boolean jaExiste = parcelaRepository.existsByDividaIdAndDataVencimento(divida.getId(), vencimentoMes);

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
            // Garantir que o status volte para PENDENTE com a nova parcela
            divida.setStatus(StatusDivida.PENDENTE);

            dividaRepository.save(divida);
            geradas++;
            log.info("[tenant={}] Parcela #{} gerada para dívida recorrente id={} valor={}",
                    divida.getTenantId(), proximoNumero, divida.getId(), valor);
        }

        log.info("Processamento de dívidas recorrentes finalizado: {} parcelas geradas", geradas);
    }

    /**
     * Gera parcelas faltantes para dívidas recorrentes quando alguém consulta um mês específico.
     * Garante que a parcela do mês consultado existe para que o valor e status apareçam corretos.
     */
    private void gerarParcelasRecorrentesFaltantes(Integer ano, Integer mes, TipoDivida tipo, Long pessoaId) {
        YearMonth mesConsultado = YearMonth.of(ano, mes);
        // Limitar a geração sob demanda para no máximo 12 meses no futuro (previsibilidade)
        if (mesConsultado.isAfter(YearMonth.now().plusMonths(12))) return;

        List<Divida> recorrentes = dividaRepository.findByRecorrenteTrue();
        for (Divida divida : recorrentes) {
            // Filtrar por tipo e pessoa se especificados
            if (tipo != null && divida.getTipo() != tipo) continue;
            if (pessoaId != null && (divida.getPessoa() == null || !divida.getPessoa().getId().equals(pessoaId))) continue;

            // Verificar se a recorrência cobre este mês
            YearMonth mesInicio = YearMonth.from(divida.getDataInicio());
            if (mesConsultado.isBefore(mesInicio)) continue;
            if (divida.getDataFim() != null && mesConsultado.isAfter(YearMonth.from(divida.getDataFim()))) continue;

            // Calcular vencimento para este mês
            int dia = divida.getDiaVencimento() != null
                    ? Math.min(divida.getDiaVencimento(), mesConsultado.lengthOfMonth())
                    : divida.getDataInicio().getDayOfMonth();
            LocalDate vencimentoMes = mesConsultado.atDay(dia);

            // Verificar se já existe parcela
            boolean jaExiste = parcelaRepository.existsByDividaIdAndDataVencimento(divida.getId(), vencimentoMes);
            if (jaExiste) continue;

            // Gerar a parcela
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

            divida.setValorTotal(divida.getValorTotal().add(valor));
            divida.setValorRestante(divida.getValorRestante().add(valor));
            divida.setStatus(com.gestao.financeiro.entity.enums.StatusDivida.PENDENTE);

            dividaRepository.save(divida);
            log.info("[on-demand] Parcela #{} gerada para dívida recorrente id={} mês={}/{}",
                    proximoNumero, divida.getId(), mes, ano);
        }
    }

    private Divida findById(Long id) {
        com.gestao.financeiro.util.ValidationUtils.validateId(id, "Dívida");
        return dividaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dívida", id));
    }
}
