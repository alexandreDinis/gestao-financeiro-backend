package com.gestao.financeiro.mapper;

import com.gestao.financeiro.dto.request.OrcamentoRequest;
import com.gestao.financeiro.dto.response.OrcamentoResponse;
import com.gestao.financeiro.entity.Orcamento;
import org.springframework.stereotype.Component;

@Component
public class OrcamentoMapper {

    public OrcamentoResponse toResponse(Orcamento entity) {
        return new OrcamentoResponse(
                entity.getId(),
                entity.getLimite(),
                entity.getMes(),
                entity.getAno(),
                entity.getCategoria().getId(),
                entity.getCategoria().getNome(),
                entity.getCategoria().getCor(),
                entity.getCreatedAt()
        );
    }

    public Orcamento toEntity(OrcamentoRequest request) {
        return Orcamento.builder()
                .limite(request.limite())
                .mes(request.mes())
                .ano(request.ano())
                .build();
    }
}
