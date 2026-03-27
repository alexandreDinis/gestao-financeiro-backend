package com.gestao.financeiro.entity;

import com.gestao.financeiro.entity.enums.DirecaoLancamento;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import lombok.*;

import java.math.BigDecimal;

/**
 * Lançamento contábil (ledger entry).
 * Cada lançamento movimenta uma conta em uma direção (DEBITO ou CREDITO).
 *
 * Regra de ouro: para cada Transacao,
 *   SUM(DEBITO) = SUM(CREDITO)
 *
 * Lancamentos são IMUTÁVEIS — para corrigir, cria-se estorno (nova transação).
 */
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "lancamento", indexes = {
        @Index(name = "idx_lancamento_tenant_conta", columnList = "tenant_id, conta_id"),
        @Index(name = "idx_lancamento_conta_direcao", columnList = "conta_id, direcao"),
        @Index(name = "idx_lancamento_transacao", columnList = "transacao_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lancamento extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transacao_id", nullable = false)
    private Transacao transacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id", nullable = false)
    private Conta conta;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DirecaoLancamento direcao;

    private String descricao;
}
