package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.response.TenantAdminResponse;
import com.gestao.financeiro.entity.Assinatura;
import com.gestao.financeiro.entity.Tenant;
import com.gestao.financeiro.entity.enums.StatusTenant;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.repository.AssinaturaRepository;
import com.gestao.financeiro.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final TenantRepository tenantRepository;
    private final AssinaturaRepository assinaturaRepository;

    @Transactional(readOnly = true)
    public Page<TenantAdminResponse> listarTenants(Pageable pageable) {
        return tenantRepository.findAll(pageable).map(tenant -> {
            Assinatura assinatura = assinaturaRepository.findByTenantId(tenant.getId()).orElse(null);
            
            String plano = assinatura != null ? assinatura.getPlano().getNome() : "Sem Plano";
            BigDecimal mrr = assinatura != null ? assinatura.getValorMensal() : BigDecimal.ZERO;
            
            return new TenantAdminResponse(
                    tenant.getId(),
                    tenant.getNome(),
                    tenant.getSubdominio(),
                    tenant.getStatus().name(),
                    tenant.getCriadoEm(),
                    plano,
                    mrr
            );
        });
    }

    @Transactional
    public void alternarStatusTenant(Long tenantId, StatusTenant status) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado"));
        
        tenant.setStatus(status);
        tenantRepository.save(tenant);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardMetrics() {
        long totalTenants = tenantRepository.count();
        long tenantsAtivos = tenantRepository.findAll().stream().filter(t -> t.getStatus() == StatusTenant.ATIVO).count();
        
        // Calcular MRR Total
        BigDecimal mrr = assinaturaRepository.findAll().stream()
                .filter(a -> a.getTenant().getStatus() == StatusTenant.ATIVO)
                .map(Assinatura::getValorMensal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalTenants", totalTenants);
        metrics.put("tenantsAtivos", tenantsAtivos);
        metrics.put("mrrTotal", mrr);

        return metrics;
    }
}
