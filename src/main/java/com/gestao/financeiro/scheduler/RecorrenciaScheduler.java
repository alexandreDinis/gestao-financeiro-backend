package com.gestao.financeiro.scheduler;

import com.gestao.financeiro.service.RecorrenciaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecorrenciaScheduler {

    private final RecorrenciaService recorrenciaService;

    // Executa todo dia às 02:00 AM
    @Scheduled(cron = "0 0 2 * * ?")
    public void runAutomaticRecurrences() {
        log.info("Iniciando Job agendado de Recorrências...");
        recorrenciaService.processarRecorrenciasAgendadas();
        log.info("Job de Recorrências finalizado.");
    }
}
