package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.response.DashboardResponse;
import com.gestao.financeiro.entity.Categoria;
import com.gestao.financeiro.entity.Conta;
import com.gestao.financeiro.entity.Transacao;
import com.gestao.financeiro.entity.enums.StatusTransacao;
import com.gestao.financeiro.entity.enums.TipoCategoria;
import com.gestao.financeiro.entity.enums.TipoConta;
import com.gestao.financeiro.entity.enums.TipoTransacao;
import com.gestao.financeiro.repository.CategoriaRepository;
import com.gestao.financeiro.repository.ContaRepository;
import com.gestao.financeiro.repository.TransacaoRepository;
import com.gestao.financeiro.config.TenantContext;
import com.gestao.financeiro.entity.enums.StatusTenant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class DashboardServiceTest {

    @Autowired
    private DashboardService dashboardService;

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
    private Long categoriaId;

    @BeforeEach
    void setup() {
        com.gestao.financeiro.entity.Tenant tenant = new com.gestao.financeiro.entity.Tenant();
        tenant.setNome("Test Tenant " + System.currentTimeMillis());
        tenant.setStatus(StatusTenant.ATIVO);
        tenantId = tenantRepository.save(tenant).getId();
        
        TenantContext.setTenantId(tenantId);

        Conta conta = Conta.builder()
                .nome("Nubank")
                .tipo(TipoConta.CORRENTE)
                .saldoInicial(BigDecimal.ZERO)
                .build();
        conta.setTenantId(tenantId);
        contaId = contaRepository.save(conta).getId();

        Categoria categoria = Categoria.builder()
                .nome("Energia")
                .tipo(TipoCategoria.DESPESA)
                .icone("zap")
                .cor("#000000")
                .build();
        categoria.setTenantId(tenantId);
        categoriaId = categoriaRepository.save(categoria).getId();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void deveRetornarValorPrevistoNoResumoDeUltimasTransacoes() {
        Transacao tx = Transacao.builder()
                .descricao("Conta de Luz")
                .valor(BigDecimal.valueOf(120.0))
                .valorPrevisto(BigDecimal.valueOf(100.0)) // Valor previsto original
                .tipo(TipoTransacao.DESPESA)
                .data(LocalDate.now()) // Mesma data do dashboard
                .status(StatusTransacao.PAGO)
                .categoria(categoriaRepository.findById(categoriaId).orElseThrow())
                .build();
        tx.addLancamento(com.gestao.financeiro.entity.Lancamento.builder()
                .conta(contaRepository.findById(contaId).orElseThrow())
                .valor(tx.getValor())
                .direcao(com.gestao.financeiro.entity.enums.DirecaoLancamento.DEBITO)
                .descricao(tx.getDescricao())
                .build());
        tx.setTenantId(tenantId);
        transacaoRepository.save(tx);
        transacaoRepository.flush();

        DashboardResponse dashboard = dashboardService.getDashboard();

        assertThat(dashboard.ultimasTransacoes()).isNotEmpty();
        var ultima = dashboard.ultimasTransacoes().get(0);
        assertThat(ultima.valorPrevisto()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
        assertThat(ultima.valor()).isEqualByComparingTo(BigDecimal.valueOf(120.0));
    }

    @Test
    void deveRetornarValorPrevistoNoResumoDeVencimentos() {
        Transacao tx = Transacao.builder()
                .descricao("Conta de Luz Pendente")
                .valor(BigDecimal.valueOf(100.0))
                .valorPrevisto(BigDecimal.valueOf(100.0))
                .tipo(TipoTransacao.DESPESA)
                .data(LocalDate.now().plusDays(2)) // Data futura = vencimento
                .status(StatusTransacao.PENDENTE)
                .categoria(categoriaRepository.findById(categoriaId).orElseThrow())
                .build();
        tx.addLancamento(com.gestao.financeiro.entity.Lancamento.builder()
                .conta(contaRepository.findById(contaId).orElseThrow())
                .valor(tx.getValor())
                .direcao(com.gestao.financeiro.entity.enums.DirecaoLancamento.DEBITO)
                .descricao(tx.getDescricao())
                .build());
        tx.setTenantId(tenantId);
        transacaoRepository.save(tx);
        transacaoRepository.flush();

        DashboardResponse dashboard = dashboardService.getDashboard();

        assertThat(dashboard.proximosVencimentos().proximos30Dias()).isNotEmpty();
        var vencimento = dashboard.proximosVencimentos().proximos30Dias().get(0);
        assertThat(vencimento.valorPrevisto()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
        assertThat(vencimento.valor()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
    }
}
