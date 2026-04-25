package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.TransacaoRequest;
import com.gestao.financeiro.dto.response.TransacaoResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TransacaoServiceTest {

    @Autowired
    private TransacaoService transacaoService;

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
                .nome("Geral")
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
    void devePreservarValorPrevistoAoEditarValorReal() {
        // 1. Criar transação simulando que veio do gerador (com valorPrevisto)
        Transacao tx = Transacao.builder()
                .descricao("Conta de Água")
                .valor(BigDecimal.valueOf(100.0))
                .valorPrevisto(BigDecimal.valueOf(100.0)) // Valor original
                .tipo(TipoTransacao.DESPESA)
                .data(LocalDate.now())
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
        tx = transacaoRepository.save(tx);

        // 2. Editar a transação via Service (Request não envia valorPrevisto)
        TransacaoRequest editReq = new TransacaoRequest(
                "Conta de Água - Alterada", BigDecimal.valueOf(130.0), LocalDate.now(), null,
                TipoTransacao.DESPESA, null, categoriaId, contaId, null, null, null, false, null, null, null, null
        );
        TransacaoResponse response = transacaoService.atualizar(tx.getId(), editReq);

        // 3. Validar se o valorPrevisto não foi apagado
        assertThat(response.valor()).isEqualByComparingTo(BigDecimal.valueOf(130.0));
        assertThat(response.valorPrevisto()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
    }

    @Test
    void deveCriarTransacaoManualComSucessoSemAfetarRegra() {
        TransacaoRequest req = new TransacaoRequest(
                "Compra Avulsa", BigDecimal.valueOf(50.0), LocalDate.now(), null,
                TipoTransacao.DESPESA, null, categoriaId, contaId, null, null, null, false, null, null, null, null
        );

        TransacaoResponse response = transacaoService.criar(req);

        // Transação manual não deve ter valor previsto
        assertThat(response.valorPrevisto()).isNull();
    }

    @Test
    void deveManterValorEValorPrevistoNaEdicaoParcial() {
        // 1. Criar transação com valorPrevisto = 100 e valor = 100
        Transacao tx = Transacao.builder()
                .descricao("Conta de Água")
                .valor(BigDecimal.valueOf(100.0))
                .valorPrevisto(BigDecimal.valueOf(100.0))
                .tipo(TipoTransacao.DESPESA)
                .data(LocalDate.now())
                .status(StatusTransacao.PENDENTE)
                .categoria(categoriaRepository.findById(categoriaId).orElseThrow())
                .build();
        tx.addLancamento(com.gestao.financeiro.entity.Lancamento.builder()
                .conta(contaRepository.findById(contaId).orElseThrow())
                .valor(tx.getValor())
                .direcao(com.gestao.financeiro.entity.enums.DirecaoLancamento.DEBITO)
                .descricao(tx.getDescricao())
                .build());
        tx.setTenantId(1L);
        tx = transacaoRepository.save(tx);

        // 2. Editar APENAS a descrição
        TransacaoRequest editReq = new TransacaoRequest(
                "Conta de Água - Descrição Nova", BigDecimal.valueOf(100.0), LocalDate.now(), null,
                TipoTransacao.DESPESA, null, categoriaId, contaId, null, null, null, false, null, null, null, null
        );
        TransacaoResponse response = transacaoService.atualizar(tx.getId(), editReq);

        // 3. Validar se o valorPrevisto não foi apagado
        assertThat(response.descricao()).isEqualTo("Conta de Água - Descrição Nova");
        assertThat(response.valor()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
        assertThat(response.valorPrevisto()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
    }
}
