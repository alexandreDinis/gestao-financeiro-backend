package com.gestao.financeiro.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Parcela de compra no cartão.
 * Cada parcela é vinculada a uma fatura e a uma transação.
 * Distribuída automaticamente entre faturas no momento da compra.
 */
@Entity
@Table(name = "parcela", indexes = {
        @Index(name = "idx_parcela_tenant", columnList = "tenant_id"),
        @Index(name = "idx_parcela_fatura", columnList = "fatura_id"),
        @Index(name = "idx_parcela_transacao", columnList = "transacao_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Parcela extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transacao_id", nullable = false)
    private Transacao transacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fatura_id", nullable = false)
    private FaturaCartao fatura;

    @Column(name = "numero_parcela", nullable = false)
    private Integer numeroParcela;

    @Column(name = "total_parcelas", nullable = false)
    private Integer totalParcelas;

    @Column(name = "valor_parcela", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorParcela;

    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    @Column(nullable = false)
    @Builder.Default
    private Boolean paga = false;
}
