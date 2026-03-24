package com.gestao.financeiro.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO principal do Dashboard v2 — agrega todos os blocos em uma única resposta.
 */
public record DashboardResponse(

        BigDecimal saldoTotal,
        List<SaldoConta> saldoPorConta,

        ResumoMes mesAtual,
        ComparativoMes comparativo,
        ProjecaoMes projecao,

        List<GastoPorCategoria> gastosPorCategoria,
        List<FluxoMensal> fluxoCaixaSeisMeses,
        List<UltimaTransacao> ultimasTransacoes,

        ProximosVencimentos proximosVencimentos,

        List<ResumoMeta> metas,
        List<ResumoOrcamento> orcamentos,
        List<ResumoCartao> cartoes,

        List<Alerta> alertas

) {

    public record SaldoConta(Long id, String nome, String tipo, BigDecimal saldo) {}

    public record ResumoMes(BigDecimal receitas, BigDecimal despesas, BigDecimal saldo) {}

    public record ComparativoMes(
            BigDecimal receitasMesAtual,
            BigDecimal receitasMesAnterior,
            Double variacaoReceitasPercent,
            BigDecimal despesasMesAtual,
            BigDecimal despesasMesAnterior,
            Double variacaoDespesasPercent
    ) {}

    public record ProjecaoMes(
            int diasDecorridos,
            int diasTotais,
            BigDecimal receitasProjetadas,
            BigDecimal despesasProjetadas,
            BigDecimal saldoProjetado
    ) {}

    public record GastoPorCategoria(
            Long categoriaId,
            String nomeCategoria,
            BigDecimal total,
            Double percentualSobreTotal
    ) {}

    public record FluxoMensal(
            int ano,
            int mes,
            String mesLabel,
            BigDecimal receitas,
            BigDecimal despesas,
            BigDecimal saldo
    ) {}

    public record UltimaTransacao(
            Long id,
            String descricao,
            BigDecimal valor,
            String tipo,
            String status,
            LocalDate data,
            String categoria,
            String conta
    ) {}

    public record ProximosVencimentos(
            List<Vencimento> proximos7Dias,
            List<Vencimento> proximos15Dias,
            List<Vencimento> proximos30Dias,
            BigDecimal totalVencer7Dias,
            BigDecimal totalVencer30Dias
    ) {}

    public record Vencimento(
            Long transacaoId,
            String descricao,
            BigDecimal valor,
            LocalDate dataVencimento,
            int diasRestantes,
            String conta
    ) {}

    public record ResumoMeta(
            Long id,
            String nome,
            BigDecimal valorAlvo,
            BigDecimal valorAtual,
            Double percentualConcluido,
            LocalDate prazo,
            boolean atrasada
    ) {}

    public record ResumoOrcamento(
            Long orcamentoId,
            String categoria,
            BigDecimal limite,
            BigDecimal gasto,
            BigDecimal disponivel,
            Double percentualUtilizado,
            boolean estourado
    ) {}

    public record ResumoCartao(
            Long cartaoId,
            String nome,
            BigDecimal limite,
            BigDecimal utilizado,
            BigDecimal disponivel,
            Double percentualUtilizado,
            BigDecimal faturaAtual,
            LocalDate proximoVencimento
    ) {}

    public record Alerta(
            TipoAlerta tipo,
            String mensagem,
            NivelAlerta nivel,
            int score
    ) {}

    public enum TipoAlerta {
        SALDO_BAIXO,
        SALDO_NEGATIVO,
        ORCAMENTO_ESTOURADO,
        ORCAMENTO_PROXIMO_LIMITE,
        META_ATRASADA,
        VENCIMENTO_PROXIMO,
        CARTAO_LIMITE_ALTO,
        RISCO_COMBINADO
    }

    public enum NivelAlerta { INFO, AVISO, CRITICO }
}
