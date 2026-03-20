package com.gestao.financeiro.entity;

import com.gestao.financeiro.entity.enums.StatusTransacao;
import com.gestao.financeiro.entity.enums.TipoTransacao;
import com.gestao.financeiro.entity.enums.TipoDespesa;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
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
@Table(name = "transacao", 
    indexes = {
        @Index(name = "idx_transacao_tenant_data", columnList = "tenant_id, data"),
        @Index(name = "idx_transacao_tenant_categoria", columnList = "tenant_id, categoria_id"),
        @Index(name = "idx_transacao_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_transacao_idempotency", columnList = "idempotency_key"),
        @Index(name = "idx_transacao_recorrencia_referencia", columnList = "recorrencia_id, referencia")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_transacao_recorrencia_referencia", columnNames = {"recorrencia_id", "referencia"})
    }
)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_despesa")
    private TipoDespesa tipoDespesa;

    @Column(name = "referencia")
    private YearMonth referencia;

    @Column(name = "gerado_automaticamente")
    @Builder.Default
    private Boolean geradoAutomaticamente = false;

    @Column(name = "recorrencia_id")
    private Long recorrenciaId;

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    protected void onCreate() {
        criadoEm = LocalDateTime.now();
        atualizadoEm = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        atualizadoEm = LocalDateTime.now();
    }

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
