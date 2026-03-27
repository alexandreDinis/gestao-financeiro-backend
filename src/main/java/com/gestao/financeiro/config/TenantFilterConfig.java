package com.gestao.financeiro.config;

import jakarta.persistence.EntityManager;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filtro que ativa o Hibernate Filter de tenant em cada request.
 *
 * MVP (sem JWT): usa tenant_id fixo = 1.
 * Fase 5 (com JWT): extrair tenantId do SecurityContext.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class TenantFilterConfig implements Filter {

    private final EntityManager entityManager;

    // MVP: tenant fixo. Na Fase 5, extrair do JWT/SecurityContext.
    private static final Long DEFAULT_TENANT_ID = 1L;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // Ignorar H2 Console e Swagger
        String path = httpRequest.getRequestURI();
        if (path.startsWith("/h2-console") || path.startsWith("/swagger") || path.startsWith("/v3/api-docs")) {
            chain.doFilter(request, response);
            return;
        }

        Long tenantId = TenantContext.getTenantId();

        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        }

        log.debug("[tenant={}] Request: {} {}", tenantId, httpRequest.getMethod(), path);

        chain.doFilter(request, response);
    }
}
