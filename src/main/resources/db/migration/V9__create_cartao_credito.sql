-- V9__create_cartao_credito.sql

CREATE TABLE IF NOT EXISTS cartao_credito (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    conta_id BIGINT NOT NULL REFERENCES conta(id),
    bandeira VARCHAR(100) NOT NULL,
    limite NUMERIC(19, 2) NOT NULL,
    dia_fechamento INTEGER NOT NULL,
    dia_vencimento INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by BIGINT,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_cartao_tenant ON cartao_credito(tenant_id);
