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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

@Repository
public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

    Optional<Transacao> findByIdempotencyKey(String idempotencyKey);

    boolean existsByRecorrenciaIdAndReferencia(Long recorrenciaId, YearMonth referencia);

    @Query("""
        SELECT t FROM Transacao t
        LEFT JOIN FETCH t.categoria
        LEFT JOIN FETCH t.usuario
        WHERE (:dataInicio IS NULL OR t.data >= :dataInicio)
          AND (:dataFim IS NULL OR t.data <= :dataFim)
          AND (:categoriaId IS NULL OR t.categoria.id = :categoriaId)
          AND (:contaId IS NULL OR EXISTS (
                SELECT l FROM Lancamento l WHERE l.transacao = t AND l.conta.id = :contaId
          ))
          AND (:tipo IS NULL OR t.tipo = :tipo)
          AND (:tipoDespesa IS NULL OR t.tipoDespesa = :tipoDespesa)
          AND (:status IS NULL OR t.status = :status)
          AND (:geradoAutomaticamente IS NULL OR t.geradoAutomaticamente = :geradoAutomaticamente)
          AND (:busca IS NULL OR LOWER(CAST(t.descricao AS string)) LIKE LOWER(CONCAT('%', CAST(:busca AS string), '%')))
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
        WHERE t.deletedAt IS NULL AND t.status <> 'CANCELADO'
        ORDER BY t.data DESC, t.id DESC
    """)
    List<Transacao> findUltimasTransacoes(org.springframework.data.domain.Pageable pageable);

    // ─────────────────────────────────────────────────────────────────────────
    // Dashboard — próximos vencimentos até :dataLimite
    // ─────────────────────────────────────────────────────────────────────────

    @Query("""
        SELECT DISTINCT t FROM Transacao t
        LEFT JOIN FETCH t.lancamentos l
        LEFT JOIN FETCH l.conta
        WHERE t.status = 'PENDENTE'
          AND t.data BETWEEN :hoje AND :dataLimite
          AND t.deletedAt IS NULL
        ORDER BY t.data ASC
    """)
    List<Transacao> findProximosVencimentos(
            @Param("hoje") LocalDate hoje,
            @Param("dataLimite") LocalDate dataLimite);
}
