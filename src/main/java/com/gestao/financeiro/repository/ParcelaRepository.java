package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.Parcela;
import com.gestao.financeiro.repository.projection.CartaoCreditoResumoProjection;
import com.gestao.financeiro.repository.projection.GastoCategoriaProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ParcelaRepository extends JpaRepository<Parcela, Long> {

    List<Parcela> findByTransacaoId(Long transacaoId);

    List<Parcela> findByFaturaId(Long faturaId);

    @Query("""
        SELECT p FROM Parcela p 
        WHERE p.paga = false 
          AND (
               (p.dataVencimento BETWEEN :inicio AND :limite)
               OR (p.dataVencimento < :hoje)
          )
    """)
    List<Parcela> findProximasParcelas(
            @Param("hoje") LocalDate hoje, 
            @Param("inicio") LocalDate inicio, 
            @Param("limite") LocalDate limite, 
            Pageable pageable);

    // ─────────────────────────────────────────────────────────────────────────
    // Lançamentos — parcelas que VENCEM no período (cada parcela aparece no mês certo)
    // Maio: teste (1/8), Junho: teste (2/8), Julho: teste (3/8) ...
    // ─────────────────────────────────────────────────────────────────────────

    @Query("""
        SELECT p FROM Parcela p
        JOIN FETCH p.transacao t
        WHERE p.tenantId = :tenantId
          AND p.dataVencimento BETWEEN :inicio AND :fim
          AND t.deletedAt IS NULL
          AND t.status <> 'CANCELADO'
    """)
    List<Parcela> findByTenantIdAndDataVencimentoBetween(
            @Param("tenantId") Long tenantId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query("SELECT " +
           "COALESCE(SUM(CASE WHEN p.paga = false THEN p.valorParcela ELSE 0 END), 0) as totalUtilizado, " +
           "COALESCE(SUM(CASE WHEN f.status = 'ABERTA' AND p.paga = false THEN p.valorParcela ELSE 0 END), 0) as valorAberta, " +
           "COALESCE(SUM(CASE WHEN (f.status = 'FECHADA' OR f.status = 'ATRASADA') AND p.paga = false THEN p.valorParcela ELSE 0 END), 0) as valorFechadas " +
           "FROM Parcela p JOIN p.fatura f WHERE f.cartao.id = :cartaoId")
    CartaoCreditoResumoProjection getResumoFinanceiro(@Param("cartaoId") Long cartaoId);

    // ─────────────────────────────────────────────────────────────────────────
    // Dashboard — soma parcela mensal de compras de cartão feitas no período
    // Usa t.data (data da compra) e pega apenas parcela #1 de cada transação
    // para obter o valor mensal de impacto (ex: 3800/8 = 475)
    // ─────────────────────────────────────────────────────────────────────────

    @Query("""
        SELECT COALESCE(SUM(p.valorParcela), 0)
        FROM Parcela p
        JOIN p.transacao t
        WHERE t.data BETWEEN :inicio AND :fim
          AND p.numeroParcela = 1
          AND t.deletedAt IS NULL
          AND t.status <> 'CANCELADO'
    """)
    BigDecimal somarParcelasPorPeriodo(
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    // ─────────────────────────────────────────────────────────────────────────
    // Lançamentos — parcelas que vencem no período (para listagem)
    // ─────────────────────────────────────────────────────────────────────────

    @Query("""
        SELECT COALESCE(SUM(p.valorParcela), 0)
        FROM Parcela p
        JOIN p.transacao t
        JOIN p.fatura f
        WHERE p.dataVencimento BETWEEN :inicio AND :fim
          AND t.deletedAt IS NULL
          AND t.status <> 'CANCELADO'
          AND p.paga = false
          AND f.status <> 'PAGA'
    """)
    BigDecimal somarParcelasPorVencimento(
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query("""
        SELECT
            t.categoria.id    AS categoriaId,
            t.categoria.nome  AS nomeCategoria,
            COALESCE(SUM(p.valorParcela), 0) AS total
        FROM Parcela p
        JOIN p.transacao t
        WHERE p.dataVencimento BETWEEN :inicio AND :fim
          AND t.deletedAt IS NULL
          AND t.status <> 'CANCELADO'
          AND t.categoria IS NOT NULL
        GROUP BY t.categoria.id, t.categoria.nome
    """)
    List<GastoCategoriaProjection> somarParcelasPorCategoriaPeriodo(
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    // ─────────────────────────────────────────────────────────────────────────
    // Relatório — parcelas de cartão que vencem no período, com categoria hierárquica
    // ─────────────────────────────────────────────────────────────────────────

    @Query("""
        SELECT p FROM Parcela p
        JOIN FETCH p.transacao t
        LEFT JOIN FETCH t.categoria c
        LEFT JOIN FETCH c.categoriaPai
        JOIN p.fatura f
        WHERE p.dataVencimento BETWEEN :inicio AND :fim
          AND t.tenantId = :tenantId
          AND t.tipo = 'DESPESA'
          AND t.deletedAt IS NULL
          AND t.status <> 'CANCELADO'
    """)
    List<Parcela> findParcelasCartaoByPeriodo(
            @Param("tenantId") Long tenantId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);
}
