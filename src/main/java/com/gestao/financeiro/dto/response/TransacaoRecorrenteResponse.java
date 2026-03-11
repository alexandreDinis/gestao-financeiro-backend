package com.gestao.financeiro.dto.response;

import com.gestao.financeiro.entity.enums.Periodicidade;
import com.gestao.financeiro.entity.enums.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransacaoRecorrenteResponse(
        Long id,
        String descricao,
        BigDecimal valor,
        TipoTransacao tipo,
        Periodicidade periodicidade,
        LocalDate dataInicio,
        LocalDate dataFim,
        Integer diaVencimento,
        Boolean ativa,
        Long categoriaId,
        String categoriaNome,
        Long contaId,
        String contaNome,
        LocalDateTime createdAt
) {}
