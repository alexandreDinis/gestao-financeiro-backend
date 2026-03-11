package com.gestao.financeiro.dto.response;

import com.gestao.financeiro.entity.enums.TipoCategoria;

import java.time.LocalDateTime;

public record CategoriaResponse(
        Long id,
        String nome,
        TipoCategoria tipo,
        String cor,
        String icone,
        Long categoriaPaiId,
        String categoriaPaiNome,
        LocalDateTime createdAt
) {}
