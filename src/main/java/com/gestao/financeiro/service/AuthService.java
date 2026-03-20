package com.gestao.financeiro.service;

import com.gestao.financeiro.dto.request.LoginRequest;
import com.gestao.financeiro.dto.request.RegisterRequest;
import com.gestao.financeiro.dto.response.AuthResponse;
import com.gestao.financeiro.entity.Assinatura;
import com.gestao.financeiro.entity.Plano;
import com.gestao.financeiro.entity.Tenant;
import com.gestao.financeiro.entity.Usuario;
import com.gestao.financeiro.entity.enums.RoleUsuario;
import com.gestao.financeiro.entity.enums.StatusAssinatura;
import com.gestao.financeiro.entity.enums.StatusTenant;
import com.gestao.financeiro.entity.enums.TipoPlano;
import com.gestao.financeiro.exception.BusinessException;
import com.gestao.financeiro.mapper.UsuarioMapper;
import com.gestao.financeiro.repository.AssinaturaRepository;
import com.gestao.financeiro.repository.PlanoRepository;
import com.gestao.financeiro.repository.TenantRepository;
import com.gestao.financeiro.repository.UsuarioRepository;
import com.gestao.financeiro.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final PlanoRepository planoRepository;
    private final AssinaturaRepository assinaturaRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final UsuarioMapper usuarioMapper;

    @Transactional
    public AuthResponse registrar(RegisterRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new BusinessException("Este email já está em uso.");
        }

        // 1. Criar Tenant
        Tenant tenant = Tenant.builder()
                .nome(request.tenantNome())
                .status(StatusTenant.ATIVO)
                .build();
        tenant = tenantRepository.save(tenant);

        // 2. Criar Usuário Admin
        Usuario usuario = Usuario.builder()
                .nome(request.usuarioNome())
                .email(request.email())
                .senha(passwordEncoder.encode(request.senha()))
                .role(RoleUsuario.ADMIN_TENANT)
                .ativo(true)
                .build();
        usuario.setTenantId(tenant.getId());
        usuario = usuarioRepository.save(usuario);

        // 3. Vincular Plano Gratuito Inicial
        Plano planoGratuito = planoRepository.findByTipo(TipoPlano.GRATUITO)
                .orElseThrow(() -> new BusinessException("Plano GRATUITO não configurado base de dados."));

        Assinatura assinatura = Assinatura.builder()
                .tenant(tenant)
                .plano(planoGratuito)
                .dataInicio(LocalDate.now())
                .status(StatusAssinatura.ATIVA)
                .valorMensal(planoGratuito.getPrecoMensal())
                .build();
        // A entidade Assinatura herda de TenantEntity e tem @JoinColumn tenant_id custom injetado,
        // mas é insertable=false, logo definimos o ID em TenantEntity 
        assinatura.setTenantId(tenant.getId());
        assinaturaRepository.save(assinatura);

        log.info("Novo Tenant [{}] e Usuário [{}] criados com plano GRATUITO.", tenant.getNome(), usuario.getEmail());

        // Retornar JWT
        String token = tokenProvider.generateToken(usuario);
        return new AuthResponse(token, "Bearer", usuarioMapper.toResponse(usuario));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("Email ou senha inválidos"));

        if (!passwordEncoder.matches(request.senha(), usuario.getSenha())) {
            throw new BusinessException("Email ou senha inválidos");
        }

        if (!usuario.getAtivo()) {
            throw new BusinessException("Usuário inativo");
        }

        Tenant tenant = tenantRepository.findById(usuario.getTenantId())
                .orElseThrow(() -> new BusinessException("Tenant associado não encontrado"));

        if (tenant.getStatus() != StatusTenant.ATIVO) {
            throw new BusinessException("A conta (" + tenant.getNome() + ") não está ativa. Status: " + tenant.getStatus());
        }

        String token = tokenProvider.generateToken(usuario);
        return new AuthResponse(token, "Bearer", usuarioMapper.toResponse(usuario));
    }

    @Transactional(readOnly = true)
    public com.gestao.financeiro.dto.response.UsuarioResponse me() {
        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException("Usuário não autenticado");
        }

        com.gestao.financeiro.security.AuthPrincipal principal = (com.gestao.financeiro.security.AuthPrincipal) authentication.getPrincipal();

        Usuario usuario = usuarioRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException("Usuário não encontrado"));

        return usuarioMapper.toResponse(usuario);
    }
}
