package com.gestao.financeiro.mapper;

import com.gestao.financeiro.dto.request.MetaFinanceiraRequest;
import com.gestao.financeiro.dto.response.MetaFinanceiraResponse;
import com.gestao.financeiro.entity.MetaFinanceira;
import org.springframework.stereotype.Component;

@Component
public class MetaFinanceiraMapper {

    public MetaFinanceiraResponse toResponse(MetaFinanceira entity) {
        return new MetaFinanceiraResponse(
                entity.getId(),
                entity.getNome(),
                entity.getValorAlvo(),
                entity.getValorAtual(),
                entity.getProgresso(),
                entity.getPrazo(),
                entity.getDescricao(),
                entity.getConcluida(),
                entity.getCreatedAt()
        );
    }

    public MetaFinanceira toEntity(MetaFinanceiraRequest request) {
        return MetaFinanceira.builder()
                .nome(request.nome())
                .valorAlvo(request.valorAlvo())
                .prazo(request.prazo())
                .descricao(request.descricao())
                .build();
    }

    public void updateEntity(MetaFinanceira entity, MetaFinanceiraRequest request) {
        entity.setNome(request.nome());
        entity.setValorAlvo(request.valorAlvo());
        entity.setPrazo(request.prazo());
        entity.setDescricao(request.descricao());
    }
}
