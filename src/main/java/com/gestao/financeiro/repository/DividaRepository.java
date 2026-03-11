package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.Divida;
import com.gestao.financeiro.entity.enums.TipoDivida;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DividaRepository extends JpaRepository<Divida, Long> {
    Page<Divida> findByTipo(TipoDivida tipo, Pageable pageable);
}
