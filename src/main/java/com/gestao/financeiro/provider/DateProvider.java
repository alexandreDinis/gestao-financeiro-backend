package com.gestao.financeiro.provider;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Provedor de datas para facilitar a injeção e o mock de datas durante os testes (Time Travel).
 */
@Component
public class DateProvider {
    public LocalDate now() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }
}
