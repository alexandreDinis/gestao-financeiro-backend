package com.gestao.financeiro.entity;

import com.gestao.financeiro.entity.enums.StatusAssinatura;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Vincula um Tenant a um Plano, controlando datas e status de assinatura.
 */
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "assinatura", indexes = {
        @Index(name = "idx_assinatura_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assinatura extends TenantEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plano_id", nullable = false)
    private Plano plano;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatusAssinatura status = StatusAssinatura.ATIVA;

    @Column(name = "valor_mensal", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorMensal;
}
