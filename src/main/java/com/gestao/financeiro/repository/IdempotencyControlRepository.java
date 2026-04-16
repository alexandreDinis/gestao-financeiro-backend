package com.gestao.financeiro.repository;

import com.gestao.financeiro.entity.IdempotencyControl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface IdempotencyControlRepository extends JpaRepository<IdempotencyControl, Long> {
    Optional<IdempotencyControl> findByIdempotencyKey(String idempotencyKey);
}
