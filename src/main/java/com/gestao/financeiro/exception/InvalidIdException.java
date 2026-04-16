package com.gestao.financeiro.exception;

/**
 * Exceção lançada quando um ID fornecido é nulo, zero ou negativo.
 * Mapeada para 400 Bad Request no GlobalExceptionHandler.
 */
public class InvalidIdException extends RuntimeException {
    public InvalidIdException(String message) {
        super(message);
    }
}
