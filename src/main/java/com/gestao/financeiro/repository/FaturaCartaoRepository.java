package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.FaturaCartao;
import com.gestao.financeiro.entity.enums.StatusFatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FaturaCartaoRepository extends JpaRepository<FaturaCartao, Long> {

    Optional<FaturaCartao> findByCartaoIdAndMesReferenciaAndAnoReferencia(
            Long cartaoId, Integer mes, Integer ano);

    List<FaturaCartao> findByCartaoIdOrderByAnoReferenciaDescMesReferenciaDesc(Long cartaoId);

    List<FaturaCartao> findByStatus(StatusFatura status);
}
