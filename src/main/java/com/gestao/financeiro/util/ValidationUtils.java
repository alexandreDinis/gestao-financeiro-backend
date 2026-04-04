package com.gestao.financeiro.util;

import com.gestao.financeiro.exception.InvalidIdException;
import lombok.extern.slf4j.Slf4j;

/**
 * Utilitários para validações comuns de negócio e infraestrutura.
 */
@Slf4j
public class ValidationUtils {

    /**
     * Valida se um ID é positivo.
     * @param id O ID a ser validado.
     * @param entity Nome da entidade para contexto no log/erro (Ex: "Dívida").
     * @throws InvalidIdException se o ID for nulo ou <= 0.
     */
    public static void validateId(Long id, String entity) {
        if (id == null || id <= 0) {
            log.warn("Tentativa de acesso com ID inválido para {}: {}", entity, id);
            throw new InvalidIdException(entity + " ID inválido: " + id);
        }
    }
}
