package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.TransacaoRecorrente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransacaoRecorrenteRepository extends JpaRepository<TransacaoRecorrente, Long> {

    Page<TransacaoRecorrente> findByAtivaTrue(Pageable pageable);

    List<TransacaoRecorrente> findByAtivaTrue();
}
