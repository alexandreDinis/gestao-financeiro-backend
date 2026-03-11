package com.gestao.financeiro.entity;

import com.gestao.financeiro.entity.enums.StatusFatura;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fatura mensal do cartão de crédito.
 * Unique: cartao + mesReferencia + anoReferencia.
 */
@Entity
@Table(name = "fatura_cartao", indexes = {
        @Index(name = "idx_fatura_tenant", columnList = "tenant_id"),
        @Index(name = "idx_fatura_cartao_periodo", columnList = "cartao_id, mes_referencia, ano_referencia")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_fatura_periodo",
                columnNames = {"cartao_id", "mes_referencia", "ano_referencia"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaturaCartao extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_id", nullable = false)
    private CartaoCredito cartao;

    @Column(name = "mes_referencia", nullable = false)
    private Integer mesReferencia;

    @Column(name = "ano_referencia", nullable = false)
    private Integer anoReferencia;

    @Column(name = "valor_total", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatusFatura status = StatusFatura.ABERTA;

    @OneToMany(mappedBy = "fatura", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Parcela> parcelas = new ArrayList<>();

    public void adicionarParcela(Parcela parcela) {
        parcelas.add(parcela);
        parcela.setFatura(this);
        parcela.setTenantId(this.getTenantId());
        recalcularTotal();
    }

    public void recalcularTotal() {
        this.valorTotal = parcelas.stream()
                .map(Parcela::getValorParcela)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
