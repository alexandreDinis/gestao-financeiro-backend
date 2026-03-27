package com.gestao.financeiro.mapper;

import com.gestao.financeiro.dto.request.TransacaoRecorrenteRequest;
import com.gestao.financeiro.dto.response.TransacaoRecorrenteResponse;
import com.gestao.financeiro.entity.TransacaoRecorrente;
import org.springframework.stereotype.Component;

@Component
public class TransacaoRecorrenteMapper {

    public TransacaoRecorrenteResponse toResponse(TransacaoRecorrente entity) {
        return new TransacaoRecorrenteResponse(
                entity.getId(),
                entity.getDescricao(),
                entity.getValor(),
                entity.getTipo(),
                entity.getPeriodicidade(),
                entity.getDataInicio(),
                entity.getDataFim(),
                entity.getDiaVencimento(),
                entity.getAtiva(),
                entity.getCategoria() != null ? entity.getCategoria().getId() : null,
                entity.getCategoria() != null ? entity.getCategoria().getNome() : null,
                entity.getConta() != null ? entity.getConta().getId() : null,
                entity.getConta() != null ? entity.getConta().getNome() : null,
                entity.getCreatedAt()
        );
    }

    public TransacaoRecorrente toEntity(TransacaoRecorrenteRequest request) {
        return TransacaoRecorrente.builder()
                .descricao(request.descricao())
                .valor(request.valor())
                .tipo(request.tipo())
                .periodicidade(request.periodicidade())
                .dataInicio(request.dataInicio())
                .dataFim(request.dataFim())
                .diaVencimento(request.diaVencimento())
                .ativa(true)
                .build();
    }

    public void updateEntity(TransacaoRecorrente entity, TransacaoRecorrenteRequest request) {
        entity.setDescricao(request.descricao());
        entity.setValor(request.valor());
        entity.setTipo(request.tipo());
        entity.setPeriodicidade(request.periodicidade());
        entity.setDataInicio(request.dataInicio());
        entity.setDataFim(request.dataFim());
        entity.setDiaVencimento(request.diaVencimento());
    }
}
