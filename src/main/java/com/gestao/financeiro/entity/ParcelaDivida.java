package com.gestao.financeiro.entity;

import com.gestao.financeiro.entity.enums.StatusTransacao;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Parcela específica de uma dívida.
 */
@Entity
@Table(name = "parcela_divida", indexes = {
        @Index(name = "idx_pd_tenant", columnList = "tenant_id"),
        @Index(name = "idx_pd_divida", columnList = "divida_id"),
        @Index(name = "idx_pd_vencimento", columnList = "tenant_id, data_vencimento")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParcelaDivida extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "divida_id", nullable = false)
    private Divida divida;

    @Column(name = "numero_parcela", nullable = false)
    private Integer numeroParcela;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatusTransacao status = StatusTransacao.PENDENTE;

    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transacao_id")
    private Transacao transacaoGerada; 
}
