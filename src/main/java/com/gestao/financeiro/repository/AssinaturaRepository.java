package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.Assinatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssinaturaRepository extends JpaRepository<Assinatura, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"plano"})
    Optional<Assinatura> findByTenantId(Long tenantId);
}
