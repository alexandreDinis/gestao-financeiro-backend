package com.gestao.financeiro.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PagarTransacaoRequest(
        @NotNull(message = "ID da conta é obrigatório")
        Long contaId,

        @NotNull(message = "Valor é obrigatório")
        BigDecimal valor,

        LocalDate dataPagamento
) {}
