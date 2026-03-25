package com.gestao.financeiro.entity;

import com.gestao.financeiro.entity.enums.StatusTenant;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import lombok.*;

/**
 * Tenant (Inquilino). Representa a instância isolada de um cliente/família no SaaS.
 * Esta classe estende BaseEntity e não TenantEntity, pois ela própria é o Tenant.
 */
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant extends BaseEntity {

    @Column(nullable = false)
    private String nome;

    @Column(unique = true)
    private String subdominio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatusTenant status = StatusTenant.ATIVO;

    // A assinatura ativa do Tenant
    @OneToOne(mappedBy = "tenant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Assinatura assinaturaAtiva;
}
