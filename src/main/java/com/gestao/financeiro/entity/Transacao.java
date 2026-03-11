package com.gestao.financeiro.entity;

import com.gestao.financeiro.entity.enums.StatusTransacao;
import com.gestao.financeiro.entity.enums.TipoTransacao;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Transação = evento financeiro.
 * Cada transação gera 1+ Lancamentos (débito/crédito).
 *
 * Exemplos:
 * - Despesa: 1 lançamento DEBITO na conta
 * - Transferência: 1 DEBITO na origem + 1 CREDITO no destino
 */
@Entity
@Table(name = "transacao", indexes = {
        @Index(name = "idx_transacao_tenant_data", columnList = "tenant_id, data"),
        @Index(name = "idx_transacao_tenant_categoria", columnList = "tenant_id, categoria_id"),
        @Index(name = "idx_transacao_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_transacao_idempotency", columnList = "idempotency_key")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transacao extends TenantEntity {

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "data_vencimento")
    private LocalDate dataVencimento;

    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTransacao tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatusTransacao status = StatusTransacao.PENDENTE;

    private String observacao;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @OneToMany(mappedBy = "transacao", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Lancamento> lancamentos = new ArrayList<>();

    /**
     * Adiciona lançamento e mantém referência bidirecional.
     */
    public void addLancamento(Lancamento lancamento) {
        lancamentos.add(lancamento);
        lancamento.setTransacao(this);
        lancamento.setTenantId(this.getTenantId());
    }
}
