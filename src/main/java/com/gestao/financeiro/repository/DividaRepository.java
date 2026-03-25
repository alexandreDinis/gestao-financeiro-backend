package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.Divida;
import com.gestao.financeiro.entity.enums.TipoDivida;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DividaRepository extends JpaRepository<Divida, Long> {
    
    @EntityGraph(attributePaths = {"pessoa", "parcelas"})
    List<Divida> findByTipo(TipoDivida tipo);

    @EntityGraph(attributePaths = {"pessoa", "parcelas"})
    Page<Divida> findByTipo(TipoDivida tipo, Pageable pageable);
}
