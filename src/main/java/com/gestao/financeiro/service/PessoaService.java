package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.PessoaRequest;
import com.gestao.financeiro.dto.response.PessoaResponse;
import com.gestao.financeiro.entity.Pessoa;
import com.gestao.financeiro.exception.ResourceNotFoundException;
import com.gestao.financeiro.mapper.PessoaMapper;
import com.gestao.financeiro.repository.PessoaRepository;
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
public class PessoaService {

    private final PessoaRepository pessoaRepository;
    private final PessoaMapper pessoaMapper;



    public Page<PessoaResponse> listar(Pageable pageable) {
        return pessoaRepository.findAll(pageable).map(pessoaMapper::toResponse);
    }

    public PessoaResponse buscarPorId(Long id) {
        return pessoaMapper.toResponse(findById(id));
    }

    @Transactional
    public PessoaResponse criar(PessoaRequest request) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new com.gestao.financeiro.exception.BusinessException("Tenant ID não encontrado no contexto");
        }

        Pessoa pessoa = pessoaMapper.toEntity(request);
        pessoa.setTenantId(tenantId);
        pessoa = pessoaRepository.save(pessoa);

        log.info("[tenant={}] Pessoa criada: id={} nome={}", tenantId, pessoa.getId(), pessoa.getNome());
        return pessoaMapper.toResponse(pessoa);
    }

    @Transactional
    public PessoaResponse atualizar(Long id, PessoaRequest request) {
        Pessoa pessoa = findById(id);
        pessoaMapper.updateEntity(pessoa, request);
        pessoa = pessoaRepository.save(pessoa);

        log.info("[tenant={}] Pessoa atualizada: id={}", pessoa.getTenantId(), id);
        return pessoaMapper.toResponse(pessoa);
    }

    @Transactional
    public void deletar(Long id) {
        Pessoa pessoa = findById(id);
        pessoa.softDelete();
        pessoaRepository.save(pessoa);
        log.info("[tenant={}] Pessoa removida: id={}", pessoa.getTenantId(), id);
    }

    public Pessoa findById(Long id) {
        return pessoaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", id));
    }
}
