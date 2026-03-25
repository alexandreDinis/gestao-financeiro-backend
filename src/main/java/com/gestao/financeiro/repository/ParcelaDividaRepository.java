package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.ParcelaDivida;
import com.gestao.financeiro.entity.enums.StatusTransacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface ParcelaDividaRepository extends JpaRepository<ParcelaDivida, Long> {
    List<ParcelaDivida> findByDividaId(Long dividaId);

    List<ParcelaDivida> findByStatusInAndDataVencimentoBetween(
            Collection<StatusTransacao> statuses, 
            LocalDate inicio, 
            LocalDate fim
    );
}
