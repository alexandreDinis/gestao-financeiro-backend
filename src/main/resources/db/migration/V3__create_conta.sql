-- V3__create_conta.sql

CREATE TABLE IF NOT EXISTS conta (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    nome VARCHAR(255) NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    saldo_inicial NUMERIC(19, 2) NOT NULL DEFAULT 0,
    cor VARCHAR(20),
    icone VARCHAR(50),
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by BIGINT,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_conta_tenant ON conta(tenant_id);
