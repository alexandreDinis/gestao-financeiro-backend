-- V1__create_tenant_default.sql
-- Cria tabela tenant e insere tenant padrão (id=1) para desenvolvimento

CREATE TABLE IF NOT EXISTS tenant (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    subdominio VARCHAR(100) UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'ATIVO',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by BIGINT,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- Tenant padrão para desenvolvimento (MVP sem auth)
INSERT INTO tenant (nome, subdominio, status) VALUES ('Tenant Padrão', 'default', 'ATIVO');
