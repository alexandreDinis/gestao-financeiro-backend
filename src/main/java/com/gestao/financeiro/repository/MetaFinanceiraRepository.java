package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.MetaFinanceira;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MetaFinanceiraRepository extends JpaRepository<MetaFinanceira, Long> {

    Page<MetaFinanceira> findByConcluidaFalse(Pageable pageable);

    long countByTenantId(Long tenantId);

    /**
     * Todas as metas ativas ordenadas por prazo (mais urgente primeiro).
     * Usado no dashboard para exibir progresso.
     */
    @Query("SELECT m FROM MetaFinanceira m WHERE m.concluida = false ORDER BY m.prazo ASC NULLS LAST")
    List<MetaFinanceira> findAllAtivas();
}
