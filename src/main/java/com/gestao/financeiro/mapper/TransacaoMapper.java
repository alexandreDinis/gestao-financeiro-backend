package com.gestao.financeiro.mapper;

import com.gestao.financeiro.dto.response.CategoriaResponse;
import com.gestao.financeiro.dto.response.LancamentoResponse;
import com.gestao.financeiro.dto.response.TransacaoResponse;
import com.gestao.financeiro.entity.Lancamento;
import com.gestao.financeiro.entity.Transacao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TransacaoMapper {

    private final CategoriaMapper categoriaMapper;

    public TransacaoResponse toResponse(Transacao entity) {
        CategoriaResponse categoriaResp = entity.getCategoria() != null
                ? categoriaMapper.toResponse(entity.getCategoria())
                : null;

        List<LancamentoResponse> lancamentosResp = entity.getLancamentos().stream()
                .map(this::toLancamentoResponse)
                .toList();

        return new TransacaoResponse(
                entity.getId(),
                entity.getDescricao(),
                entity.getValor(),
                entity.getData() != null ? entity.getData().toString() : null,
                entity.getDataVencimento() != null ? entity.getDataVencimento().toString() : null,
                entity.getDataPagamento() != null ? entity.getDataPagamento().toString() : null,
                entity.getTipo(),
                entity.getTipoDespesa(),
                entity.getStatus(),
                entity.getObservacao(),
                categoriaResp,
                lancamentosResp,
                entity.getCreatedAt()
        );
    }

    public LancamentoResponse toLancamentoResponse(Lancamento lancamento) {
        return new LancamentoResponse(
                lancamento.getId(),
                lancamento.getConta().getId(),
                lancamento.getConta().getNome(),
                lancamento.getValor(),
                lancamento.getDirecao(),
                lancamento.getDescricao()
        );
    }
}
