package com.gestao.financeiro.dto.response;

import com.gestao.financeiro.entity.enums.TipoTransacao;
import com.gestao.financeiro.entity.enums.StatusTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record UltimaTransacaoResponse(
        Long id,
        String descricao,
        BigDecimal valor,
        TipoTransacao tipo,
        StatusTransacao status,
        LocalDate data,
        LocalDateTime createdAt,
        Long contaId,
        String contaNome,
        Long categoriaId,
        String categoriaNome,
        String categoriaCor
) {}
