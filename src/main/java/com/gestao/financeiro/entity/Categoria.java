package com.gestao.financeiro.entity;

import com.gestao.financeiro.entity.enums.TipoCategoria;
import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.SQLRestriction;

/**
 * Categoria de transações (ex: Alimentação, Salário, Transporte).
 * Suporta hierarquia (subcategorias via categoriaPai).
 */
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "categoria", indexes = {
        @Index(name = "idx_categoria_tenant", columnList = "tenant_id"),
        @Index(name = "idx_categoria_pai", columnList = "categoria_pai_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Categoria extends TenantEntity {

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCategoria tipo;

    private String cor;

    private String icone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_pai_id")
    private Categoria categoriaPai;
}
