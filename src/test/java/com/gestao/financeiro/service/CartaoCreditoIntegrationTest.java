package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.CartaoCreditoRequest;
import com.gestao.financeiro.dto.request.CompraCartaoRequest;
import com.gestao.financeiro.dto.response.CartaoCreditoResponse;
import com.gestao.financeiro.dto.response.FaturaCartaoResponse;
import com.gestao.financeiro.entity.Categoria;
import com.gestao.financeiro.entity.Recorrencia;
import com.gestao.financeiro.entity.enums.StatusFatura;
import com.gestao.financeiro.entity.enums.StatusRecorrencia;
import com.gestao.financeiro.entity.enums.TipoCategoria;
import com.gestao.financeiro.provider.DateProvider;
import com.gestao.financeiro.repository.CategoriaRepository;
import com.gestao.financeiro.repository.ContaRepository;
import com.gestao.financeiro.repository.RecorrenciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CartaoCreditoIntegrationTest {

    @Autowired
    private CartaoCreditoService cartaoCreditoService;

    @Autowired
    private RecorrenciaService recorrenciaService;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private RecorrenciaRepository recorrenciaRepository;

    @Autowired
    private ContaRepository contaRepository;

    @MockBean
    private DateProvider dateProvider;

    private Long cartaoId;
    private Long contaId;
    private Long categoriaId;

    @BeforeEach
    void setup() {
        // Criar categoria
        Categoria categoria = Categoria.builder()
                .nome("Testes")
                .tipo(TipoCategoria.DESPESA)
                .icone("home")
                .cor("#000000")
                .build();
        categoria.setTenantId(1L);
        categoria = categoriaRepository.save(categoria);
        categoriaId = categoria.getId();

        // Criar Cartão que fecha dia 5 e vence dia 10
        CartaoCreditoRequest cartaoReq = new CartaoCreditoRequest(
                "NuTest", "NUBANK", BigDecimal.valueOf(5000), 5, 10
        );
        CartaoCreditoResponse cartao = cartaoCreditoService.criarCartao(cartaoReq);
        cartaoId = cartao.id();
        contaId = cartao.contaId();
    }

    @Test
    void deveAgruparFaturasCorretamenteAntesEDepoisDoFechamento() {
        // 1. Data simulada: 02/03/2026 (ANTES DO FECHAMENTO: dia 5)
        when(dateProvider.now()).thenReturn(LocalDate.of(2026, 3, 2));

        // Cadastrar uma recorrência (Assinatura que cobra dia 2)
        Recorrencia rec = Recorrencia.builder()
                .descricao("Assinatura TV")
                .valorPrevisto(BigDecimal.valueOf(50.0))
                .dataInicio(LocalDate.of(2026, 3, 1))
                .diaVencimento(2)
                .status(StatusRecorrencia.ATIVA)
                .categoria(categoriaRepository.findById(categoriaId).orElseThrow())
                .conta(contaRepository.findById(contaId).orElseThrow())
                .build();
        rec.setTenantId(1L);
        recorrenciaRepository.save(rec);

        // Processar recorrências para o dia de hoje (02/03)
        recorrenciaService.processarRecorrenciasAgendadas();
        
        // Simular a primeira compra (Antes do fechamento) -> R$ 100
        cartaoCreditoService.comprar(new CompraCartaoRequest(
                cartaoId, categoriaId, "Compra 1 Antes do Fechamento", BigDecimal.valueOf(100.0), 
                1, LocalDate.of(2026, 3, 2)
        ));

        // 2. Data simulada: 06/03/2026 (DEPOIS DO FECHAMENTO: dia 5)
        when(dateProvider.now()).thenReturn(LocalDate.of(2026, 3, 6));

        // Processar faturas (O Job diário roda aqui e deve fechar a fatura de março!)
        cartaoCreditoService.processarFaturas();

        // Cadastrar a segunda compra (Depois do fechamento) -> R$ 300
        cartaoCreditoService.comprar(new CompraCartaoRequest(
                cartaoId, categoriaId, "Compra 2 Depois do Fechamento", BigDecimal.valueOf(300.0), 
                1, LocalDate.of(2026, 3, 6)
        ));

        // 3. Validações
        List<FaturaCartaoResponse> faturas = cartaoCreditoService.listarFaturas(cartaoId);
        
        // Devem existir 2 faturas (Março e Abril)
        assertThat(faturas).hasSize(2);

        FaturaCartaoResponse faturaAbril = faturas.get(0); // Ordenado desc, Abril vem primeiro
        FaturaCartaoResponse faturaMarco = faturas.get(1);

        // A fatura de Março deve estar FECHADA, pois o processamento rodou dia 06.
        assertThat(faturaMarco.status()).isEqualTo(StatusFatura.FECHADA);
        // O valor deve ser R$ 150 (R$ 100 da compra + R$ 50 da recorrência)
        assertThat(faturaMarco.valorTotal()).isEqualByComparingTo(BigDecimal.valueOf(150.0));

        // A fatura de Abril deve estar ABERTA e ser pós-fechamento
        assertThat(faturaAbril.status()).isEqualTo(StatusFatura.ABERTA);
        // O valor deve ser R$ 300 (da compra 2)
        assertThat(faturaAbril.valorTotal()).isEqualByComparingTo(BigDecimal.valueOf(300.0));
    }
}
