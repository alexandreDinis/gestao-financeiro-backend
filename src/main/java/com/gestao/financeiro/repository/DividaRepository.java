package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.Divida;
import com.gestao.financeiro.entity.enums.TipoDivida;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DividaRepository extends JpaRepository<Divida, Long> {
    
    @EntityGraph(attributePaths = {"pessoa", "parcelas"})
    List<Divida> findByTipo(TipoDivida tipo);

    @EntityGraph(attributePaths = {"pessoa", "parcelas"})
    List<Divida> findByRecorrenteTrue();

    @Query("""
        SELECT DISTINCT d FROM Divida d
        LEFT JOIN FETCH d.pessoa
        LEFT JOIN FETCH d.parcelas p
        WHERE (:tipo IS NULL OR d.tipo = :tipo)
          AND (:pessoaId IS NULL OR d.pessoa.id = :pessoaId)
          AND (:ano IS NULL OR EXISTS (
                SELECT p2 FROM ParcelaDivida p2 
                WHERE p2.divida = d 
                  AND YEAR(p2.dataVencimento) = :ano 
                  AND (:mes IS NULL OR MONTH(p2.dataVencimento) = :mes)
                  AND (:status IS NULL 
                       OR (:status = 'PENDENTE' AND p2.status != com.gestao.financeiro.entity.enums.StatusTransacao.PAGO)
                       OR (:status = 'PAGA' AND p2.status = com.gestao.financeiro.entity.enums.StatusTransacao.PAGO)
                       OR (:status = 'ATRASADA' AND p2.status = com.gestao.financeiro.entity.enums.StatusTransacao.ATRASADO)
                  )
          ))
          AND (:status IS NULL OR (:ano IS NULL AND CAST(d.status AS string) = :status) OR (:ano IS NOT NULL))
          AND d.deletedAt IS NULL
        ORDER BY d.dataInicio DESC
    """)
    Page<Divida> buscarComFiltros(
            @Param("tipo") TipoDivida tipo,
            @Param("pessoaId") Long pessoaId,
            @Param("ano") Integer ano,
            @Param("mes") Integer mes,
            @Param("status") String status,
            Pageable pageable);

    @Query("""
        SELECT SUM(p.valor) FROM ParcelaDivida p
        JOIN p.divida d
        WHERE (:tipo IS NULL OR d.tipo = :tipo)
          AND (:pessoaId IS NULL OR d.pessoa.id = :pessoaId)
          AND YEAR(p.dataVencimento) = :ano 
          AND (:mes IS NULL OR MONTH(p.dataVencimento) = :mes)
          AND (:status IS NULL 
               OR (:status = 'PENDENTE' AND p.status != com.gestao.financeiro.entity.enums.StatusTransacao.PAGO)
               OR (:status = 'PAGA' AND p.status = com.gestao.financeiro.entity.enums.StatusTransacao.PAGO)
               OR (:status = 'ATRASADA' AND p.status = com.gestao.financeiro.entity.enums.StatusTransacao.ATRASADO)
          )
          AND d.deletedAt IS NULL
          AND p.deletedAt IS NULL
    """)
    java.math.BigDecimal somarParcelasNoMes(
            @Param("tipo") TipoDivida tipo,
            @Param("pessoaId") Long pessoaId,
            @Param("ano") Integer ano,
            @Param("mes") Integer mes,
            @Param("status") String status);

    @Query("""
        SELECT SUM(d.valorRestante) FROM Divida d
        WHERE (:tipo IS NULL OR d.tipo = :tipo)
          AND (:pessoaId IS NULL OR d.pessoa.id = :pessoaId)
          AND (:status IS NULL OR CAST(d.status AS string) = :status)
          AND d.deletedAt IS NULL
    """)
    java.math.BigDecimal somarValorRestante(
            @Param("tipo") TipoDivida tipo,
            @Param("pessoaId") Long pessoaId,
            @Param("status") String status);

    @Query("""
        SELECT DISTINCT d FROM Divida d
        LEFT JOIN FETCH d.pessoa
        LEFT JOIN FETCH d.parcelas p
        WHERE (:tipo IS NULL OR d.tipo = :tipo)
          AND (:pessoaId IS NULL OR d.pessoa.id = :pessoaId)
          AND (:ano IS NULL OR EXISTS (
                SELECT p2 FROM ParcelaDivida p2 
                WHERE p2.divida = d 
                  AND YEAR(p2.dataVencimento) = :ano 
                  AND (:mes IS NULL OR MONTH(p2.dataVencimento) = :mes)
                  AND (:status IS NULL 
                       OR (:status = 'PENDENTE' AND p2.status != com.gestao.financeiro.entity.enums.StatusTransacao.PAGO)
                       OR (:status = 'PAGA' AND p2.status = com.gestao.financeiro.entity.enums.StatusTransacao.PAGO)
                       OR (:status = 'ATRASADA' AND p2.status = com.gestao.financeiro.entity.enums.StatusTransacao.ATRASADO)
                  )
          ))
          AND (:status IS NULL OR (:ano IS NULL AND CAST(d.status AS string) = :status) OR (:ano IS NOT NULL))
          AND d.deletedAt IS NULL
        ORDER BY d.dataInicio DESC
    """)
    List<Divida> buscarComFiltrosSemPaginacao(
            @Param("tipo") TipoDivida tipo,
            @Param("pessoaId") Long pessoaId,
            @Param("ano") Integer ano,
            @Param("mes") Integer mes,
            @Param("status") String status);
}
