package com.gestao.financeiro.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.Where;

/**
 * Classe base para entidades com isolamento por tenant.
 * Aplica automaticamente filtro Hibernate por tenant_id
 * e exclui registros soft-deleted.
 */
@MappedSuperclass
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Where(clause = "deleted_at IS NULL")
@Getter
@Setter
public abstract class TenantEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
}
