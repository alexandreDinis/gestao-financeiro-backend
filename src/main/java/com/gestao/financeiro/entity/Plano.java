package com.gestao.financeiro.entity;

import com.gestao.financeiro.entity.enums.TipoPlano;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Planos de assinatura disponíveis na plataforma SaaS.
 */
@Entity
@Table(name = "plano")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plano extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private TipoPlano tipo;

    @Column(name = "preco_mensal", nullable = false, precision = 19, scale = 2)
    private BigDecimal precoMensal;

    @Column(name = "max_contas", nullable = false)
    private Integer maxContas;

    @Column(name = "max_categorias", nullable = false)
    private Integer maxCategorias;

    @Column(name = "max_cartoes", nullable = false)
    private Integer maxCartoes;

    @Column(name = "max_transacoes_mes", nullable = false)
    private Integer maxTransacoesMes;

    @Column(name = "max_metas", nullable = false)
    private Integer maxMetas;

    @Column(name = "max_dividas", nullable = false)
    private Integer maxDividas;

    @Column(name = "max_usuarios", nullable = false)
    private Integer maxUsuarios;

    @Column(name = "relatorios_avancados", nullable = false)
    @Builder.Default
    private Boolean relatoriosAvancados = false;

    @Column(name = "projecao_saldo", nullable = false)
    @Builder.Default
    private Boolean projecaoSaldo = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;
}
