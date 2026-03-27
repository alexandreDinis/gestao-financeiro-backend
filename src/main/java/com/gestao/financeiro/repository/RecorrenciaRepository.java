package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.Recorrencia;
import com.gestao.financeiro.entity.enums.StatusRecorrencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecorrenciaRepository extends JpaRepository<Recorrencia, Long> {
    List<Recorrencia> findByStatus(StatusRecorrencia status);
}
