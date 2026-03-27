package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.Parcela;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParcelaRepository extends JpaRepository<Parcela, Long> {

    List<Parcela> findByTransacaoId(Long transacaoId);

    List<Parcela> findByFaturaId(Long faturaId);
}
