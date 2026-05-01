package com.gestao.financeiro.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "previsao_ajuste", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "mes", "ano"})
})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class PrevisaoAjuste {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Integer mes;

    @Column(nullable = false)
    private Integer ano;

    @Column(name = "ajuste_entrada", precision = 19, scale = 2)
    private BigDecimal ajusteEntrada;

    @Column(name = "ajuste_saida", precision = 19, scale = 2)
    private BigDecimal ajusteSaida;
}
