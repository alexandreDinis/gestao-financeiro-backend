package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.Conta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface ContaRepository extends JpaRepository<Conta, Long> {

    Page<Conta> findByAtivaTrue(Pageable pageable);

    long countByTenantId(Long tenantId);

    boolean existsByNomeAndTenantId(String nome, Long tenantId);

    /**
     * Calcula saldo real da conta a partir dos lançamentos.
     * saldo = saldoInicial + SUM(creditos) - SUM(debitos)
     *
     * Retorna saldoInicial se não há lançamentos.
     */
    @Query("""
        SELECT c.saldoInicial
            + COALESCE(SUM(CASE WHEN l.direcao = 'CREDITO' THEN l.valor ELSE 0 END), 0)
            - COALESCE(SUM(CASE WHEN l.direcao = 'DEBITO' THEN l.valor ELSE 0 END), 0)
        FROM Conta c
        LEFT JOIN Lancamento l ON l.conta.id = c.id AND l.deletedAt IS NULL
        WHERE c.id = :contaId
    """)
    BigDecimal calcularSaldo(@Param("contaId") Long contaId);
}
