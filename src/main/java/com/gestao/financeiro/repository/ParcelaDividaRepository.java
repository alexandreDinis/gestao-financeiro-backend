package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.ParcelaDivida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParcelaDividaRepository extends JpaRepository<ParcelaDivida, Long> {
    List<ParcelaDivida> findByDividaId(Long dividaId);
}
