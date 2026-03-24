package com.gestao.financeiro.dto.response;

import com.gestao.financeiro.entity.enums.StatusTransacao;
import com.gestao.financeiro.entity.enums.TipoTransacao;
import com.gestao.financeiro.entity.enums.TipoDespesa;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TransacaoResponse(
        Long id,
        String descricao,
        BigDecimal valor,
        String data,
        String dataVencimento,
        String dataPagamento,
        TipoTransacao tipo,
        TipoDespesa tipoDespesa,
        StatusTransacao status,
        String observacao,
        CategoriaResponse categoria,
        List<LancamentoResponse> lancamentos,
        LocalDateTime createdAt
) {}
