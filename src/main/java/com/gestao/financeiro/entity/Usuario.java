package com.gestao.financeiro.entity;

import com.gestao.financeiro.entity.enums.RoleUsuario;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import lombok.*;

/**
 * Usuário do sistema.
 * Pertence a um Tenant via TenantEntity.
 *
 * MVP: sem autenticação (senha ignorada).
 * Fase 5: JWT + Spring Security.
 */
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "usuario", indexes = {
        @Index(name = "idx_usuario_tenant", columnList = "tenant_id"),
        @Index(name = "idx_usuario_email", columnList = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario extends TenantEntity {

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleUsuario role;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;
}
