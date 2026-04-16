package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.FaturaCartao;
import com.gestao.financeiro.entity.enums.StatusFatura;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FaturaCartaoRepository extends JpaRepository<FaturaCartao, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT f FROM FaturaCartao f WHERE f.id = :id")
    Optional<FaturaCartao> findByIdWithLock(@Param("id") Long id);

    Optional<FaturaCartao> findByCartaoIdAndMesReferenciaAndAnoReferencia(
            Long cartaoId, Integer mes, Integer ano);

    List<FaturaCartao> findByCartaoIdOrderByAnoReferenciaDescMesReferenciaDesc(Long cartaoId);

    List<FaturaCartao> findByStatus(StatusFatura status);

    List<FaturaCartao> findByCartaoIdAndStatusIn(Long cartaoId, List<StatusFatura> statuses);
}
