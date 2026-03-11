package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.Transacao;
import com.gestao.financeiro.entity.enums.StatusTransacao;
import com.gestao.financeiro.entity.enums.TipoTransacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

    Optional<Transacao> findByIdempotencyKey(String idempotencyKey);

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
          AND (:status IS NULL OR t.status = :status)
    """)
    Page<Transacao> buscarComFiltros(
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim,
            @Param("categoriaId") Long categoriaId,
            @Param("contaId") Long contaId,
            @Param("tipo") TipoTransacao tipo,
            @Param("status") StatusTransacao status,
            Pageable pageable);

    long countByTenantIdAndDataBetween(Long tenantId, LocalDate dataInicio, LocalDate dataFim);
}
