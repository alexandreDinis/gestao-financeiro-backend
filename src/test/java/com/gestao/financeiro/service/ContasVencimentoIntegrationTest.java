package com.gestao.financeiro.service;

import com.gestao.financeiro.config.TenantContext;
import com.gestao.financeiro.dto.request.TransacaoRecorrenteRequest;
import com.gestao.financeiro.dto.request.TransacaoRequest;
import com.gestao.financeiro.dto.response.DashboardResponse.Vencimento;
import com.gestao.financeiro.entity.*;
import com.gestao.financeiro.entity.enums.*;
import com.gestao.financeiro.provider.DateProvider;
import com.gestao.financeiro.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class ContasVencimentoIntegrationTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private TransacaoService transacaoService;

    @Autowired
    private TransacaoRecorrenteService recorrenteService;

    @Autowired
    private DividaRepository dividaRepository;

    @Autowired
    private FaturaCartaoRepository faturaCartaoRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private CartaoCreditoRepository cartaoCreditoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @MockBean
    private DateProvider dateProvider;

    private Long tenantId;
    private Long contaCorrenteId;
    private Long contaCreditoId;
    private CartaoCredito cartaoCredito;
    private Long categoriaId;

    @BeforeEach
    void setup() {
        Tenant tenant = new Tenant();
        tenant.setNome("Contas Test Tenant");
        tenant.setStatus(StatusTenant.ATIVO);
        tenantId = tenantRepository.save(tenant).getId();
        TenantContext.setTenantId(tenantId);

        Conta cc = Conta.builder()
                .nome("Banco Corrente")
                .tipo(TipoConta.CORRENTE)
                .saldoInicial(BigDecimal.valueOf(1000))
                .build();
        cc.setTenantId(tenantId);
        contaCorrenteId = contaRepository.save(cc).getId();

        Conta contaCartao = Conta.builder()
                .nome("Visa Platinum")
                .tipo(TipoConta.CARTAO_CREDITO)
                .saldoInicial(BigDecimal.ZERO)
                .build();
        contaCartao.setTenantId(tenantId);
        contaCreditoId = contaRepository.save(contaCartao).getId();

        cartaoCredito = CartaoCredito.builder()
                .conta(contaCartao)
                .bandeira("VISA")
                .limite(BigDecimal.valueOf(5000))
                .diaFechamento(1)
                .diaVencimento(10)
                .build();
        cartaoCredito.setTenantId(tenantId);
        cartaoCreditoRepository.save(cartaoCredito);

        Categoria cat = Categoria.builder()
                .nome("Geral")
                .tipo(TipoCategoria.DESPESA)
                .icone("home")
                .cor("#ff0000")
                .build();
        cat.setTenantId(tenantId);
        categoriaId = categoriaRepository.save(cat).getId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void deveExibirVencimentosCorretamenteNoMes() {
        LocalDate hoje = LocalDate.of(2026, 4, 20);
        when(dateProvider.now()).thenReturn(hoje);

        // 1. Transação padrão (DEVE aparecer)
        transacaoService.criar(new TransacaoRequest(
                "Internet", BigDecimal.valueOf(100), hoje.plusDays(5), hoje.plusDays(5),
                TipoTransacao.DESPESA, null, categoriaId, contaCorrenteId, null, "Doc", null, false, null, null, StatusTransacao.PENDENTE, null
        ));

        // 2. Transação no Cartão (NÃO DEVE aparecer individualmente)
        transacaoService.criar(new TransacaoRequest(
                "Netflix no Cartão", BigDecimal.valueOf(50), hoje.plusDays(2), hoje.plusDays(2),
                TipoTransacao.DESPESA, null, categoriaId, contaCreditoId, null, "Cartao", null, false, null, null, StatusTransacao.PENDENTE, null
        ));

        // 3. Fatura de Cartão Fechada (DEVE aparecer)
        FaturaCartao fatura = new FaturaCartao();
        fatura.setTenantId(tenantId);
        fatura.setCartao(cartaoCredito); // Agora usando CartaoCredito
        fatura.setMesReferencia(4);
        fatura.setAnoReferencia(2026);
        fatura.setDataVencimento(LocalDate.of(2026, 4, 10));
        fatura.setValorTotal(BigDecimal.valueOf(500));
        fatura.setStatus(StatusFatura.FECHADA);
        faturaCartaoRepository.save(fatura);

        // 4. Recorrência Fixa para 26/04 (DEVE aparecer agora proativamente)
        recorrenteService.criar(new TransacaoRecorrenteRequest(
                "Microsoft Office", BigDecimal.valueOf(45), TipoTransacao.DESPESA,
                Periodicidade.MENSAL, LocalDate.of(2026, 4, 26), null, 26, categoriaId, contaCorrenteId, false
        ));

        // 5. Transação em outro mês (NÃO DEVE aparecer no filtro de Abril)
        transacaoService.criar(new TransacaoRequest(
                "Aluguel Maio", BigDecimal.valueOf(1000), LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 5),
                TipoTransacao.DESPESA, null, categoriaId, contaCorrenteId, null, "Futuro", null, false, null, null, StatusTransacao.PENDENTE, null
        ));

        // Executar busca do Dashboard Hub (mes 4, ano 2026)
        List<Vencimento> vencimentos = dashboardService.getTodosVencimentos(tenantId, 4, 2026);

        // Asserts
        assertThat(vencimentos).hasSize(3); // Internet, Fatura, Microsoft Office
        
        assertThat(vencimentos).extracting(Vencimento::descricao)
                .containsExactlyInAnyOrder("Internet", "Fatura Visa Platinum - 04/2026", "Microsoft Office (recorrente)");

        assertThat(vencimentos).extracting(Vencimento::descricao)
                .doesNotContain("Netflix no Cartão", "Aluguel Maio");
    }

    @Test
    void deveExibirParcelasDeDividaNoMes() {
        LocalDate hoje = LocalDate.of(2026, 4, 1);
        when(dateProvider.now()).thenReturn(hoje);

        Divida divida = Divida.builder()
                .descricao("Empréstimo João")
                .valorTotal(BigDecimal.valueOf(200))
                .valorRestante(BigDecimal.valueOf(200))
                .dataInicio(hoje)
                .tipo(TipoDivida.A_PAGAR)
                .status(StatusDivida.PENDENTE)
                .build();
        divida.setTenantId(tenantId);
        
        ParcelaDivida p1 = new ParcelaDivida();
        p1.setDivida(divida);
        p1.setNumeroParcela(1);
        p1.setValor(BigDecimal.valueOf(100));
        p1.setDataVencimento(LocalDate.of(2026, 4, 15));
        p1.setStatus(StatusTransacao.PENDENTE);
        p1.setTenantId(tenantId);
        
        ParcelaDivida p2 = new ParcelaDivida();
        p2.setDivida(divida);
        p2.setNumeroParcela(2);
        p2.setValor(BigDecimal.valueOf(100));
        p2.setDataVencimento(LocalDate.of(2026, 5, 15));
        p2.setStatus(StatusTransacao.PENDENTE);
        p2.setTenantId(tenantId);

        divida.setParcelas(new java.util.ArrayList<>(List.of(p1, p2)));
        dividaRepository.save(divida);

        List<Vencimento> vencimentosAbril = dashboardService.getTodosVencimentos(tenantId, 4, 2026);
        assertThat(vencimentosAbril).extracting(Vencimento::descricao).contains("Empréstimo João (1/2)");
        assertThat(vencimentosAbril).hasSize(1);

        List<Vencimento> vencimentosMaio = dashboardService.getTodosVencimentos(tenantId, 5, 2026);
        assertThat(vencimentosMaio).extracting(Vencimento::descricao).contains("Empréstimo João (2/2)");
    }
}
