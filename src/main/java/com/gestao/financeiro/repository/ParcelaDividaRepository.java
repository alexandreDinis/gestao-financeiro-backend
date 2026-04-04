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
        WHERE p.dataVencimento BETWEEN :hoje AND :limite
          AND p.transacaoGerada IS NULL
          AND p.status IN (com.gestao.financeiro.entity.enums.StatusTransacao.PENDENTE, com.gestao.financeiro.entity.enums.StatusTransacao.ATRASADO)
          AND d.deletedAt IS NULL
    """)
    List<ParcelaDivida> findProximasParcelas(
            @org.springframework.data.repository.query.Param("hoje") LocalDate hoje,
            @org.springframework.data.repository.query.Param("limite") LocalDate limite,
            org.springframework.data.domain.Pageable pageable
    );
}
