package com.gestao.financeiro.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Cartão de crédito — vinculado a uma Conta do tipo CARTAO_CREDITO.
 * O saldo da Conta ligada reflete a fatura acumulada.
 */
@Entity
@Table(name = "cartao_credito", indexes = {
        @Index(name = "idx_cartao_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartaoCredito extends TenantEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id", nullable = false)
    private Conta conta;

    @Column(nullable = false)
    private String bandeira;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal limite;

    @Column(name = "dia_fechamento", nullable = false)
    private Integer diaFechamento;

    @Column(name = "dia_vencimento", nullable = false)
    private Integer diaVencimento;
}
