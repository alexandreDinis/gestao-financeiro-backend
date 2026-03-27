package com.gestao.financeiro.config;

import com.gestao.financeiro.security.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TenantFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        Long tenantId = null;

        // Tenta pegar do JWT
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            tenantId = principal.getTenantId();
        } 
        // Fallback pro header (útil pra testar no swagger provisoriamente ou se o endpoint permitir bypass)
        else {
            String tenantIdStr = request.getHeader(TENANT_HEADER);
            if (tenantIdStr != null && !tenantIdStr.isBlank()) {
                try {
                    tenantId = Long.parseLong(tenantIdStr);
                } catch (NumberFormatException ignored) {}
            }
        }
        
        // MVP: fallback implícito para tenant=1 nas rotas públicas sem auth/header
        if (tenantId == null) {
            tenantId = 1L; 
        }

        TenantContext.setTenantId(tenantId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
