package com.gestao.financeiro.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.PAYMENT_REQUIRED) // HTTP 402
public class PlanLimitExceededException extends RuntimeException {

    public PlanLimitExceededException(String recurso, int limite) {
        super(String.format("Limite do plano excedido para o recurso '%s'. O limite máximo é %d. Faça upgrade do seu plano.", recurso, limite));
    }

    public PlanLimitExceededException(String mensagem) {
        super(mensagem);
    }
}
