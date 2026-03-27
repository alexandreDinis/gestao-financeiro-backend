package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.CartaoCredito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartaoCreditoRepository extends JpaRepository<CartaoCredito, Long> {

    Page<CartaoCredito> findAll(Pageable pageable);

    boolean existsByContaId(Long contaId);

    java.util.Optional<CartaoCredito> findByContaId(Long contaId);
}
