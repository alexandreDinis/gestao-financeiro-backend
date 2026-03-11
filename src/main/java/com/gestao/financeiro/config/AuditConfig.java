package com.gestao.financeiro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

/**
 * Configuração de auditoria JPA.
 * Fornece o ID do usuário atual para @CreatedBy.
 *
 * MVP: retorna null (sem auth).
 * Fase 5: extrair do SecurityContext.
 */
@Configuration
public class AuditConfig {

    @Bean
    public AuditorAware<Long> auditorProvider() {
        // MVP: sem autenticação, retorna empty
        // Fase 5: SecurityContextHolder.getContext().getAuthentication().getPrincipal().getUserId()
        return () -> Optional.empty();
    }
}
