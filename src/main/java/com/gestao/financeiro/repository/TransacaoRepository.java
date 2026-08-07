package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.Transacao;
import com.gestao.financeiro.entity.enums.StatusTransacao;
import com.gestao.financeiro.entity.enums.TipoTransacao;
import com.gestao.financeiro.entity.enums.TipoDespesa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

    Optional<Transacao> findByIdempotencyKey(String idempotencyKey);

    List<Transacao> findByRecorrenciaId(Long recorrenciaId);

    boolean existsByRecorrenciaIdAndReferencia(Long recorrenciaId, YearMonth referencia);
    
    @Query(value = "SELECT COUNT(*) > 0 FROM transacao WHERE recorrencia_id = :recorrenciaId AND referencia = :referencia", nativeQuery = true)
    boolean existsByRecorrenciaIdAndReferenciaIgnoreSoftDelete(@Param("recorrenciaId") Long recorrenciaId, @Param("referencia") String referencia);

    @Query("""
        SELECT t FROM Transacao t
        LEFT JOIN FETCH t.categoria
        LEFT JOIN FETCH t.usuario
        WHERE (CAST(:dataInicio AS LocalDate) IS NULL OR t.data >= :dataInicio)
          AND (CAST(:dataFim AS LocalDate) IS NULL OR t.data <= :dataFim)
          AND (CAST(:categoriaId AS long) IS NULL OR t.categoria.id = :categoriaId)
          AND (CAST(:contaId AS long) IS NULL OR EXISTS (
                SELECT l FROM Lancamento l WHERE l.transacao = t AND l.conta.id = :contaId
          ))
          AND (:tipo IS NULL OR t.tipo = :tipo)
          AND (:tipoDespesa IS NULL OR t.tipoDespesa = :tipoDespesa)
          AND (:status IS NULL OR t.status = :status)
          AND (:geradoAutomaticamente IS NULL OR t.geradoAutomaticamente = :geradoAutomaticamente)
          AND (:busca IS NULL OR LOWER(CAST(t.descricao AS string)) LIKE LOWER(CONCAT('%', CAST(:busca AS string), '%')))
          AND (t.numeroParcelas IS NULL OR t.numeroParcelas <= 1)
          AND NOT EXISTS (
                SELECT l2 FROM Lancamento l2 WHERE l2.transacao = t AND l2.conta.tipo = 'CARTAO_CREDITO'
          )
          AND t.deletedAt IS NULL
    """)
    Page<Transacao> buscarComFiltros(
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim,
            @Param("categoriaId") Long categoriaId,
            @Param("contaId") Long contaId,
            @Param("tipo") TipoTransacao tipo,
            @Param("tipoDespesa") TipoDespesa tipoDespesa,
            @Param("status") StatusTransacao status,
            @Param("geradoAutomaticamente") Boolean geradoAutomaticamente,
            @Param("busca") String busca,
            Pageable pageable);

    long countByTenantIdAndDataBetween(Long tenantId, LocalDate dataInicio, LocalDate dataFim);

    // ─────────────────────────────────────────────────────────────────────────
    // Dashboard — últimas N transações com fetch join (evita N+1)
    // ─────────────────────────────────────────────────────────────────────────

    @Query("""
        SELECT DISTINCT t FROM Transacao t
        LEFT JOIN FETCH t.categoria
        LEFT JOIN FETCH t.lancamentos l
        LEFT JOIN FETCH l.conta
        WHERE t.tenantId = :tenantId AND t.deletedAt IS NULL AND t.status <> 'CANCELADO'
          AND NOT EXISTS (
                SELECT l2 FROM Lancamento l2 WHERE l2.transacao = t AND l2.conta.tipo = 'CARTAO_CREDITO'
          )
        ORDER BY t.data DESC, t.id DESC
    """)
    List<Transacao> findUltimasTransacoes(@Param("tenantId") Long tenantId, Pageable pageable);

    // ─────────────────────────────────────────────────────────────────────────
    // Dashboard — próximos vencimentos até :dataLimite
    // ─────────────────────────────────────────────────────────────────────────

    @Query("""
        SELECT t FROM Transacao t
        LEFT JOIN FETCH t.lancamentos l
        LEFT JOIN FETCH l.conta
        WHERE t.tenantId = :tenantId
          AND t.status = 'PENDENTE'
          AND t.deletedAt IS NULL
          AND (t.numeroParcelas IS NULL OR t.numeroParcelas <= 1)
          AND (
               (COALESCE(t.dataVencimento, t.data) BETWEEN :inicio AND :dataLimite)
               OR (COALESCE(t.dataVencimento, t.data) < :hoje AND t.status = 'PENDENTE')
          )
        ORDER BY COALESCE(t.dataVencimento, t.data) ASC
    """)
    List<Transacao> findProximosVencimentos(
            @Param("tenantId") Long tenantId,
            @Param("hoje") LocalDate hoje,
            @Param("inicio") LocalDate inicio,
            @Param("dataLimite") LocalDate dataLimite,
            org.springframework.data.domain.Pageable pageable);

    // ─────────────────────────────────────────────────────────────────────────
    // Relatório — despesas pagas num período, com categoria + categoria pai
    // ─────────────────────────────────────────────────────────────────────────

    @Query("""
        SELECT t FROM Transacao t
        LEFT JOIN FETCH t.categoria c
        LEFT JOIN FETCH c.categoriaPai cp
        WHERE t.tenantId = :tenantId
          AND t.tipo = 'DESPESA'
          AND t.status = 'PAGO'
          AND t.data BETWEEN :inicio AND :fim
          AND t.deletedAt IS NULL
        ORDER BY t.data DESC
    """)
    List<Transacao> findDespesasPagasByPeriodo(
            @Param("tenantId") Long tenantId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query("""
        SELECT t FROM Transacao t
        LEFT JOIN FETCH t.categoria c
        LEFT JOIN FETCH c.categoriaPai cp
        WHERE t.tenantId = :tenantId
          AND t.tipo = 'RECEITA'
          AND t.status = 'PAGO'
          AND t.data BETWEEN :inicio AND :fim
          AND t.deletedAt IS NULL
        ORDER BY t.data DESC
    """)
    List<Transacao> findReceitasPagasByPeriodo(
            @Param("tenantId") Long tenantId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query("""
        SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t
        WHERE t.tenantId = :tenantId
          AND t.tipo = 'RECEITA'
          AND t.status = 'PAGO'
          AND t.data BETWEEN :inicio AND :fim
          AND t.deletedAt IS NULL
    """)
    BigDecimal somarReceitasPagasByPeriodo(
            @Param("tenantId") Long tenantId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query("""
        SELECT
            t.categoria.id AS categoriaId,
            t.categoria.nome AS nomeCategoria,
            CAST(EXTRACT(MONTH FROM t.data) AS int) AS mes,
            CAST(EXTRACT(YEAR FROM t.data) AS int) AS ano,
            COALESCE(SUM(t.valor), 0) AS total
        FROM Transacao t
        WHERE t.tipo = 'DESPESA'
          AND t.status = 'PAGO'
          AND t.recorrenciaId IS NULL
          AND (t.tipoDespesa IS NULL OR t.tipoDespesa = 'VARIAVEL')
          AND t.data BETWEEN :inicio AND :fim
          AND t.deletedAt IS NULL
          AND t.categoria IS NOT NULL
        GROUP BY t.categoria.id, t.categoria.nome, EXTRACT(YEAR FROM t.data), EXTRACT(MONTH FROM t.data)
    """)
    List<com.gestao.financeiro.repository.projection.GastoMensalCategoriaProjection> somarGastosVariaveisMensaisPorCategoria(
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);
}
