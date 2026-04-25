package com.gestao.financeiro.service;

import com.gestao.financeiro.config.TenantContext;
import com.gestao.financeiro.dto.response.DashboardResponse.Vencimento;
import com.gestao.financeiro.entity.*;
import com.gestao.financeiro.entity.enums.*;
import com.gestao.financeiro.provider.DateProvider;
import com.gestao.financeiro.repository.*;
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
class FaturaStatusIntegrationTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private FaturaCartaoRepository faturaCartaoRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private CartaoCreditoRepository cartaoCreditoRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @MockBean
    private DateProvider dateProvider;

    private Long tenantId;
    private CartaoCredito cartao;

    @BeforeEach
    void setup() {
        Tenant tenant = new Tenant();
        tenant.setNome("Fatura Test Tenant");
        tenant.setStatus(StatusTenant.ATIVO);
        tenantId = tenantRepository.save(tenant).getId();
        TenantContext.setTenantId(tenantId);

        Conta contaCartao = Conta.builder()
                .nome("Nubank Test")
                .tipo(TipoConta.CARTAO_CREDITO)
                .saldoInicial(BigDecimal.ZERO)
                .build();
        contaCartao.setTenantId(tenantId);
        contaRepository.save(contaCartao);

        cartao = CartaoCredito.builder()
                .conta(contaCartao)
                .bandeira("NUBANK")
                .limite(BigDecimal.valueOf(1000))
                .diaFechamento(1)
                .diaVencimento(10)
                .build();
        cartao.setTenantId(tenantId);
        cartaoCreditoRepository.save(cartao);
    }

    @Test
    void deveExibirApenasFaturasFechadasOuAtrasadasNoContasAPagar() {
        LocalDate hoje = LocalDate.of(2026, 4, 15);
        when(dateProvider.now()).thenReturn(hoje);

        // 1. Fatura ABERTA (Mês 5) -> NÃO deve aparecer
        FaturaCartao aberta = new FaturaCartao();
        aberta.setTenantId(tenantId);
        aberta.setCartao(cartao);
        aberta.setMesReferencia(5);
        aberta.setAnoReferencia(2026);
        aberta.setDataVencimento(LocalDate.of(2026, 5, 10));
        aberta.setValorTotal(BigDecimal.valueOf(100));
        aberta.setStatus(StatusFatura.ABERTA);
        faturaCartaoRepository.save(aberta);

        // 2. Fatura FECHADA (Mês 4) -> DEVE aparecer
        FaturaCartao fechada = new FaturaCartao();
        fechada.setTenantId(tenantId);
        fechada.setCartao(cartao);
        fechada.setMesReferencia(4);
        fechada.setAnoReferencia(2026);
        fechada.setDataVencimento(LocalDate.of(2026, 4, 10));
        fechada.setValorTotal(BigDecimal.valueOf(250));
        fechada.setStatus(StatusFatura.FECHADA);
        faturaCartaoRepository.save(fechada);

        // 3. Fatura ATRASADA (Mês 3) -> DEVE aparecer como atrasada
        FaturaCartao atrasada = new FaturaCartao();
        atrasada.setTenantId(tenantId);
        atrasada.setCartao(cartao);
        atrasada.setMesReferencia(3);
        atrasada.setAnoReferencia(2026);
        atrasada.setDataVencimento(LocalDate.of(2026, 3, 10));
        atrasada.setValorTotal(BigDecimal.valueOf(50));
        atrasada.setStatus(StatusFatura.ATRASADA);
        faturaCartaoRepository.save(atrasada);

        // Buscando vencimentos de Abril (Mês 4)
        List<Vencimento> vencimentos = dashboardService.getTodosVencimentos(tenantId, 4, 2026);

        // Asserts
        assertThat(vencimentos).hasSize(2);
        
        // Verifica se a fatura fechada de Abril está lá
        assertThat(vencimentos).anyMatch(v -> v.descricao().contains("Fatura 4/2026") && v.valor().compareTo(BigDecimal.valueOf(250)) == 0);
        
        // Verifica se a fatura atrasada de Março também aparece (pois o sistema mostra pendências passadas)
        assertThat(vencimentos).anyMatch(v -> v.descricao().contains("Fatura 3/2026") && v.atrasado());

        // Verifica que a fatura ABERTA (Mês 5) não aparece em Abril
        assertThat(vencimentos).noneMatch(v -> v.descricao().contains("Fatura 5/2026"));
    }
}
