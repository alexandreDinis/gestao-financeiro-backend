package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.response.FluxoMensalResponse;
import com.gestao.financeiro.dto.response.GastoPorCategoriaResponse;
import com.gestao.financeiro.dto.response.RelatorioGastosMensaisResponse;
import com.gestao.financeiro.dto.response.RelatorioReceitasMensaisResponse;
import com.gestao.financeiro.dto.response.RelatorioGastosMensaisResponse.CategoriaGastoResponse;
import com.gestao.financeiro.dto.response.RelatorioGastosMensaisResponse.SubcategoriaGastoResponse;
import com.gestao.financeiro.dto.response.RelatorioGastosMensaisResponse.TransacaoGastoResponse;
import com.gestao.financeiro.entity.Categoria;
import com.gestao.financeiro.entity.Conta;
import com.gestao.financeiro.entity.Parcela;
import com.gestao.financeiro.entity.Transacao;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.config.TenantContext;
import com.gestao.financeiro.repository.ContaRepository;
import com.gestao.financeiro.repository.LancamentoRepository;
import com.gestao.financeiro.repository.ParcelaRepository;
import com.gestao.financeiro.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class RelatorioService {

    private final EntityManager entityManager;
    private final ContaRepository contaRepository;
    private final LancamentoRepository lancamentoRepository;
    private final TransacaoRepository transacaoRepository;
    private final ParcelaRepository parcelaRepository;

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

    /**
     * Relatório detalhado de gastos mensais com agrupamento hierárquico.
     *
     * Considera:
     * - Transações DESPESA com status PAGO (gastos regulares)
     * - Parcelas de cartão de crédito cujo vencimento cai no mês (tipo DESPESA, não canceladas)
     *
     * Retorna estrutura vazia (totalGeral=0, categorias=[]) se não houver dados.
     *
     * Percentuais calculados com RoundingMode.HALF_UP em 2 casas decimais.
     * Pequenas diferenças de arredondamento (soma != 100%) são esperadas.
     */
    public RelatorioGastosMensaisResponse gastosMensaisDetalhados(int ano, int mes) {
        // Validação de parâmetros
        if (mes < 1 || mes > 12) {
            throw new BusinessException("Mês inválido: " + mes + ". Deve estar entre 1 e 12.");
        }
        int anoAtual = LocalDate.now().getYear();
        if (ano < 2000 || ano > anoAtual + 1) {
            throw new BusinessException("Ano inválido: " + ano + ". Deve estar entre 2000 e " + (anoAtual + 1) + ".");
        }

        LocalDate inicio = LocalDate.of(ano, mes, 1);
        LocalDate fim = inicio.with(TemporalAdjusters.lastDayOfMonth());

        Long tenantId = TenantContext.getTenantId();

        // 1. Transações regulares pagas (sem cartão de crédito)
        List<Transacao> transacoes = transacaoRepository.findDespesasPagasByPeriodo(tenantId, inicio, fim);

        // 2. Parcelas de cartão de crédito que vencem no período
        List<Parcela> parcelasCartao = (tenantId != null)
                ? parcelaRepository.findParcelasCartaoByPeriodo(tenantId, inicio, fim)
                : List.of();

        // Caso vazio
        if (transacoes.isEmpty() && parcelasCartao.isEmpty()) {
            return new RelatorioGastosMensaisResponse(mes, ano, BigDecimal.ZERO, List.of());
        }

        // Calcular total geral (regulares + cartão)
        BigDecimal totalRegulares = transacoes.stream()
                .map(Transacao::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCartao = parcelasCartao.stream()
                .map(Parcela::getValorParcela)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalGeral = totalRegulares.add(totalCartao);

        // ─── Converter tudo em ItemGasto para agrupamento unificado ───

        // Converter transações regulares em ItemGasto
        List<ItemGasto> itens = new ArrayList<>();
        for (Transacao t : transacoes) {
            itens.add(new ItemGasto(
                    t.getId(), t.getDescricao(), t.getValor(), t.getData(),
                    t.getStatus().name(), null, t.getCategoria()));
        }

        // Converter parcelas de cartão em ItemGasto
        for (Parcela p : parcelasCartao) {
            Transacao t = p.getTransacao();
            String descParcela = t.getDescricao();
            if (p.getTotalParcelas() != null && p.getTotalParcelas() > 1) {
                descParcela += " (parcela " + p.getNumeroParcela() + "/" + p.getTotalParcelas() + ")";
            }
            itens.add(new ItemGasto(
                    t.getId(), descParcela, p.getValorParcela(), p.getDataVencimento(),
                    "CARTAO", "CARTAO", t.getCategoria()));
        }

        // Agrupar itens por categoria pai
        // Chave: ID da categoria pai (ou ID da própria categoria se não tem pai, ou -1 se sem categoria)
        Map<Long, List<ItemGasto>> porCategoriaPai = new LinkedHashMap<>();
        // Mapa auxiliar para guardar a entidade da categoria pai
        Map<Long, Categoria> categoriaPaiMap = new HashMap<>();

        for (ItemGasto item : itens) {
            Categoria cat = item.categoria;
            if (cat == null) {
                // Sem categoria
                porCategoriaPai.computeIfAbsent(-1L, k -> new ArrayList<>()).add(item);
            } else if (cat.getCategoriaPai() != null) {
                // É subcategoria → agrupa pela categoria pai
                Long paiId = cat.getCategoriaPai().getId();
                porCategoriaPai.computeIfAbsent(paiId, k -> new ArrayList<>()).add(item);
                categoriaPaiMap.putIfAbsent(paiId, cat.getCategoriaPai());
            } else {
                // É categoria raiz (sem pai)
                porCategoriaPai.computeIfAbsent(cat.getId(), k -> new ArrayList<>()).add(item);
                categoriaPaiMap.putIfAbsent(cat.getId(), cat);
            }
        }

        // Construir resposta hierárquica
        List<CategoriaGastoResponse> categorias = new ArrayList<>();

        for (Map.Entry<Long, List<ItemGasto>> entry : porCategoriaPai.entrySet()) {
            Long catPaiId = entry.getKey();
            List<ItemGasto> catItens = entry.getValue();

            // Determinar dados da categoria pai
            String catNome;
            String catCor;
            String catIcone;
            Long catId;

            if (catPaiId == -1L) {
                catId = null;
                catNome = "Sem Categoria";
                catCor = "#6B7280";
                catIcone = "help-circle";
            } else {
                Categoria pai = categoriaPaiMap.get(catPaiId);
                catId = pai.getId();
                catNome = pai.getNome();
                catCor = pai.getCor();
                catIcone = pai.getIcone();
            }

            // Separar transações diretas (categoria == pai) e de subcategorias
            Map<Long, List<ItemGasto>> porSubcategoria = new LinkedHashMap<>();
            List<ItemGasto> itensDiretos = new ArrayList<>();

            for (ItemGasto item : catItens) {
                Categoria cat = item.categoria;
                if (cat == null) {
                    // Sem categoria → transação direta
                    itensDiretos.add(item);
                } else if (cat.getCategoriaPai() != null) {
                    // É subcategoria
                    porSubcategoria.computeIfAbsent(cat.getId(), k -> new ArrayList<>()).add(item);
                } else {
                    // Categoria raiz → transação direta
                    itensDiretos.add(item);
                }
            }

            // Calcular total da categoria
            BigDecimal totalCategoria = catItens.stream()
                    .map(i -> i.valor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal percentualCategoria = calcularPercentual(totalCategoria, totalGeral);

            // Construir subcategorias
            List<SubcategoriaGastoResponse> subcategorias = new ArrayList<>();
            // Mapear IDs de subcategorias para entidades
            Map<Long, Categoria> subcategoriaMap = new HashMap<>();
            for (ItemGasto item : catItens) {
                if (item.categoria != null && item.categoria.getCategoriaPai() != null) {
                    subcategoriaMap.putIfAbsent(item.categoria.getId(), item.categoria);
                }
            }

            for (Map.Entry<Long, List<ItemGasto>> subEntry : porSubcategoria.entrySet()) {
                Categoria subCat = subcategoriaMap.get(subEntry.getKey());
                List<ItemGasto> subItens = subEntry.getValue();

                BigDecimal totalSub = subItens.stream()
                        .map(i -> i.valor)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal percentualSub = calcularPercentual(totalSub, totalGeral);

                List<TransacaoGastoResponse> transacoesResponse = subItens.stream()
                        .map(RelatorioService::toTransacaoResponse)
                        .toList();

                subcategorias.add(new SubcategoriaGastoResponse(
                        subCat.getId(),
                        subCat.getNome(),
                        subCat.getCor(),
                        subCat.getIcone(),
                        totalSub,
                        percentualSub,
                        transacoesResponse
                ));
            }

            // Ordenar subcategorias por valor (maior → menor)
            subcategorias.sort((a, b) -> b.totalSubcategoria().compareTo(a.totalSubcategoria()));

            // Converter transações diretas
            List<TransacaoGastoResponse> transacoesDir = itensDiretos.stream()
                    .map(RelatorioService::toTransacaoResponse)
                    .toList();

            categorias.add(new CategoriaGastoResponse(
                    catId,
                    catNome,
                    catCor,
                    catIcone,
                    totalCategoria,
                    percentualCategoria,
                    subcategorias,
                    transacoesDir
            ));
        }

        // Ordenar categorias por valor (maior → menor)
        categorias.sort((a, b) -> b.totalCategoria().compareTo(a.totalCategoria()));

        return new RelatorioGastosMensaisResponse(mes, ano, totalGeral, categorias);
    }

    /**
     * Relatório detalhado de receitas (entradas) mensais com estatísticas do ano.
     *
     * Considera:
     * - Transações RECEITA com status PAGO
     * - Total do mês selecionado
     * - Total acumulado no ano selecionado
     * - Média mensal de entradas do ano
     * - Agrupamento hierárquico por categoria
     */
    public RelatorioReceitasMensaisResponse receitasMensaisDetalhadas(int ano, int mes) {
        if (mes < 1 || mes > 12) {
            throw new BusinessException("Mês inválido: " + mes + ". Deve estar entre 1 e 12.");
        }
        int anoAtual = LocalDate.now().getYear();
        if (ano < 2000 || ano > anoAtual + 1) {
            throw new BusinessException("Ano inválido: " + ano + ". Deve estar entre 2000 e " + (anoAtual + 1) + ".");
        }

        LocalDate inicioMes = LocalDate.of(ano, mes, 1);
        LocalDate fimMes = inicioMes.with(TemporalAdjusters.lastDayOfMonth());

        LocalDate inicioAno = LocalDate.of(ano, 1, 1);
        LocalDate fimAno = LocalDate.of(ano, 12, 31);

        Long tenantId = TenantContext.getTenantId();

        // 1. Transações de receita pagas do mês
        List<Transacao> transacoesMes = transacaoRepository.findReceitasPagasByPeriodo(tenantId, inicioMes, fimMes);

        // 2. Total do mês
        BigDecimal totalMes = transacoesMes.stream()
                .map(Transacao::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. Total acumulado no ano
        BigDecimal totalAno = transacaoRepository.somarReceitasPagasByPeriodo(tenantId, inicioAno, fimAno);

        // 4. Média mensal
        int mesesConsiderados;
        if (ano == anoAtual) {
            mesesConsiderados = Math.max(1, LocalDate.now().getMonthValue());
        } else if (ano < anoAtual) {
            mesesConsiderados = 12;
        } else {
            mesesConsiderados = Math.max(1, mes);
        }

        BigDecimal mediaMensal = totalAno.divide(BigDecimal.valueOf(mesesConsiderados), 2, RoundingMode.HALF_UP);

        if (transacoesMes.isEmpty()) {
            return new RelatorioReceitasMensaisResponse(mes, ano, totalMes, totalAno, mediaMensal, List.of());
        }

        // Converter transações de receita em ItemGasto para reagrupar hierarquicamente por categoria
        List<ItemGasto> itens = new ArrayList<>();
        for (Transacao t : transacoesMes) {
            itens.add(new ItemGasto(
                    t.getId(), t.getDescricao(), t.getValor(), t.getData(),
                    t.getStatus().name(), null, t.getCategoria()));
        }

        // Agrupar itens por categoria pai
        Map<Long, List<ItemGasto>> porCategoriaPai = new LinkedHashMap<>();
        Map<Long, Categoria> categoriaPaiMap = new HashMap<>();

        for (ItemGasto item : itens) {
            Categoria cat = item.categoria;
            if (cat == null) {
                porCategoriaPai.computeIfAbsent(-1L, k -> new ArrayList<>()).add(item);
            } else if (cat.getCategoriaPai() != null) {
                Long paiId = cat.getCategoriaPai().getId();
                porCategoriaPai.computeIfAbsent(paiId, k -> new ArrayList<>()).add(item);
                categoriaPaiMap.putIfAbsent(paiId, cat.getCategoriaPai());
            } else {
                porCategoriaPai.computeIfAbsent(cat.getId(), k -> new ArrayList<>()).add(item);
                categoriaPaiMap.putIfAbsent(cat.getId(), cat);
            }
        }

        List<CategoriaGastoResponse> categorias = new ArrayList<>();

        for (Map.Entry<Long, List<ItemGasto>> entry : porCategoriaPai.entrySet()) {
            Long catPaiId = entry.getKey();
            List<ItemGasto> catItens = entry.getValue();

            String catNome;
            String catCor;
            String catIcone;
            Long catId;

            if (catPaiId == -1L) {
                catId = null;
                catNome = "Sem Categoria";
                catCor = "#6B7280";
                catIcone = "help-circle";
            } else {
                Categoria pai = categoriaPaiMap.get(catPaiId);
                catId = pai.getId();
                catNome = pai.getNome();
                catCor = pai.getCor();
                catIcone = pai.getIcone();
            }

            Map<Long, List<ItemGasto>> porSubcategoria = new LinkedHashMap<>();
            List<ItemGasto> itensDiretos = new ArrayList<>();

            for (ItemGasto item : catItens) {
                Categoria cat = item.categoria;
                if (cat == null) {
                    itensDiretos.add(item);
                } else if (cat.getCategoriaPai() != null) {
                    porSubcategoria.computeIfAbsent(cat.getId(), k -> new ArrayList<>()).add(item);
                } else {
                    itensDiretos.add(item);
                }
            }

            BigDecimal totalCategoria = catItens.stream()
                    .map(i -> i.valor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal percentualCategoria = calcularPercentual(totalCategoria, totalMes);

            List<SubcategoriaGastoResponse> subcategorias = new ArrayList<>();
            Map<Long, Categoria> subcategoriaMap = new HashMap<>();
            for (ItemGasto item : catItens) {
                if (item.categoria != null && item.categoria.getCategoriaPai() != null) {
                    subcategoriaMap.putIfAbsent(item.categoria.getId(), item.categoria);
                }
            }

            for (Map.Entry<Long, List<ItemGasto>> subEntry : porSubcategoria.entrySet()) {
                Categoria subCat = subcategoriaMap.get(subEntry.getKey());
                List<ItemGasto> subItens = subEntry.getValue();

                BigDecimal totalSub = subItens.stream()
                        .map(i -> i.valor)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal percentualSub = calcularPercentual(totalSub, totalMes);

                List<TransacaoGastoResponse> transacoesResponse = subItens.stream()
                        .map(RelatorioService::toTransacaoResponse)
                        .toList();

                subcategorias.add(new SubcategoriaGastoResponse(
                        subCat.getId(),
                        subCat.getNome(),
                        subCat.getCor(),
                        subCat.getIcone(),
                        totalSub,
                        percentualSub,
                        transacoesResponse
                ));
            }

            subcategorias.sort((a, b) -> b.totalSubcategoria().compareTo(a.totalSubcategoria()));

            List<TransacaoGastoResponse> transacoesDir = itensDiretos.stream()
                    .map(RelatorioService::toTransacaoResponse)
                    .toList();

            categorias.add(new CategoriaGastoResponse(
                    catId,
                    catNome,
                    catCor,
                    catIcone,
                    totalCategoria,
                    percentualCategoria,
                    subcategorias,
                    transacoesDir
            ));
        }

        categorias.sort((a, b) -> b.totalCategoria().compareTo(a.totalCategoria()));

        return new RelatorioReceitasMensaisResponse(mes, ano, totalMes, totalAno, mediaMensal, categorias);
    }

    private BigDecimal calcularPercentual(BigDecimal parte, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return parte.multiply(BigDecimal.valueOf(100))
                .divide(total, 2, RoundingMode.HALF_UP);
    }

    private static TransacaoGastoResponse toTransacaoResponse(ItemGasto item) {
        return new TransacaoGastoResponse(
                item.id,
                item.descricao,
                item.valor,
                item.data.toString(),
                item.status,
                item.origem
        );
    }

    /**
     * Wrapper interno para unificar transações regulares e parcelas de cartão
     * no agrupamento do relatório de gastos mensais.
     */
    private record ItemGasto(
            Long id,
            String descricao,
            BigDecimal valor,
            LocalDate data,
            String status,
            String origem,
            Categoria categoria
    ) {}
}
