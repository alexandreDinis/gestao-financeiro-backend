package com.gestao.financeiro.service;

import com.gestao.financeiro.config.TenantContext;
import com.gestao.financeiro.dto.response.RelatorioPrevisaoResponse;
import com.gestao.financeiro.dto.response.PrevisaoMesResponse;
import com.gestao.financeiro.entity.*;
import com.gestao.financeiro.entity.enums.*;
import com.gestao.financeiro.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PrevisaoCaixaServiceTest {

    @Autowired private PrevisaoCaixaService previsaoCaixaService;
    @Autowired private ContaRepository contaRepository;
    @Autowired private CartaoCreditoRepository cartaoCreditoRepository;
    @Autowired private FaturaCartaoRepository faturaCartaoRepository;
    @Autowired private TransacaoRepository transacaoRepository;
    @Autowired private CategoriaRepository categoriaRepository;

    private Long tenantId = 1L;
    private Conta contaCorrente;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantId);
        
        contaCorrente = Conta.builder()
                .nome("Conta Corrente Teste")
                .tipo(TipoConta.CORRENTE)
                .saldoInicial(BigDecimal.valueOf(1000))
                .ativa(true)
                .build();
        contaCorrente.setTenantId(tenantId);
        contaCorrente = contaRepository.saveAndFlush(contaCorrente);
    }

    @Test
    void encadeamentoSaldoEntreMesesDeveEstarCorreto() {
        RelatorioPrevisaoResponse relatorio = previsaoCaixaService.gerarPrevisao(3);
        
        List<PrevisaoMesResponse> meses = relatorio.meses();
        assertThat(meses).hasSize(3);
        
        // Mês 1 inicial deve ser o saldo inicial real (aqui = 1000)
        assertThat(meses.get(0).saldoInicial()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        
        // Mês 2 inicial == Mês 1 final
        assertThat(meses.get(1).saldoInicial()).isEqualByComparingTo(meses.get(0).saldoFinal());
        
        // Mês 3 inicial == Mês 2 final
        assertThat(meses.get(2).saldoInicial()).isEqualByComparingTo(meses.get(1).saldoFinal());
    }

    @Test
    void faturaCartaoPagaNaoDeveSerDuplamenteDeduzida() {
        // Criar Cartão
        Conta contaCartao = Conta.builder().nome("Cartao").tipo(TipoConta.CARTAO_CREDITO).saldoInicial(BigDecimal.ZERO).ativa(true).build();
        contaCartao.setTenantId(tenantId);
        contaCartao = contaRepository.saveAndFlush(contaCartao);

        CartaoCredito cartao = CartaoCredito.builder().conta(contaCartao).bandeira("Visa").limite(BigDecimal.valueOf(5000)).diaFechamento(1).diaVencimento(10).build();
        cartao.setTenantId(tenantId);
        cartao = cartaoCreditoRepository.saveAndFlush(cartao);

        // Criar Fatura deste mês
        YearMonth mesAtual = YearMonth.now();
        FaturaCartao fatura = FaturaCartao.builder()
                .cartao(cartao)
                .mesReferencia(mesAtual.getMonthValue())
                .anoReferencia(mesAtual.getYear())
                .dataVencimento(LocalDate.now().withDayOfMonth(10))
                .status(StatusFatura.PAGA) // FATURA JÁ PAGA
                .build();
        fatura.setTenantId(tenantId);
        fatura = faturaCartaoRepository.saveAndFlush(fatura);

        // Criar Transação de Compra e Parcela associada
        Categoria cat = Categoria.builder().nome("Teste").tipo(TipoCategoria.DESPESA).build();
        cat.setTenantId(tenantId);
        cat = categoriaRepository.saveAndFlush(cat);
        Transacao t = Transacao.builder().descricao("Compra").valor(BigDecimal.valueOf(200)).data(LocalDate.now()).tipo(TipoTransacao.DESPESA).status(StatusTransacao.PENDENTE).categoria(cat).build();
        t.setTenantId(tenantId);

        Parcela p = Parcela.builder().transacao(t).fatura(fatura).numeroParcela(1).totalParcelas(1).valorParcela(BigDecimal.valueOf(200)).dataVencimento(fatura.getDataVencimento()).paga(true).build(); // PARCELA JÁ PAGA
        fatura.adicionarParcela(p);
        transacaoRepository.saveAndFlush(t);

        // Simular que o pagamento foi feito hoje (Transferência debitando da CC)
        Transacao pgto = Transacao.builder().descricao("Pgto").valor(BigDecimal.valueOf(200)).data(LocalDate.now()).tipo(TipoTransacao.TRANSFERENCIA).status(StatusTransacao.PAGO).build();
        pgto.setTenantId(tenantId);
        Lancamento debito = Lancamento.builder().conta(contaCorrente).direcao(DirecaoLancamento.DEBITO).valor(BigDecimal.valueOf(200)).build();
        pgto.addLancamento(debito);
        transacaoRepository.saveAndFlush(pgto);

        RelatorioPrevisaoResponse relatorio = previsaoCaixaService.gerarPrevisao(1);
        PrevisaoMesResponse mes = relatorio.meses().get(0);

        // Saldo inicial real deve ser 1000 - 200 = 800
        assertThat(mes.saldoInicial()).isEqualByComparingTo(BigDecimal.valueOf(800));
        // Despesas fixas (que incluiria o cartão se houvesse bug) deve ser 0
        assertThat(mes.despesasFixas()).isEqualByComparingTo(BigDecimal.ZERO);
        // Saldo final deve ser 800
        assertThat(mes.saldoFinal()).isEqualByComparingTo(BigDecimal.valueOf(800));
    }

    @Test
    void saldoInicialDaProjecaoDeveSerIgualAoSaldoAtualDasContas() {
        RelatorioPrevisaoResponse relatorio = previsaoCaixaService.gerarPrevisao(1);
        BigDecimal saldoRealBanco = contaRepository.calcularSaldo(contaCorrente.getId());
        if (saldoRealBanco == null) saldoRealBanco = contaCorrente.getSaldoInicial();

        assertThat(relatorio.saldoAtual()).isEqualByComparingTo(saldoRealBanco);
        assertThat(relatorio.meses().get(0).saldoInicial()).isEqualByComparingTo(saldoRealBanco);
    }

    @Test
    void receitaRecorrentePagaNaoDeveSerDuplamenteSomada() {
        // Criar categoria
        Categoria cat = Categoria.builder().nome("Salário").tipo(TipoCategoria.RECEITA).build();
        cat.setTenantId(tenantId);
        cat = categoriaRepository.saveAndFlush(cat);

        // Transacao recorrente (gerada mas paga)
        Transacao pgto = Transacao.builder().descricao("Salário Mês").valor(BigDecimal.valueOf(5000)).data(LocalDate.now()).tipo(TipoTransacao.RECEITA).status(StatusTransacao.PAGO).categoria(cat).recorrenciaId(999L).referencia(YearMonth.now()).build();
        pgto.setTenantId(tenantId);
        Lancamento credito = Lancamento.builder().conta(contaCorrente).direcao(DirecaoLancamento.CREDITO).valor(BigDecimal.valueOf(5000)).build();
        pgto.addLancamento(credito);
        transacaoRepository.saveAndFlush(pgto);

        RelatorioPrevisaoResponse relatorio = previsaoCaixaService.gerarPrevisao(1);
        PrevisaoMesResponse mes = relatorio.meses().get(0);

        // O saldo inicial tem os 1000 da conta + 5000 do salário = 6000
        assertThat(mes.saldoInicial()).isEqualByComparingTo(BigDecimal.valueOf(6000));
        
        // A receita não deve aparecer pendente (receitasFixas deve ser 0)
        assertThat(mes.receitasFixas()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void estimativaVariavelComMenosDeTresMesesDeHistorico() {
        Categoria cat = Categoria.builder().nome("Lanche").tipo(TipoCategoria.DESPESA).build();
        cat.setTenantId(tenantId);
        cat = categoriaRepository.saveAndFlush(cat);

        // Criar uma despesa variável no mês passado (único histórico)
        Transacao t = Transacao.builder().descricao("Hamburguer").valor(BigDecimal.valueOf(60)).data(LocalDate.now().minusMonths(1).withDayOfMonth(15)).tipo(TipoTransacao.DESPESA).tipoDespesa(TipoDespesa.VARIAVEL).status(StatusTransacao.PAGO).categoria(cat).build();
        t.setTenantId(tenantId);
        transacaoRepository.saveAndFlush(t);

        RelatorioPrevisaoResponse relatorio = previsaoCaixaService.gerarPrevisao(1);
        PrevisaoMesResponse mes = relatorio.meses().get(0);

        // Como foi a única no mês passado (e os meses -2 e -3 estão vazios), 
        // e o código faz a divisão por 3 (mesesReais = 3, independentemente de haver transação).
        // Wait, 60 / 3 = 20.00
        assertThat(mes.estimativaVariavel().valor()).isEqualByComparingTo(BigDecimal.valueOf(20.00));
        assertThat(mes.estimativaVariavel().mesesConsiderados()).isEqualTo(3);
    }
}
