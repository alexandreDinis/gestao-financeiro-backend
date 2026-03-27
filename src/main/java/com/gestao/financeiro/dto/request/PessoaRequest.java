package com.gestao.financeiro.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PessoaRequest(
        @NotBlank(message = "Nome é obrigatório")
        String nome,

        String telefone,

        String observacao
) {}
