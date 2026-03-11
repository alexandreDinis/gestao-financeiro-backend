-- V2__create_usuario.sql

CREATE TABLE IF NOT EXISTS usuario (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    senha VARCHAR(255),
    role VARCHAR(50) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by BIGINT,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_usuario_tenant ON usuario(tenant_id);
CREATE INDEX idx_usuario_email ON usuario(email);

-- Usuário admin padrão para desenvolvimento
INSERT INTO usuario (tenant_id, nome, email, role, ativo)
VALUES (1, 'Admin', 'admin@financeiro.com', 'ADMIN_TENANT', TRUE);
