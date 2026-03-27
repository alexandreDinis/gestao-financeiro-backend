package com.gestao.financeiro.entity;

import com.gestao.financeiro.entity.enums.ScoreConfiabilidade;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import lombok.*;

/**
 * Pessoa para quem emprestamos dinheiro ou de quem pegamos emprestado.
 * Mantém um histórico/score baseado em pagamentos em dia ou atrasos.
 */
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "pessoa", indexes = {
        @Index(name = "idx_pessoa_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pessoa extends TenantEntity {

    @Column(nullable = false)
    private String nome;

    private String telefone;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "score_confiabilidade", nullable = false)
    @Builder.Default
    private ScoreConfiabilidade score = ScoreConfiabilidade.REGULAR;

    @Column(name = "total_emprestimos", nullable = false)
    @Builder.Default
    private Integer totalEmprestimos = 0;

    @Column(name = "total_pagos_em_dia", nullable = false)
    @Builder.Default
    private Integer totalPagosEmDia = 0;

    @Column(name = "total_atrasados", nullable = false)
    @Builder.Default
    private Integer totalAtrasados = 0;

    public void registrarPagamento(boolean noPrazo) {
        if (noPrazo) {
            this.totalPagosEmDia++;
        } else {
            this.totalAtrasados++;
        }
        recalcularScore();
    }

    private void recalcularScore() {
        if (totalEmprestimos == 0) {
            score = ScoreConfiabilidade.REGULAR;
            return;
        }

        double taxaInadimplencia = (double) totalAtrasados / (totalPagosEmDia + totalAtrasados);

        if (taxaInadimplencia == 0 && totalPagosEmDia > 3) {
            score = ScoreConfiabilidade.EXCELENTE;
        } else if (taxaInadimplencia <= 0.1) {
            score = ScoreConfiabilidade.BOM;
        } else if (taxaInadimplencia <= 0.3) {
            score = ScoreConfiabilidade.REGULAR;
        } else if (taxaInadimplencia <= 0.5) {
            score = ScoreConfiabilidade.RISCO_BAIXO;
        } else {
            score = ScoreConfiabilidade.RISCO_ALTO;
        }
    }
}
