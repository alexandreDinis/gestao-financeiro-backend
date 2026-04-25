package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.CartaoCreditoRequest;
import com.gestao.financeiro.dto.request.TransacaoRecorrenteRequest;
import com.gestao.financeiro.dto.response.TransacaoRecorrenteResponse;
import com.gestao.financeiro.dto.response.CartaoCreditoResponse;
import com.gestao.financeiro.dto.response.CartaoCreditoResponse.*;
import com.gestao.financeiro.entity.Categoria;
import com.gestao.financeiro.entity.enums.Periodicidade;
import com.gestao.financeiro.entity.enums.TipoCategoria;
import com.gestao.financeiro.entity.enums.TipoTransacao;
import com.gestao.financeiro.provider.DateProvider;
import com.gestao.financeiro.repository.CategoriaRepository;
import com.gestao.financeiro.repository.FaturaCartaoRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CreditCardRecurrenceTest {

    @Autowired
    private CartaoCreditoService cartaoCreditoService;

    @Autowired
    private TransacaoRecorrenteService transacaoRecorrenteService;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private FaturaCartaoRepository faturaRepository;

    @MockBean
    private DateProvider dateProvider;

    @Autowired
    private com.gestao.financeiro.repository.TenantRepository tenantRepository;

    private Long tenantId;
    private Long cartaoId;
    private Long contaId;
    private Long categoriaId;

    @BeforeEach
    void setup() {
        com.gestao.financeiro.entity.Tenant tenant = new com.gestao.financeiro.entity.Tenant();
        tenant.setNome("Test Tenant " + System.currentTimeMillis());
        tenant.setStatus(StatusTenant.ATIVO);
        tenantId = tenantRepository.save(tenant).getId();

        TenantContext.setTenantId(tenantId);
        when(dateProvider.now()).thenReturn(LocalDate.now());

        // Criar categoria de testes
        Categoria categoria = Categoria.builder()
                .nome("Assinaturas")
                .tipo(TipoCategoria.DESPESA)
                .icone("tv")
                .cor("#FF0000")
                .build();
        categoria.setTenantId(tenantId);
        categoria = categoriaRepository.save(categoria);
        categoriaId = categoria.getId();

        // Criar Cartão (Fechamento 25, Vencimento 05)
        CartaoCreditoRequest cartaoReq = new CartaoCreditoRequest(
                "Visa Platinum", "VISA", BigDecimal.valueOf(10000), 25, 5
        );
        CartaoCreditoResponse cartao = cartaoCreditoService.criarCartao(cartaoReq);
        cartaoId = cartao.id();
        contaId = cartao.contaId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void deveGerarTransacaoRecorrenteNoCicloCorretoDoCartao() {
        // --- MÊS 1: ABRIL ---
        // Simular hoje = 11/04/2026
        LocalDate hojeAbril = LocalDate.of(2026, 4, 11);
        when(dateProvider.now()).thenReturn(hojeAbril);

        // Criar recorrência de R$ 50,00 começando hoje
        TransacaoRecorrenteRequest recReq = new TransacaoRecorrenteRequest(
                "Netflix", BigDecimal.valueOf(50.0), TipoTransacao.DESPESA,
                Periodicidade.MENSAL, hojeAbril, null, 11, categoriaId, contaId, false
        );
        TransacaoRecorrenteResponse recRes = transacaoRecorrenteService.criar(recReq);
        Long recId = recRes.id();

        // O Job de processamento deve ser acionado (Simulando o Job)
        transacaoRecorrenteService.processarRecorrencias();

        // Verificar se a transação foi gerada para hoje (Filtra pelo ID da recorrência)
        var transacoesAbril = transacaoRepository.findByRecorrenciaId(recId);
        assertThat(transacoesAbril).hasSize(1);
        assertThat(transacoesAbril.get(0).getValor()).isEqualByComparingTo(BigDecimal.valueOf(50.0));

        // Verificar se caiu na fatura correta (Maio/2026, pois hoje 11/04 < fechamento 25/04)
        var faturaMaio = faturaRepository.findByCartaoIdAndMesReferenciaAndAnoReferencia(cartaoId, 5, 2026);
        assertThat(faturaMaio).isPresent();
        assertThat(faturaMaio.get().getValorTotal()).isEqualByComparingTo(BigDecimal.valueOf(50.0));

        // --- MÊS 2: MAIO ---
        // Simular hoje = 11/05/2026
        LocalDate hojeMaio = LocalDate.of(2026, 5, 11);
        when(dateProvider.now()).thenReturn(hojeMaio);

        // Rodar processamento novamente
        transacaoRecorrenteService.processarRecorrencias();

        // Deve existir agora 2 transações no total para ESTA recorrência
        var transacoesTotal = transacaoRepository.findByRecorrenciaId(recId);
        assertThat(transacoesTotal).hasSize(2);

        // A segunda transação deve cair na fatura de Junho/2026
        var faturaJunho = faturaRepository.findByCartaoIdAndMesReferenciaAndAnoReferencia(cartaoId, 6, 2026);
        assertThat(faturaJunho).isPresent();
        assertThat(faturaJunho.get().getValorTotal()).isEqualByComparingTo(BigDecimal.valueOf(50.0));
        
        // Verificar o resumo do cartão
        CartaoCreditoResponse resumo = cartaoCreditoService.buscarCartao(cartaoId);
        assertThat(resumo.utilizado()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
        assertThat(resumo.disponivel()).isEqualByComparingTo(BigDecimal.valueOf(9900.0));
    }
}
