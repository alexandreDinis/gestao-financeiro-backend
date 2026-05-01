package com.gestao.financeiro.mapper;

import com.gestao.financeiro.dto.response.CategoriaResponse;
import com.gestao.financeiro.dto.response.LancamentoLegResponse;
import com.gestao.financeiro.dto.response.LancamentoResponse;
import com.gestao.financeiro.dto.response.TransacaoResponse;
import com.gestao.financeiro.entity.Lancamento;
import com.gestao.financeiro.entity.Parcela;
import com.gestao.financeiro.entity.Transacao;
import com.gestao.financeiro.entity.enums.OrigemLancamento;
import com.gestao.financeiro.entity.enums.TipoTransacao;
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

        List<LancamentoLegResponse> lancamentosResp = entity.getLancamentos().stream()
                .map(this::toLancamentoLegResponse)
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
                entity.getGeradoAutomaticamente(),
                entity.getRecorrenciaId(),
                entity.getValorPrevisto(),
                entity.getCreatedAt()
        );
    }

    public LancamentoLegResponse toLancamentoLegResponse(Lancamento lancamento) {
        return new LancamentoLegResponse(
                lancamento.getId(),
                lancamento.getConta().getId(),
                lancamento.getConta().getNome(),
                lancamento.getValor(),
                lancamento.getDirecao(),
                lancamento.getDescricao()
        );
    }

    /**
     * Mapeamento Unificado: Transação -> LancamentoResponse (Ledger)
     */
    public LancamentoResponse toLedgerResponse(Transacao t) {
        String contaNome = t.getLancamentos().isEmpty() ? "Conta Padrão" : t.getLancamentos().get(0).getConta().getNome();
        Long contaId = t.getLancamentos().isEmpty() ? null : t.getLancamentos().get(0).getConta().getId();
        Long contaDestinoId = (t.getTipo() == TipoTransacao.TRANSFERENCIA && t.getLancamentos().size() > 1) 
                              ? t.getLancamentos().get(1).getConta().getId() : null;

        return new LancamentoResponse(
                t.getId(),
                t.getDescricao(),
                t.getValor(),
                t.getData(), // dataReferencia = data da compra/evento
                t.getTipo(),
                null,
                null,
                OrigemLancamento.TRANSACAO,
                t.getCategoria() != null ? t.getCategoria().getNome() : null,
                t.getCategoria() != null ? t.getCategoria().getId() : null,
                contaNome,
                contaId,
                contaDestinoId,
                t.getStatus(),
                t.getId(),
                t.getGeradoAutomaticamente(),
                t.getTipoDespesa(),
                t.getValorPrevisto(),
                t.getObservacao(),
                t.getDataVencimento(),
                t.getRecorrenciaId()
        );
    }

    public LancamentoResponse toLedgerResponse(Parcela p) {
        Transacao t = p.getTransacao();
        
        // UX Recommendation: Descrição (X/Y)
        String descricaoFormatada = String.format("%s (%d/%d)", 
                t.getDescricao(), p.getNumeroParcela(), p.getTotalParcelas());
        
        String contaNome = t.getLancamentos().isEmpty() ? "Cartão de Crédito" : t.getLancamentos().get(0).getConta().getNome();
        Long contaId = t.getLancamentos().isEmpty() ? null : t.getLancamentos().get(0).getConta().getId();

        return new LancamentoResponse(
                p.getId(),
                descricaoFormatada,
                p.getValorParcela(),
                p.getDataVencimento(), // dataReferencia = impacto financeiro
                TipoTransacao.DESPESA, // Parcela é sempre despesa de cartão
                p.getNumeroParcela(),
                p.getTotalParcelas(),
                OrigemLancamento.PARCELA,
                t.getCategoria() != null ? t.getCategoria().getNome() : null,
                t.getCategoria() != null ? t.getCategoria().getId() : null,
                contaNome,
                contaId,
                null,
                p.getPaga() ? com.gestao.financeiro.entity.enums.StatusTransacao.PAGO : com.gestao.financeiro.entity.enums.StatusTransacao.PENDENTE,
                t.getId(),
                false, // Parcela não é gerada automaticamente no sentido de recorrência
                null,  // Parcela não tem tipo de despesa fixa/variável diretamente
                null,  // Parcela não tem valor previsto
                t.getObservacao(),
                p.getDataVencimento(),
                t.getRecorrenciaId()
        );
    }
}
