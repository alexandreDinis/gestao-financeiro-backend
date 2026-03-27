package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.Orcamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrcamentoRepository extends JpaRepository<Orcamento, Long> {

    List<Orcamento> findByMesAndAno(Integer mes, Integer ano);

    boolean existsByCategoriaIdAndMesAndAnoAndTenantId(Long categoriaId, Integer mes, Integer ano, Long tenantId);

    /**
     * Orçamentos do mês com categoria carregada (fetch join — evita N+1).
     */
    @Query("""
        SELECT o FROM Orcamento o
        JOIN FETCH o.categoria
        WHERE o.mes = :mes AND o.ano = :ano
        ORDER BY o.categoria.nome ASC
    """)
    List<Orcamento> findByMesAndAnoWithCategoria(
            @Param("mes") Integer mes,
            @Param("ano") Integer ano);
}
