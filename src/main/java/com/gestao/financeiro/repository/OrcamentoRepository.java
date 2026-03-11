package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.Orcamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrcamentoRepository extends JpaRepository<Orcamento, Long> {

    List<Orcamento> findByMesAndAno(Integer mes, Integer ano);

    boolean existsByCategoriaIdAndMesAndAnoAndTenantId(Long categoriaId, Integer mes, Integer ano, Long tenantId);
}
