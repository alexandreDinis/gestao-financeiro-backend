package com.gestao.financeiro.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record GerarOrcamentoLoteRequest(
        @NotNull(message = "Mês é obrigatório")
        @Min(value = 1, message = "Mês deve ser entre 1 e 12")
        @Max(value = 12, message = "Mês deve ser entre 1 e 12")
        Integer mes,

        @NotNull(message = "Ano é obrigatório")
        @Min(value = 2000, message = "Ano inválido")
        Integer ano,

        @NotEmpty(message = "Lista de orçamentos não pode ser vazia")
        @Valid
        List<OrcamentoItemLoteRequest> orcamentos
) {}
