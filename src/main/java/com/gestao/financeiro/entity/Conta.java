package com.gestao.financeiro.entity;

import com.gestao.financeiro.entity.enums.TipoConta;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

import org.hibernate.annotations.SQLRestriction;

/**
 * Conta financeira (corrente, poupança, carteira, investimento, cartão de crédito).
 * Saldo é cache: source of truth = saldoInicial + SUM(lancamentos).
 */
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "conta", indexes = {
        @Index(name = "idx_conta_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conta extends TenantEntity {

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoConta tipo;

    @Column(name = "saldo_inicial", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal saldoInicial = BigDecimal.ZERO;

    private String cor;

    private String icone;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativa = true;
}
