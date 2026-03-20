package com.gestao.financeiro.entity;

import com.gestao.financeiro.entity.enums.StatusRecorrencia;
import com.gestao.financeiro.entity.enums.TipoRecorrencia;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "recorrencia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recorrencia extends TenantEntity {

    @Column(nullable = false)
    private String descricao;

    @Column(name = "valor_previsto", precision = 19, scale = 2)
    private BigDecimal valorPrevisto;

    @Column(name = "dia_vencimento", nullable = false)
    private Integer diaVencimento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TipoRecorrencia tipo = TipoRecorrencia.FIXA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatusRecorrencia status = StatusRecorrencia.ATIVA;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id")
    private Conta conta;
}
