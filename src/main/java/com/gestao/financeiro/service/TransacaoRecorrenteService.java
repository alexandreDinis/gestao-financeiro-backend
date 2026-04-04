package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.TransacaoRecorrenteRequest;
import com.gestao.financeiro.dto.request.TransacaoRequest;
import com.gestao.financeiro.dto.response.TransacaoRecorrenteResponse;
import com.gestao.financeiro.entity.Categoria;
import com.gestao.financeiro.entity.Conta;
import com.gestao.financeiro.entity.TransacaoRecorrente;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.exception.ResourceNotFoundException;
import com.gestao.financeiro.mapper.TransacaoRecorrenteMapper;
import com.gestao.financeiro.repository.CategoriaRepository;
import com.gestao.financeiro.repository.ContaRepository;
import com.gestao.financeiro.repository.TransacaoRecorrenteRepository;
import com.gestao.financeiro.repository.TransacaoRepository;
import com.gestao.financeiro.entity.enums.StatusTransacao;
import java.time.YearMonth;
import com.gestao.financeiro.config.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TransacaoRecorrenteService {

    private final TransacaoRecorrenteRepository recorrenteRepository;
    private final ContaRepository contaRepository;
    private final CategoriaRepository categoriaRepository;
    private final TransacaoRecorrenteMapper recorrenteMapper;
    private final TransacaoService transacaoService;
    private final TransacaoRepository transacaoRepository;



    public Page<TransacaoRecorrenteResponse> listar(Pageable pageable) {
        return recorrenteRepository.findByAtivaTrue(pageable)
                .map(recorrenteMapper::toResponse);
    }

    public TransacaoRecorrenteResponse buscarPorId(Long id) {
        return recorrenteMapper.toResponse(findById(id));
    }

    @Transactional
    public TransacaoRecorrenteResponse criar(TransacaoRecorrenteRequest request) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("Tenant ID não encontrado no contexto");
        }

        Long contaId = Objects.requireNonNull(request.contaId(), "ID da conta não pode ser nulo");
        Conta conta = contaRepository.findById(contaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta", contaId));

        Categoria categoria = null;
        if (request.categoriaId() != null) {
            Long catId = request.categoriaId();
            categoria = categoriaRepository.findById(catId)
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria", catId));
        }

        TransacaoRecorrente recorrente = recorrenteMapper.toEntity(request);
        recorrente.setTenantId(tenantId);
        recorrente.setConta(conta);
        recorrente.setCategoria(categoria);

        recorrente = recorrenteRepository.save(recorrente);
        log.info("[tenant={}] Recorrência criada: id={} desc={} periodicidade={}",
                tenantId, recorrente.getId(), recorrente.getDescricao(), recorrente.getPeriodicidade());

        // Geração imediata da primeira transação se a data de início for hoje
        LocalDate hoje = LocalDate.now();
        if (recorrente.isAtivaEm(hoje) && deveGerarHoje(recorrente, hoje)) {
            try {
                log.info("[tenant={}] Gerando primeira transação da recorrência id={}", tenantId, recorrente.getId());
                gerarTransacao(recorrente, hoje);
            } catch (Exception e) {
                log.error("[tenant={}] Erro na geração imediata da recorrência id={}: {}",
                        tenantId, recorrente.getId(), e.getMessage());
            }
        }

        return recorrenteMapper.toResponse(recorrente);
    }

    @Transactional
    public TransacaoRecorrenteResponse atualizar(Long id, TransacaoRecorrenteRequest request) {
        TransacaoRecorrente recorrente = findById(id);

        Conta conta = contaRepository.findById(request.contaId())
                .orElseThrow(() -> new ResourceNotFoundException("Conta", request.contaId()));

        recorrenteMapper.updateEntity(recorrente, request);
        recorrente.setConta(conta);

        if (request.categoriaId() != null) {
            Categoria categoria = categoriaRepository.findById(request.categoriaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria", request.categoriaId()));
            recorrente.setCategoria(categoria);
        }

        recorrente = recorrenteRepository.save(recorrente);
        log.info("[tenant={}] Recorrência atualizada: id={}", recorrente.getTenantId(), id);

        return recorrenteMapper.toResponse(recorrente);
    }

    @Transactional
    public void deletar(Long id) {
        TransacaoRecorrente recorrente = findById(id);
        recorrente.setAtiva(false);
        recorrente.softDelete();
        recorrenteRepository.save(recorrente);
        log.info("[tenant={}] Recorrência desativada: id={}", recorrente.getTenantId(), id);
    }

    /**
     * Job diário: gera transações para recorrências ativas.
     * Roda todo dia às 06:00.
     */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void processarRecorrencias() {
        log.info("Iniciando processamento de recorrências...");
        LocalDate hoje = LocalDate.now();

        List<TransacaoRecorrente> ativas = recorrenteRepository.findByAtivaTrue();
        int geradas = 0;

        for (TransacaoRecorrente rec : ativas) {
            if (!rec.isAtivaEm(hoje)) continue;
            
            // Lógica de "Catch-up": Se não foi gerada para este mês e já passou (ou é) o dia, gera.
            YearMonth referencia = YearMonth.from(hoje);
            // IMPORTANTE: Usamos a query que ignora soft-delete para não recriar algo que o usuário deletou
            String refStr = referencia.atDay(1).toString(); 
            boolean jaGerada = transacaoRepository.existsByRecorrenciaIdAndReferenciaIgnoreSoftDelete(rec.getId(), refStr);
            
            if (!jaGerada && deveGerarParaReferencia(rec, hoje)) {
                try {
                    gerarTransacao(rec, hoje);
                    geradas++;
                } catch (Exception e) {
                    log.error("[tenant={}] Erro ao gerar recorrência id={}: {}",
                            rec.getTenantId(), rec.getId(), e.getMessage());
                }
            }
        }
        log.info("Processamento de recorrências finalizado: {} transações geradas", geradas);
    }

    private boolean deveGerarParaReferencia(TransacaoRecorrente rec, LocalDate hoje) {
        // Se a recorrência é para um dia que já passou neste mês, ou é hoje, deve gerar (catch-up)
        return switch (rec.getPeriodicidade()) {
            case DIARIA -> true; // Diária sempre gera se não houver no dia (ajustar se necessário para multiplas por mês)
            case SEMANAL, QUINZENAL -> deveGerarHoje(rec, hoje); // Mantemos restrito para não duplicar na semana
            case MENSAL, ANUAL -> {
                int diaRec = (rec.getDiaVencimento() != null) 
                    ? Math.min(rec.getDiaVencimento(), hoje.lengthOfMonth())
                    : rec.getDataInicio().getDayOfMonth();
                yield hoje.getDayOfMonth() >= diaRec;
            }
        };
    }

    private void gerarTransacao(TransacaoRecorrente rec, LocalDate hoje) {
        TransacaoRequest request = new TransacaoRequest(
                rec.getDescricao() + " (recorrente)",
                rec.getValor(),
                hoje,
                rec.getDiaVencimento() != null
                        ? hoje.withDayOfMonth(Math.min(rec.getDiaVencimento(), hoje.lengthOfMonth()))
                        : null,
                rec.getTipo(),
                null, // tipoDespesa
                rec.getCategoria() != null ? rec.getCategoria().getId() : null,
                rec.getConta().getId(),
                null, // contaDestinoId
                "Gerada automaticamente pela recorrência #" + rec.getId(),
                null, // idempotencyKey
                true, // geradoAutomaticamente
                rec.getId(), // recorrenciaId
                YearMonth.from(hoje), // referencia
                null // status
        );

        transacaoService.criar(request);
    }

    private boolean deveGerarHoje(TransacaoRecorrente rec, LocalDate hoje) {
        return switch (rec.getPeriodicidade()) {
            case DIARIA -> true;
            case SEMANAL -> hoje.getDayOfWeek() == rec.getDataInicio().getDayOfWeek();
            case QUINZENAL -> {
                long days = rec.getDataInicio().until(hoje).getDays();
                yield days % 14 == 0;
            }
            case MENSAL -> hoje.getDayOfMonth() == (rec.getDiaVencimento() != null
                    ? Math.min(rec.getDiaVencimento(), hoje.lengthOfMonth())
                    : rec.getDataInicio().getDayOfMonth());
            case ANUAL -> hoje.getMonthValue() == rec.getDataInicio().getMonthValue()
                    && hoje.getDayOfMonth() == rec.getDataInicio().getDayOfMonth();
        };
    }

    private TransacaoRecorrente findById(Long id) {
        return recorrenteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transação recorrente", id));
    }
}
