package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.TransacaoRecorrenteRequest;
import com.gestao.financeiro.dto.request.TransacaoRequest;
import com.gestao.financeiro.dto.response.DashboardResponse;
import com.gestao.financeiro.entity.Categoria;
import com.gestao.financeiro.entity.Conta;
import com.gestao.financeiro.entity.Transacao;
import com.gestao.financeiro.entity.enums.Periodicidade;
import com.gestao.financeiro.entity.enums.TipoCategoria;
import com.gestao.financeiro.entity.enums.TipoConta;
import com.gestao.financeiro.entity.enums.TipoTransacao;
import com.gestao.financeiro.provider.DateProvider;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class VariableExpenseIntegrationTest {

    @Autowired
    private TransacaoRecorrenteService transacaoRecorrenteService;

    @Autowired
    private TransacaoService transacaoService;

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

    @MockBean
    private DateProvider dateProvider;

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

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void deveExecutarFluxoCompletoDeContaVariavelERegressao() {
        // (1) Criar Conta de Luz (Variável, R$ 100) no mês 4
        LocalDate hojeAbril = LocalDate.of(2026, 4, 10);
        when(dateProvider.now()).thenReturn(hojeAbril);

        TransacaoRecorrenteRequest recReq = new TransacaoRecorrenteRequest(
                "Conta de Luz", BigDecimal.valueOf(100.0), TipoTransacao.DESPESA,
                Periodicidade.MENSAL, hojeAbril, null, 10, categoriaId, contaId, true
        );
        Long recId = transacaoRecorrenteService.criar(recReq).id();

        // (2) Acionar scheduler (gera o mês 4)
        transacaoRecorrenteService.processarRecorrencias();

        // (3) Buscar no Repositório e afirmar que valor = 100 e valorPrevisto = 100
        List<Transacao> geradas = transacaoRepository.findByRecorrenciaId(recId);
        assertThat(geradas).isNotEmpty();
        
        var txAbril = geradas.get(0);
        assertThat(txAbril.getValor()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
        assertThat(txAbril.getValorPrevisto()).isEqualByComparingTo(BigDecimal.valueOf(100.0));

        // (4) Ajustar a transação gerada para R$ 120 e marcar como PAGO
        TransacaoRequest editReq = new TransacaoRequest(
                "Conta de Luz", BigDecimal.valueOf(120.0), hojeAbril, null,
                TipoTransacao.DESPESA, null, categoriaId, contaId, null, null, null, false, null, null, null, null
        );
        transacaoService.atualizar(txAbril.getId(), editReq);
        transacaoService.pagar(txAbril.getId());

        // (5) Buscar do BD novamente e afirmar que valor = 120 e valorPrevisto = 100
        Transacao txPagaAbril = transacaoRepository.findById(txAbril.getId()).orElseThrow();
        assertThat(txPagaAbril.getValor()).isEqualByComparingTo(BigDecimal.valueOf(120.0));
        assertThat(txPagaAbril.getValorPrevisto()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
        assertThat(txPagaAbril.getStatus()).isEqualTo(com.gestao.financeiro.entity.enums.StatusTransacao.PAGO);

        // (6) Garantir que a recorrência mãe continua intacta (valor padrão 100)
        var recorrencia = transacaoRecorrenteService.buscarPorId(recId);
        assertThat(recorrencia.valor()).isEqualByComparingTo(BigDecimal.valueOf(100.0));

        // (7) REGRESSÃO: Editar a recorrência para novo valor base de R$ 200
        TransacaoRecorrenteRequest recUpdateReq = new TransacaoRecorrenteRequest(
                "Conta de Luz", BigDecimal.valueOf(200.0), TipoTransacao.DESPESA,
                Periodicidade.MENSAL, hojeAbril, null, 10, categoriaId, contaId, true
        );
        transacaoRecorrenteService.atualizar(recId, recUpdateReq);

        // (8) Mudar a data para mês 5 e Gerar próximo mês
        LocalDate hojeMaio = hojeAbril.plusMonths(1);
        when(dateProvider.now()).thenReturn(hojeMaio);
        transacaoRecorrenteService.processarRecorrencias();

        // (9) Verificar que nova transação usa valor atualizado da recorrência
        List<Transacao> transacoesMaio = transacaoRepository.findByRecorrenciaId(recId);
        
        var txMaio = transacoesMaio.stream().filter(t -> t.getData().getMonthValue() == hojeMaio.getMonthValue()).findFirst().orElseThrow();
        assertThat(txMaio.getValor()).isEqualByComparingTo(BigDecimal.valueOf(200.0));
        assertThat(txMaio.getValorPrevisto()).isEqualByComparingTo(BigDecimal.valueOf(200.0));

        // (10) Verificar que a transação antiga (mês 4) continuou com 120 / previsto 100
        Transacao txAntiga = transacaoRepository.findById(txAbril.getId()).orElseThrow();
        assertThat(txAntiga.getValor()).isEqualByComparingTo(BigDecimal.valueOf(120.0));
        assertThat(txAntiga.getValorPrevisto()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
    }
}
