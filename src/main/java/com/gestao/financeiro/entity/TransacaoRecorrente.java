package com.gestao.financeiro.entity;

import com.gestao.financeiro.entity.enums.Periodicidade;
import com.gestao.financeiro.entity.enums.TipoTransacao;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Template de transação que gera transações automaticamente.
 * Ex: Aluguel mensal, salário, Netflix.
 */
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "transacao_recorrente", indexes = {
        @Index(name = "idx_recorrente_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransacaoRecorrente extends TenantEntity {

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTransacao tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Periodicidade periodicidade;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Column(name = "dia_vencimento")
    private Integer diaVencimento;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativa = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id")
    private Conta conta;

    /**
     * Verifica se a recorrência está ativa na data fornecida.
     */
    public boolean isAtivaEm(LocalDate data) {
        if (!ativa) return false;
        if (data.isBefore(dataInicio)) return false;
        return dataFim == null || !data.isAfter(dataFim);
    }
}
