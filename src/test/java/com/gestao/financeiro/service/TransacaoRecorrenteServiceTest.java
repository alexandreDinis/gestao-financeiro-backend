package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.TransacaoRecorrenteRequest;
import com.gestao.financeiro.dto.response.TransacaoRecorrenteResponse;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TransacaoRecorrenteServiceTest {

    @Autowired
    private TransacaoRecorrenteService transacaoRecorrenteService;

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
        when(dateProvider.now()).thenReturn(LocalDate.of(2026, 4, 15));

        Conta conta = Conta.builder()
                .nome("Nubank")
                .tipo(TipoConta.CORRENTE)
                .saldoInicial(BigDecimal.ZERO)
                .build();
        conta.setTenantId(tenantId);
        contaId = contaRepository.save(conta).getId();

        Categoria categoria = Categoria.builder()
                .nome("Contas Casa")
                .tipo(TipoCategoria.DESPESA)
                .icone("home")
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
    void deveCriarRecorrenciaVariavelComSucesso() {
        TransacaoRecorrenteRequest request = new TransacaoRecorrenteRequest(
                "Conta de Luz", BigDecimal.valueOf(150.0), TipoTransacao.DESPESA,
                Periodicidade.MENSAL, LocalDate.of(2026, 4, 15), null, 15, categoriaId, contaId, true
        );

        TransacaoRecorrenteResponse response = transacaoRecorrenteService.criar(request);

        assertThat(response.valorVariavel()).isTrue();
        assertThat(response.valor()).isEqualByComparingTo(BigDecimal.valueOf(150.0));
    }

    @Test
    void deveGerarTransacaoComValorPrevisto() {
        // 1. Criar Recorrência Variável
        TransacaoRecorrenteRequest request = new TransacaoRecorrenteRequest(
                "Conta de Luz", BigDecimal.valueOf(100.0), TipoTransacao.DESPESA,
                Periodicidade.MENSAL, LocalDate.of(2026, 4, 15), null, 15, categoriaId, contaId, true
        );
        Long recId = transacaoRecorrenteService.criar(request).id();

        // 2. Rodar Geração
        transacaoRecorrenteService.processarRecorrencias();

        // 3. Validar a Transação
        List<Transacao> transacoes = transacaoRepository.findByRecorrenciaId(recId);
        assertThat(transacoes).hasSize(1);
        
        Transacao tx = transacoes.get(0);
        assertThat(tx.getValorPrevisto()).isNotNull();
        assertThat(tx.getValorPrevisto()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
        assertThat(tx.getValor()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
    }

    @Test
    void deveGerarTransacaoComValorPrevistoNuloParaRecorrenciaFixa() {
        // 1. Criar Recorrência Fixa
        TransacaoRecorrenteRequest request = new TransacaoRecorrenteRequest(
                "Internet", BigDecimal.valueOf(99.90), TipoTransacao.DESPESA,
                Periodicidade.MENSAL, LocalDate.of(2026, 4, 15), null, 15, categoriaId, contaId, false
        );
        Long recId = transacaoRecorrenteService.criar(request).id();

        // 2. Rodar Geração
        transacaoRecorrenteService.processarRecorrencias();

        // 3. Validar a Transação
        List<Transacao> transacoes = transacaoRepository.findByRecorrenciaId(recId);
        assertThat(transacoes).hasSize(1);
        
        Transacao tx = transacoes.get(0);
        assertThat(tx.getValorPrevisto()).isNull();
        assertThat(tx.getValor()).isEqualByComparingTo(BigDecimal.valueOf(99.90));
    }

    @Test
    void naoDeveDuplicarTransacaoAoRodarGeracaoDuasVezes() {
        // 1. Criar Recorrência
        TransacaoRecorrenteRequest request = new TransacaoRecorrenteRequest(
                "Conta de Luz", BigDecimal.valueOf(100.0), TipoTransacao.DESPESA,
                Periodicidade.MENSAL, LocalDate.of(2026, 4, 15), null, 15, categoriaId, contaId, true
        );
        Long recId = transacaoRecorrenteService.criar(request).id();

        // 2. Rodar Geração (1ª vez)
        transacaoRecorrenteService.processarRecorrencias();
        assertThat(transacaoRepository.findByRecorrenciaId(recId)).hasSize(1);

        // 3. Rodar Geração Novamente (2ª vez)
        transacaoRecorrenteService.processarRecorrencias();
        assertThat(transacaoRepository.findByRecorrenciaId(recId)).hasSize(1); // Não deve ter criado outra
    }

    @Test
    void deveEvitarDuplicacaoEmExecucaoConcorrente() throws InterruptedException {
        // 1. Criar Recorrência
        TransacaoRecorrenteRequest request = new TransacaoRecorrenteRequest(
                "Netflix Concorrente", BigDecimal.valueOf(50.0), TipoTransacao.DESPESA,
                Periodicidade.MENSAL, LocalDate.of(2026, 4, 15), null, 15, categoriaId, contaId, true
        );
        Long recId = transacaoRecorrenteService.criar(request).id();

        // 2. Simular 5 threads chamando o processarRecorrencias ao mesmo tempo
        int numThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    latch.await(); // Todas threads esperam aqui
                    transacaoRecorrenteService.processarRecorrencias();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Libera todas as threads simultaneamente
        latch.countDown();
        doneLatch.await(); // Aguarda todas terminarem

        // 3. Validar que apenas 1 transação foi criada, não 5
        assertThat(transacaoRepository.findByRecorrenciaId(recId)).hasSize(1);
    }

    @Test
    void devePropagarAtualizacaoParaTransacoesPendentes() {
        // 1. Criar Recorrência com dia de vencimento 10
        TransacaoRecorrenteRequest request = new TransacaoRecorrenteRequest(
                "Conta de Luz Original", BigDecimal.valueOf(100.0), TipoTransacao.DESPESA,
                Periodicidade.MENSAL, LocalDate.of(2026, 4, 10), null, 10, categoriaId, contaId, false
        );
        Long recId = transacaoRecorrenteService.criar(request).id();

        // 2. Materializar transação pendente
        List<Transacao> transacoes = transacaoRepository.findByRecorrenciaId(recId);
        assertThat(transacoes).hasSize(1);
        Transacao txPendente = transacoes.get(0);
        assertThat(txPendente.getDataVencimento()).isEqualTo(LocalDate.of(2026, 4, 10));

        // 3. Atualizar a Recorrência alterando dia de vencimento para 25 e valor para 120.0
        TransacaoRecorrenteRequest updateRequest = new TransacaoRecorrenteRequest(
                "Conta de Luz Atualizada", BigDecimal.valueOf(120.0), TipoTransacao.DESPESA,
                Periodicidade.MENSAL, LocalDate.of(2026, 4, 25), null, 25, categoriaId, contaId, false
        );
        transacaoRecorrenteService.atualizar(recId, updateRequest);

        // 4. Verificar se a transação pendente foi atualizada com o novo vencimento e valor
        Transacao txAtualizada = transacaoRepository.findById(txPendente.getId()).orElseThrow();
        assertThat(txAtualizada.getDataVencimento()).isEqualTo(LocalDate.of(2026, 4, 25));
        assertThat(txAtualizada.getData()).isEqualTo(LocalDate.of(2026, 4, 25));
        assertThat(txAtualizada.getValor()).isEqualByComparingTo(BigDecimal.valueOf(120.0));
        assertThat(txAtualizada.getDescricao()).contains("Conta de Luz Atualizada");
    }
}
