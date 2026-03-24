package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.ContaRequest;
import com.gestao.financeiro.dto.response.ContaResponse;
import com.gestao.financeiro.entity.Conta;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.exception.ResourceNotFoundException;
import com.gestao.financeiro.mapper.ContaMapper;
import com.gestao.financeiro.repository.ContaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ContaService {

    private final ContaRepository contaRepository;
    private final ContaMapper contaMapper;

    private static final Long DEFAULT_TENANT_ID = 1L;

    public Page<ContaResponse> listar(Pageable pageable) {
        return contaRepository.findByAtivaTrue(pageable)
                .map(conta -> {
                    BigDecimal saldoAtual = contaRepository.calcularSaldo(conta.getId());
                    if (saldoAtual == null) {
                        saldoAtual = conta.getSaldoInicial() != null ? conta.getSaldoInicial() : BigDecimal.ZERO;
                    }
                    return contaMapper.toResponse(conta, saldoAtual);
                });
    }

    public ContaResponse buscarPorId(Long id) {
        Conta conta = findById(id);
        BigDecimal saldoAtual = contaRepository.calcularSaldo(id);
        return contaMapper.toResponse(conta, saldoAtual);
    }

    /**
     * Retorna o saldo calculado da conta (source of truth).
     * saldo = saldoInicial + SUM(creditos) - SUM(debitos)
     */
    public BigDecimal calcularSaldo(Long id) {
        findById(id); // valida existência
        return contaRepository.calcularSaldo(id);
    }

    @Transactional
    public ContaResponse criar(ContaRequest request) {
        if (contaRepository.existsByNomeAndTenantId(request.nome(), DEFAULT_TENANT_ID)) {
            throw new BusinessException("Já existe uma conta com o nome: " + request.nome());
        }

        Conta conta = contaMapper.toEntity(request);
        conta.setTenantId(DEFAULT_TENANT_ID);

        conta = contaRepository.save(conta);
        log.info("[tenant={}] Conta criada: id={} nome={} tipo={}", DEFAULT_TENANT_ID, conta.getId(), conta.getNome(), conta.getTipo());

        return contaMapper.toResponse(conta);
    }

    @Transactional
    public ContaResponse atualizar(Long id, ContaRequest request) {
        Conta conta = findById(id);

        if (!conta.getNome().equals(request.nome()) && contaRepository.existsByNomeAndTenantId(request.nome(), conta.getTenantId())) {
            throw new BusinessException("Já existe uma conta com o nome: " + request.nome());
        }

        contaMapper.updateEntity(conta, request);
        conta = contaRepository.save(conta);
        log.info("[tenant={}] Conta atualizada: id={}", conta.getTenantId(), id);

        BigDecimal saldoAtual = contaRepository.calcularSaldo(id);
        return contaMapper.toResponse(conta, saldoAtual);
    }

    @Transactional
    public void deletar(Long id) {
        Conta conta = findById(id);
        conta.softDelete();
        contaRepository.save(conta);
        log.info("[tenant={}] Conta desativada (soft delete): id={}", conta.getTenantId(), id);
    }

    private Conta findById(Long id) {
        return contaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conta", id));
    }
}
