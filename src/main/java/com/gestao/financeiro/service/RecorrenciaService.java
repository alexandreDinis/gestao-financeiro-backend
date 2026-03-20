package com.gestao.financeiro.service;

import com.gestao.financeiro.entity.Recorrencia;
import com.gestao.financeiro.entity.Transacao;
import com.gestao.financeiro.entity.enums.StatusRecorrencia;
import com.gestao.financeiro.entity.enums.StatusTransacao;
import com.gestao.financeiro.entity.enums.TipoTransacao;
import com.gestao.financeiro.repository.RecorrenciaRepository;
import com.gestao.financeiro.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecorrenciaService {

    private final RecorrenciaRepository recorrenciaRepository;
    private final TransacaoRepository transacaoRepository;
    private static final ZoneId TIMEZONE = ZoneId.of("America/Sao_Paulo");

    @Transactional
    public void processarRecorrenciasAgendadas() {
        log.info("Iniciando processamento de recorrências agendadas...");
        List<Recorrencia> ativas = recorrenciaRepository.findByStatus(StatusRecorrencia.ATIVA);
        
        LocalDate hojePelaTimezone = LocalDate.now(TIMEZONE);
        YearMonth referenciaAtual = YearMonth.from(hojePelaTimezone);

        for (Recorrencia rec : ativas) {
            try {
                processarGencaoSeNecessario(rec, hojePelaTimezone, referenciaAtual);
            } catch (Exception e) {
                log.error("Erro ao processar recorrência {}: {}", rec.getId(), e.getMessage());
            }
        }
    }

    private void processarGencaoSeNecessario(Recorrencia rec, LocalDate hoje, YearMonth referencia) {
        // Validação de período
        if (hoje.isBefore(rec.getDataInicio())) return;
        if (rec.getDataFim() != null && hoje.isAfter(rec.getDataFim())) return;

        // Idempotência: verificar se já existe para este mês/referência
        if (transacaoRepository.existsByRecorrenciaIdAndReferencia(rec.getId(), referencia)) {
            return;
        }

        // Determinar dia de vencimento (respeitando fim de mês curto)
        int diaVencimentoEfetivo = Math.min(rec.getDiaVencimento(), referencia.lengthOfMonth());
        LocalDate dataVencimento = referencia.atDay(diaVencimentoEfetivo);

        // Criar transação
        Transacao transacao = Transacao.builder()
                .descricao(rec.getDescricao())
                .valor(rec.getValorPrevisto())
                .data(hoje) // Data da geração
                .dataVencimento(dataVencimento)
                .tipo(TipoTransacao.DESPESA) // Atualmente focado em despesas
                .tipoDespesa(rec.getTipo().toTipoDespesa())
                .status(StatusTransacao.PENDENTE)
                .categoria(rec.getCategoria())
                .geradoAutomaticamente(true)
                .recorrenciaId(rec.getId())
                .referencia(referencia)
                .build();
        
        // Atribuir tenant do criador
        transacao.setTenantId(rec.getTenantId());

        try {
            transacaoRepository.save(transacao);
            log.info("Gerada transação de recorrência {} para referência {}", rec.getId(), referencia);
        } catch (DataIntegrityViolationException e) {
            log.warn("Tentativa de duplicidade ignorada para recorrência {}/{}", rec.getId(), referencia);
        }
    }
}
