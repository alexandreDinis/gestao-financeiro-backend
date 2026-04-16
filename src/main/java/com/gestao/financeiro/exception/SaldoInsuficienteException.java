package com.gestao.financeiro.exception;

import lombok.Getter;
import java.math.BigDecimal;

/**
 * Exceção lançada quando uma conta não possui saldo suficiente para uma operação.
 */
@Getter
public class SaldoInsuficienteException extends BusinessException {
    
    private final BigDecimal saldoAtual;
    private final BigDecimal valorOperacao;
    private final String nomeConta;

    public SaldoInsuficienteException(String nomeConta, BigDecimal saldoAtual, BigDecimal valorOperacao) {
        super(String.format("Saldo insuficiente na conta %s. Saldo: %.2f, Necessário: %.2f", 
                nomeConta, saldoAtual, valorOperacao));
        this.nomeConta = nomeConta;
        this.saldoAtual = saldoAtual;
        this.valorOperacao = valorOperacao;
    }
}
