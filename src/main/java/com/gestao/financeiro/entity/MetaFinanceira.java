package com.gestao.financeiro.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Meta financeira (ex: "Viagem Europa", "Fundo de Emergência").
 * Acumula depósitos até atingir o valor alvo.
 */
@Entity
@Table(name = "meta_financeira", indexes = {
        @Index(name = "idx_meta_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetaFinanceira extends TenantEntity {

    @Column(nullable = false)
    private String nome;

    @Column(name = "valor_alvo", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorAlvo;

    @Column(name = "valor_atual", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal valorAtual = BigDecimal.ZERO;

    private LocalDate prazo;

    private String descricao;

    @Column(nullable = false)
    @Builder.Default
    private Boolean concluida = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    public double getProgresso() {
        if (valorAlvo.compareTo(BigDecimal.ZERO) == 0) return 0;
        return valorAtual.multiply(BigDecimal.valueOf(100))
                .divide(valorAlvo, 1, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    public void depositar(BigDecimal valor) {
        this.valorAtual = this.valorAtual.add(valor);
        if (this.valorAtual.compareTo(this.valorAlvo) >= 0) {
            this.concluida = true;
        }
    }
}
