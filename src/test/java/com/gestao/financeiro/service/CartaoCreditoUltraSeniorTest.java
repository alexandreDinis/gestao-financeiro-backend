package com.gestao.financeiro.service;

import com.gestao.financeiro.config.TenantContext;
import com.gestao.financeiro.dto.request.CartaoCreditoRequest;
import com.gestao.financeiro.dto.request.CompraCartaoRequest;
import com.gestao.financeiro.dto.request.PagarFaturaRequest;
import com.gestao.financeiro.dto.response.FaturaCartaoResponse;
import com.gestao.financeiro.entity.Categoria;
import com.gestao.financeiro.entity.Conta;
import com.gestao.financeiro.entity.enums.StatusFatura;
import com.gestao.financeiro.entity.enums.TipoCategoria;
import com.gestao.financeiro.entity.enums.TipoConta;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.exception.SaldoInsuficienteException;
import com.gestao.financeiro.provider.DateProvider;
import com.gestao.financeiro.repository.CategoriaRepository;
import com.gestao.financeiro.repository.ContaRepository;
import com.gestao.financeiro.repository.FaturaCartaoRepository;
import com.gestao.financeiro.repository.LancamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class CartaoCreditoUltraSeniorTest {

    @Autowired
    private CartaoCreditoService cartaoCreditoService;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private FaturaCartaoRepository faturaRepository;

    @Autowired
    private com.gestao.financeiro.repository.CartaoCreditoRepository cartaoRepository;

    @Autowired
    private LancamentoRepository lancamentoRepository;

    @MockBean
    private DateProvider dateProvider;

    private Long cartaoId;
    private Long contaPagadoraId;
    private Long categoriaId;

    @BeforeEach
    void setup() {
        TenantContext.setTenantId(1L);

        // Simular data atual: 16/04/2026 (Usando doReturn para maior robustez)
        org.mockito.Mockito.doReturn(LocalDate.of(2026, 4, 16)).when(dateProvider).now();

        // 1. Criar categoria
        Categoria categoria = Categoria.builder()
                .nome("Testes Senior")
                .tipo(TipoCategoria.DESPESA)
                .icone("code")
                .cor("#FF0000")
                .build();
        categoria.setTenantId(1L);
        categoria = categoriaRepository.save(categoria);
        categoriaId = categoria.getId();

        // 2. Criar conta com saldo inicial (R$ 1000,00)
        Conta conta = Conta.builder()
                .nome("Conta Corrente Teste")
                .tipo(TipoConta.CORRENTE)
                .saldoInicial(BigDecimal.valueOf(1000.0))
                .ativa(true)
                .build();
        conta.setTenantId(1L);
        conta = contaRepository.saveAndFlush(conta);
        contaPagadoraId = conta.getId();

        // 3. Criar Cartão (Fechamento dia 5, Vencimento dia 10)
        CartaoCreditoRequest cartaoReq = new CartaoCreditoRequest(
                "Visa Senior", "VISA", BigDecimal.valueOf(5000), 5, 10
        );
        var cartaoRes = cartaoCreditoService.criarCartao(cartaoReq);
        cartaoId = cartaoRes.id();
    }

    @Test
    void devePermitirPagamentoDeFaturaAbertaEFuturaComSucesso() {
        // 1. Registrar compra parcelada em 3x (Abril, Maio, Junho) - Total R$ 600
        cartaoCreditoService.comprar(new CompraCartaoRequest(
                cartaoId, categoriaId, "Compra Mix", BigDecimal.valueOf(600.0), 3, LocalDate.of(2026, 4, 16)
        ));

        // Busca faturas (Abril é ABERTA pois dia 5 já passou e o job não rodou, mas pela data da compra o venc é Maio)
        // Na verdade, 16/04 com fechamento 5 vai para vencimento 10/05.
        // P1: Maio, P2: Junho, P3: Julho.
        
        var faturas = cartaoCreditoService.listarFaturas(cartaoId);
        assertThat(faturas).hasSize(3); // Maio, Junho, Julho

        FaturaCartaoResponse faturaMaio = faturas.stream().filter(f -> f.mesReferencia() == 5).findFirst().orElseThrow();
        FaturaCartaoResponse faturaJunho = faturas.stream().filter(f -> f.mesReferencia() == 6).findFirst().orElseThrow();

        // 2. Pagar fatura de Maio (Aberta/Atual)
        cartaoCreditoService.pagarFatura(faturaMaio.id(), new PagarFaturaRequest(contaPagadoraId, LocalDate.of(2026, 4, 16), "idemp-01"));

        // Validar saldo da conta (Ledger)
        BigDecimal saldoAposMaio = contaRepository.calcularSaldo(contaPagadoraId);
        assertThat(saldoAposMaio).isEqualByComparingTo(BigDecimal.valueOf(800.0));

        // 3. Pagar fatura de Junho (Futura/Aberta) - Antecipação
        cartaoCreditoService.pagarFatura(faturaJunho.id(), new PagarFaturaRequest(contaPagadoraId, LocalDate.of(2026, 4, 17), "idemp-02"));

        // Validar saldo da conta (Ledger)
        BigDecimal saldoAposJunho = contaRepository.calcularSaldo(contaPagadoraId);
        assertThat(saldoAposJunho).isEqualByComparingTo(BigDecimal.valueOf(600.0));

        // Validar Status no DB
        var faturaDb = faturaRepository.findById(faturaJunho.id()).orElseThrow();
        assertThat(faturaDb.getStatus()).isEqualTo(StatusFatura.PAGA);
    }

    @Test
    void deveBloquearPagamentoDuplicadoMesmaChaveIdempotencia() {
        cartaoCreditoService.comprar(new CompraCartaoRequest(
                cartaoId, categoriaId, "Compra Única", BigDecimal.valueOf(100.0), 1, LocalDate.of(2026, 4, 16)
        ));
        var fatura = cartaoCreditoService.listarFaturas(cartaoId).get(0);

        // Primeira chamada
        cartaoCreditoService.pagarFatura(fatura.id(), new PagarFaturaRequest(contaPagadoraId, LocalDate.of(2026, 4, 16), "idemp-dup"));
        
        // Segunda chamada com mesma chave
        cartaoCreditoService.pagarFatura(fatura.id(), new PagarFaturaRequest(contaPagadoraId, LocalDate.of(2026, 4, 16), "idemp-dup"));

        // Saldo deve ter reduzido apenas 1x (1000 - 100 = 900)
        BigDecimal saldo = contaRepository.calcularSaldo(contaPagadoraId);
        assertThat(saldo).isEqualByComparingTo(BigDecimal.valueOf(900.0));
    }

    @Test
    void deveFazerShiftDeLancamentoSeFaturaEstiverPaga() {
        // 1. Compra original em Maio
        cartaoCreditoService.comprar(new CompraCartaoRequest(
                cartaoId, categoriaId, "Compra original", BigDecimal.valueOf(100.0), 1, LocalDate.of(2026, 4, 16)
        ));
        var faturaMaio = cartaoCreditoService.listarFaturas(cartaoId).get(0);
        
        // 2. Pagar a fatura de Maio
        cartaoCreditoService.pagarFatura(faturaMaio.id(), new PagarFaturaRequest(contaPagadoraId, LocalDate.of(2026, 4, 16), "idemp-shift"));

        // 3. Nova compra que cairia em Maio (pela data e ciclo)
        cartaoCreditoService.comprar(new CompraCartaoRequest(
                cartaoId, categoriaId, "Nova compra mesmo ciclo", BigDecimal.valueOf(50.0), 1, LocalDate.of(2026, 4, 16)
        ));

        // 4. Validar que a compra de 50.0 caiu em JUNHO (shift de Maio PAGA)
        var faturas = cartaoCreditoService.listarFaturas(cartaoId);
        FaturaCartaoResponse faturaMaioRes = faturas.stream().filter(f -> f.mesReferencia() == 5).findFirst().orElseThrow();
        FaturaCartaoResponse faturaJunhoRes = faturas.stream().filter(f -> f.mesReferencia() == 6).findFirst().orElseThrow();

        assertThat(faturaMaioRes.valorTotal()).isEqualByComparingTo(BigDecimal.valueOf(100.0)); // Não mudou
        assertThat(faturaJunhoRes.valorTotal()).isEqualByComparingTo(BigDecimal.valueOf(50.0)); // Recebeu o shift
    }

    @Test
    void deveBloquearPagamentoDeFaturaVazia() {
        // Fatura de Maio existe mas está vazia (sem parcelas)
        // Vamos forçar a criação de uma fatura vazia
        cartaoCreditoService.comprar(new CompraCartaoRequest(
                cartaoId, categoriaId, "Compra", BigDecimal.valueOf(100.0), 1, LocalDate.of(2026, 4, 16)
        ));
        var faturaMaio = faturaRepository.findByCartaoIdAndMesReferenciaAndAnoReferencia(cartaoId, 5, 2026).get();
        
        // Tentar pagar uma fatura inexistente/vazia de Dezembro
        var faturaDez = cartaoCreditoService.obterFaturaParaLancamento(
                cartaoRepository.findById(cartaoId).get(), 12, 2026
        );

        assertThrows(BusinessException.class, () -> {
            cartaoCreditoService.pagarFatura(faturaDez.getId(), new PagarFaturaRequest(contaPagadoraId, LocalDate.of(2026, 4, 16), "idemp-vazia"));
        });
    }
}
