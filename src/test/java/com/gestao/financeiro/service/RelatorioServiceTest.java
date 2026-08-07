package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.response.RelatorioGastosMensaisResponse;
import com.gestao.financeiro.dto.response.RelatorioReceitasMensaisResponse;
import com.gestao.financeiro.dto.response.RelatorioGastosMensaisResponse.CategoriaGastoResponse;
import com.gestao.financeiro.entity.*;
import com.gestao.financeiro.entity.enums.*;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.repository.CategoriaRepository;
import com.gestao.financeiro.repository.ContaRepository;
import com.gestao.financeiro.repository.TransacaoRepository;
import com.gestao.financeiro.config.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class RelatorioServiceTest {

    @Autowired
    private RelatorioService relatorioService;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private com.gestao.financeiro.repository.TenantRepository tenantRepository;

    private Long tenantId;
    private Long contaId;

    @BeforeEach
    void setup() {
        Tenant tenant = new Tenant();
        tenant.setNome("Test Tenant Relatorio " + System.currentTimeMillis());
        tenant.setStatus(StatusTenant.ATIVO);
        tenantId = tenantRepository.save(tenant).getId();

        TenantContext.setTenantId(tenantId);

        Conta conta = Conta.builder()
                .nome("Nubank Test")
                .tipo(TipoConta.CORRENTE)
                .saldoInicial(BigDecimal.ZERO)
                .build();
        conta.setTenantId(tenantId);
        contaId = contaRepository.save(conta).getId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Categoria criarCategoria(String nome, TipoCategoria tipo, Categoria pai) {
        Categoria cat = Categoria.builder()
                .nome(nome)
                .tipo(tipo)
                .icone("tag")
                .cor("#000000")
                .categoriaPai(pai)
                .build();
        cat.setTenantId(tenantId);
        return categoriaRepository.save(cat);
    }

    private Transacao criarTransacao(String descricao, BigDecimal valor, LocalDate data,
                                     TipoTransacao tipo, StatusTransacao status, Categoria categoria) {
        Transacao tx = Transacao.builder()
                .descricao(descricao)
                .valor(valor)
                .tipo(tipo)
                .data(data)
                .status(status)
                .categoria(categoria)
                .build();
        tx.addLancamento(Lancamento.builder()
                .conta(contaRepository.findById(contaId).orElseThrow())
                .valor(valor)
                .direcao(tipo == TipoTransacao.RECEITA ? DirecaoLancamento.CREDITO : DirecaoLancamento.DEBITO)
                .descricao(descricao)
                .build());
        tx.setTenantId(tenantId);
        return transacaoRepository.save(tx);
    }

    // ── Testes ───────────────────────────────────────────────────────────────

    @Test
    void deveRetornarEstruturaVaziaParaMesSemDados() {
        RelatorioGastosMensaisResponse result = relatorioService.gastosMensaisDetalhados(2025, 1);

        assertThat(result.mes()).isEqualTo(1);
        assertThat(result.ano()).isEqualTo(2025);
        assertThat(result.totalGeral()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.categorias()).isEmpty();
    }

    @Test
    void deveExcluirTransacoesPendentesAtrasadasECanceladas() {
        Categoria cat = criarCategoria("Alimentação", TipoCategoria.DESPESA, null);
        LocalDate data = LocalDate.of(2026, 6, 15);

        // Somente esta deve aparecer
        criarTransacao("Almoço Pago", BigDecimal.valueOf(50), data, TipoTransacao.DESPESA, StatusTransacao.PAGO, cat);
        // Estas NÃO devem aparecer
        criarTransacao("Almoço Pendente", BigDecimal.valueOf(30), data, TipoTransacao.DESPESA, StatusTransacao.PENDENTE, cat);
        criarTransacao("Almoço Atrasado", BigDecimal.valueOf(20), data, TipoTransacao.DESPESA, StatusTransacao.ATRASADO, cat);
        criarTransacao("Almoço Cancelado", BigDecimal.valueOf(10), data, TipoTransacao.DESPESA, StatusTransacao.CANCELADO, cat);

        RelatorioGastosMensaisResponse result = relatorioService.gastosMensaisDetalhados(2026, 6);

        assertThat(result.totalGeral()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(result.categorias()).hasSize(1);
        assertThat(result.categorias().get(0).transacoesDiretas()).hasSize(1);
        assertThat(result.categorias().get(0).transacoesDiretas().get(0).descricao()).isEqualTo("Almoço Pago");
    }

    @Test
    void deveExcluirReceitasETransferencias() {
        Categoria catDespesa = criarCategoria("Alimentação", TipoCategoria.DESPESA, null);
        Categoria catReceita = criarCategoria("Salário", TipoCategoria.RECEITA, null);
        LocalDate data = LocalDate.of(2026, 6, 10);

        criarTransacao("Almoço", BigDecimal.valueOf(50), data, TipoTransacao.DESPESA, StatusTransacao.PAGO, catDespesa);
        criarTransacao("Salário", BigDecimal.valueOf(5000), data, TipoTransacao.RECEITA, StatusTransacao.PAGO, catReceita);
        criarTransacao("Transferência", BigDecimal.valueOf(100), data, TipoTransacao.TRANSFERENCIA, StatusTransacao.PAGO, null);

        RelatorioGastosMensaisResponse result = relatorioService.gastosMensaisDetalhados(2026, 6);

        assertThat(result.totalGeral()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(result.categorias()).hasSize(1);
    }

    @Test
    void deveAgruparTransacaoSemCategoria() {
        LocalDate data = LocalDate.of(2026, 6, 5);
        criarTransacao("Gasto avulso", BigDecimal.valueOf(100), data, TipoTransacao.DESPESA, StatusTransacao.PAGO, null);

        RelatorioGastosMensaisResponse result = relatorioService.gastosMensaisDetalhados(2026, 6);

        assertThat(result.totalGeral()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(result.categorias()).hasSize(1);

        CategoriaGastoResponse semCategoria = result.categorias().get(0);
        assertThat(semCategoria.categoriaId()).isNull();
        assertThat(semCategoria.nome()).isEqualTo("Sem Categoria");
        assertThat(semCategoria.transacoesDiretas()).hasSize(1);
    }

    @Test
    void deveAgruparTransacoesSemSubcategoriaComoTransacoesDiretas() {
        Categoria catPai = criarCategoria("Transporte", TipoCategoria.DESPESA, null);
        LocalDate data = LocalDate.of(2026, 6, 20);

        criarTransacao("Uber", BigDecimal.valueOf(25), data, TipoTransacao.DESPESA, StatusTransacao.PAGO, catPai);
        criarTransacao("Gasolina", BigDecimal.valueOf(200), data, TipoTransacao.DESPESA, StatusTransacao.PAGO, catPai);

        RelatorioGastosMensaisResponse result = relatorioService.gastosMensaisDetalhados(2026, 6);

        assertThat(result.categorias()).hasSize(1);
        CategoriaGastoResponse cat = result.categorias().get(0);
        assertThat(cat.nome()).isEqualTo("Transporte");
        assertThat(cat.subcategorias()).isEmpty();
        assertThat(cat.transacoesDiretas()).hasSize(2);
        assertThat(cat.totalCategoria()).isEqualByComparingTo(BigDecimal.valueOf(225));
    }

    @Test
    void deveAgruparComHierarquiaCategoriaSubcategoria() {
        Categoria catPai = criarCategoria("Alimentação", TipoCategoria.DESPESA, null);
        Categoria subRestaurantes = criarCategoria("Restaurantes", TipoCategoria.DESPESA, catPai);
        Categoria subMercado = criarCategoria("Supermercado", TipoCategoria.DESPESA, catPai);
        LocalDate data = LocalDate.of(2026, 6, 10);

        criarTransacao("Almoço", BigDecimal.valueOf(45), data, TipoTransacao.DESPESA, StatusTransacao.PAGO, subRestaurantes);
        criarTransacao("Jantar", BigDecimal.valueOf(80), data, TipoTransacao.DESPESA, StatusTransacao.PAGO, subRestaurantes);
        criarTransacao("Compras do mês", BigDecimal.valueOf(500), data, TipoTransacao.DESPESA, StatusTransacao.PAGO, subMercado);

        RelatorioGastosMensaisResponse result = relatorioService.gastosMensaisDetalhados(2026, 6);

        assertThat(result.totalGeral()).isEqualByComparingTo(BigDecimal.valueOf(625));
        assertThat(result.categorias()).hasSize(1);

        CategoriaGastoResponse cat = result.categorias().get(0);
        assertThat(cat.nome()).isEqualTo("Alimentação");
        assertThat(cat.totalCategoria()).isEqualByComparingTo(BigDecimal.valueOf(625));
        assertThat(cat.percentual()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(cat.subcategorias()).hasSize(2);
        assertThat(cat.transacoesDiretas()).isEmpty();

        // Subcategorias ordenadas por valor (maior → menor)
        assertThat(cat.subcategorias().get(0).nome()).isEqualTo("Supermercado");
        assertThat(cat.subcategorias().get(0).totalSubcategoria()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(cat.subcategorias().get(1).nome()).isEqualTo("Restaurantes");
        assertThat(cat.subcategorias().get(1).totalSubcategoria()).isEqualByComparingTo(BigDecimal.valueOf(125));
    }

    @Test
    void deveCalcularPercentuaisCorretamente() {
        Categoria catAlimentacao = criarCategoria("Alimentação", TipoCategoria.DESPESA, null);
        Categoria catTransporte = criarCategoria("Transporte", TipoCategoria.DESPESA, null);
        LocalDate data = LocalDate.of(2026, 6, 15);

        criarTransacao("Almoço", BigDecimal.valueOf(300), data, TipoTransacao.DESPESA, StatusTransacao.PAGO, catAlimentacao);
        criarTransacao("Uber", BigDecimal.valueOf(100), data, TipoTransacao.DESPESA, StatusTransacao.PAGO, catTransporte);

        RelatorioGastosMensaisResponse result = relatorioService.gastosMensaisDetalhados(2026, 6);

        assertThat(result.totalGeral()).isEqualByComparingTo(BigDecimal.valueOf(400));

        // Categorias ordenadas por valor
        assertThat(result.categorias().get(0).nome()).isEqualTo("Alimentação");
        assertThat(result.categorias().get(0).percentual()).isEqualByComparingTo(BigDecimal.valueOf(75));

        assertThat(result.categorias().get(1).nome()).isEqualTo("Transporte");
        assertThat(result.categorias().get(1).percentual()).isEqualByComparingTo(BigDecimal.valueOf(25));

        // Soma dos percentuais ~100% (com tolerância de arredondamento)
        BigDecimal somaPercentuais = result.categorias().stream()
                .map(CategoriaGastoResponse::percentual)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(somaPercentuais).isCloseTo(BigDecimal.valueOf(100), within(BigDecimal.valueOf(0.1)));
    }

    @Test
    void deveOrdenarCategoriasPorValorDescendente() {
        Categoria cat1 = criarCategoria("Pequena", TipoCategoria.DESPESA, null);
        Categoria cat2 = criarCategoria("Grande", TipoCategoria.DESPESA, null);
        Categoria cat3 = criarCategoria("Média", TipoCategoria.DESPESA, null);
        LocalDate data = LocalDate.of(2026, 6, 10);

        criarTransacao("P1", BigDecimal.valueOf(50), data, TipoTransacao.DESPESA, StatusTransacao.PAGO, cat1);
        criarTransacao("G1", BigDecimal.valueOf(500), data, TipoTransacao.DESPESA, StatusTransacao.PAGO, cat2);
        criarTransacao("M1", BigDecimal.valueOf(200), data, TipoTransacao.DESPESA, StatusTransacao.PAGO, cat3);

        RelatorioGastosMensaisResponse result = relatorioService.gastosMensaisDetalhados(2026, 6);

        assertThat(result.categorias()).hasSize(3);
        assertThat(result.categorias().get(0).nome()).isEqualTo("Grande");
        assertThat(result.categorias().get(1).nome()).isEqualTo("Média");
        assertThat(result.categorias().get(2).nome()).isEqualTo("Pequena");
    }

    @Test
    void deveLancarExcecaoParaMesInvalido() {
        assertThatThrownBy(() -> relatorioService.gastosMensaisDetalhados(2026, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Mês inválido");

        assertThatThrownBy(() -> relatorioService.gastosMensaisDetalhados(2026, 13))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Mês inválido");
    }

    @Test
    void deveLancarExcecaoParaAnoInvalido() {
        assertThatThrownBy(() -> relatorioService.gastosMensaisDetalhados(1999, 6))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ano inválido");
    }

    @Test
    void deveIsolamentoMultiTenant() {
        // Criar transação no tenant atual
        Categoria cat = criarCategoria("Alimentação", TipoCategoria.DESPESA, null);
        LocalDate data = LocalDate.of(2026, 6, 15);
        criarTransacao("Almoço Tenant 1", BigDecimal.valueOf(50), data, TipoTransacao.DESPESA, StatusTransacao.PAGO, cat);

        // Criar outro tenant com transação
        Tenant outroTenant = new Tenant();
        outroTenant.setNome("Outro Tenant " + System.currentTimeMillis());
        outroTenant.setStatus(StatusTenant.ATIVO);
        Long outroTenantId = tenantRepository.save(outroTenant).getId();

        TenantContext.setTenantId(outroTenantId);

        Conta outraConta = Conta.builder()
                .nome("Outra Conta")
                .tipo(TipoConta.CORRENTE)
                .saldoInicial(BigDecimal.ZERO)
                .build();
        outraConta.setTenantId(outroTenantId);
        contaRepository.save(outraConta);

        Categoria outraCat = Categoria.builder()
                .nome("Lazer")
                .tipo(TipoCategoria.DESPESA)
                .icone("gamepad")
                .cor("#FF0000")
                .build();
        outraCat.setTenantId(outroTenantId);
        outraCat = categoriaRepository.save(outraCat);

        Transacao txOutro = Transacao.builder()
                .descricao("Cinema Tenant 2")
                .valor(BigDecimal.valueOf(200))
                .tipo(TipoTransacao.DESPESA)
                .data(data)
                .status(StatusTransacao.PAGO)
                .categoria(outraCat)
                .build();
        txOutro.addLancamento(Lancamento.builder()
                .conta(outraConta)
                .valor(BigDecimal.valueOf(200))
                .direcao(DirecaoLancamento.DEBITO)
                .descricao("Cinema")
                .build());
        txOutro.setTenantId(outroTenantId);
        transacaoRepository.save(txOutro);

        // Buscar relatório do outro tenant: deve ver só a transação dele
        RelatorioGastosMensaisResponse resultOutro = relatorioService.gastosMensaisDetalhados(2026, 6);
        assertThat(resultOutro.totalGeral()).isEqualByComparingTo(BigDecimal.valueOf(200));

        // Voltar ao tenant original: deve ver só a transação dele
        TenantContext.setTenantId(tenantId);
        RelatorioGastosMensaisResponse resultOriginal = relatorioService.gastosMensaisDetalhados(2026, 6);
        assertThat(resultOriginal.totalGeral()).isEqualByComparingTo(BigDecimal.valueOf(50));
    }

    @Test
    void deveCalcularRelatorioReceitasMensaisComSucesso() {
        Categoria catSalario = criarCategoria("Salário", TipoCategoria.RECEITA, null);
        Categoria catFreelance = criarCategoria("Freelance", TipoCategoria.RECEITA, null);

        // Receitas no mês 6 de 2026
        criarTransacao("Salário Junho", BigDecimal.valueOf(5000), LocalDate.of(2026, 6, 5), TipoTransacao.RECEITA, StatusTransacao.PAGO, catSalario);
        criarTransacao("Projeto Web", BigDecimal.valueOf(1500), LocalDate.of(2026, 6, 20), TipoTransacao.RECEITA, StatusTransacao.PAGO, catFreelance);

        // Receita em outro mês (mês 1) do mesmo ano
        criarTransacao("Salário Janeiro", BigDecimal.valueOf(5000), LocalDate.of(2026, 1, 5), TipoTransacao.RECEITA, StatusTransacao.PAGO, catSalario);

        // Receita pendente (NÃO deve contar)
        criarTransacao("Freelance Pendente", BigDecimal.valueOf(2000), LocalDate.of(2026, 6, 25), TipoTransacao.RECEITA, StatusTransacao.PENDENTE, catFreelance);

        RelatorioReceitasMensaisResponse result = relatorioService.receitasMensaisDetalhadas(2026, 6);

        assertThat(result.mes()).isEqualTo(6);
        assertThat(result.ano()).isEqualTo(2026);
        assertThat(result.totalMes()).isEqualByComparingTo(BigDecimal.valueOf(6500));
        assertThat(result.totalAno()).isEqualByComparingTo(BigDecimal.valueOf(11500));
        assertThat(result.mediaMensal()).isNotNull();
        assertThat(result.categorias()).hasSize(2);
        assertThat(result.categorias().get(0).nome()).isEqualTo("Salário");
        assertThat(result.categorias().get(0).totalCategoria()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }
}
