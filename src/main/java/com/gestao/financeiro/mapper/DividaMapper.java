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
        return toResponse(entity, null, null, null);
    }

    public DividaResponse toResponse(Divida entity, Integer ano, Integer mes, com.gestao.financeiro.entity.enums.StatusDivida status) {
        List<ParcelaDividaResponse> parcelas = entity.getParcelas().stream()
                .filter(p -> {
                    if (ano != null) {
                        if (p.getDataVencimento().getYear() != (int)ano || (mes != null && p.getDataVencimento().getMonthValue() != (int)mes)) {
                            return false;
                        }
                    }
                    
                    if (status != null) {
                        if (status == com.gestao.financeiro.entity.enums.StatusDivida.PENDENTE) {
                            return p.getStatus() != com.gestao.financeiro.entity.enums.StatusTransacao.PAGO;
                        } else if (status == com.gestao.financeiro.entity.enums.StatusDivida.PAGA) {
                            return p.getStatus() == com.gestao.financeiro.entity.enums.StatusTransacao.PAGO;
                        } else if (status == com.gestao.financeiro.entity.enums.StatusDivida.ATRASADA) {
                            return p.getStatus() == com.gestao.financeiro.entity.enums.StatusTransacao.ATRASADO;
                        }
                    }
                    
                    return true;
                })
                .map(this::toParcelaResponse)
                .toList();

        return new DividaResponse(
                entity.getId(),
                entity.getPessoa() != null ? entity.getPessoa().getId() : null,
                entity.getPessoa() != null ? entity.getPessoa().getNome() : "Não vinculada",
                entity.getDescricao(),
                entity.getTipo(),
                entity.getValorTotal(),
                entity.getValorRestante(),
                entity.getDataInicio(),
                entity.getDataFim(),
                entity.getStatus(),
                entity.getObservacao(),
                parcelas,
                entity.getParcelas().size(),
                entity.getCreatedAt(),
                entity.getRecorrente(),
                entity.getPeriodicidade(),
                entity.getDiaVencimento(),
                entity.getValorParcelaRecorrente()
        );
    }

    public ParcelaDividaResponse toParcelaResponse(ParcelaDivida entity) {
        Long dividaId = entity.getDivida() != null ? entity.getDivida().getId() : null;
        String nomeDivida = entity.getDivida() != null ? entity.getDivida().getDescricao() : "Dívida";

        return new ParcelaDividaResponse(
                entity.getId(),
                dividaId,
                nomeDivida,
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
                .valorRestante(request.valorTotal())
                .dataInicio(request.dataInicio())
                .dataFim(request.dataFim())
                .observacao(request.observacao())
                .recorrente(request.recorrente() != null ? request.recorrente() : false)
                .periodicidade(request.periodicidade())
                .diaVencimento(request.diaVencimento())
                .valorParcelaRecorrente(request.valorParcelaRecorrente())
                .build();
    }
}

