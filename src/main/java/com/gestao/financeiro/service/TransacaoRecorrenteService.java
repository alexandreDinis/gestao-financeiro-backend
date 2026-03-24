package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.TransacaoRecorrenteRequest;
import com.gestao.financeiro.dto.request.TransacaoRequest;
import com.gestao.financeiro.dto.response.TransacaoRecorrenteResponse;
import com.gestao.financeiro.entity.Categoria;
import com.gestao.financeiro.entity.Conta;
import com.gestao.financeiro.entity.TransacaoRecorrente;
import com.gestao.financeiro.entity.enums.Periodicidade;
import com.gestao.financeiro.exception.ResourceNotFoundException;
import com.gestao.financeiro.mapper.TransacaoRecorrenteMapper;
import com.gestao.financeiro.repository.CategoriaRepository;
import com.gestao.financeiro.repository.ContaRepository;
import com.gestao.financeiro.repository.TransacaoRecorrenteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

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

    private static final Long DEFAULT_TENANT_ID = 1L;

    public Page<TransacaoRecorrenteResponse> listar(Pageable pageable) {
        return recorrenteRepository.findByAtivaTrue(pageable)
                .map(recorrenteMapper::toResponse);
    }

    public TransacaoRecorrenteResponse buscarPorId(Long id) {
        return recorrenteMapper.toResponse(findById(id));
    }

    @Transactional
    public TransacaoRecorrenteResponse criar(TransacaoRecorrenteRequest request) {
        Conta conta = contaRepository.findById(request.contaId())
                .orElseThrow(() -> new ResourceNotFoundException("Conta", request.contaId()));

        Categoria categoria = null;
        if (request.categoriaId() != null) {
            categoria = categoriaRepository.findById(request.categoriaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria", request.categoriaId()));
        }

        TransacaoRecorrente recorrente = recorrenteMapper.toEntity(request);
        recorrente.setTenantId(DEFAULT_TENANT_ID);
        recorrente.setConta(conta);
        recorrente.setCategoria(categoria);

        recorrente = recorrenteRepository.save(recorrente);
        log.info("[tenant={}] Recorrência criada: id={} desc={} periodicidade={}",
                DEFAULT_TENANT_ID, recorrente.getId(), recorrente.getDescricao(), recorrente.getPeriodicidade());

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
            if (!deveGerarHoje(rec, hoje)) continue;

            try {
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
                        rec.getId() // recorrenciaId
                );

                transacaoService.criar(request);
                geradas++;
            } catch (Exception e) {
                log.error("[tenant={}] Erro ao gerar recorrência id={}: {}",
                        rec.getTenantId(), rec.getId(), e.getMessage());
            }
        }

        log.info("Processamento de recorrências finalizado: {} transações geradas", geradas);
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
