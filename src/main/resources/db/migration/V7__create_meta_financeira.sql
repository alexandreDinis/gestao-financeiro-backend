-- V7__create_meta_financeira.sql

CREATE TABLE IF NOT EXISTS meta_financeira (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    nome VARCHAR(255) NOT NULL,
    valor_alvo NUMERIC(19, 2) NOT NULL,
    valor_atual NUMERIC(19, 2) NOT NULL DEFAULT 0,
    prazo DATE,
    descricao TEXT,
    concluida BOOLEAN NOT NULL DEFAULT FALSE,
    usuario_id BIGINT REFERENCES usuario(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by BIGINT,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_meta_tenant ON meta_financeira(tenant_id);
