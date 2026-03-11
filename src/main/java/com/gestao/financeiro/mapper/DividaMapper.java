package com.gestao.financeiro.mapper;

import com.gestao.financeiro.dto.request.DividaRequest;
import com.gestao.financeiro.dto.response.DividaResponse;
import com.gestao.financeiro.dto.response.ParcelaDividaResponse;
import com.gestao.financeiro.entity.Divida;
import com.gestao.financeiro.entity.ParcelaDivida;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DividaMapper {

    public DividaResponse toResponse(Divida entity) {
        List<ParcelaDividaResponse> parcelas = entity.getParcelas().stream()
                .map(this::toParcelaResponse)
                .toList();

        return new DividaResponse(
                entity.getId(),
                entity.getPessoa().getId(),
                entity.getPessoa().getNome(),
                entity.getDescricao(),
                entity.getTipo(),
                entity.getValorTotal(),
                entity.getValorRestante(),
                entity.getDataInicio(),
                entity.getDataFim(),
                entity.getStatus(),
                entity.getObservacao(),
                parcelas,
                entity.getCreatedAt()
        );
    }

    public ParcelaDividaResponse toParcelaResponse(ParcelaDivida entity) {
        return new ParcelaDividaResponse(
                entity.getId(),
                entity.getNumeroParcela(),
                entity.getValor(),
                entity.getDataVencimento(),
                entity.getStatus(),
                entity.getDataPagamento(),
                entity.getTransacaoGerada() != null ? entity.getTransacaoGerada().getId() : null
        );
    }

    public Divida toEntity(DividaRequest request) {
        return Divida.builder()
                .descricao(request.descricao())
                .tipo(request.tipo())
                .valorTotal(request.valorTotal())
                .valorRestante(request.valorTotal()) // Inicia com o valor total
                .dataInicio(request.dataInicio())
                .dataFim(request.dataFim())
                .observacao(request.observacao())
                .build();
    }
}
