package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.Lancamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.gestao.financeiro.repository.projection.GastoCategoriaProjection;
import com.gestao.financeiro.repository.projection.ResumoContaPeriodoProjection;

@Repository
public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {

    List<Lancamento> findByTransacaoId(Long transacaoId);

    List<Lancamento> findByContaId(Long contaId);

    // ─────────────────────────────────────────────────────────────────────────
    // Queries individuais por conta (mantidas para uso em outros serviços)
    // ─────────────────────────────────────────────────────────────────────────

    @Query("""
        SELECT COALESCE(SUM(l.valor), 0)
        FROM Lancamento l JOIN l.transacao t
        WHERE l.conta.id = :contaId
          AND l.direcao = 'CREDITO'
          AND t.data BETWEEN :inicio AND :fim
          AND t.deletedAt IS NULL AND t.status <> 'CANCELADO'
          AND (t.status = 'PAGO' OR (l.conta.tipo = 'CARTAO_CREDITO' AND t.status = 'PENDENTE'))
    """)
    BigDecimal somarCreditosPorContaEPeriodo(
            @Param("contaId") Long contaId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query("""
        SELECT COALESCE(SUM(l.valor), 0)
        FROM Lancamento l JOIN l.transacao t
        WHERE l.conta.id = :contaId
          AND l.direcao = 'DEBITO'
          AND t.data BETWEEN :inicio AND :fim
          AND t.deletedAt IS NULL AND t.status <> 'CANCELADO'
          AND (t.status = 'PAGO' OR (l.conta.tipo = 'CARTAO_CREDITO' AND t.status = 'PENDENTE'))
    """)
    BigDecimal somarDebitosPorContaEPeriodo(
            @Param("contaId") Long contaId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    // ─────────────────────────────────────────────────────────────────────────
    // ✅ FIX Problema 3 — query agregada: substitui o loop de 2*N queries
    //    Traz créditos E débitos de TODAS as contas num único round-trip ao banco.
    // ─────────────────────────────────────────────────────────────────────────

    @Query("""
        SELECT
            l.conta.id                                          AS contaId,
            CAST(l.conta.tipo AS string)                        AS tipoConta,
            COALESCE(SUM(CASE WHEN l.direcao = 'CREDITO' THEN l.valor ELSE 0 END), 0) AS totalCreditos,
            COALESCE(SUM(CASE WHEN l.direcao = 'DEBITO'  THEN l.valor ELSE 0 END), 0) AS totalDebitos
        FROM Lancamento l
        JOIN l.transacao t
        WHERE t.data BETWEEN :inicio AND :fim
          AND t.deletedAt IS NULL
          AND t.status <> 'CANCELADO'
          AND (t.status = 'PAGO' OR (l.conta.tipo = 'CARTAO_CREDITO' AND t.status = 'PENDENTE'))
        GROUP BY l.conta.id, l.conta.tipo
    """)
    List<ResumoContaPeriodoProjection> resumoTodasContasPorPeriodo(
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    // ─────────────────────────────────────────────────────────────────────────
    // Resumo incluindo transações PENDENTES — usado para projeção do mês
    // ─────────────────────────────────────────────────────────────────────────

    @Query("""
        SELECT
            l.conta.id                                          AS contaId,
            CAST(l.conta.tipo AS string)                        AS tipoConta,
            COALESCE(SUM(CASE WHEN l.direcao = 'CREDITO' THEN l.valor ELSE 0 END), 0) AS totalCreditos,
            COALESCE(SUM(CASE WHEN l.direcao = 'DEBITO'  THEN l.valor ELSE 0 END), 0) AS totalDebitos
        FROM Lancamento l
        JOIN l.transacao t
        WHERE t.data BETWEEN :inicio AND :fim
          AND t.deletedAt IS NULL
          AND t.status <> 'CANCELADO'
        GROUP BY l.conta.id, l.conta.tipo
    """)
    List<ResumoContaPeriodoProjection> resumoTodasContasComPendentes(
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    // ─────────────────────────────────────────────────────────────────────────
    // Totais globais (para fluxo de caixa e comparativo)
    // ─────────────────────────────────────────────────────────────────────────

    @Query("""
        SELECT COALESCE(SUM(l.valor), 0)
        FROM Lancamento l JOIN l.transacao t
        WHERE l.direcao = 'CREDITO'
          AND l.conta.tipo <> 'CARTAO_CREDITO'
          AND t.data BETWEEN :inicio AND :fim
          AND t.status = 'PAGO'
          AND t.deletedAt IS NULL AND t.status <> 'CANCELADO'
    """)
    BigDecimal somarTotalCreditosPeriodo(
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query("""
        SELECT COALESCE(SUM(l.valor), 0)
        FROM Lancamento l JOIN l.transacao t
        WHERE l.direcao = 'DEBITO'
          AND t.data BETWEEN :inicio AND :fim
          AND t.deletedAt IS NULL AND t.status <> 'CANCELADO'
          AND (t.status = 'PAGO' OR (l.conta.tipo = 'CARTAO_CREDITO' AND t.status = 'PENDENTE'))
    """)
    BigDecimal somarTotalDebitosPeriodo(
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    // ─────────────────────────────────────────────────────────────────────────
    // ✅ FIX Problema 2 — projection tipada (antes retornava Object[])
    //    Traz gastos de TODAS as categorias de uma vez; orçamentos montam em memória.
    // ─────────────────────────────────────────────────────────────────────────

    @Query("""
        SELECT
            t.categoria.id    AS categoriaId,
            t.categoria.nome  AS nomeCategoria,
            COALESCE(SUM(l.valor), 0) AS total
        FROM Lancamento l JOIN l.transacao t
        WHERE l.direcao = 'DEBITO'
          AND t.categoria IS NOT NULL
          AND t.data BETWEEN :inicio AND :fim
          AND t.deletedAt IS NULL AND t.status <> 'CANCELADO'
          AND (t.status = 'PAGO' OR (l.conta.tipo = 'CARTAO_CREDITO' AND t.status = 'PENDENTE'))
        GROUP BY t.categoria.id, t.categoria.nome
        ORDER BY total DESC
    """)
    List<GastoCategoriaProjection> somarGastosPorCategoriaPeriodo(
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);
}
