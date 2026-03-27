package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.Plano;
import com.gestao.financeiro.entity.enums.TipoPlano;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlanoRepository extends JpaRepository<Plano, Long> {
    Optional<Plano> findByTipo(TipoPlano tipo);
}
