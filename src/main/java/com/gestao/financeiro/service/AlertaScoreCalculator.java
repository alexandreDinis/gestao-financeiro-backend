package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.response.DashboardResponse.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Responsável exclusivamente por gerar e pontuar alertas do dashboard.
 */
@Component
public class AlertaScoreCalculator {

    private static final BigDecimal SALDO_BAIXO_THRESHOLD    = new BigDecimal("100.00");
    private static final double     ORCAMENTO_AVISO_PERCENT  = 0.80;
    private static final double     CARTAO_AVISO_PERCENT     = 0.80;
    private static final double     CARTAO_CRITICO_PERCENT   = 0.90;

    public List<Alerta> calcular(
            List<SaldoConta>     saldos,
            List<ResumoOrcamento> orcamentos,
            List<ResumoMeta>     metas,
            ProximosVencimentos  vencimentos,
            List<ResumoCartao>   cartoes) {

        List<Alerta> alertas = new ArrayList<>();

        alertas.addAll(alertasSaldo(saldos, vencimentos));
        alertas.addAll(alertasOrcamento(orcamentos));
        alertas.addAll(alertasMetas(metas));
        alertas.addAll(alertasVencimentos(vencimentos));
        alertas.addAll(alertasCartao(cartoes));

        alertas.sort(Comparator.comparingInt(Alerta::score).reversed());

        return alertas;
    }

    private List<Alerta> alertasSaldo(List<SaldoConta> saldos, ProximosVencimentos vencimentos) {
        List<Alerta> alertas = new ArrayList<>();
        boolean temVencimento7Dias = !vencimentos.proximos7Dias().isEmpty();

        for (SaldoConta sc : saldos) {
            // Cartões de crédito naturalmente têm saldo "negativo" (dívida), não gerar alerta de saldo baixo/negativo para eles.
            if ("CARTAO_CREDITO".equals(sc.tipo())) {
                continue;
            }

            if (sc.saldo().compareTo(BigDecimal.ZERO) < 0) {
                alertas.add(new Alerta(
                        TipoAlerta.SALDO_NEGATIVO,
                        "Saldo negativo na conta \"" + sc.nome() + "\": R$ " + sc.saldo(),
                        NivelAlerta.CRITICO,
                        95
                ));
                continue;
            }

            boolean saldoBaixo = sc.saldo().compareTo(SALDO_BAIXO_THRESHOLD) < 0;

            if (saldoBaixo && temVencimento7Dias) {
                alertas.add(new Alerta(
                        TipoAlerta.RISCO_COMBINADO,
                        "Saldo baixo (R$ " + sc.saldo() + ") na conta \"" + sc.nome()
                                + "\" com vencimentos nos próximos 7 dias",
                        NivelAlerta.CRITICO,
                        85
                ));
            } else if (saldoBaixo) {
                alertas.add(new Alerta(
                        TipoAlerta.SALDO_BAIXO,
                        "Saldo baixo na conta \"" + sc.nome() + "\": R$ " + sc.saldo(),
                        NivelAlerta.AVISO,
                        40
                ));
            }
        }
        return alertas;
    }

    private List<Alerta> alertasOrcamento(List<ResumoOrcamento> orcamentos) {
        List<Alerta> alertas = new ArrayList<>();
        for (ResumoOrcamento o : orcamentos) {
            double pct = o.percentualUtilizado() / 100.0;
            if (o.estourado()) {
                alertas.add(new Alerta(
                        TipoAlerta.ORCAMENTO_ESTOURADO,
                        "Orçamento de \"" + o.categoria() + "\" estourado ("
                                + formatPct(o.percentualUtilizado()) + " utilizado)",
                        NivelAlerta.CRITICO,
                        90
                ));
            } else if (pct >= ORCAMENTO_AVISO_PERCENT) {
                alertas.add(new Alerta(
                        TipoAlerta.ORCAMENTO_PROXIMO_LIMITE,
                        "Orçamento de \"" + o.categoria() + "\" em "
                                + formatPct(o.percentualUtilizado()) + " do limite",
                        NivelAlerta.AVISO,
                        55
                ));
            }
        }
        return alertas;
    }

