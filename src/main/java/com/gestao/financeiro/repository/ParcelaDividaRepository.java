package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.ParcelaDivida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ParcelaDividaRepository extends JpaRepository<ParcelaDivida, Long> {
    List<ParcelaDivida> findByDividaId(Long dividaId);

    @org.springframework.data.jpa.repository.Query(value = "SELECT COUNT(*) > 0 FROM parcela_divida WHERE divida_id = :dividaId AND data_vencimento = :vencimento", nativeQuery = true)
    boolean existsByDividaIdAndDataVencimento(@org.springframework.data.repository.query.Param("dividaId") Long dividaId, @org.springframework.data.repository.query.Param("vencimento") LocalDate vencimento);

    @org.springframework.data.jpa.repository.Query("""
        SELECT p FROM ParcelaDivida p
        JOIN FETCH p.divida d
        WHERE p.transacaoGerada IS NULL
          AND d.deletedAt IS NULL
          AND (
               (p.dataVencimento BETWEEN :inicio AND :limite)
               OR (p.dataVencimento < :hoje)
          )
    """)
    List<ParcelaDivida> findProximasParcelas(
            @org.springframework.data.repository.query.Param("hoje") LocalDate hoje,
            @org.springframework.data.repository.query.Param("inicio") LocalDate inicio,
            @org.springframework.data.repository.query.Param("limite") LocalDate limite,
            org.springframework.data.domain.Pageable pageable
    );

    @org.springframework.data.jpa.repository.Query("""
        SELECT COALESCE(SUM(p.valor), 0)
        FROM ParcelaDivida p
        JOIN p.divida d
        WHERE d.tenantId = :tenantId
          AND d.tipo = :tipo
          AND p.dataVencimento BETWEEN :inicio AND :fim
          AND p.status = 'PENDENTE'
          AND d.deletedAt IS NULL
    """)
    java.math.BigDecimal somarParcelasPendentesPorPeriodoETipo(
            @org.springframework.data.repository.query.Param("tenantId") Long tenantId,
            @org.springframework.data.repository.query.Param("inicio") java.time.LocalDate inicio,
            @org.springframework.data.repository.query.Param("fim") java.time.LocalDate fim,
            @org.springframework.data.repository.query.Param("tipo") com.gestao.financeiro.entity.enums.TipoDivida tipo
    );
}
