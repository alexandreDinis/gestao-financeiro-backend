package com.gestao.financeiro.config;

import com.gestao.financeiro.entity.Usuario;
import com.gestao.financeiro.entity.enums.RoleUsuario;
import com.gestao.financeiro.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicializa dados padrão no primeiro boot do sistema.
 *
 * SUPER_ADMIN (SaaS): superadmin@financeiro.com / admin123
 *   - Criado automaticamente se não existir.
 *   - Acessa /api/admin (dashboard, gerenciar tenants).
 *
 * ADMIN_TENANT (Tenant): admin@financeiro.com / admin123
 *   - Já existe via migration V2, mas sem senha.
 *   - Senha definida no primeiro boot; se já tiver, mantém.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final String SUPER_ADMIN_EMAIL = "superadmin@financeiro.com";
    private static final String TENANT_ADMIN_EMAIL = "admin@financeiro.com";
    private static final String DEFAULT_PASSWORD = "admin123";
    private static final Long DEFAULT_TENANT_ID = 1L;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        ensureSuperAdmin();
        ensureTenantAdminPassword();
    }

    /**
     * Cria o SUPER_ADMIN do SaaS se não existir.
     * Se já existir e já tiver senha, mantém.
     */
    private void ensureSuperAdmin() {
        if (usuarioRepository.existsByEmail(SUPER_ADMIN_EMAIL)) {
            usuarioRepository.findByEmail(SUPER_ADMIN_EMAIL).ifPresent(this::ensurePassword);
            return;
        }

        Usuario superAdmin = Usuario.builder()
                .nome("Super Admin")
                .email(SUPER_ADMIN_EMAIL)
                .senha(passwordEncoder.encode(DEFAULT_PASSWORD))
                .role(RoleUsuario.SUPER_ADMIN)
                .ativo(true)
                .build();
        superAdmin.setTenantId(DEFAULT_TENANT_ID);
        usuarioRepository.save(superAdmin);

        log.info("SUPER_ADMIN criado: email=[{}] senha=[{}]", SUPER_ADMIN_EMAIL, DEFAULT_PASSWORD);
    }

    /**
     * Define senha padrão para o admin do tenant se ele não tiver uma.
     */
    private void ensureTenantAdminPassword() {
        usuarioRepository.findByEmail(TENANT_ADMIN_EMAIL).ifPresent(this::ensurePassword);
    }

    private void ensurePassword(Usuario usuario) {
        if (usuario.getSenha() == null || usuario.getSenha().isBlank()) {
            usuario.setSenha(passwordEncoder.encode(DEFAULT_PASSWORD));
            usuarioRepository.save(usuario);
            log.info("Senha padrão definida para [{}] (role={}).", usuario.getEmail(), usuario.getRole());
        }
    }
}
