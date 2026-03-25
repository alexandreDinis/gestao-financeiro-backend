package com.gestao.financeiro.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import lombok.*;

import java.math.BigDecimal;

/**
 * Orçamento mensal por categoria.
 * Define limite de gastos para cada categoria em um mês.
 */
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "orcamento", indexes = {
        @Index(name = "idx_orcamento_tenant", columnList = "tenant_id"),
        @Index(name = "idx_orcamento_periodo", columnList = "tenant_id, mes, ano")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_orcamento_categoria_periodo",
                columnNames = {"tenant_id", "categoria_id", "mes", "ano"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Orcamento extends TenantEntity {

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal limite;

    @Column(nullable = false)
    private Integer mes;

    @Column(nullable = false)
    private Integer ano;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
}
