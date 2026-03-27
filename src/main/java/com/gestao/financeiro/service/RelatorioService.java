package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.response.FluxoMensalResponse;
import com.gestao.financeiro.dto.response.GastoPorCategoriaResponse;
import com.gestao.financeiro.entity.Conta;
import com.gestao.financeiro.repository.ContaRepository;
import com.gestao.financeiro.repository.LancamentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class RelatorioService {

    private final EntityManager entityManager;
    private final ContaRepository contaRepository;
    private final LancamentoRepository lancamentoRepository;

    /**
     * Gastos agrupados por categoria num período.
     */
    @SuppressWarnings("unchecked")
    public List<GastoPorCategoriaResponse> gastosPorCategoria(LocalDate inicio, LocalDate fim) {
        String jpql = """
            SELECT c.id, c.nome, c.cor, c.icone, COALESCE(SUM(t.valor), 0)
            FROM Transacao t
            JOIN t.categoria c
            WHERE t.tipo = 'DESPESA'
              AND t.status = 'PAGO'
              AND t.data BETWEEN :inicio AND :fim
              AND t.deletedAt IS NULL
            GROUP BY c.id, c.nome, c.cor, c.icone
            ORDER BY SUM(t.valor) DESC
        """;

        List<Object[]> results = entityManager.createQuery(jpql)
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .getResultList();

        return results.stream()
                .map(r -> new GastoPorCategoriaResponse(
                        (Long) r[0],
                        (String) r[1],
                        (String) r[2],
                        (String) r[3],
                        (BigDecimal) r[4]
                ))
                .toList();
    }

    /**
     * Fluxo mensal: receitas vs despesas por mês (últimos N meses).
     */
    public List<FluxoMensalResponse> fluxoMensal(int meses) {
        List<FluxoMensalResponse> resultado = new ArrayList<>();
        LocalDate hoje = LocalDate.now();

        for (int i = meses - 1; i >= 0; i--) {
            LocalDate mesRef = hoje.minusMonths(i);
            LocalDate inicio = mesRef.with(TemporalAdjusters.firstDayOfMonth());
            LocalDate fim = mesRef.with(TemporalAdjusters.lastDayOfMonth());

            BigDecimal receitas = BigDecimal.ZERO;
            BigDecimal despesas = BigDecimal.ZERO;

            List<Conta> contas = contaRepository.findByAtivaTrue(
                    org.springframework.data.domain.Pageable.unpaged()).getContent();

            for (Conta conta : contas) {
                receitas = receitas.add(
                        lancamentoRepository.somarCreditosPorContaEPeriodo(conta.getId(), inicio, fim));
                despesas = despesas.add(
                        lancamentoRepository.somarDebitosPorContaEPeriodo(conta.getId(), inicio, fim));
            }

            resultado.add(new FluxoMensalResponse(
                    mesRef.getMonthValue(),
                    mesRef.getYear(),
                    receitas,
                    despesas,
                    receitas.subtract(despesas)
            ));
        }

        return resultado;
    }
}
