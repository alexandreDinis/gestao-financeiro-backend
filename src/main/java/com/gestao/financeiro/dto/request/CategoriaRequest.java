package com.gestao.financeiro.dto.request;

import com.gestao.financeiro.entity.enums.TipoCategoria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CategoriaRequest(
        @NotBlank(message = "Nome é obrigatório")
        String nome,

        @NotNull(message = "Tipo de categoria é obrigatório")
        TipoCategoria tipo,

        String cor,

        String icone,

        Long categoriaPaiId
) {}
