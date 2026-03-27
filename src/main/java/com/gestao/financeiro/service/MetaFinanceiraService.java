package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.DepositoMetaRequest;
import com.gestao.financeiro.dto.request.MetaFinanceiraRequest;
import com.gestao.financeiro.dto.response.MetaFinanceiraResponse;
import com.gestao.financeiro.entity.MetaFinanceira;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.exception.ResourceNotFoundException;
import com.gestao.financeiro.mapper.MetaFinanceiraMapper;
import com.gestao.financeiro.repository.MetaFinanceiraRepository;
import com.gestao.financeiro.config.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MetaFinanceiraService {

    private final MetaFinanceiraRepository metaRepository;
    private final MetaFinanceiraMapper metaMapper;



    public Page<MetaFinanceiraResponse> listar(Pageable pageable) {
        return metaRepository.findAll(pageable).map(metaMapper::toResponse);
    }

    public MetaFinanceiraResponse buscarPorId(Long id) {
        return metaMapper.toResponse(findById(id));
    }

    @Transactional
    public MetaFinanceiraResponse criar(MetaFinanceiraRequest request) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("Tenant ID não encontrado no contexto");
        }

        MetaFinanceira meta = metaMapper.toEntity(request);
        meta.setTenantId(tenantId);
        meta = metaRepository.save(meta);

        log.info("[tenant={}] Meta criada: id={} nome={} alvo={}",
                tenantId, meta.getId(), meta.getNome(), meta.getValorAlvo());

        return metaMapper.toResponse(meta);
    }

    @Transactional
    public MetaFinanceiraResponse atualizar(Long id, MetaFinanceiraRequest request) {
        MetaFinanceira meta = findById(id);
        metaMapper.updateEntity(meta, request);
        meta = metaRepository.save(meta);

        log.info("[tenant={}] Meta atualizada: id={}", meta.getTenantId(), id);
        return metaMapper.toResponse(meta);
    }

    @Transactional
    public MetaFinanceiraResponse depositar(Long id, DepositoMetaRequest request) {
        MetaFinanceira meta = findById(id);

        if (meta.getConcluida()) {
            throw new BusinessException("Meta já foi concluída.");
        }

        meta.depositar(request.valor());
        meta = metaRepository.save(meta);

        log.info("[tenant={}] Depósito na meta: id={} valor={} progresso={}%",
                meta.getTenantId(), id, request.valor(), meta.getProgresso());

        return metaMapper.toResponse(meta);
    }

    @Transactional
    public void deletar(Long id) {
        MetaFinanceira meta = findById(id);
        meta.softDelete();
        metaRepository.save(meta);
        log.info("[tenant={}] Meta removida: id={}", meta.getTenantId(), id);
    }

    private MetaFinanceira findById(Long id) {
        return metaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Meta financeira", id));
    }
}
