package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.PrevisaoAjuste;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PrevisaoAjusteRepository extends JpaRepository<PrevisaoAjuste, Long> {
    Optional<PrevisaoAjuste> findByTenantIdAndMesAndAno(Long tenantId, Integer mes, Integer ano);
}
