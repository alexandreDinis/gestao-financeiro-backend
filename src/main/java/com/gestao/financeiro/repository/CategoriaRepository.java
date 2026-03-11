package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.Categoria;
import com.gestao.financeiro.entity.enums.TipoCategoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    Page<Categoria> findByTipo(TipoCategoria tipo, Pageable pageable);

    List<Categoria> findByCategoriaPaiId(Long categoriaPaiId);

    boolean existsByNomeAndTenantId(String nome, Long tenantId);

    long countByTenantId(Long tenantId);
}
