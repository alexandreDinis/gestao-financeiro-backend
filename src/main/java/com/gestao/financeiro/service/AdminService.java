package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.TenantCreateRequest;
import com.gestao.financeiro.dto.response.TenantAdminResponse;
import com.gestao.financeiro.entity.Assinatura;
import com.gestao.financeiro.entity.Plano;
import com.gestao.financeiro.entity.Tenant;
import com.gestao.financeiro.entity.Usuario;
import com.gestao.financeiro.entity.enums.StatusTenant;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.repository.AssinaturaRepository;
import com.gestao.financeiro.repository.PlanoRepository;
import com.gestao.financeiro.repository.TenantRepository;
import com.gestao.financeiro.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final TenantRepository tenantRepository;
    private final AssinaturaRepository assinaturaRepository;
    private final PlanoRepository planoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

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
                    tenant.getCreatedAt(),
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

    @Transactional
    public TenantAdminResponse criarTenant(TenantCreateRequest request) {
        // Validações
        if (tenantRepository.findBySubdominio(request.getSubdominio()).isPresent()) {
            throw new BusinessException("Subdomínio já está em uso");
        }
        if (usuarioRepository.existsByEmail(request.getAdminEmail())) {
            throw new BusinessException("Email já cadastrado para outro usuário");
        }

        Plano plano = planoRepository.findById(request.getPlanoId())
                .orElseThrow(() -> new BusinessException("Plano não encontrado"));

        // 1. Criar Tenant
        Tenant tenant = Tenant.builder()
                .nome(request.getTenantNome())
                .subdominio(request.getSubdominio())
                .status(StatusTenant.ATIVO)
                .build();
        tenant = tenantRepository.save(tenant);

        // 2. Criar Assinatura
        Assinatura assinatura = Assinatura.builder()
                .tenant(tenant)
                .plano(plano)
                .dataInicio(LocalDate.now())
                .status(com.gestao.financeiro.entity.enums.StatusAssinatura.ATIVA)
                .valorMensal(plano.getPrecoMensal())
                .build();
        assinatura.setTenantId(tenant.getId()); // Preenche o ID pro TenantEntity base
        assinaturaRepository.save(assinatura);

        // 3. Criar Usuário Admin do Tenant
        Usuario adminUser = Usuario.builder()
                .nome(request.getAdminNome())
                .email(request.getAdminEmail())
                .senha(passwordEncoder.encode(request.getAdminSenha()))
                .role(com.gestao.financeiro.entity.enums.RoleUsuario.ADMIN_TENANT)
                .ativo(true)
                .build();
        adminUser.setTenantId(tenant.getId());
        usuarioRepository.save(adminUser);

        return new TenantAdminResponse(
                tenant.getId(),
                tenant.getNome(),
                tenant.getSubdominio(),
                tenant.getStatus().name(),
                tenant.getCreatedAt(),
                plano.getNome(),
                assinatura.getValorMensal()
        );
    }
}
