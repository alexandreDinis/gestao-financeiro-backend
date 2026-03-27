package com.gestao.financeiro.dto.response;

import com.gestao.financeiro.entity.enums.ScoreConfiabilidade;

import java.time.LocalDateTime;

public record PessoaResponse(
        Long id,
        String nome,
        String telefone,
        String observacao,
        ScoreConfiabilidade score,
        Integer totalEmprestimos,
        Integer totalPagosEmDia,
        Integer totalAtrasados,
        LocalDateTime createdAt
) {}
