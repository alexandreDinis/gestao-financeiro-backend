package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.Conta;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface ContaRepository extends JpaRepository<Conta, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT c FROM Conta c WHERE c.id = :id")
    Optional<Conta> findByIdWithLock(@Param("id") Long id);

    Page<Conta> findByAtivaTrue(Pageable pageable);

    java.util.List<Conta> findByTenantIdAndTipo(Long tenantId, com.gestao.financeiro.entity.enums.TipoConta tipo);

    long countByTenantId(Long tenantId);

    boolean existsByNomeAndTenantId(String nome, Long tenantId);

    /**
     * Calcula saldo real da conta a partir dos lançamentos.
     * saldo = saldoInicial + SUM(creditos) - SUM(debitos)
     *
     * Retorna saldoInicial se não há lançamentos.
     */
    @Query("""
        SELECT c.saldoInicial + (
            SELECT COALESCE(SUM(l.valor), 0)
            FROM Lancamento l
            WHERE l.conta.id = c.id
              AND l.direcao = 'CREDITO'
              AND l.transacao.deletedAt IS NULL
              AND l.transacao.status <> 'CANCELADO'
              AND (
                  l.transacao.status = 'PAGO'
                  OR (c.tipo = 'CARTAO_CREDITO' AND l.transacao.status = 'PENDENTE')
              )
              AND l.deletedAt IS NULL
        ) - (
            SELECT COALESCE(SUM(l.valor), 0)
            FROM Lancamento l
            WHERE l.conta.id = c.id
              AND l.direcao = 'DEBITO'
              AND l.transacao.deletedAt IS NULL
              AND l.transacao.status <> 'CANCELADO'
              AND (
                  l.transacao.status = 'PAGO'
                  OR (c.tipo = 'CARTAO_CREDITO' AND l.transacao.status = 'PENDENTE')
              )
              AND l.deletedAt IS NULL
        )
        FROM Conta c
        WHERE c.id = :contaId
    """)
    BigDecimal calcularSaldo(@Param("contaId") Long contaId);
}