    private List<Alerta> alertasMetas(List<ResumoMeta> metas) {
        List<Alerta> alertas = new ArrayList<>();
        for (ResumoMeta m : metas) {
            if (m.atrasada()) {
                alertas.add(new Alerta(
                        TipoAlerta.META_ATRASADA,
                        "Meta \"" + m.nome() + "\" está atrasada (prazo: " + m.prazo() + ")",
                        NivelAlerta.AVISO,
                        65
                ));
            }
        }
        return alertas;
    }

    private List<Alerta> alertasVencimentos(ProximosVencimentos vencimentos) {
        List<Alerta> alertas = new ArrayList<>();

        if (!vencimentos.proximos7Dias().isEmpty()) {
            alertas.add(new Alerta(
                    TipoAlerta.VENCIMENTO_PROXIMO,
                    vencimentos.proximos7Dias().size()
                            + " pagamento(s) vencem nos próximos 7 dias "
                            + "(total: R$ " + vencimentos.totalVencer7Dias() + ")",
                    NivelAlerta.AVISO,
                    75
            ));
        } else if (!vencimentos.proximos15Dias().isEmpty()) {
            int qtd = vencimentos.proximos15Dias().size() - vencimentos.proximos7Dias().size();
            if (qtd > 0) {
                alertas.add(new Alerta(
                        TipoAlerta.VENCIMENTO_PROXIMO,
                        qtd + " pagamento(s) vencem entre 8 e 15 dias",
                        NivelAlerta.INFO,
                        20
                ));
            }
        }

        return alertas;
    }

    private List<Alerta> alertasCartao(List<ResumoCartao> cartoes) {
        List<Alerta> alertas = new ArrayList<>();
        LocalDate hoje = LocalDate.now();

        for (ResumoCartao c : cartoes) {
            // 1. Alertas de Vencimento de Fatura
            if (c.proximoVencimento() != null) {
                long diasParaVencer = ChronoUnit.DAYS.between(hoje, c.proximoVencimento());

                if (diasParaVencer == 0) {
                    alertas.add(new Alerta(
                            TipoAlerta.VENCIMENTO_PROXIMO,
                            "Fatura do cartão \"" + c.nome() + "\" vence HOJE!",
                            NivelAlerta.CRITICO,
                            98
                    ));
                } else if (diasParaVencer > 0 && diasParaVencer <= 5) {
                    alertas.add(new Alerta(
                            TipoAlerta.VENCIMENTO_PROXIMO,
                            "Fatura do cartão \"" + c.nome() + "\" vence em " + diasParaVencer + " dias",
                            NivelAlerta.AVISO,
                            80
                    ));
                }
            }

            // 2. Alertas de Limite Utilizado
            double pct = c.percentualUtilizado() / 100.0;
            if (pct >= CARTAO_CRITICO_PERCENT) {
                alertas.add(new Alerta(
                        TipoAlerta.CARTAO_LIMITE_ALTO,
                        "Cartão \"" + c.nome() + "\" com " + formatPct(c.percentualUtilizado())
                                + " do limite utilizado — risco de bloqueio",
                        NivelAlerta.CRITICO,
                        70
                ));
            } else if (pct >= CARTAO_AVISO_PERCENT) {
                alertas.add(new Alerta(
                        TipoAlerta.CARTAO_LIMITE_ALTO,
                        "Cartão \"" + c.nome() + "\" com " + formatPct(c.percentualUtilizado())
                                + " do limite utilizado",
                        NivelAlerta.AVISO,
                        30
                ));
            }
        }
        return alertas;
    }

    private String formatPct(double pct) {
        return String.format("%.0f%%", pct);
    }
}
