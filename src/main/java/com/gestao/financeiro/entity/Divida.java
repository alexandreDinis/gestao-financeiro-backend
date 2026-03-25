package com.gestao.financeiro.entity;

import com.gestao.financeiro.entity.enums.StatusDivida;
import com.gestao.financeiro.entity.enums.TipoDivida;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa um empréstimo (para receber) ou uma dívida (para pagar).
 */
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "divida", indexes = {
        @Index(name = "idx_divida_tenant", columnList = "tenant_id"),
        @Index(name = "idx_divida_pessoa", columnList = "pessoa_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Divida extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id", nullable = false)
    private Pessoa pessoa;

    @Column(nullable = false)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDivida tipo;

    @Column(name = "valor_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "valor_restante", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorRestante;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatusDivida status = StatusDivida.PENDENTE;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @OneToMany(mappedBy = "divida", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ParcelaDivida> parcelas = new ArrayList<>();

    public void adicionarParcela(ParcelaDivida parcela) {
        parcelas.add(parcela);
        parcela.setDivida(this);
        parcela.setTenantId(this.getTenantId());
    }

    public void abaterValor(BigDecimal valorPago) {
        this.valorRestante = this.valorRestante.subtract(valorPago);
        if (this.valorRestante.compareTo(BigDecimal.ZERO) <= 0) {
            this.valorRestante = BigDecimal.ZERO;
            this.status = StatusDivida.PAGA;
        }
    }
}
