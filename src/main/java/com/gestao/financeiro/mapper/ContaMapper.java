package com.gestao.financeiro.mapper;

import com.gestao.financeiro.dto.request.ContaRequest;
import com.gestao.financeiro.dto.response.ContaResponse;
import com.gestao.financeiro.entity.Conta;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ContaMapper {

    public ContaResponse toResponse(Conta entity, BigDecimal saldoAtual) {
        return new ContaResponse(
                entity.getId(),
                entity.getNome(),
                entity.getTipo(),
                entity.getSaldoInicial(),
                saldoAtual,
                entity.getCor(),
                entity.getIcone(),
                entity.getAtiva(),
                entity.getCreatedAt()
        );
    }

    /**
     * Cria response sem saldo calculado (usa saldoInicial como fallback).
     * Útil em listagens onde calcular saldo individual seria caro.
     */
    public ContaResponse toResponse(Conta entity) {
        return toResponse(entity, entity.getSaldoInicial());
    }

    public Conta toEntity(ContaRequest request) {
        return Conta.builder()
                .nome(request.nome())
                .tipo(request.tipo())
                .saldoInicial(request.saldoInicial() != null ? request.saldoInicial() : BigDecimal.ZERO)
                .cor(request.cor())
                .icone(request.icone())
                .ativa(true)
                .build();
    }

    public void updateEntity(Conta entity, ContaRequest request) {
        entity.setNome(request.nome());
        entity.setTipo(request.tipo());
        entity.setCor(request.cor());
        entity.setIcone(request.icone());
        // saldoInicial não é editável após criação
    }
}
