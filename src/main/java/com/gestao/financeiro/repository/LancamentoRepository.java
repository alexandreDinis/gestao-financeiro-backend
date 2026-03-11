package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.Lancamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {

    List<Lancamento> findByTransacaoId(Long transacaoId);

    List<Lancamento> findByContaId(Long contaId);

    /**
     * Soma receitas (CREDITO) por conta num período.
     */
    @Query("""
        SELECT COALESCE(SUM(l.valor), 0)
        FROM Lancamento l
        JOIN l.transacao t
        WHERE l.conta.id = :contaId
          AND l.direcao = 'CREDITO'
          AND t.data BETWEEN :inicio AND :fim
          AND t.status = 'PAGO'
    """)
    BigDecimal somarCreditosPorContaEPeriodo(
            @Param("contaId") Long contaId,
            @Param("inicio") java.time.LocalDate inicio,
            @Param("fim") java.time.LocalDate fim);

    /**
     * Soma despesas (DEBITO) por conta num período.
     */
    @Query("""
        SELECT COALESCE(SUM(l.valor), 0)
        FROM Lancamento l
        JOIN l.transacao t
        WHERE l.conta.id = :contaId
          AND l.direcao = 'DEBITO'
          AND t.data BETWEEN :inicio AND :fim
          AND t.status = 'PAGO'
    """)
    BigDecimal somarDebitosPorContaEPeriodo(
            @Param("contaId") Long contaId,
            @Param("inicio") java.time.LocalDate inicio,
            @Param("fim") java.time.LocalDate fim);
}
