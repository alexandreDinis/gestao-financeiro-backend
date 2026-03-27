package com.gestao.financeiro.mapper;

import com.gestao.financeiro.dto.request.PessoaRequest;
import com.gestao.financeiro.dto.response.PessoaResponse;
import com.gestao.financeiro.entity.Pessoa;
import org.springframework.stereotype.Component;

@Component
public class PessoaMapper {

    public PessoaResponse toResponse(Pessoa entity) {
        return new PessoaResponse(
                entity.getId(),
                entity.getNome(),
                entity.getTelefone(),
                entity.getObservacao(),
                entity.getScore(),
                entity.getTotalEmprestimos(),
                entity.getTotalPagosEmDia(),
                entity.getTotalAtrasados(),
                entity.getCreatedAt()
        );
    }

    public Pessoa toEntity(PessoaRequest request) {
        return Pessoa.builder()
                .nome(request.nome())
                .telefone(request.telefone())
                .observacao(request.observacao())
                .build();
    }

    public void updateEntity(Pessoa entity, PessoaRequest request) {
        entity.setNome(request.nome());
        entity.setTelefone(request.telefone());
        entity.setObservacao(request.observacao());
    }
}
